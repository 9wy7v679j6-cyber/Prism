package com.prism.docs.core.data.duplicates

import android.content.Context
import android.net.Uri
import com.prism.docs.core.data.db.DocumentIndexDao
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.storage.DocumentCategoryClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Provides byte access for duplicate hashing across local storage and SAF `content://` URIs.
 */
interface DuplicateByteReader {
  /** Computes SHA-256 over the first [windowBytes] + last [windowBytes] of [uriString]. */
  suspend fun computePartialHash(uriString: String, windowBytes: Int): String?

  /** Computes full-stream SHA-256 of [uriString]. */
  suspend fun computeFullHash(uriString: String): String?
}

@Singleton
class AndroidDuplicateByteReader
@Inject
constructor(
  @ApplicationContext private val context: Context,
) : DuplicateByteReader {

  override suspend fun computePartialHash(uriString: String, windowBytes: Int): String? =
    withContext(Dispatchers.IO) {
      val activeCheck = { coroutineContext.ensureActive() }
      val local = resolveLocalFile(uriString)
      if (local != null && local.exists() && local.isFile && local.canRead()) {
        return@withContext try {
          computeLocalPartialSha256(local, windowBytes, activeCheck)
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          null
        }
      }
      val stream = openInputStream(uriString) ?: return@withContext null
      return@withContext stream.use { input ->
        try {
          computeStreamPartialSha256(input, windowBytes, activeCheck)
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          null
        }
      }
    }

  override suspend fun computeFullHash(uriString: String): String? =
    withContext(Dispatchers.IO) {
      val activeCheck = { coroutineContext.ensureActive() }
      val stream = openInputStream(uriString) ?: return@withContext null
      stream.use { input ->
        try {
          computeStreamFullSha256(input, activeCheck)
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Throwable) {
          null
        }
      }
    }

  private fun openInputStream(uriString: String): InputStream? {
    val local = resolveLocalFile(uriString)
    return if (local != null && local.exists()) {
      runCatching { FileInputStream(local) }.getOrNull()
    } else {
      runCatching { context.contentResolver.openInputStream(Uri.parse(uriString)) }.getOrNull()
    }
  }

  private fun resolveLocalFile(uriString: String): File? =
    when {
      uriString.startsWith("file://") -> Uri.parse(uriString).path?.let { File(it) }
      uriString.startsWith("/") -> File(uriString)
      else -> null
    }
}

/**
 * Three-stage duplicate detection engine:
 * 1. Group active files by exact `sizeBytes` (> 0).
 * 2. Compute partial hash (first 64 KB + last 64 KB) only on size-collision groups.
 * 3. Compute full SHA-256 only on remaining partial-hash-collision candidates.
 */
