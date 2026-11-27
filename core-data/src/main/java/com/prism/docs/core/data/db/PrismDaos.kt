package com.prism.docs.core.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.prism.docs.core.data.storage.DocumentCategoryClassifier
import kotlinx.coroutines.flow.Flow

data class DocumentIndexSnapshot(
  val id: Long,
  val uriString: String,
  val lastModifiedEpochMs: Long,
  val sizeBytes: Long,
  val isFavorite: Boolean,
  val isTrashed: Boolean,
  val lastOpenedEpochMs: Long?,
  val contentHash: String?,
)

data class CategoryCountRow(
  val category: String,
  val itemCount: Int,
  val totalBytes: Long,
)

data class FolderStorageStatRow(
  val parentPath: String,
  val itemCount: Int,
  val totalBytes: Long,
)

data class CollectionWithCountRow(
  val id: Long,
  val name: String,
  val accentHex: String,
  val iconName: String,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
  val documentCount: Int,
  val totalBytes: Long,
)

data class TagWithCountRow(
  val id: Long,
  val name: String,
  val colorHex: String,
  val createdAtEpochMs: Long,
  val documentCount: Int,
)

@Dao
interface DocumentIndexDao {
  @RawQuery(observedEntities = [IndexedDocumentEntity::class, DocumentCollectionCrossRef::class, DocumentTagCrossRef::class])
  fun pagingSourceRaw(query: SupportSQLiteQuery): PagingSource<Int, IndexedDocumentEntity>

  @Query("SELECT * FROM indexed_documents WHERE isTrashed = 0 ORDER BY lastModifiedEpochMs DESC")
  fun observeActiveDocuments(): Flow<List<IndexedDocumentEntity>>

  @Query(
    "SELECT * FROM indexed_documents WHERE isTrashed = 0 ORDER BY sizeBytes DESC, lastModifiedEpochMs DESC LIMIT :limit"
  )
  fun observeLargestDocuments(limit: Int = 40): Flow<List<IndexedDocumentEntity>>

  @Query(
    "SELECT parentPath, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes " +
      "FROM indexed_documents WHERE isTrashed = 0 GROUP BY parentPath ORDER BY totalBytes DESC LIMIT :limit"
  )
  fun observeFolderStorageStats(limit: Int = 25): Flow<List<FolderStorageStatRow>>

  @Query(
    "SELECT * FROM indexed_documents WHERE isTrashed = 0 AND sizeBytes > 0 " +
      "ORDER BY sizeBytes DESC, lastModifiedEpochMs DESC, id ASC"
  )
  suspend fun getActiveDuplicateCandidates(): List<IndexedDocumentEntity>

  @Query(
    "SELECT * FROM indexed_documents WHERE isTrashed = 0 AND contentHash IS NOT NULL " +
      "ORDER BY sizeBytes DESC, lastModifiedEpochMs DESC, id ASC"
  )
  fun observeHashedActiveDocuments(): Flow<List<IndexedDocumentEntity>>

  @Query(
    "UPDATE indexed_documents SET partialHash = :partialHash, contentHash = :contentHash WHERE id = :id"
  )
  suspend fun updateDocumentHashes(id: Long, partialHash: String?, contentHash: String?)

  @Query("UPDATE indexed_documents SET partialHash = NULL, contentHash = NULL WHERE isTrashed = 0")
  suspend fun clearActiveHashes()

  @Transaction
  suspend fun replaceActiveHashes(
    partialById: Map<Long, String>,
    fullById: Map<Long, String>,
  ) {
    clearActiveHashes()
    val allIds = HashSet<Long>(partialById.size + fullById.size)
    allIds.addAll(partialById.keys)
    allIds.addAll(fullById.keys)
    for (id in allIds) {
      updateDocumentHashes(id = id, partialHash = partialById[id], contentHash = fullById[id])
    }
  }

  @Query(
    "SELECT * FROM indexed_documents WHERE isTrashed = 0 AND lastOpenedEpochMs IS NOT NULL ORDER BY lastOpenedEpochMs DESC LIMIT :limit"
  )
  fun observeRecentDocuments(limit: Int = 30): Flow<List<IndexedDocumentEntity>>

