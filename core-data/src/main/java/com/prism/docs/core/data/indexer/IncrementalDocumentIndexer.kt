package com.prism.docs.core.data.indexer

import androidx.room.withTransaction
import com.prism.docs.core.data.db.DocumentIndexDao
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.IndexerStateDao
import com.prism.docs.core.data.db.IndexerStateEntity
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.model.IndexSyncSummary
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.paging.DocumentRepository
import com.prism.docs.core.data.storage.DocumentStorageGateway
import com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performs incremental delta synchronization between [DocumentStorageGateway] and Room:
 * - Inserts newly discovered documents in batches.
 * - Updates only documents whose lastModified timestamp or size changed, preserving user
 *   favorites, recents, tags, and collections.
 * - Prunes vanished non-trashed files while leaving 30-day Recycle Bin entries intact.
 * - Purges expired Recycle Bin entries past their 30-day retention window.
 * - Supports fast single-directory re-indexing triggered by [FileObserver] events.
 */
@Singleton
class IncrementalDocumentIndexer
@Inject
constructor(
  private val database: PrismDatabase,
  private val documentIndexDao: DocumentIndexDao,
  private val indexerStateDao: IndexerStateDao,
  private val documentRepository: DocumentRepository,
  private val storageGateway: DocumentStorageGateway,
) {
  companion object {
    private const val DELETE_CHUNK_SIZE = 400
  }

  suspend fun runIncrementalSync(
    nowEpochMs: Long = System.currentTimeMillis(),
    onProgress: suspend (scannedCount: Int) -> Unit = {},
  ): IndexSyncSummary {
    val priorState = indexerStateDao.getState() ?: IndexerStateEntity()
    indexerStateDao.upsert(priorState.copy(status = "SCANNING"))

    val existingByUri = documentIndexDao.getAllSnapshots().associateBy { it.uriString }
    val seenUris = HashSet<String>(existingByUri.size.coerceAtLeast(256))

    var scannedCount = 0
    var insertedCount = 0
    var updatedCount = 0

    storageGateway.scanAccessibleDocuments(batchSize = 250) { batch ->
      val toInsert = ArrayList<IndexedDocumentEntity>()
      val toUpdate = ArrayList<Pair<Long, ScannedDocumentMetadata>>()

      for (meta in batch) {
        if (
          meta.isDirectory ||
            UnifiedDocumentStorageGateway.isDirectoryMimeType(meta.mimeType) ||
            meta.displayName.startsWith(".") ||
            UnifiedDocumentStorageGateway.isHiddenOrSystemPath(meta.parentPath)
        ) {
          continue
        }
        if (!seenUris.add(meta.uriString)) continue
        scannedCount++
        val existing = existingByUri[meta.uriString]
        if (existing == null) {
          toInsert.add(meta.toNewEntity(nowEpochMs))
        } else if (
          !existing.isTrashed &&
            (existing.lastModifiedEpochMs != meta.lastModifiedEpochMs ||
              existing.sizeBytes != meta.sizeBytes)
        ) {
          toUpdate.add(existing.id to meta)
        }
      }

      if (toInsert.isNotEmpty() || toUpdate.isNotEmpty()) {
        database.withTransaction {
          if (toInsert.isNotEmpty()) {
            documentIndexDao.upsertAll(toInsert)
          }
          for ((id, meta) in toUpdate) {
            documentIndexDao.updateScannedMetadata(
              id = id,
              displayName = meta.displayName,
              normalizedName = meta.displayName.lowercase(),
              parentPath = meta.parentPath,
              mimeType = meta.mimeType,
              extension = meta.extension,
              category = meta.category.name,
              sizeBytes = meta.sizeBytes,
              lastModifiedEpochMs = meta.lastModifiedEpochMs,
              sourceType = meta.sourceType.name,
              lastIndexedEpochMs = nowEpochMs,
            )
          }
        }
        insertedCount += toInsert.size
        updatedCount += toUpdate.size
      }

      onProgress(scannedCount)
    }

    // Remove vanished non-trashed documents (including any stale directory rows no longer emitted)
    val staleIds =
      existingByUri.values
        .filter { !it.isTrashed && it.uriString !in seenUris }
        .map { it.id }

    if (staleIds.isNotEmpty()) {
      staleIds.chunked(DELETE_CHUNK_SIZE).forEach { chunk -> documentIndexDao.deleteByIds(chunk) }
    }

    // Enforce 30-day Recycle Bin expiry
    val expiredPurged = documentRepository.purgeExpiredRecycleBin(nowEpochMs)
    val finalCount = (scannedCount).coerceAtLeast(0)

    indexerStateDao.upsert(
      IndexerStateEntity(
        id = 1,
        lastFullScanEpochMs = nowEpochMs,
        indexedDocumentCount = finalCount,
        lastInsertedCount = insertedCount,
        lastUpdatedCount = updatedCount,
        lastRemovedCount = staleIds.size,
        status = "IDLE",
      )
    )

    return IndexSyncSummary(
      scannedCount = scannedCount,
      insertedCount = insertedCount,
      updatedCount = updatedCount,
      removedCount = staleIds.size,
      expiredTrashPurged = expiredPurged,
      completedAtEpochMs = nowEpochMs,
    )
  }

  /**
   * Fast non-recursive delta refresh for a single currently-open folder, invoked when
   * [DocumentStorageGateway.observeOpenDirectory] reports a [FileObserver] or [ContentObserver] change.
   */
  suspend fun syncSingleDirectory(
    directoryPathOrUri: String,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): IndexSyncSummary {
    val existingByUri =
      documentIndexDao.getSnapshotsInDirectory(directoryPathOrUri).associateBy { it.uriString }
    val children =
      storageGateway.listDirectoryChildren(directoryPathOrUri).filterNot {
        it.isDirectory ||
          UnifiedDocumentStorageGateway.isDirectoryMimeType(it.mimeType) ||
          it.displayName.startsWith(".")
      }
    val seenUris = HashSet<String>(children.size)

    val toInsert = ArrayList<IndexedDocumentEntity>()
    val toUpdate = ArrayList<Pair<Long, ScannedDocumentMetadata>>()

    for (meta in children) {
      seenUris.add(meta.uriString)
      val existing = existingByUri[meta.uriString]
      if (existing == null) {
        toInsert.add(meta.toNewEntity(nowEpochMs))
      } else if (
        !existing.isTrashed &&
          (existing.lastModifiedEpochMs != meta.lastModifiedEpochMs ||
            existing.sizeBytes != meta.sizeBytes)
      ) {
        toUpdate.add(existing.id to meta)
      }
    }

    val staleIds =
      existingByUri.values
        .filter { !it.isTrashed && it.uriString !in seenUris }
        .map { it.id }

    database.withTransaction {
      if (toInsert.isNotEmpty()) {
        documentIndexDao.upsertAll(toInsert)
      }
      for ((id, meta) in toUpdate) {
        documentIndexDao.updateScannedMetadata(
          id = id,
          displayName = meta.displayName,
          normalizedName = meta.displayName.lowercase(),
          parentPath = meta.parentPath,
          mimeType = meta.mimeType,
          extension = meta.extension,
          category = meta.category.name,
          sizeBytes = meta.sizeBytes,
          lastModifiedEpochMs = meta.lastModifiedEpochMs,
          sourceType = meta.sourceType.name,
          lastIndexedEpochMs = nowEpochMs,
        )
      }
      if (staleIds.isNotEmpty()) {
        staleIds.chunked(DELETE_CHUNK_SIZE).forEach { chunk -> documentIndexDao.deleteByIds(chunk) }
      }
    }

    return IndexSyncSummary(
      scannedCount = children.size,
      insertedCount = toInsert.size,
      updatedCount = toUpdate.size,
      removedCount = staleIds.size,
      expiredTrashPurged = 0,
      completedAtEpochMs = nowEpochMs,
    )
  }

  private fun ScannedDocumentMetadata.toNewEntity(indexedAtEpochMs: Long): IndexedDocumentEntity =
    IndexedDocumentEntity(
      uriString = uriString,
      displayName = displayName,
      normalizedName = displayName.lowercase(),
      parentPath = parentPath,
      mimeType = mimeType,
      extension = extension,
      category = category.name,
      sizeBytes = sizeBytes,
      lastModifiedEpochMs = lastModifiedEpochMs,
      sourceType = sourceType.name,
      lastIndexedEpochMs = indexedAtEpochMs,
    )
}