@Singleton
class DuplicateFinderEngine
@Inject
constructor(
  private val documentIndexDao: DocumentIndexDao,
  private val byteReader: DuplicateByteReader,
) {
  companion object {
    const val PARTIAL_HASH_WINDOW_BYTES = 64 * 1024

    fun groupConfirmedDuplicates(documents: List<IndexedDocumentEntity>): List<DuplicateFileGroup> {
      return documents
        .asSequence()
        .filter {
          !it.isTrashed &&
            it.sizeBytes > 0L &&
            !it.contentHash.isNullOrBlank() &&
            DocumentCategoryClassifier.isPrimaryDocument(it.category, it.extension, it.mimeType)
        }
        .groupBy { "${it.sizeBytes}:${it.contentHash}" }
        .values
        .filter { it.size >= 2 }
        .map { bucket ->
          val sortedNewestFirst =
            bucket.sortedWith(
              compareByDescending<IndexedDocumentEntity> { it.lastModifiedEpochMs }
                .thenBy { it.id }
            )
          val first = sortedNewestFirst.first()
          val hash = first.contentHash.orEmpty()
          DuplicateFileGroup(
            groupKey = "${first.sizeBytes}:$hash",
            sizeBytes = first.sizeBytes,
            contentHash = hash,
            documents = sortedNewestFirst,
          )
        }
        .sortedWith(
          compareByDescending<DuplicateFileGroup> { it.reclaimableBytes }
            .thenByDescending { it.sizeBytes }
        )
    }

    fun selectDocumentsToTrash(groups: List<DuplicateFileGroup>): List<Long> =
      groups.flatMap { group -> group.duplicatesToTrash.map { it.id } }.distinct()
  }

  fun observeConfirmedDuplicateGroups(): Flow<List<DuplicateFileGroup>> =
    documentIndexDao.observeHashedActiveDocuments().map { docs -> groupConfirmedDuplicates(docs) }

  suspend fun scanActiveDuplicates(
    onProgress: suspend (DuplicateScanProgress) -> Unit = {},
  ): DuplicateScanResult {
    val activeDocs = documentIndexDao.getActiveDuplicateCandidates()
    return findDuplicates(documents = activeDocs, persistHashes = true, onProgress = onProgress)
  }

  suspend fun findDuplicates(
    documents: List<IndexedDocumentEntity>,
    persistHashes: Boolean = true,
    onProgress: suspend (DuplicateScanProgress) -> Unit = {},
  ): DuplicateScanResult {
    coroutineContext.ensureActive()

    // Stage 1: Group active primary documents by exact file size (> 0),
    // excluding images, video, audio, APK, ZIP, and other archives.
    val sizeBuckets =
      documents
        .asSequence()
        .filter {
          !it.isTrashed &&
            it.sizeBytes > 0L &&
            DocumentCategoryClassifier.isPrimaryDocument(it.category, it.extension, it.mimeType)
        }
        .groupBy { it.sizeBytes }
        .filterValues { it.size >= 2 }

    val sizeCandidates = sizeBuckets.values.flatten()
    val sizeGroupCount = sizeBuckets.size

    onProgress(
      DuplicateScanProgress(
        stage = DuplicateScanStage.GROUPING_BY_SIZE,
        totalCandidates = sizeCandidates.size,
        processedCount = 0,
        sizeGroupCount = sizeGroupCount,
      )
    )

    if (sizeCandidates.isEmpty()) {
      if (persistHashes) {
        documentIndexDao.clearActiveHashes()
      }
      onProgress(
        DuplicateScanProgress(
          stage = DuplicateScanStage.COMPLETED,
          totalCandidates = 0,
          processedCount = 0,
          sizeGroupCount = 0,
        )
      )
      return DuplicateScanResult(
        groups = emptyList(),
        sizeCandidateCount = 0,
        partialCandidateCount = 0,
        fullHashedCount = 0,
      )
    }

    // Stage 2: Compute partial hash (first 64 KB + last 64 KB) for size-collision candidates
    val partialBuckets = LinkedHashMap<Pair<Long, String>, MutableList<IndexedDocumentEntity>>()
    val resolvedPartialById = HashMap<Long, String>(sizeCandidates.size)
    var partialMatchGroupCount = 0

    for ((idx, doc) in sizeCandidates.withIndex()) {
      coroutineContext.ensureActive()
      val partial = byteReader.computePartialHash(doc.uriString, PARTIAL_HASH_WINDOW_BYTES)
      if (!partial.isNullOrBlank()) {
        resolvedPartialById[doc.id] = partial
        val bucket = partialBuckets.getOrPut(doc.sizeBytes to partial) { mutableListOf() }
        bucket.add(doc)
        if (bucket.size == 2) partialMatchGroupCount++
      }
      onProgress(
        DuplicateScanProgress(
          stage = DuplicateScanStage.PARTIAL_HASHING,
          totalCandidates = sizeCandidates.size,
          processedCount = idx + 1,
          sizeGroupCount = sizeGroupCount,
          partialMatchCount = partialMatchGroupCount,
        )
      )
    }

    val partialCollisionGroups = partialBuckets.filterValues { it.size >= 2 }
    val partialCandidates = partialCollisionGroups.values.flatten()

    if (partialCandidates.isEmpty()) {
      if (persistHashes) {
        documentIndexDao.clearActiveHashes()
      }
      onProgress(
        DuplicateScanProgress(
          stage = DuplicateScanStage.COMPLETED,
          totalCandidates = sizeCandidates.size,
          processedCount = sizeCandidates.size,
          sizeGroupCount = sizeGroupCount,
          partialMatchCount = 0,
          confirmedGroupCount = 0,
          reclaimableBytes = 0L,
        )
      )
      return DuplicateScanResult(
        groups = emptyList(),
        sizeCandidateCount = sizeCandidates.size,
        partialCandidateCount = 0,
        fullHashedCount = 0,
      )
    }

    // Stage 3: Full SHA-256 only for remaining partial-hash-collision candidates
    val fullBuckets = LinkedHashMap<Pair<Long, String>, MutableList<IndexedDocumentEntity>>()
    val resolvedFullById = HashMap<Long, String>(partialCandidates.size)
    var confirmedMatchGroupCount = 0

    for ((idx, doc) in partialCandidates.withIndex()) {
      coroutineContext.ensureActive()
      val fullHash = byteReader.computeFullHash(doc.uriString)
      if (!fullHash.isNullOrBlank()) {
        resolvedFullById[doc.id] = fullHash
        val updatedDoc = doc.copy(partialHash = resolvedPartialById[doc.id], contentHash = fullHash)
        val bucket = fullBuckets.getOrPut(doc.sizeBytes to fullHash) { mutableListOf() }
        bucket.add(updatedDoc)
        if (bucket.size == 2) confirmedMatchGroupCount++
      }
      onProgress(
        DuplicateScanProgress(
          stage = DuplicateScanStage.FULL_HASHING,
          totalCandidates = partialCandidates.size,
          processedCount = idx + 1,
          sizeGroupCount = sizeGroupCount,
          partialMatchCount = partialCollisionGroups.size,
          confirmedGroupCount = confirmedMatchGroupCount,
        )
      )
    }

    val confirmedGroups =
      groupConfirmedDuplicates(
        fullBuckets.values.filter { it.size >= 2 }.flatten()
      )

    if (persistHashes) {
      val confirmedPartialById = HashMap<Long, String>()
      val confirmedFullById = HashMap<Long, String>()
      for (group in confirmedGroups) {
        for (doc in group.documents) {
          val partial = doc.partialHash ?: resolvedPartialById[doc.id]
          if (partial != null) confirmedPartialById[doc.id] = partial
          confirmedFullById[doc.id] = group.contentHash
        }
      }
      documentIndexDao.replaceActiveHashes(
        partialById = confirmedPartialById,
        fullById = confirmedFullById,
      )
    }

    val reclaimable = confirmedGroups.sumOf { it.reclaimableBytes }
    onProgress(
      DuplicateScanProgress(
        stage = DuplicateScanStage.COMPLETED,
        totalCandidates = partialCandidates.size,
        processedCount = partialCandidates.size,
        sizeGroupCount = sizeGroupCount,
        partialMatchCount = partialCollisionGroups.size,
        confirmedGroupCount = confirmedGroups.size,
        reclaimableBytes = reclaimable,
      )
    )

    return DuplicateScanResult(
      groups = confirmedGroups,
      sizeCandidateCount = sizeCandidates.size,
      partialCandidateCount = partialCandidates.size,
      fullHashedCount = resolvedFullById.size,
    )
  }
}