  @Query(
    "SELECT * FROM indexed_documents WHERE isTrashed = 0 AND isFavorite = 1 ORDER BY lastModifiedEpochMs DESC"
  )
  fun observeFavoriteDocuments(): Flow<List<IndexedDocumentEntity>>

  @Query(
    "SELECT * FROM indexed_documents WHERE isTrashed = 0 AND " +
      DocumentCategoryClassifier.PRIMARY_DOCUMENT_SQL_WHERE +
      " ORDER BY sizeBytes DESC, lastModifiedEpochMs DESC LIMIT :limit"
  )
  fun observeLargestPrimaryDocuments(limit: Int = 40): Flow<List<IndexedDocumentEntity>>

  @Query(
    "SELECT parentPath, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes " +
      "FROM indexed_documents WHERE isTrashed = 0 AND " +
      DocumentCategoryClassifier.PRIMARY_DOCUMENT_SQL_WHERE +
      " GROUP BY parentPath ORDER BY totalBytes DESC LIMIT :limit"
  )
  fun observePrimaryDocumentFolderStorageStats(limit: Int = 25): Flow<List<FolderStorageStatRow>>

  @Query(
    "SELECT COUNT(*) FROM indexed_documents WHERE isTrashed = 0 AND " +
      DocumentCategoryClassifier.PRIMARY_DOCUMENT_SQL_WHERE
  )
  fun observeActivePrimaryDocumentCount(): Flow<Int>

  @Query(
    "SELECT COALESCE(SUM(sizeBytes), 0) FROM indexed_documents WHERE isTrashed = 0 AND " +
      DocumentCategoryClassifier.PRIMARY_DOCUMENT_SQL_WHERE
  )
  fun observeTotalPrimaryDocumentBytes(): Flow<Long>

  @Query(
    "SELECT category, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes " +
      "FROM indexed_documents WHERE isTrashed = 0 AND " +
      DocumentCategoryClassifier.PRIMARY_DOCUMENT_SQL_WHERE +
      " GROUP BY category"
  )
  fun observePrimaryDocumentCategoryCounts(): Flow<List<CategoryCountRow>>

  @Query("SELECT COUNT(*) FROM indexed_documents WHERE isTrashed = 0")
  fun observeActiveCount(): Flow<Int>

  @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM indexed_documents WHERE isTrashed = 0")
  fun observeTotalIndexedBytes(): Flow<Long>

  @Query("SELECT COUNT(*) FROM indexed_documents WHERE isTrashed = 0 AND isFavorite = 1")
  fun observeFavoriteCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM indexed_documents WHERE isTrashed = 0 AND lastOpenedEpochMs IS NOT NULL")
  fun observeRecentCount(): Flow<Int>

  @Query(
    "SELECT category, COUNT(*) AS itemCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes " +
      "FROM indexed_documents WHERE isTrashed = 0 GROUP BY category"
  )
  fun observeCategoryCounts(): Flow<List<CategoryCountRow>>

