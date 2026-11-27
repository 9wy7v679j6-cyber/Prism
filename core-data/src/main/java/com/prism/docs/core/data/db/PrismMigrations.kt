package com.prism.docs.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PrismMigrations {

  /**
   * Upgrades Phase 0 v1 schema (`indexed_documents`, `collections`) to Phase 1a v2 schema:
   * - Adds normalizedName, extension, lastOpenedEpochMs, trashedAtEpochMs, originalParentPath,
   *   contentHash, sourceType, lastIndexedEpochMs to `indexed_documents` (preserving existing rows).
   * - Adds iconName to `collections`.
   * - Creates indexes for displayName, normalizedName, parentPath, mimeType, sizeBytes,
   *   isFavorite, lastOpenedEpochMs, and (isTrashed, trashedAtEpochMs).
   * - Creates `document_collection_cross_ref`, `tags`, `document_tag_cross_ref`,
   *   `recycle_bin_entries`, and `indexer_state`.
   */
  val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Expand indexed_documents preserving all existing v1 rows
        db.execSQL(
          "ALTER TABLE `indexed_documents` ADD COLUMN `normalizedName` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
          "ALTER TABLE `indexed_documents` ADD COLUMN `extension` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL("ALTER TABLE `indexed_documents` ADD COLUMN `lastOpenedEpochMs` INTEGER")
        db.execSQL("ALTER TABLE `indexed_documents` ADD COLUMN `trashedAtEpochMs` INTEGER")
        db.execSQL("ALTER TABLE `indexed_documents` ADD COLUMN `originalParentPath` TEXT")
        db.execSQL("ALTER TABLE `indexed_documents` ADD COLUMN `contentHash` TEXT")
        db.execSQL(
          "ALTER TABLE `indexed_documents` ADD COLUMN `sourceType` TEXT NOT NULL DEFAULT 'FILESYSTEM'"
        )
        db.execSQL(
          "ALTER TABLE `indexed_documents` ADD COLUMN `lastIndexedEpochMs` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL("UPDATE `indexed_documents` SET `normalizedName` = LOWER(`displayName`)")

        // Create v2 indexes on indexed_documents (v1 already had uriString, category, lastModifiedEpochMs)
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_displayName` ON `indexed_documents` (`displayName`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_normalizedName` ON `indexed_documents` (`normalizedName`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_parentPath` ON `indexed_documents` (`parentPath`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_mimeType` ON `indexed_documents` (`mimeType`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_sizeBytes` ON `indexed_documents` (`sizeBytes`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_isFavorite` ON `indexed_documents` (`isFavorite`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_lastOpenedEpochMs` ON `indexed_documents` (`lastOpenedEpochMs`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_isTrashed_trashedAtEpochMs` ON `indexed_documents` (`isTrashed`, `trashedAtEpochMs`)"
        )

        // 2. Expand collections
        db.execSQL(
          "ALTER TABLE `collections` ADD COLUMN `iconName` TEXT NOT NULL DEFAULT 'folder'"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)"
        )

        // 3. Create document_collection_cross_ref
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `document_collection_cross_ref` (
            `documentId` INTEGER NOT NULL,
            `collectionId` INTEGER NOT NULL,
            `addedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`documentId`, `collectionId`),
            FOREIGN KEY(`documentId`) REFERENCES `indexed_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_document_collection_cross_ref_documentId` ON `document_collection_cross_ref` (`documentId`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_document_collection_cross_ref_collectionId` ON `document_collection_cross_ref` (`collectionId`)"
        )

        // 4. Create tags + document_tag_cross_ref
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `tags` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `colorHex` TEXT NOT NULL,
            `createdAtEpochMs` INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `document_tag_cross_ref` (
            `documentId` INTEGER NOT NULL,
            `tagId` INTEGER NOT NULL,
            `taggedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`documentId`, `tagId`),
            FOREIGN KEY(`documentId`) REFERENCES `indexed_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_document_tag_cross_ref_documentId` ON `document_tag_cross_ref` (`documentId`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_document_tag_cross_ref_tagId` ON `document_tag_cross_ref` (`tagId`)"
        )

        // 5. Create 30-day recycle_bin_entries
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `recycle_bin_entries` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `documentId` INTEGER NOT NULL,
            `originalUriString` TEXT NOT NULL,
            `originalPath` TEXT NOT NULL,
            `trashedFilePath` TEXT NOT NULL,
            `displayName` TEXT NOT NULL,
            `sizeBytes` INTEGER NOT NULL,
            `trashedAtEpochMs` INTEGER NOT NULL,
            `expiresAtEpochMs` INTEGER NOT NULL,
            FOREIGN KEY(`documentId`) REFERENCES `indexed_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_recycle_bin_entries_documentId` ON `recycle_bin_entries` (`documentId`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_recycle_bin_entries_expiresAtEpochMs` ON `recycle_bin_entries` (`expiresAtEpochMs`)"
        )

        // 6. Create indexer_state checkpoint table
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `indexer_state` (
            `id` INTEGER NOT NULL,
            `lastFullScanEpochMs` INTEGER NOT NULL,
            `indexedDocumentCount` INTEGER NOT NULL,
            `lastInsertedCount` INTEGER NOT NULL,
            `lastUpdatedCount` INTEGER NOT NULL,
            `lastRemovedCount` INTEGER NOT NULL,
            `status` TEXT NOT NULL,
            PRIMARY KEY(`id`)
          )
          """.trimIndent()
        )
      }
    }

  /**
   * Purges directory rows that were previously indexed (e.g., MediaStore "Android", "media"
   * folders or directory MIME rows) and flags the indexer for a fresh incremental sync.
   */
  val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          DELETE FROM `indexed_documents`
          WHERE `isTrashed` = 0
            AND (
              `mimeType` IN ('vnd.android.document/directory', 'resource/folder', 'inode/directory')
              OR (`sourceType` = 'MEDIASTORE' AND `extension` = '' AND `category` = 'OTHER')
            )
          """.trimIndent()
        )
        db.execSQL(
          "UPDATE `indexer_state` SET `lastFullScanEpochMs` = 0, `status` = 'NEEDS_RESYNC' WHERE `id` = 1"
        )
      }
    }

  /**
   * Upgrades v3 schema to v4 for Duplicate Finder hash caching, Storage Analyzer size indexing,
   * and collection update timestamps:
   * - Adds `partialHash` column and indexes on `contentHash` and `(isTrashed, sizeBytes)` to
   *   `indexed_documents`.
   * - Adds `updatedAtEpochMs` to `collections` initialized from `createdAtEpochMs`.
   */
  val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `indexed_documents` ADD COLUMN `partialHash` TEXT")
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_contentHash` ON `indexed_documents` (`contentHash`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_indexed_documents_isTrashed_sizeBytes` ON `indexed_documents` (`isTrashed`, `sizeBytes`)"
        )
        db.execSQL(
          "ALTER TABLE `collections` ADD COLUMN `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
          "UPDATE `collections` SET `updatedAtEpochMs` = `createdAtEpochMs` WHERE `updatedAtEpochMs` = 0"
        )
      }
    }

  /**
   * Upgrades v4 schema to v5 for Document Viewer reading state (last page, zoom, paper mode,
   * scroll mode) and per-page user bookmarks.
   */
  val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `document_reading_states` (
            `uriString` TEXT NOT NULL,
            `lastPage` INTEGER NOT NULL,
            `zoomScale` REAL NOT NULL,
            `scrollOffsetFraction` REAL NOT NULL,
            `scrollMode` TEXT NOT NULL,
            `paperMode` TEXT NOT NULL,
            `updatedAtEpochMs` INTEGER NOT NULL,
            PRIMARY KEY(`uriString`)
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `document_bookmarks` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `uriString` TEXT NOT NULL,
            `pageIndex` INTEGER NOT NULL,
            `label` TEXT NOT NULL,
            `createdAtEpochMs` INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_document_bookmarks_uriString_pageIndex` ON `document_bookmarks` (`uriString`, `pageIndex`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_document_bookmarks_uriString` ON `document_bookmarks` (`uriString`)"
        )
      }
    }

  val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