internal fun computeLocalPartialSha256(
  file: File,
  windowBytes: Int,
  checkActive: () -> Unit = {},
): String {
  val digest = MessageDigest.getInstance("SHA-256")
  RandomAccessFile(file, "r").use { raf ->
    val length = raf.length()
    val headSize = minOf(length, windowBytes.toLong()).toInt()
    val buffer = ByteArray(maxOf(headSize, 1))
    if (headSize > 0) {
      raf.seek(0L)
      var totalRead = 0
      while (totalRead < headSize) {
        checkActive()
        val read = raf.read(buffer, totalRead, headSize - totalRead)
        if (read <= 0) break
        totalRead += read
      }
      if (totalRead > 0) digest.update(buffer, 0, totalRead)
    }

    val tailOffset = maxOf(0L, length - windowBytes.toLong())
    val tailSize = minOf(length, windowBytes.toLong()).toInt()
    if (tailSize > 0) {
      val tailBuffer = ByteArray(tailSize)
      raf.seek(tailOffset)
      var totalRead = 0
      while (totalRead < tailSize) {
        checkActive()
        val read = raf.read(tailBuffer, totalRead, tailSize - totalRead)
        if (read <= 0) break
        totalRead += read
      }
      if (totalRead > 0) digest.update(tailBuffer, 0, totalRead)
    }
  }
  return digest.digest().toHex()
}

internal fun computeStreamPartialSha256(
  input: InputStream,
  windowBytes: Int,
  checkActive: () -> Unit = {},
): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val headBuffer = ByteArray(windowBytes)
  var headLen = 0
  val tailRing = ByteArray(windowBytes)
  var totalReadBytes = 0L
  var ringPos = 0

  val ioBuf = ByteArray(16 * 1024)
  while (true) {
    checkActive()
    val n = input.read(ioBuf)
    if (n <= 0) break
    var offset = 0
    if (headLen < windowBytes) {
      val toCopy = minOf(n, windowBytes - headLen)
      System.arraycopy(ioBuf, 0, headBuffer, headLen, toCopy)
      headLen += toCopy
    }
    while (offset < n) {
      val space = windowBytes - ringPos
      val chunk = minOf(n - offset, space)
      System.arraycopy(ioBuf, offset, tailRing, ringPos, chunk)
      ringPos = (ringPos + chunk) % windowBytes
      offset += chunk
    }
    totalReadBytes += n
  }

  if (headLen > 0) {
    digest.update(headBuffer, 0, headLen)
  }
  val tailLen = minOf(totalReadBytes, windowBytes.toLong()).toInt()
  if (tailLen > 0) {
    if (totalReadBytes <= windowBytes) {
      digest.update(tailRing, 0, tailLen)
    } else {
      val firstPart = windowBytes - ringPos
      if (firstPart > 0) digest.update(tailRing, ringPos, firstPart)
      if (ringPos > 0) digest.update(tailRing, 0, ringPos)
    }
  }
  return digest.digest().toHex()
}

internal fun computeStreamFullSha256(
  input: InputStream,
  checkActive: () -> Unit = {},
): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val buf = ByteArray(64 * 1024)
  while (true) {
    checkActive()
    val n = input.read(buf)
    if (n <= 0) break
    digest.update(buf, 0, n)
  }
  return digest.digest().toHex()
}

private val HEX_CHARS = charArrayOf(
  '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
)

private fun ByteArray.toHex(): String {
  val chars = CharArray(size * 2)
  for (i in indices) {
    val v = this[i].toInt() and 0xFF
    chars[i * 2] = HEX_CHARS[v ushr 4]
    chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
  }
  return String(chars)
}
