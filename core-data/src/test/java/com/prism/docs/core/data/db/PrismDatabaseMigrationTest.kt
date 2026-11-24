package com.prism.docs.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrismDatabaseMigrationTest {

  private val testDbName = "prism_migration_test.db"

  @get:Rule
  val helper: MigrationTestHelper =
    MigrationTestHelper(
      InstrumentationRegistry.getInstrumentation(),
      PrismDatabase::class.java,
    )

  @Test
  fun migrate1To2PreservesExistingRowsAndCreatesAllRequiredIndexesAndTables() = runTest {
    // 1. Create authentic v1 schema database from schemas/1.json and seed v1 rows
    helper.createDatabase(testDbName, 1).apply {
      execSQL(
        """
        INSERT INTO indexed_documents
          (id, uriString, displayName, parentPath, mimeType, category, sizeBytes, lastModifiedEpochMs, isFavorite, isTrashed)
        VALUES
          (1, 'file:///storage/emulated/0/Documents/Contract.PDF', 'Contract.PDF', '/storage/emulated/0/Documents', 'application/pdf', 'PDF', 48200, 1700000000000, 1, 0)
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO collections (id, name, accentHex, createdAtEpochMs)
        VALUES (10, 'Taxes 2026', '#0066E6', 1700000005000)
        """.trimIndent()
      )
      close()
    }

    // 2. Run MIGRATION_1_2 and validate migrated schema strictly against exported 2.json
    val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, PrismMigrations.MIGRATION_1_2)

    // Verify all required SQLite indexes exist on indexed_documents
    val indexNames = mutableSetOf<String>()
    migratedDb.query("PRAGMA index_list('indexed_documents')").use { cursor ->
      val nameCol = cursor.getColumnIndex("name")
      while (cursor.moveToNext()) {
        if (nameCol >= 0) indexNames.add(cursor.getString(nameCol))
      }
    }
    val expectedIndexes =
      listOf(
        "index_indexed_documents_uriString",
        "index_indexed_documents_displayName",
        "index_indexed_documents_normalizedName",
        "index_indexed_documents_parentPath",
        "index_indexed_documents_mimeType",
        "index_indexed_documents_category",
        "index_indexed_documents_lastModifiedEpochMs",
        "index_indexed_documents_sizeBytes",
        "index_indexed_documents_isFavorite",
        "index_indexed_documents_lastOpenedEpochMs",
        "index_indexed_documents_isTrashed_trashedAtEpochMs",
      )
    expectedIndexes.forEach { expected ->
      assertTrue("Missing SQLite index $expected (found: $indexNames)", expected in indexNames)
    }
    migratedDb.close()

    // 3. Re-open via RoomDatabase and confirm migrated data + new DAOs operate cleanly
    val roomDb =
      Room.databaseBuilder(
          ApplicationProvider.getApplicationContext(),
          PrismDatabase::class.java,
          testDbName,
        )
        .addMigrations(*PrismMigrations.ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()
    try {
      val doc = roomDb.documentIndexDao().getById(1L)
      assertEquals("Contract.PDF", doc?.displayName)
      assertEquals("contract.pdf", doc?.normalizedName)
      assertEquals(true, doc?.isFavorite)

      val collections = roomDb.collectionDao().observeCollections().first()
      assertEquals(1, collections.size)
      assertEquals("Taxes 2026", collections.first().name)
      assertEquals("folder", collections.first().iconName)

      val tagId = roomDb.tagDao().insert(TagEntity(name = "Urgent", colorHex = "#E5484D", createdAtEpochMs = 1L))
      roomDb.tagDao().attachTag(DocumentTagCrossRef(documentId = 1L, tagId = tagId))
      val attached = roomDb.tagDao().observeTagsForDocument(1L).first()
      assertEquals(listOf("Urgent"), attached.map { it.name })
    } finally {
      roomDb.close()
    }
  }

  @Test
  fun migrate2To3RemovesPreviouslyIndexedDirectoryRowsAndPreservesFiles() = runTest {
    val dbName = "prism_migration_2_to_3_test.db"
    helper.createDatabase(dbName, 2).apply {
      // Valid PDF document that must survive
      execSQL(
        """
        INSERT INTO indexed_documents
          (id, uriString, displayName, normalizedName, parentPath, mimeType, extension, category, sizeBytes, lastModifiedEpochMs, isFavorite, isTrashed, sourceType, lastIndexedEpochMs)
        VALUES
          (1, 'file:///storage/emulated/0/Documents/Quarterly.pdf', 'Quarterly.pdf', 'quarterly.pdf', '/storage/emulated/0/Documents', 'application/pdf', 'pdf', 'PDF', 92000, 1700000000000, 1, 0, 'FILESYSTEM', 1700000001000)
        """.trimIndent()
      )
      // Previously indexed MediaStore folder rows ("Android", "media") that must be deleted
      execSQL(
        """
        INSERT INTO indexed_documents
          (id, uriString, displayName, normalizedName, parentPath, mimeType, extension, category, sizeBytes, lastModifiedEpochMs, isFavorite, isTrashed, sourceType, lastIndexedEpochMs)
        VALUES
          (2, 'content://media/external/file/101', 'Android', 'android', '/storage/emulated/0', 'application/octet-stream', '', 'OTHER', 4096, 1700000000000, 0, 0, 'MEDIASTORE', 1700000001000),
          (3, 'content://media/external/file/102', 'media', 'media', '/storage/emulated/0/Android', 'application/octet-stream', '', 'OTHER', 4096, 1700000000000, 0, 0, 'MEDIASTORE', 1700000001000),
          (4, 'content://com.android.externalstorage.documents/tree/primary%3AWork/document/primary%3AWork%2FFolder', 'Folder', 'folder', 'content://com.android.externalstorage.documents/tree/primary%3AWork', 'vnd.android.document/directory', '', 'OTHER', 0, 1700000000000, 0, 0, 'SAF', 1700000001000)
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO indexer_state
          (id, lastFullScanEpochMs, indexedDocumentCount, lastInsertedCount, lastUpdatedCount, lastRemovedCount, status)
        VALUES
          (1, 1700000001000, 4, 4, 0, 0, 'IDLE')
        """.trimIndent()
      )
      close()
    }

    val migratedDb = helper.runMigrationsAndValidate(dbName, 3, true, PrismMigrations.MIGRATION_2_3)
    migratedDb.close()

    val roomDb =
      Room.databaseBuilder(
          ApplicationProvider.getApplicationContext(),
          PrismDatabase::class.java,
          dbName,
        )
        .addMigrations(*PrismMigrations.ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()
    try {
      val remaining = roomDb.documentIndexDao().observeActiveDocuments().first()
      assertEquals(listOf("Quarterly.pdf"), remaining.map { it.displayName })
      val state = roomDb.indexerStateDao().getState()
      assertEquals("NEEDS_RESYNC", state?.status)
      assertEquals(0L, state?.lastFullScanEpochMs)
    } finally {
      roomDb.close()
    }
  }
}
