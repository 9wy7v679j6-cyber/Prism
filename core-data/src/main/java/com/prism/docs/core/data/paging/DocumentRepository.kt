package com.prism.docs.core.data.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.prism.docs.core.data.db.CategoryCountRow
import com.prism.docs.core.data.db.CollectionDao
import com.prism.docs.core.data.db.CollectionEntity
import com.prism.docs.core.data.db.CollectionWithCountRow
import com.prism.docs.core.data.db.DocumentCollectionCrossRef
import com.prism.docs.core.data.db.DocumentIndexDao
import com.prism.docs.core.data.db.DocumentTagCrossRef
import com.prism.docs.core.data.db.FolderStorageStatRow
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.db.RecycleBinDao
import com.prism.docs.core.data.db.RecycleBinEntryEntity
import com.prism.docs.core.data.db.TagDao
import com.prism.docs.core.data.db.TagEntity
import com.prism.docs.core.data.db.TagWithCountRow
import com.prism.docs.core.data.model.DirectoryChangeEvent
import com.prism.docs.core.data.model.FileListQuery
import com.prism.docs.core.data.storage.DocumentStorageGateway
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class DocumentRepository
@Inject
constructor(
  private val database: PrismDatabase,
  private val documentIndexDao: DocumentIndexDao,
  private val collectionDao: CollectionDao,
  private val tagDao: TagDao,
  private val recycleBinDao: RecycleBinDao,
  private val storageGateway: DocumentStorageGateway,
) {
  companion object {
    const val RECYCLE_BIN_RETENTION_MS: Long = 30L * 24L * 60L * 60L * 1000L
    const val DEFAULT_PAGE_SIZE: Int = 60
  }

  fun pagedDocuments(
    query: FileListQuery,
    pageSize: Int = DEFAULT_PAGE_SIZE,
  ): Flow<PagingData<IndexedDocumentEntity>> {
    val sqlQuery = FileQueryBuilder.build(query)
    return Pager(
        config =
          PagingConfig(
            pageSize = pageSize,
            prefetchDistance = pageSize / 3,
            enablePlaceholders = false,
            initialLoadSize = pageSize * 2,
          ),
        pagingSourceFactory = { documentIndexDao.pagingSourceRaw(sqlQuery) },
      )
      .flow
  }

  fun observeActiveCount(): Flow<Int> = documentIndexDao.observeActiveCount()

  fun observeTotalIndexedBytes(): Flow<Long> = documentIndexDao.observeTotalIndexedBytes()

  fun observeFavoriteCount(): Flow<Int> = documentIndexDao.observeFavoriteCount()

  fun observeRecentCount(): Flow<Int> = documentIndexDao.observeRecentCount()

  fun observeCategoryCounts(): Flow<List<CategoryCountRow>> =
    documentIndexDao.observeCategoryCounts()

  fun observeLargestDocuments(limit: Int = 40): Flow<List<IndexedDocumentEntity>> =
    documentIndexDao.observeLargestDocuments(limit)

  fun observeFolderStorageStats(limit: Int = 25): Flow<List<FolderStorageStatRow>> =
    documentIndexDao.observeFolderStorageStats(limit)

  fun observeRecents(limit: Int = 30): Flow<List<IndexedDocumentEntity>> =
    documentIndexDao.observeRecentDocuments(limit)

  suspend fun observeActiveDocumentsSnapshot(): List<IndexedDocumentEntity> =
    documentIndexDao.observeActiveDocuments().first()

  fun observeFavorites(): Flow<List<IndexedDocumentEntity>> =
    documentIndexDao.observeFavoriteDocuments()

  fun observeCollections(): Flow<List<CollectionEntity>> = collectionDao.observeCollections()

  fun observeCollectionsWithStats(): Flow<List<CollectionWithCountRow>> =
    collectionDao.observeCollectionsWithStats()

  fun observeCollection(collectionId: Long): Flow<CollectionEntity?> =
    collectionDao.observeById(collectionId)

  fun observeDocumentsInCollection(collectionId: Long): Flow<List<IndexedDocumentEntity>> =
    collectionDao.observeDocumentsInCollection(collectionId)

  fun observeTags(): Flow<List<TagEntity>> = tagDao.observeTags()

  fun observeTagsWithStats(): Flow<List<TagWithCountRow>> = tagDao.observeTagsWithStats()

  fun observeCollectionCrossRefsForDocuments(documentIds: List<Long>): Flow<List<DocumentCollectionCrossRef>> =
    if (documentIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
    else collectionDao.observeCrossRefsForDocuments(documentIds)

  fun observeTagCrossRefsForDocuments(documentIds: List<Long>): Flow<List<DocumentTagCrossRef>> =
    if (documentIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
    else tagDao.observeCrossRefsForDocuments(documentIds)

  fun observeRecycleBin(): Flow<List<RecycleBinEntryEntity>> = recycleBinDao.observeAll()

  fun observeRecycleBinCount(): Flow<Int> = recycleBinDao.observeCount()

  fun observeRecycleBinBytes(): Flow<Long> = recycleBinDao.observeTotalBytes()

  suspend fun getDocumentsByUris(uris: List<String>): List<IndexedDocumentEntity> =
    if (uris.isEmpty()) emptyList() else documentIndexDao.getByUris(uris)

  suspend fun getDocumentsByIds(ids: List<Long>): List<IndexedDocumentEntity> =
    if (ids.isEmpty()) emptyList() else documentIndexDao.getByIds(ids)

  suspend fun listStorageRoots() = storageGateway.listStorageRoots()

  suspend fun listDirectoryChildren(directoryPathOrUri: String) =
    storageGateway.listDirectoryChildren(directoryPathOrUri)

  fun observeOpenDirectory(directoryPathOrUri: String): Flow<DirectoryChangeEvent> =
    storageGateway.observeOpenDirectory(directoryPathOrUri)

  suspend fun setFavorite(documentId: Long, favorite: Boolean) {
    documentIndexDao.setFavorite(documentId, favorite)
  }

  suspend fun recordDocumentOpened(
    documentId: Long,
    openedAtEpochMs: Long = System.currentTimeMillis(),
  ) {
    documentIndexDao.recordOpened(documentId, openedAtEpochMs)
  }

  suspend fun createCollection(
    name: String,
    accentHex: String,
    iconName: String = "folder",
    createdAtEpochMs: Long = System.currentTimeMillis(),
  ): Long {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Collection name cannot be blank" }
    return collectionDao.upsert(
      CollectionEntity(
        name = clean,
        accentHex = accentHex,
        iconName = iconName,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = createdAtEpochMs,
      )
    )
  }

  suspend fun renameCollection(
    collectionId: Long,
    newName: String,
    updatedAtEpochMs: Long = System.currentTimeMillis(),
  ): Boolean {
    val clean = newName.trim()
    if (clean.isEmpty()) return false
    collectionDao.rename(collectionId, clean, updatedAtEpochMs)
    return true
  }

  suspend fun deleteCollection(collectionId: Long) {
    collectionDao.deleteById(collectionId)
  }

  suspend fun addDocumentToCollection(documentId: Long, collectionId: Long) {
    val now = System.currentTimeMillis()
    collectionDao.addDocumentToCollection(
      DocumentCollectionCrossRef(
        documentId = documentId,
        collectionId = collectionId,
        addedAtEpochMs = now,
      )
    )
    collectionDao.touchUpdated(collectionId, now)
  }

  suspend fun addDocumentsToCollection(documentIds: List<Long>, collectionId: Long) {
    if (documentIds.isEmpty()) return
    val now = System.currentTimeMillis()
    collectionDao.addDocumentsToCollection(
      documentIds.distinct().map {
        DocumentCollectionCrossRef(documentId = it, collectionId = collectionId, addedAtEpochMs = now)
      }
    )
    collectionDao.touchUpdated(collectionId, now)
  }

  suspend fun removeDocumentFromCollection(documentId: Long, collectionId: Long) {
    collectionDao.removeDocumentFromCollection(documentId, collectionId)
    collectionDao.touchUpdated(collectionId, System.currentTimeMillis())
  }

  suspend fun removeDocumentsFromCollection(documentIds: List<Long>, collectionId: Long) {
    if (documentIds.isEmpty()) return
    collectionDao.removeDocumentsFromCollection(documentIds, collectionId)
    collectionDao.touchUpdated(collectionId, System.currentTimeMillis())
  }

  suspend fun createTag(
    name: String,
    colorHex: String,
    createdAtEpochMs: Long = System.currentTimeMillis(),
  ): Long {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Tag name cannot be blank" }
    val existing = tagDao.findByNameIgnoreCase(clean)
    if (existing != null) return existing.id
    return tagDao.insert(
      TagEntity(
        name = clean,
        colorHex = colorHex,
        createdAtEpochMs = createdAtEpochMs,
      )
    )
  }

  suspend fun renameTag(tagId: Long, newName: String): Boolean {
    val clean = newName.trim()
    if (clean.isEmpty()) return false
    val collision = tagDao.findByNameIgnoreCase(clean)
    if (collision != null && collision.id != tagId) return false
    tagDao.rename(tagId, clean)
    return true
  }

  suspend fun deleteTag(tagId: Long) {
    tagDao.deleteById(tagId)
  }

  suspend fun attachTag(documentId: Long, tagId: Long) {
    tagDao.attachTag(DocumentTagCrossRef(documentId = documentId, tagId = tagId))
  }

  suspend fun attachTagToDocuments(documentIds: List<Long>, tagId: Long) {
    if (documentIds.isEmpty()) return
    val now = System.currentTimeMillis()
    tagDao.attachTags(
      documentIds.distinct().map {
        DocumentTagCrossRef(documentId = it, tagId = tagId, taggedAtEpochMs = now)
      }
    )
  }

  suspend fun detachTag(documentId: Long, tagId: Long) {
    tagDao.detachTag(documentId, tagId)
  }

  suspend fun detachTagFromDocuments(documentIds: List<Long>, tagId: Long) {
    if (documentIds.isEmpty()) return
    tagDao.detachTagFromDocuments(documentIds, tagId)
  }

  /**
   * Moves an indexed document into Prism's 30-day Recycle Bin atomically.
   * Physical staging path is recorded so Phase 1b can restore or permanently purge on disk.
   */
  suspend fun moveToRecycleBin(
    documentId: Long,
    trashedFilePath: String,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): Boolean {
    val doc = documentIndexDao.getById(documentId) ?: return false
    database.withTransaction {
      documentIndexDao.markTrashed(
        id = doc.id,
        trashedAtEpochMs = nowEpochMs,
        originalParentPath = doc.parentPath,
      )
      recycleBinDao.upsert(
        RecycleBinEntryEntity(
          documentId = doc.id,
          originalUriString = doc.uriString,
          originalPath = doc.parentPath,
          trashedFilePath = trashedFilePath,
          displayName = doc.displayName,
          sizeBytes = doc.sizeBytes,
          trashedAtEpochMs = nowEpochMs,
          expiresAtEpochMs = nowEpochMs + RECYCLE_BIN_RETENTION_MS,
        )
      )
    }
    return true
  }

  suspend fun restoreFromRecycleBin(documentId: Long) {
    database.withTransaction {
      documentIndexDao.restoreFromTrash(documentId)
      recycleBinDao.deleteByDocumentId(documentId)
    }
  }

  suspend fun purgeExpiredRecycleBin(nowEpochMs: Long = System.currentTimeMillis()): Int {
    val expired = recycleBinDao.getExpiredEntries(nowEpochMs)
    if (expired.isEmpty()) return 0
    for (entry in expired) {
      if (entry.trashedFilePath.isNotBlank() && !entry.trashedFilePath.startsWith("content://")) {
        runCatching { File(entry.trashedFilePath).delete() }
      }
    }
    val docIds = expired.map { it.documentId }
    database.withTransaction {
      recycleBinDao.deleteExpiredEntries(nowEpochMs)
      documentIndexDao.deleteByIds(docIds)
    }
    return expired.size
  }
}
