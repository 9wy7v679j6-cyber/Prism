package com.prism.docs.core.data.storage

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.prism.docs.core.data.db.CollectionEntity
import com.prism.docs.core.data.db.DocumentCollectionCrossRef
import com.prism.docs.core.data.db.DocumentTagCrossRef
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.db.TagEntity
import com.prism.docs.core.data.model.DirectoryChangeEvent
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.NameCollisionPolicy
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.StorageAccessState
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.model.TransferOperationType
import com.prism.docs.core.data.paging.DocumentRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileOperationsAndRecycleBinTest {

  private lateinit var context: Context
  private lateinit var database: PrismDatabase
  private lateinit var volumeRootA: File
  private lateinit var volumeRootB: File
  private lateinit var fakeGateway: FakeVolumeStorageGateway

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, PrismDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val base = File(context.cacheDir, "file_ops_test_${System.nanoTime()}").apply { mkdirs() }
    volumeRootA = File(base, "vol_internal").apply { mkdirs() }
    volumeRootB = File(base, "vol_sdcard").apply { mkdirs() }
    fakeGateway = FakeVolumeStorageGateway(volumeRootA, volumeRootB)
  }

  @After
  fun tearDown() {
    database.close()
    volumeRootA.parentFile?.deleteRecursively()
  }

  private fun createManager(verifier: CopyIntegrityVerifier? = null): FileOperationsManager =
    FileOperationsManager(
      context = context,
      database = database,
      documentIndexDao = database.documentIndexDao(),
      recycleBinDao = database.recycleBinDao(),
      storageGateway = fakeGateway,
      volumeRootResolver = { file ->
        val path = file.canonicalPath
        if (path.startsWith(volumeRootB.canonicalPath)) volumeRootB else volumeRootA
      },
      customVerifier = verifier,
    )

  private suspend fun seedLocalFile(
    dir: File,
    name: String,
    content: String,
    favorite: Boolean = false,
  ): Pair<File, Long> {
    dir.mkdirs()
    val file = File(dir, name).apply { writeText(content) }
    val mime = DocumentCategoryClassifier.inferMimeType(name, null)
    val id =
      database.documentIndexDao().upsert(
        IndexedDocumentEntity(
          uriString = Uri.fromFile(file).toString(),
          displayName = name,
          normalizedName = name.lowercase(),
          parentPath = dir.absolutePath,
          mimeType = mime,
          extension = DocumentCategoryClassifier.extractExtension(name),
          category = DocumentCategoryClassifier.classify(mime, name).name,
          sizeBytes = file.length(),
          lastModifiedEpochMs = file.lastModified(),
          isFavorite = favorite,
          sourceType = StorageSourceType.FILESYSTEM.name,
        )
      )
    return file to id
  }

  @Test
  fun collisionHandlingAutoRenamesSkipsAndOverwritesSafely() = runTest {
    val manager = createManager()
    val srcDir = File(volumeRootA, "Source")
    val dstDir = File(volumeRootA, "Dest").apply { mkdirs() }

    val (srcFile, srcId) = seedLocalFile(srcDir, "report.pdf", "NEW_CONTENT_V2")
    val existingDest = File(dstDir, "report.pdf").apply { writeText("ORIGINAL_DEST_V1") }

    // 1. AUTO_RENAME produces report (1).pdf and then report (2).pdf
    val r1 =
      manager.copyOrMoveDocuments(
        documentIds = listOf(srcId),
        destinationDirPathOrUri = dstDir.absolutePath,
        operationType = TransferOperationType.COPY,
        collisionPolicy = NameCollisionPolicy.AUTO_RENAME,
      )
    assertTrue(r1.success)
    assertEquals(1, r1.affectedCount)
    val renamed1 = File(dstDir, "report (1).pdf")
    assertTrue(renamed1.exists())
    assertEquals("NEW_CONTENT_V2", renamed1.readText())
    assertEquals("ORIGINAL_DEST_V1", existingDest.readText())

    val r2 =
      manager.copyOrMoveDocuments(
        documentIds = listOf(srcId),
        destinationDirPathOrUri = dstDir.absolutePath,
        operationType = TransferOperationType.COPY,
        collisionPolicy = NameCollisionPolicy.AUTO_RENAME,
      )
    assertTrue(r2.success)
    assertTrue(File(dstDir, "report (2).pdf").exists())

    // 2. SKIP leaves existing destination untouched
    val skipped =
      manager.copyOrMoveDocuments(
        documentIds = listOf(srcId),
        destinationDirPathOrUri = dstDir.absolutePath,
        operationType = TransferOperationType.COPY,
        collisionPolicy = NameCollisionPolicy.SKIP,
      )
    assertEquals(1, skipped.skippedCount)
    assertEquals(0, skipped.affectedCount)
    assertEquals("ORIGINAL_DEST_V1", existingDest.readText())

    // 3. OVERWRITE replaces existing destination atomically with verified new content
    val overwritten =
      manager.copyOrMoveDocuments(
        documentIds = listOf(srcId),
        destinationDirPathOrUri = dstDir.absolutePath,
        operationType = TransferOperationType.COPY,
        collisionPolicy = NameCollisionPolicy.OVERWRITE,
      )
    assertTrue(overwritten.success)
    assertEquals(1, overwritten.affectedCount)
    assertEquals("NEW_CONTENT_V2", existingDest.readText())
    assertTrue("Source must remain intact after COPY", srcFile.exists())
  }

  @Test
  fun crossVolumeMoveCopiesVerifiesThenDeletesAndNeverDeletesSourceOnVerificationFailure() =
    runTest {
      val srcDir = File(volumeRootA, "Docs")
      val dstDir = File(volumeRootB, "Backup").apply { mkdirs() }

      // A) Simulated verification failure: source MUST stay intact and no partial file remains
      val (protectedSrc, protectedId) =
        seedLocalFile(srcDir, "contract.pdf", "CRITICAL_SIGNED_CONTRACT_DATA")
      val failingVerifierManager = createManager(verifier = { _, _, _, _ -> false })

      val failedMove =
        failingVerifierManager.copyOrMoveDocuments(
          documentIds = listOf(protectedId),
          destinationDirPathOrUri = dstDir.absolutePath,
          operationType = TransferOperationType.MOVE,
          collisionPolicy = NameCollisionPolicy.AUTO_RENAME,
        )
      assertFalse(failedMove.success)
      assertEquals(1, failedMove.failedCount)
      assertTrue("Source MUST NOT be deleted when verification fails", protectedSrc.exists())
      assertEquals("CRITICAL_SIGNED_CONTRACT_DATA", protectedSrc.readText())
      assertEquals(
        "No partial or committed target file should remain on verification failure",
        0,
        dstDir.listFiles().orEmpty().size,
      )

      // B) Real SHA-256 + length verification across volumes: copies, verifies, deletes source, keeps Room id
      val realManager = createManager()
      val tagId =
        database.tagDao().insert(TagEntity(name = "Legal", colorHex = "#0066E6", createdAtEpochMs = 1L))
      database.tagDao().attachTag(DocumentTagCrossRef(documentId = protectedId, tagId = tagId))

      val okMove =
        realManager.copyOrMoveDocuments(
          documentIds = listOf(protectedId),
          destinationDirPathOrUri = dstDir.absolutePath,
          operationType = TransferOperationType.MOVE,
          collisionPolicy = NameCollisionPolicy.AUTO_RENAME,
        )
      assertTrue(okMove.success)
      assertEquals(1, okMove.affectedCount)
      assertFalse("Source should be deleted ONLY after verified copy", protectedSrc.exists())

      val movedDest = File(dstDir, "contract.pdf")
      assertTrue(movedDest.exists())
      assertEquals("CRITICAL_SIGNED_CONTRACT_DATA", movedDest.readText())

      // Room primary key + tags preserved across in-app move!
      val updatedRow = database.documentIndexDao().getById(protectedId)
      assertNotNull(updatedRow)
      assertEquals(Uri.fromFile(movedDest).toString(), updatedRow!!.uriString)
      val tagsAfterMove = database.tagDao().observeTagsForDocument(protectedId).first()
      assertEquals(listOf("Legal"), tagsAfterMove.map { it.name })
    }

  @Test
  fun recycleBinUsesSameVolumeHiddenFolderRestoresWithConflictHandlingAndPurgesAfter30Days() =
    runTest {
      val manager = createManager()
      val docsDir = File(volumeRootA, "Work")
      val (fileA, idA) = seedLocalFile(docsDir, "budget.xlsx", "Q4_BUDGET_2026", favorite = true)
      val (fileB, idB) = seedLocalFile(docsDir, "old_notes.txt", "EXPIRED_NOTES")

      // Attach collection & tag to fileA to prove metadata survives Trash -> Restore
      val colId =
        database.collectionDao()
          .upsert(CollectionEntity(name = "Finance", accentHex = "#1F9D6B", createdAtEpochMs = 1L))
      database.collectionDao()
        .addDocumentToCollection(DocumentCollectionCrossRef(documentId = idA, collectionId = colId))

      val t0 = 1_700_000_000_000L
      val trashRes = manager.moveToRecycleBin(listOf(idA, idB), nowEpochMs = t0)
      assertTrue(trashRes.success)
      assertEquals(2, trashRes.affectedCount)
      assertFalse(fileA.exists())
      assertFalse(fileB.exists())

      // Verify hidden .PrismTrash + .nomedia exists on the SAME volume root (volumeRootA)
      val hiddenTrash = File(volumeRootA, FileOperationsManager.HIDDEN_TRASH_DIR_NAME)
      assertTrue(hiddenTrash.exists() && hiddenTrash.isDirectory)
      assertTrue(File(hiddenTrash, ".nomedia").exists())

      val entryA = database.recycleBinDao().getByDocumentId(idA)!!
      val entryB = database.recycleBinDao().getByDocumentId(idB)!!
      assertTrue(File(entryA.trashedFilePath).parentFile?.canonicalPath == hiddenTrash.canonicalPath)
      assertEquals(fileA.absolutePath, entryA.originalPath)

      // Create a conflicting file at fileA's original path before restoring!
      File(docsDir, "budget.xlsx").writeText("NEW_FILE_AT_ORIGINAL_PATH")

      val restoreRes =
        manager.restoreFromRecycleBin(listOf(idA), collisionPolicy = NameCollisionPolicy.AUTO_RENAME)
      assertTrue(restoreRes.success)
      assertEquals(1, restoreRes.affectedCount)
      assertFalse(File(entryA.trashedFilePath).exists())

      // New file untouched; restored file cleanly auto-renamed to budget (1).xlsx
      assertEquals("NEW_FILE_AT_ORIGINAL_PATH", File(docsDir, "budget.xlsx").readText())
      val restoredFile = File(docsDir, "budget (1).xlsx")
      assertTrue(restoredFile.exists())
      assertEquals("Q4_BUDGET_2026", restoredFile.readText())

      // Favorite & Collection survive trash -> restore
      val restoredEntity = database.documentIndexDao().getById(idA)!!
      assertFalse(restoredEntity.isTrashed)
      assertTrue(restoredEntity.isFavorite)
      assertEquals(Uri.fromFile(restoredFile).toString(), restoredEntity.uriString)
      val colDocs = database.collectionDao().observeDocumentsInCollection(colId).first()
      assertEquals(listOf(idA), colDocs.map { it.id })

      // 29 days later: fileB should NOT be purged yet
      val day29 = t0 + (29L * 24L * 60L * 60L * 1000L)
      assertEquals(0, manager.purgeExpiredRecycleBin(day29))
      assertTrue(File(entryB.trashedFilePath).exists())

      // 31 days later: fileB is physically deleted from .PrismTrash AND removed from Room
      val day31 = t0 + DocumentRepository.RECYCLE_BIN_RETENTION_MS + 1_000L
      assertEquals(1, manager.purgeExpiredRecycleBin(day31))
      assertFalse("Expired staged file must be physically deleted", File(entryB.trashedFilePath).exists())
      assertNull(database.recycleBinDao().getByDocumentId(idB))
      assertNull(database.documentIndexDao().getById(idB))
    }

  private class FakeVolumeStorageGateway(
    private val volA: File,
    private val volB: File,
  ) : DocumentStorageGateway {
    override suspend fun currentAccessState(): StorageAccessState =
      StorageAccessState(
        level = StorageAccessLevel.FULL_ALL_FILES,
        sdkInt = 36,
        hasAllFilesPermission = true,
        safFolders = emptyList(),
      )

    override suspend fun listStorageRoots(): List<StorageVolumeRoot> =
      listOf(
        StorageVolumeRoot("vol_a", "Internal", volA.absolutePath, StorageSourceType.FILESYSTEM),
        StorageVolumeRoot("vol_b", "SD Card", volB.absolutePath, StorageSourceType.FILESYSTEM, true),
      )

    override suspend fun listDirectoryChildren(
      directoryPathOrUri: String,
    ): List<ScannedDocumentMetadata> = emptyList()

    override suspend fun scanAccessibleDocuments(
      batchSize: Int,
      onBatch: suspend (List<ScannedDocumentMetadata>) -> Unit,
    ): Int = 0

    override fun observeOpenDirectory(directoryPathOrUri: String): Flow<DirectoryChangeEvent> =
      emptyFlow()
  }
}
