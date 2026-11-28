package com.prism.docs.feature.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe Android [PdfRenderer] controller enforcing a single per-document [Mutex]
 * (since [PdfRenderer] permits only one open [PdfRenderer.Page] at a time) and a hard-capped
 * memory bitmap cache set to 1/8 of the app JVM heap.
 */
class PdfDocumentController(
  private val context: Context,
) : Closeable {

  data class PageCacheKey(
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val tileBucket: Int,
    val leftBucket: Int,
    val topBucket: Int,
    val rightBucket: Int = 100,
    val bottomBucket: Int = 100,
  )

  private val renderMutex = Mutex()
  private val highPriorityWaiters = AtomicInteger(0)
  private var fileDescriptor: ParcelFileDescriptor? = null
  private var pdfRenderer: PdfRenderer? = null

  private val pageAspectRatios = ConcurrentHashMap<Int, Float>()
  private var defaultAspectRatio: Float = 0.707f // Standard A4 fallback until page 0 is inspected

  @Volatile var pageCount: Int = 0
    private set

  @Volatile var isOpen: Boolean = false
    private set

  private val maxMemoryCacheKb: Int =
    ((Runtime.getRuntime().maxMemory() / 8L) / 1024L)
      .coerceIn(16L * 1024L, 128L * 1024L)
      .toInt()

  private val pageBitmapCache =
    object : LruCache<PageCacheKey, Bitmap>(maxMemoryCacheKb) {
      override fun sizeOf(key: PageCacheKey, value: Bitmap): Int =
        (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

  private val thumbnailCache =
    object : LruCache<Int, Bitmap>((maxMemoryCacheKb / 4).coerceIn(4096, 20 * 1024)) {
      override fun sizeOf(key: Int, value: Bitmap): Int =
        (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

  suspend fun open(uriString: String): Result<Int> =
    withContext(Dispatchers.IO) {
      renderMutex.withLock {
        runCatching {
          closeInternal()
          val pfd =
            openReadOnlyDescriptor(context, uriString)
              ?: throw IllegalStateException("Unable to open file descriptor for $uriString")
          val renderer = PdfRenderer(pfd)
          fileDescriptor = pfd
          pdfRenderer = renderer
          pageCount = renderer.pageCount.coerceAtLeast(0)
          if (pageCount <= 0) {
            throw IllegalStateException("PDF contains 0 pages")
          }
          renderer.openPage(0).use { firstPage ->
            if (firstPage.width > 0 && firstPage.height > 0) {
              val ratio = firstPage.width.toFloat() / firstPage.height.toFloat()
              defaultAspectRatio = ratio.coerceIn(0.35f, 2.8f)
              pageAspectRatios[0] = defaultAspectRatio
            }
          }
          isOpen = true
          pageCount
        }.onFailure {
          closeInternal()
        }
      }
    }

  fun aspectRatioForPage(pageIndex: Int): Float =
    pageAspectRatios[pageIndex] ?: defaultAspectRatio

  fun peekCachedPage(
    pageIndex: Int,
    targetWidthPx: Int,
    tileSpec: TileRenderSpec? = null,
  ): Bitmap? {
    val key = buildCacheKey(pageIndex, targetWidthPx, tileSpec) ?: return null
    return pageBitmapCache.get(key)
  }

  fun peekCachedThumbnail(pageIndex: Int): Bitmap? = thumbnailCache.get(pageIndex)

  suspend fun renderPage(
    pageIndex: Int,
    targetWidthPx: Int,
    tileSpec: TileRenderSpec? = null,
    isPrefetch: Boolean = false,
  ): Bitmap? =
    withContext(Dispatchers.Default) {
      val safeWidth = targetWidthPx.coerceIn(240, 2600)
      val cachedKey = buildCacheKey(pageIndex, safeWidth, tileSpec)
      if (cachedKey != null) {
        pageBitmapCache.get(cachedKey)?.let { return@withContext it }
      }
      ensureActive()
      if (isPrefetch) {
        while (highPriorityWaiters.get() > 0) {
          ensureActive()
          delay(6L)
        }
      } else {
        highPriorityWaiters.incrementAndGet()
      }
      try {
        renderMutex.withLock {
          ensureActive()
          if (isPrefetch && highPriorityWaiters.get() > 0) return@withContext null
          val renderer = pdfRenderer ?: return@withContext null
          if (pageIndex !in 0 until pageCount) return@withContext null

          // Check again under lock in case another coroutine just finished rendering this key
          if (cachedKey != null) {
            pageBitmapCache.get(cachedKey)?.let { return@withContext it }
          }

          try {
            renderer.openPage(pageIndex).use { page ->
              val pageW = page.width.coerceAtLeast(1)
              val pageH = page.height.coerceAtLeast(1)
              val ratio = (pageW.toFloat() / pageH.toFloat()).coerceIn(0.35f, 2.8f)
              pageAspectRatios[pageIndex] = ratio

              if (tileSpec != null && tileSpec.scaleFactor > 1.25f) {
                val regionWFrac = (tileSpec.regionRightFraction - tileSpec.regionLeftFraction).coerceIn(0.05f, 1f)
                val regionHFrac = (tileSpec.regionBottomFraction - tileSpec.regionTopFraction).coerceIn(0.05f, 1f)
                val idealW = safeWidth * tileSpec.scaleFactor * regionWFrac
                val idealH = (safeWidth / ratio) * tileSpec.scaleFactor * regionHFrac
                val maxDim = maxOf(idealW, idealH, 1f)
                val downscale = (2048f / maxDim).coerceAtMost(1f)
                val tileW = (idealW * downscale).roundToInt().coerceIn(64, 2048)
                val tileH = (idealH * downscale).roundToInt().coerceIn(64, 2048)
                val key =
                  PageCacheKey(
                    pageIndex = pageIndex,
                    widthPx = tileW,
                    heightPx = tileH,
                    tileBucket = (tileSpec.scaleFactor * 10f).roundToInt(),
                    leftBucket = (tileSpec.regionLeftFraction * 100f).roundToInt(),
                    topBucket = (tileSpec.regionTopFraction * 100f).roundToInt(),
                    rightBucket = (tileSpec.regionRightFraction * 100f).roundToInt(),
                    bottomBucket = (tileSpec.regionBottomFraction * 100f).roundToInt(),
                  )
                pageBitmapCache.get(key)?.let { return@withContext it }

                val bitmap = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                val fullScaledW = tileW / regionWFrac
                val fullScaledH = tileH / regionHFrac
                val scaleX = fullScaledW / pageW.toFloat()
                val scaleY = fullScaledH / pageH.toFloat()
                val offsetX = -tileSpec.regionLeftFraction * fullScaledW
                val offsetY = -tileSpec.regionTopFraction * fullScaledH
                val matrix =
                  Matrix().apply {
                    postScale(scaleX, scaleY)
                    postTranslate(offsetX, offsetY)
                  }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.prepareToDraw()
                pageBitmapCache.put(key, bitmap)
                bitmap
              } else {
                val destH = (safeWidth / ratio).roundToInt().coerceIn(240, 3600)
                val key =
                  PageCacheKey(
                    pageIndex = pageIndex,
                    widthPx = safeWidth,
                    heightPx = destH,
                    tileBucket = 10,
                    leftBucket = 0,
                    topBucket = 0,
                  )
                pageBitmapCache.get(key)?.let { return@withContext it }

                val bitmap = Bitmap.createBitmap(safeWidth, destH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.prepareToDraw()
                pageBitmapCache.put(key, bitmap)
                bitmap
              }
            }
          } catch (ce: CancellationException) {
            throw ce
          } catch (_: Throwable) {
            null
          }
        }
      } finally {
        if (!isPrefetch) {
          highPriorityWaiters.decrementAndGet()
        }
      }
    }

  suspend fun renderThumbnail(
    pageIndex: Int,
    maxWidthPx: Int = 200,
  ): Bitmap? =
    withContext(Dispatchers.Default) {
      thumbnailCache.get(pageIndex)?.let { return@withContext it }
      ensureActive()
      while (highPriorityWaiters.get() > 0) {
        ensureActive()
        delay(6L)
      }
      renderMutex.withLock {
        ensureActive()
        thumbnailCache.get(pageIndex)?.let { return@withContext it }
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex !in 0 until pageCount) return@withContext null
        try {
          renderer.openPage(pageIndex).use { page ->
            val pageW = page.width.coerceAtLeast(1)
            val pageH = page.height.coerceAtLeast(1)
            val ratio = (pageW.toFloat() / pageH.toFloat()).coerceIn(0.35f, 2.8f)
            pageAspectRatios[pageIndex] = ratio
            val w = maxWidthPx.coerceIn(96, 260)
            val h = (w / ratio).roundToInt().coerceIn(96, 380)
            // PdfRenderer requires ARGB_8888 (RGB_565 throws IllegalArgumentException on Android)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.prepareToDraw()
            thumbnailCache.put(pageIndex, bitmap)
            bitmap
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          null
        }
      }
    }

  suspend fun prefetchAround(
    centerPage: Int,
    targetWidthPx: Int,
  ) {
    if (!isOpen || pageCount <= 0) return
    val offsets = listOf(1, -1, 2, -2)
    for (offset in offsets) {
      val idx = centerPage + offset
      if (idx in 0 until pageCount) {
        renderPage(pageIndex = idx, targetWidthPx = targetWidthPx, isPrefetch = true)
      }
    }
    for (offset in -3..3) {
      val idx = centerPage + offset
      if (idx in 0 until pageCount && thumbnailCache.get(idx) == null) {
        renderThumbnail(pageIndex = idx)
      }
    }
  }

  private fun buildCacheKey(
    pageIndex: Int,
    targetWidthPx: Int,
    tileSpec: TileRenderSpec?,
  ): PageCacheKey? {
    val safeWidth = targetWidthPx.coerceIn(240, 2600)
    val ratio = pageAspectRatios[pageIndex] ?: defaultAspectRatio
    return if (tileSpec != null && tileSpec.scaleFactor > 1.25f) {
      val regionWFrac = (tileSpec.regionRightFraction - tileSpec.regionLeftFraction).coerceIn(0.05f, 1f)
      val regionHFrac = (tileSpec.regionBottomFraction - tileSpec.regionTopFraction).coerceIn(0.05f, 1f)
      val idealW = safeWidth * tileSpec.scaleFactor * regionWFrac
      val idealH = (safeWidth / ratio) * tileSpec.scaleFactor * regionHFrac
      val maxDim = maxOf(idealW, idealH, 1f)
      val downscale = (2048f / maxDim).coerceAtMost(1f)
      val tileW = (idealW * downscale).roundToInt().coerceIn(64, 2048)
      val tileH = (idealH * downscale).roundToInt().coerceIn(64, 2048)
      PageCacheKey(
        pageIndex = pageIndex,
        widthPx = tileW,
        heightPx = tileH,
        tileBucket = (tileSpec.scaleFactor * 10f).roundToInt(),
        leftBucket = (tileSpec.regionLeftFraction * 100f).roundToInt(),
        topBucket = (tileSpec.regionTopFraction * 100f).roundToInt(),
        rightBucket = (tileSpec.regionRightFraction * 100f).roundToInt(),
        bottomBucket = (tileSpec.regionBottomFraction * 100f).roundToInt(),
      )
    } else {
      val destH = (safeWidth / ratio).roundToInt().coerceIn(240, 3600)
      PageCacheKey(
        pageIndex = pageIndex,
        widthPx = safeWidth,
        heightPx = destH,
        tileBucket = 10,
        leftBucket = 0,
        topBucket = 0,
      )
    }
  }

  override fun close() {
    closeInternal()
  }

  private fun closeInternal() {
    isOpen = false
    pageCount = 0
    pageBitmapCache.evictAll()
    thumbnailCache.evictAll()
    pageAspectRatios.clear()
    runCatching { pdfRenderer?.close() }
    pdfRenderer = null
    runCatching { fileDescriptor?.close() }
    fileDescriptor = null
  }

  companion object {
    fun openReadOnlyDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
      if (uriString.isBlank()) return null
      val parsed = Uri.parse(uriString)
      return when (parsed.scheme?.lowercase()) {
        null, "" -> {
          val file = File(uriString)
          if (file.exists() && file.canRead()) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
          } else null
        }
        "file" -> {
          val path = parsed.path ?: return null
          val file = File(path)
          if (file.exists() && file.canRead()) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
          } else null
        }
        else -> runCatching { context.contentResolver.openFileDescriptor(parsed, "r") }.getOrNull()
      }
    }
  }
}