  @Query("SELECT * FROM indexed_documents WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): IndexedDocumentEntity?

  @Query("SELECT * FROM indexed_documents WHERE id IN (:ids)")
  suspend fun getByIds(ids: List<Long>): List<IndexedDocumentEntity>

  @Query("SELECT * FROM indexed_documents WHERE uriString = :uriString LIMIT 1")
  suspend fun getByUri(uriString: String): IndexedDocumentEntity?

  @Query("SELECT * FROM indexed_documents WHERE uriString IN (:uris)")
  suspend fun getByUris(uris: List<String>): List<IndexedDocumentEntity>

  @Query(
    "SELECT id, uriString, lastModifiedEpochMs, sizeBytes, isFavorite, isTrashed, lastOpenedEpochMs, contentHash " +
      "FROM indexed_documents"
  )
  suspend fun getAllSnapshots(): List<DocumentIndexSnapshot>

  @Query(
    "SELECT id, uriString, lastModifiedEpochMs, sizeBytes, isFavorite, isTrashed, lastOpenedEpochMs, contentHash " +
      "FROM indexed_documents WHERE parentPath = :parentPath"
  )
  suspend fun getSnapshotsInDirectory(parentPath: String): List<DocumentIndexSnapshot>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(items: List<IndexedDocumentEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnoreAll(items: List<IndexedDocumentEntity>): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: IndexedDocumentEntity): Long

  @Query(
    "UPDATE indexed_documents SET displayName = :displayName, normalizedName = :normalizedName, " +
      "parentPath = :parentPath, mimeType = :mimeType, extension = :extension, category = :category, " +
      "sizeBytes = :sizeBytes, lastModifiedEpochMs = :lastModifiedEpochMs, sourceType = :sourceType, " +
      "lastIndexedEpochMs = :lastIndexedEpochMs, partialHash = NULL, contentHash = NULL WHERE id = :id"
  )
  suspend fun updateScannedMetadata(
    id: Long,
    displayName: String,
    normalizedName: String,
    parentPath: String,
    mimeType: String,
    extension: String,
    category: String,
    sizeBytes: Long,
    lastModifiedEpochMs: Long,
    sourceType: String,
    lastIndexedEpochMs: Long,
  )

  @Query("UPDATE indexed_documents SET isFavorite = :favorite WHERE id = :id")
  suspend fun setFavorite(id: Long, favorite: Boolean)

  @Query("UPDATE indexed_documents SET lastOpenedEpochMs = :openedAtEpochMs WHERE id = :id")
  suspend fun recordOpened(id: Long, openedAtEpochMs: Long)

  @Query(
    "UPDATE indexed_documents SET isTrashed = 1, trashedAtEpochMs = :trashedAtEpochMs, " +
      "originalParentPath = :originalParentPath WHERE id = :id"
  )
  suspend fun markTrashed(id: Long, trashedAtEpochMs: Long, originalParentPath: String)

  @Query(
    "UPDATE indexed_documents SET isTrashed = 0, trashedAtEpochMs = NULL, " +
      "parentPath = COALESCE(originalParentPath, parentPath), originalParentPath = NULL WHERE id = :id"
  )
  suspend fun restoreFromTrash(id: Long)

  @Query(
    "UPDATE indexed_documents SET uriString = :uriString, displayName = :displayName, " +
      "normalizedName = :normalizedName, parentPath = :parentPath, mimeType = :mimeType, " +
      "extension = :extension, category = :category, lastModifiedEpochMs = :lastModifiedEpochMs, " +
      "sourceType = :sourceType WHERE id = :id"
  )
  suspend fun updateIdentityInPlace(
    id: Long,
    uriString: String,
    displayName: String,
    normalizedName: String,
    parentPath: String,
    mimeType: String,
    extension: String,
    category: String,
    lastModifiedEpochMs: Long,
    sourceType: String,
  )

  @Query(
    "UPDATE indexed_documents SET isTrashed = 0, trashedAtEpochMs = NULL, " +
      "uriString = :uriString, displayName = :displayName, normalizedName = :normalizedName, " +
      "parentPath = :parentPath, originalParentPath = NULL, lastModifiedEpochMs = :lastModifiedEpochMs " +
      "WHERE id = :id"
  )
  suspend fun restoreFromTrashAtLocation(
    id: Long,
    uriString: String,
    displayName: String,
    normalizedName: String,
    parentPath: String,
    lastModifiedEpochMs: Long,
  )

  @Query("DELETE FROM indexed_documents WHERE id IN (:ids)")
  suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface CollectionDao {
  @Query("SELECT * FROM collections ORDER BY updatedAtEpochMs DESC, createdAtEpochMs DESC")
  fun observeCollections(): Flow<List<CollectionEntity>>

  @Query(
    "SELECT c.id, c.name, c.accentHex, c.iconName, c.createdAtEpochMs, c.updatedAtEpochMs, " +
      "SUM(CASE WHEN d.id IS NOT NULL AND d.isTrashed = 0 THEN 1 ELSE 0 END) AS documentCount, " +
      "COALESCE(SUM(CASE WHEN d.id IS NOT NULL AND d.isTrashed = 0 THEN d.sizeBytes ELSE 0 END), 0) AS totalBytes " +
      "FROM collections c " +
      "LEFT JOIN document_collection_cross_ref r ON c.id = r.collectionId " +
      "LEFT JOIN indexed_documents d ON d.id = r.documentId " +
      "GROUP BY c.id " +
      "ORDER BY c.updatedAtEpochMs DESC, c.createdAtEpochMs DESC"
  )
  fun observeCollectionsWithStats(): Flow<List<CollectionWithCountRow>>

  @Query("SELECT * FROM collections WHERE id = :collectionId LIMIT 1")
  fun observeById(collectionId: Long): Flow<CollectionEntity?>

  @Query("SELECT * FROM collections WHERE id = :collectionId LIMIT 1")
  suspend fun getById(collectionId: Long): CollectionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(collection: CollectionEntity): Long

  @Query("UPDATE collections SET name = :newName, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :collectionId")
  suspend fun rename(collectionId: Long, newName: String, updatedAtEpochMs: Long)

  @Query("UPDATE collections SET updatedAtEpochMs = :updatedAtEpochMs WHERE id = :collectionId")
  suspend fun touchUpdated(collectionId: Long, updatedAtEpochMs: Long)

  @Query("DELETE FROM collections WHERE id = :collectionId")
  suspend fun deleteById(collectionId: Long)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun addDocumentToCollection(crossRef: DocumentCollectionCrossRef)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun addDocumentsToCollection(crossRefs: List<DocumentCollectionCrossRef>)

  @Query(
    "DELETE FROM document_collection_cross_ref WHERE documentId = :documentId AND collectionId = :collectionId"
  )
  suspend fun removeDocumentFromCollection(documentId: Long, collectionId: Long)

  @Query(
    "DELETE FROM document_collection_cross_ref WHERE collectionId = :collectionId AND documentId IN (:documentIds)"
  )
  suspend fun removeDocumentsFromCollection(documentIds: List<Long>, collectionId: Long)

  @Query(
    "SELECT d.* FROM indexed_documents d " +
      "INNER JOIN document_collection_cross_ref r ON d.id = r.documentId " +
      "WHERE r.collectionId = :collectionId AND d.isTrashed = 0 " +
      "ORDER BY r.addedAtEpochMs DESC, d.lastModifiedEpochMs DESC"
  )
  fun observeDocumentsInCollection(collectionId: Long): Flow<List<IndexedDocumentEntity>>

  @Query("SELECT * FROM document_collection_cross_ref WHERE documentId IN (:documentIds)")
  fun observeCrossRefsForDocuments(documentIds: List<Long>): Flow<List<DocumentCollectionCrossRef>>

  @Query("SELECT * FROM document_collection_cross_ref WHERE documentId IN (:documentIds)")
  suspend fun getCrossRefsForDocuments(documentIds: List<Long>): List<DocumentCollectionCrossRef>
}

@Dao
interface TagDao {
  @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
  fun observeTags(): Flow<List<TagEntity>>

  @Query(
    "SELECT t.id, t.name, t.colorHex, t.createdAtEpochMs, " +
      "SUM(CASE WHEN d.id IS NOT NULL AND d.isTrashed = 0 THEN 1 ELSE 0 END) AS documentCount " +
      "FROM tags t " +
      "LEFT JOIN document_tag_cross_ref r ON t.id = r.tagId " +
      "LEFT JOIN indexed_documents d ON d.id = r.documentId " +
      "GROUP BY t.id " +
      "ORDER BY t.name COLLATE NOCASE ASC"
  )
  fun observeTagsWithStats(): Flow<List<TagWithCountRow>>

  @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
  suspend fun findByNameIgnoreCase(name: String): TagEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(tag: TagEntity): Long

  @Query("UPDATE tags SET name = :newName WHERE id = :tagId")
  suspend fun rename(tagId: Long, newName: String)

  @Query("DELETE FROM tags WHERE id = :tagId")
  suspend fun deleteById(tagId: Long)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun attachTag(crossRef: DocumentTagCrossRef)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun attachTags(crossRefs: List<DocumentTagCrossRef>)

  @Query("DELETE FROM document_tag_cross_ref WHERE documentId = :documentId AND tagId = :tagId")
  suspend fun detachTag(documentId: Long, tagId: Long)

  @Query("DELETE FROM document_tag_cross_ref WHERE tagId = :tagId AND documentId IN (:documentIds)")
  suspend fun detachTagFromDocuments(documentIds: List<Long>, tagId: Long)

  @Query(
    "SELECT t.* FROM tags t " +
      "INNER JOIN document_tag_cross_ref r ON t.id = r.tagId " +
      "WHERE r.documentId = :documentId ORDER BY t.name COLLATE NOCASE ASC"
  )
  fun observeTagsForDocument(documentId: Long): Flow<List<TagEntity>>

  @Query("SELECT * FROM document_tag_cross_ref WHERE documentId IN (:documentIds)")
  fun observeCrossRefsForDocuments(documentIds: List<Long>): Flow<List<DocumentTagCrossRef>>

  @Query("SELECT * FROM document_tag_cross_ref WHERE documentId IN (:documentIds)")
  suspend fun getCrossRefsForDocuments(documentIds: List<Long>): List<DocumentTagCrossRef>
}

@Dao
interface RecycleBinDao {
  @Query("SELECT * FROM recycle_bin_entries ORDER BY trashedAtEpochMs DESC")
  fun observeAll(): Flow<List<RecycleBinEntryEntity>>

  @Query("SELECT COUNT(*) FROM recycle_bin_entries")
  fun observeCount(): Flow<Int>

  @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM recycle_bin_entries")
  fun observeTotalBytes(): Flow<Long>

  @Query("SELECT * FROM recycle_bin_entries ORDER BY trashedAtEpochMs DESC")
  suspend fun getAllEntries(): List<RecycleBinEntryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entry: RecycleBinEntryEntity): Long

  @Query("SELECT * FROM recycle_bin_entries WHERE documentId = :documentId LIMIT 1")
  suspend fun getByDocumentId(documentId: Long): RecycleBinEntryEntity?

  @Query("SELECT * FROM recycle_bin_entries WHERE documentId IN (:documentIds)")
  suspend fun getByDocumentIds(documentIds: List<Long>): List<RecycleBinEntryEntity>

  @Query("SELECT * FROM recycle_bin_entries WHERE expiresAtEpochMs <= :nowEpochMs")
  suspend fun getExpiredEntries(nowEpochMs: Long): List<RecycleBinEntryEntity>

  @Query("DELETE FROM recycle_bin_entries WHERE documentId = :documentId")
  suspend fun deleteByDocumentId(documentId: Long)

  @Query("DELETE FROM recycle_bin_entries WHERE documentId IN (:documentIds)")
  suspend fun deleteByDocumentIds(documentIds: List<Long>)

  @Query("DELETE FROM recycle_bin_entries")
  suspend fun deleteAll()

  @Query("DELETE FROM recycle_bin_entries WHERE expiresAtEpochMs <= :nowEpochMs")
  suspend fun deleteExpiredEntries(nowEpochMs: Long): Int
}

@Dao
interface IndexerStateDao {
  @Query("SELECT * FROM indexer_state WHERE id = 1")
  fun observeState(): Flow<IndexerStateEntity?>

