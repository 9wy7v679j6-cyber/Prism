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

  val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
