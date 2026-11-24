package com.prism.docs.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "indexed_documents",
  indices = [
    Index(value = ["uriString"], unique = true),
    Index(value = ["displayName"]),
    Index(value = ["normalizedName"]),
    Index(value = ["parentPath"]),
    Index(value = ["mimeType"]),
    Index(value = ["category"]),
    Index(value = ["lastModifiedEpochMs"]),
    Index(value = ["sizeBytes"]),
    Index(value = ["isFavorite"]),
    Index(value = ["lastOpenedEpochMs"]),
    Index(value = ["isTrashed", "trashedAtEpochMs"]),
    Index(value = ["contentHash"]),
    Index(value = ["isTrashed", "sizeBytes"]),
  ],
)
data class IndexedDocumentEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val uriString: String,
  val displayName: String,
  @ColumnInfo(defaultValue = "") val normalizedName: String = displayName.lowercase(),
  val parentPath: String,
  val mimeType: String,
  @ColumnInfo(defaultValue = "") val extension: String = "",
  val category: String,
  val sizeBytes: Long,
  val lastModifiedEpochMs: Long,
  val lastOpenedEpochMs: Long? = null,
  val isFavorite: Boolean = false,
  val isTrashed: Boolean = false,
  val trashedAtEpochMs: Long? = null,
  val originalParentPath: String? = null,
  val partialHash: String? = null,
  val contentHash: String? = null,
  @ColumnInfo(defaultValue = "FILESYSTEM") val sourceType: String = "FILESYSTEM",
  @ColumnInfo(defaultValue = "0") val lastIndexedEpochMs: Long = 0L,
)

@Entity(
  tableName = "collections",
  indices = [Index(value = ["name"])],
)
data class CollectionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val name: String,
  val accentHex: String,
  @ColumnInfo(defaultValue = "folder") val iconName: String = "folder",
  val createdAtEpochMs: Long,
  @ColumnInfo(defaultValue = "0") val updatedAtEpochMs: Long = createdAtEpochMs,
)

@Entity(
  tableName = "document_collection_cross_ref",
  primaryKeys = ["documentId", "collectionId"],
  foreignKeys = [
    ForeignKey(
      entity = IndexedDocumentEntity::class,
      parentColumns = ["id"],
      childColumns = ["documentId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = CollectionEntity::class,
      parentColumns = ["id"],
      childColumns = ["collectionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["documentId"]),
    Index(value = ["collectionId"]),
  ],
)
data class DocumentCollectionCrossRef(
  val documentId: Long,
  val collectionId: Long,
  val addedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "tags",
  indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val name: String,
  val colorHex: String,
  val createdAtEpochMs: Long,
)

@Entity(
  tableName = "document_tag_cross_ref",
  primaryKeys = ["documentId", "tagId"],
  foreignKeys = [
    ForeignKey(
      entity = IndexedDocumentEntity::class,
      parentColumns = ["id"],
      childColumns = ["documentId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = TagEntity::class,
      parentColumns = ["id"],
      childColumns = ["tagId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["documentId"]),
    Index(value = ["tagId"]),
  ],
)
data class DocumentTagCrossRef(
  val documentId: Long,
  val tagId: Long,
  val taggedAtEpochMs: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "recycle_bin_entries",
  foreignKeys = [
    ForeignKey(
      entity = IndexedDocumentEntity::class,
      parentColumns = ["id"],
      childColumns = ["documentId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [
    Index(value = ["documentId"], unique = true),
    Index(value = ["expiresAtEpochMs"]),
  ],
)
data class RecycleBinEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val documentId: Long,
  val originalUriString: String,
  val originalPath: String,
  val trashedFilePath: String,
  val displayName: String,
  val sizeBytes: Long,
  val trashedAtEpochMs: Long,
  val expiresAtEpochMs: Long,
)

@Entity(tableName = "indexer_state")
data class IndexerStateEntity(
  @PrimaryKey val id: Int = 1,
  val lastFullScanEpochMs: Long = 0L,
  val indexedDocumentCount: Int = 0,
  val lastInsertedCount: Int = 0,
  val lastUpdatedCount: Int = 0,
  val lastRemovedCount: Int = 0,
  val status: String = "IDLE",
)
