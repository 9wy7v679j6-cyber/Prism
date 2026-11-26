package com.prism.docs.core.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.prism.docs.core.data.model.DocumentCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ThumbnailRequest(
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val category: DocumentCategory,
  val lastModifiedEpochMs: Long,
  val sizeBytes: Long,
  val targetSizePx: Int = DEFAULT_TARGET_SIZE_PX,
) {
  val cacheKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ThumbnailCacheKey.compute(
      pathOrUri = uriString,
      lastModifiedEpochMs = lastModifiedEpochMs,
      sizeBytes = sizeBytes,
      targetSizePx = targetSizePx,
    )
  }

  companion object {
    const val DEFAULT_TARGET_SIZE_PX: Int = 320
  }
}

data class ThumbnailCacheStats(
  val memoryEntryCount: Int,
  val memorySizeBytes: Long,
  val diskEntryCount: Int,
  val diskSizeBytes: Long,
  val maxDiskSizeBytes: Long,
)

object ThumbnailCacheKey {
  private val HEX_CHARS = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
  )

  fun compute(
    pathOrUri: String,
    lastModifiedEpochMs: Long,
    sizeBytes: Long,
    targetSizePx: Int = ThumbnailRequest.DEFAULT_TARGET_SIZE_PX,
  ): String {
    val raw = "$pathOrUri|$lastModifiedEpochMs|$sizeBytes|$targetSizePx"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    val chars = CharArray(digest.size * 2)
    for (i in digest.indices) {
      val v = digest[i].toInt() and 0xFF
      chars[i * 2] = HEX_CHARS[v ushr 4]
      chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(chars)
  }
}

/**
 * On-device two-tier thumbnail pipeline (bounded memory LruCache + bounded disk LRU cache)
 * keyed strictly by path/URI + modified timestamp + file size.
 *
 * Concurrency & format guarantees:
 * - Serializes [PdfRenderer] access per document via a per-document [Mutex] so only one page
 *   is ever open at a time (PdfRenderer is not thread-safe).
 * - Extracts `docProps/thumbnail.jpeg` (or `.jpg`/`.png`) from OOXML files (`.docx`, `.xlsx`,
 *   `.pptx`) when embedded; returns null otherwise so the UI renders a crisp type-icon card.
 * - Supports Images (`ImageDecoder` API 28+ / sampled `BitmapFactory` API 26-27) and Videos
 *   (`MediaMetadataRetriever` scaled frame).
 * - Coalesces concurrent loads for identical cache keys and bounds global decode parallelism.
 */