  @Query("SELECT * FROM indexer_state WHERE id = 1")
  suspend fun getState(): IndexerStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(state: IndexerStateEntity)
}

@Dao
interface ViewerStateDao {
  @Query("SELECT * FROM document_reading_states WHERE uriString = :uriString LIMIT 1")
  fun observeReadingState(uriString: String): Flow<DocumentReadingStateEntity?>

  @Query("SELECT * FROM document_reading_states WHERE uriString = :uriString LIMIT 1")
  suspend fun getReadingState(uriString: String): DocumentReadingStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertReadingState(state: DocumentReadingStateEntity)

  @Query("SELECT * FROM document_bookmarks WHERE uriString = :uriString ORDER BY pageIndex ASC")
  fun observeBookmarks(uriString: String): Flow<List<DocumentBookmarkEntity>>

  @Query("SELECT * FROM document_bookmarks WHERE uriString = :uriString ORDER BY pageIndex ASC")
  suspend fun getBookmarks(uriString: String): List<DocumentBookmarkEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertBookmark(bookmark: DocumentBookmarkEntity): Long

  @Query("DELETE FROM document_bookmarks WHERE uriString = :uriString AND pageIndex = :pageIndex")
  suspend fun deleteBookmarkByPage(uriString: String, pageIndex: Int)

  @Query("DELETE FROM document_bookmarks WHERE id = :id")
  suspend fun deleteBookmarkById(id: Long)
}