@Singleton
class DocumentThumbnailPipeline
internal constructor(
  private val context: Context,
  private val diskCacheDir: File,
  private val maxDiskBytes: Long,
  maxMemoryCacheKb: Int,
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
  ) : this(
    context = context,
    diskCacheDir = File(context.cacheDir, DISK_CACHE_FOLDER),
    maxDiskBytes = DEFAULT_MAX_DISK_BYTES,
    maxMemoryCacheKb = defaultMemoryCacheKb(),
  )

  private val memoryCache =
    object : LruCache<String, Bitmap>(maxMemoryCacheKb.coerceAtLeast(1024)) {
      override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.allocationByteCount / 1024).coerceAtLeast(1)
      }
    }

  /** Tracks keys known to have no decodable thumbnail (e.g. OOXML without docProps/thumbnail). */
  private val missingThumbnailKeys = object : LruCache<String, Boolean>(2048) {}

  /** Fixed-size striped mutex table guaranteeing strictly serialized PdfRenderer access per document. */
  private val pdfDocumentLocks = Array(PDF_LOCK_STRIPES) { Mutex() }

  /** Prevents concurrent duplicate work for the exact same cache key. */
  private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

  /** Bounds concurrent native bitmap/PDF/video decoding to avoid OOM during rapid scrolling. */
  private val decodeSemaphore = Semaphore(permits = MAX_CONCURRENT_DECODES)

  /** Serializes disk cache eviction passes. */
  private val diskTrimMutex = Mutex()

  /** Approximate total bytes on disk (-1 = uninitialized, triggers initial scan on first write). */
  private val estimatedDiskBytes = AtomicLong(-1L)

  fun peekMemoryCache(request: ThumbnailRequest): Bitmap? = memoryCache.get(request.cacheKey)

  fun hasKnownMissingThumbnail(request: ThumbnailRequest): Boolean =
    missingThumbnailKeys.get(request.cacheKey) == true

  suspend fun loadThumbnail(request: ThumbnailRequest): Bitmap? {
    val key = request.cacheKey

    memoryCache.get(key)?.let { cached ->
      if (!cached.isRecycled) return cached
      memoryCache.remove(key)
    }

    if (missingThumbnailKeys.get(key) == true) {
      return null
    }

    // Coalesce concurrent requests for the exact same key, retrying once if an earlier owner cancels
    var attempts = 0
    while (attempts < 2) {
      attempts++
      val existingDeferred = inFlightRequests[key]
      if (existingDeferred != null) {
        return try {
          existingDeferred.await()
        } catch (ce: CancellationException) {
          currentCoroutineContext().ensureActive()
          continue
        }
      }

      val newDeferred = CompletableDeferred<Bitmap?>()
      val raced = inFlightRequests.putIfAbsent(key, newDeferred)
      if (raced != null) {
        return try {
          raced.await()
        } catch (ce: CancellationException) {
          currentCoroutineContext().ensureActive()
          continue
        }
      }

      return try {
        val bitmap =
          withContext(Dispatchers.IO) {
            readFromDiskCache(key)?.also { diskBitmap -> memoryCache.put(key, diskBitmap) }
              ?: decodeSemaphore.withPermit {
                currentCoroutineContext().ensureActive()
                decodeThumbnailForRequest(request)?.also { decoded ->
                  memoryCache.put(key, decoded)
                  writeToDiskCache(key, decoded)
                }
              }
          }
        if (bitmap == null) {
          missingThumbnailKeys.put(key, true)
        }
        newDeferred.complete(bitmap)
        bitmap
      } catch (ce: CancellationException) {
        inFlightRequests.remove(key, newDeferred)
        newDeferred.cancel(ce)
        throw ce
      } catch (_: Throwable) {
        newDeferred.complete(null)
        null
      } finally {
        inFlightRequests.remove(key, newDeferred)
      }
    }
    return null
  }

  suspend fun computeCacheStats(): ThumbnailCacheStats =
    withContext(Dispatchers.IO) {
      val files = diskCacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
      val totalDiskBytes = files.sumOf { it.length() }
      estimatedDiskBytes.set(totalDiskBytes)
      ThumbnailCacheStats(
        memoryEntryCount = memoryCache.snapshot().size,
        memorySizeBytes = memoryCache.size().toLong() * 1024L,
        diskEntryCount = files.size,
        diskSizeBytes = totalDiskBytes,
        maxDiskSizeBytes = maxDiskBytes,
      )
    }

  suspend fun clearCaches(): Long =
    withContext(Dispatchers.IO) {
      memoryCache.evictAll()
      missingThumbnailKeys.evictAll()
      var freedBytes = 0L
      diskTrimMutex.withLock {
        val files = diskCacheDir.listFiles().orEmpty()
        for (file in files) {
          val len = file.length()
          if (file.delete()) {
            freedBytes += len
          }
        }
        estimatedDiskBytes.set(0L)
      }
      freedBytes
    }

  internal suspend fun decodeThumbnailForRequest(request: ThumbnailRequest): Bitmap? {
    val ext = request.displayName.substringAfterLast('.', "").lowercase()
    return when {
      request.category == DocumentCategory.PDF || ext == "pdf" ->
        renderPdfFirstPageSerialized(request.uriString, request.targetSizePx)
      request.category == DocumentCategory.IMAGES ->
        decodeImageThumbnail(request.uriString, request.targetSizePx)
      request.category == DocumentCategory.VIDEOS ->
        decodeVideoFrameThumbnail(request.uriString, request.targetSizePx)
      ext in OOXML_EXTENSIONS ->
        extractOoxmlEmbeddedThumbnail(request.uriString, request.targetSizePx)
      else -> null
    }
  }

  /**
   * Serializes [PdfRenderer] access per document URI/path so only one page is open at any moment.
   */
  private suspend fun renderPdfFirstPageSerialized(
    uriString: String,
    targetSizePx: Int,
  ): Bitmap? {
    val stripe = (uriString.hashCode() and Int.MAX_VALUE) % pdfDocumentLocks.size
    val documentMutex = pdfDocumentLocks[stripe]
    return documentMutex.withLock {
      currentCoroutineContext().ensureActive()
      try {
        openReadOnlyPfd(uriString)?.use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount <= 0) return@use null
            renderer.openPage(0).use { page ->
              val srcW = page.width.coerceAtLeast(1)
              val srcH = page.height.coerceAtLeast(1)
              val scale = targetSizePx.toFloat() / maxOf(srcW, srcH).toFloat()
              val dstW = (srcW * scale).toInt().coerceIn(32, targetSizePx)
              val dstH = (srcH * scale).toInt().coerceIn(32, targetSizePx)

              val bitmap = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
              val canvas = Canvas(bitmap)
              canvas.drawColor(Color.WHITE)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              bitmap
            }
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Throwable) {
        null
      }
    }
  }

  private fun decodeImageThumbnail(uriString: String, targetSizePx: Int): Bitmap? {
    return runCatching {
        val localFile = resolveLocalFile(uriString)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val source =
            if (localFile != null) {
              ImageDecoder.createSource(localFile)
            } else {
              ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
            }
          ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val srcW = info.size.width.coerceAtLeast(1)
            val srcH = info.size.height.coerceAtLeast(1)
            val scale = minOf(1f, targetSizePx.toFloat() / maxOf(srcW, srcH).toFloat())
            decoder.setTargetSize(
              (srcW * scale).toInt().coerceAtLeast(1),
              (srcH * scale).toInt().coerceAtLeast(1),
            )
          }
        } else {
          decodeSampledBitmapFromStream(
            openStream = { openInputStream(uriString) },
            targetSizePx = targetSizePx,
          )
        }
      }
      .getOrNull()
  }

  private fun decodeVideoFrameThumbnail(uriString: String, targetSizePx: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
      val localFile = resolveLocalFile(uriString)
      if (localFile != null) {
        retriever.setDataSource(localFile.absolutePath)
      } else {
        retriever.setDataSource(context, Uri.parse(uriString))
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        retriever.getScaledFrameAtTime(
          1_000_000L,
          MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
          targetSizePx,
          targetSizePx,
        )
      } else {
        val raw = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        raw?.let { scaleDownIfNeeded(it, targetSizePx) }
      }
    } catch (_: Throwable) {
      null
    } finally {
      runCatching { retriever.release() }
    }
  }

  /**
   * Inspects OOXML ZIP packages (`.docx`, `.xlsx`, `.pptx`) for `docProps/thumbnail.jpeg`
   * (or `.jpg`/`.png`). Returns null when absent so the UI renders a type-icon card.
   */
  private fun extractOoxmlEmbeddedThumbnail(uriString: String, targetSizePx: Int): Bitmap? {
    return runCatching {
        val localFile = resolveLocalFile(uriString)
        if (localFile != null && localFile.exists()) {
          ZipFile(localFile).use { zipFile ->
            val directEntry =
              OOXML_THUMB_PATHS.firstNotNullOfOrNull { path -> zipFile.getEntry(path) }
                ?: run {
                  var scanned = 0
                  var matched: java.util.zip.ZipEntry? = null
                  val entries = zipFile.entries()
                  while (entries.hasMoreElements() && scanned < MAX_ZIP_ENTRIES_TO_SCAN) {
                    val e = entries.nextElement()
                    scanned++
                    val normalized = e.name.replace('\\', '/').lowercase()
                    if (normalized in OOXML_THUMB_PATHS_LOWER) {
                      matched = e
                      break
                    }
                  }
                  matched
                }
            if (directEntry != null) {
              val bytes = zipFile.getInputStream(directEntry).use { readBoundedBytes(it, MAX_EMBEDDED_THUMB_BYTES) }
              if (bytes.isNotEmpty()) return@use decodeSampledBitmapFromBytes(bytes, targetSizePx)
            }
            return@use null
          }
        }
        openInputStream(uriString)?.use { input ->
          ZipInputStream(input.buffered()).use { zip ->
            var scannedEntries = 0
            while (scannedEntries < MAX_ZIP_ENTRIES_TO_SCAN) {
              val entry = zip.nextEntry ?: break
              scannedEntries++
              val normalized = entry.name.replace('\\', '/').lowercase()
              if (normalized in OOXML_THUMB_PATHS_LOWER) {
                val bytes = readBoundedBytes(zip, MAX_EMBEDDED_THUMB_BYTES)
                if (bytes.isNotEmpty()) {
                  return@use decodeSampledBitmapFromBytes(bytes, targetSizePx)
                }
                return@use null
              }
            }
            null
          }
        }
      }
      .getOrNull()
  }

  private fun readFromDiskCache(cacheKey: String): Bitmap? {
    val file = File(diskCacheDir, "$cacheKey.thumb")
    if (!file.exists() || file.length() <= 0L) return null
    val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    if (decoded != null) {
      runCatching { file.setLastModified(System.currentTimeMillis()) }
    } else {
      val badLen = file.length()
      if (runCatching { file.delete() }.getOrDefault(false)) {
        estimatedDiskBytes.updateAndGet { cur -> if (cur >= badLen) cur - badLen else cur }
      }
    }
    return decoded
  }

  private suspend fun writeToDiskCache(cacheKey: String, bitmap: Bitmap) {
    var wroteDelta = 0L
    runCatching {
      if (!diskCacheDir.exists()) {
        diskCacheDir.mkdirs()
      }
      val finalFile = File(diskCacheDir, "$cacheKey.thumb")
      val prevLen = if (finalFile.exists()) finalFile.length() else 0L
      val tempFile = File(diskCacheDir, "$cacheKey.${System.nanoTime()}.tmp")
      FileOutputStream(tempFile).use { out ->
        val format =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
          } else {
            Bitmap.CompressFormat.JPEG
          }
        bitmap.compress(format, 82, out)
      }
      if (!tempFile.renameTo(finalFile)) {
        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()
      }
      wroteDelta = finalFile.length() - prevLen
    }
    val currentEstimate =
      estimatedDiskBytes.updateAndGet { cur ->
        if (cur < 0L) -1L else (cur + wroteDelta).coerceAtLeast(0L)
      }
    if (currentEstimate < 0L || currentEstimate > maxDiskBytes) {
      trimDiskCacheIfNeeded()
    }
  }

  private suspend fun trimDiskCacheIfNeeded() {
    diskTrimMutex.withLock {
      val files =
        diskCacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: run {
          estimatedDiskBytes.set(0L)
          return
        }
      var totalBytes = files.sumOf { it.length() }
      if (totalBytes <= maxDiskBytes) {
        estimatedDiskBytes.set(totalBytes)
        return
      }

      val sortedOldestFirst = files.sortedBy { it.lastModified() }
      val targetBytes = (maxDiskBytes * 8) / 10
      for (file in sortedOldestFirst) {
        if (totalBytes <= targetBytes) break
        val len = file.length()
        if (file.delete()) {
          totalBytes -= len
        }
      }
      estimatedDiskBytes.set(totalBytes)
    }
  }

  private fun openReadOnlyPfd(uriString: String): ParcelFileDescriptor? {
    val localFile = resolveLocalFile(uriString)
    return if (localFile != null && localFile.exists()) {
      ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
    } else {
      context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
    }
  }

  private fun openInputStream(uriString: String): InputStream? {
    val localFile = resolveLocalFile(uriString)
    return if (localFile != null && localFile.exists()) {
      localFile.inputStream()
    } else {
      context.contentResolver.openInputStream(Uri.parse(uriString))
    }
  }

  private fun resolveLocalFile(uriString: String): File? {
    return when {
      uriString.startsWith("file://") -> Uri.parse(uriString).path?.let { File(it) }
      uriString.startsWith("/") -> File(uriString)
      else -> null
    }
  }

  private fun decodeSampledBitmapFromStream(
    openStream: () -> InputStream?,
    targetSizePx: Int,
  ): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val opts =
      BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSizePx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    return scaleDownIfNeeded(decoded, targetSizePx)
  }

  private fun decodeSampledBitmapFromBytes(bytes: ByteArray, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val opts =
      BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSizePx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    return scaleDownIfNeeded(decoded, targetSizePx)
  }

  private fun scaleDownIfNeeded(bitmap: Bitmap, targetSizePx: Int): Bitmap {
    val maxDim = maxOf(bitmap.width, bitmap.height)
    if (maxDim <= targetSizePx) return bitmap
    val scale = targetSizePx.toFloat() / maxDim.toFloat()
    val dstW = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val dstH = (bitmap.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
  }

  private fun calculateInSampleSize(width: Int, height: Int, targetSizePx: Int): Int {
    var sampleSize = 1
    var halfW = width / 2
    var halfH = height / 2
    while (halfW / sampleSize >= targetSizePx && halfH / sampleSize >= targetSizePx) {
      sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
  }

  private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < maxBytes) {
      val read = stream.read(buf, 0, minOf(buf.size, maxBytes - total))
      if (read <= 0) break
      out.write(buf, 0, read)
      total += read
    }
    return out.toByteArray()
  }

  companion object {
    private const val DISK_CACHE_FOLDER = "prism_thumbs"
    private const val DEFAULT_MAX_DISK_BYTES = 64L * 1024L * 1024L
    private const val MAX_CONCURRENT_DECODES = 4
    private const val PDF_LOCK_STRIPES = 64
    private const val MAX_ZIP_ENTRIES_TO_SCAN = 256
    private const val MAX_EMBEDDED_THUMB_BYTES = 2 * 1024 * 1024
    private val OOXML_EXTENSIONS = setOf("docx", "xlsx", "pptx")
    private val OOXML_THUMB_PATHS =
      listOf("docProps/thumbnail.jpeg", "docProps/thumbnail.jpg", "docProps/thumbnail.png")
    private val OOXML_THUMB_PATHS_LOWER =
      setOf("docprops/thumbnail.jpeg", "docprops/thumbnail.jpg", "docprops/thumbnail.png")

    private fun defaultMemoryCacheKb(): Int {
      val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
      return (maxMemoryKb / 12).coerceIn(8 * 1024, 32 * 1024)
    }
  }
}
