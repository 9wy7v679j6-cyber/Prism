package com.prism.docs.core.data.duplicates

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.model.DirectoryChangeEvent
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.storage.DocumentStorageGateway
import com.prism.docs.core.data.storage.FileOperationsManager
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuplicateFinderEngineTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var database: PrismDatabase
  private lateinit var realReader: AndroidDuplicateByteReader

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, PrismDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    realReader = AndroidDuplicateByteReader(context)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun threeStageGroupingFiltersBySizeThenPartialHashThenFullSha256() = runTest {
    // Prepare >128 KB files so first 64 KB + last 64 KB differ from middle bytes
    val totalSize = 180 * 1024
    val basePayload = ByteArray(totalSize) { idx -> (idx % 251).toByte() }

    // Group A (true duplicates: id 1 & 2, id 2 is newer)
    val dupFileOld = tempFolder.newFile("contract_old.pdf").apply { writeBytes(basePayload) }
    val dupFileNew = tempFolder.newFile("contract_new.pdf").apply { writeBytes(basePayload) }

    // Same size & IDENTICAL first 64 KB + last 64 KB, DIFFERENT middle byte -> survives stage 2, caught in stage 3!
    val middleDiffPayload = basePayload.copyOf().apply { this[90_000] = (this[90_000] + 7).toByte() }
    val middleDiffFile =
      tempFolder.newFile("contract_mid_diff.pdf").apply { writeBytes(middleDiffPayload) }

    // Same size as above, DIFFERENT head byte -> survives stage 1, eliminated at stage 2 (never full-hashed!)
    val headDiffPayload = basePayload.copyOf().apply { this[128] = (this[128] + 13).toByte() }
    val headDiffFile =
      tempFolder.newFile("contract_head_diff.pdf").apply { writeBytes(headDiffPayload) }

    // Unique size -> eliminated at stage 1 (never partial-hashed or full-hashed!)
    val uniqueSizeFile =
      tempFolder.newFile("notes.pdf").apply { writeBytes(ByteArray(4096) { 1 }) }

    val dao = database.documentIndexDao()
    val entities =
      listOf(
        makeEntity(1L, dupFileOld, lastModified = 1_000L),
        makeEntity(2L, dupFileNew, lastModified = 5_000L),
        makeEntity(3L, middleDiffFile, lastModified = 3_000L),
        makeEntity(4L, headDiffFile, lastModified = 4_000L),
        makeEntity(5L, uniqueSizeFile, lastModified = 6_000L),
      )
    dao.upsertAll(entities)

    val partialCalls = mutableListOf<String>()
    val fullCalls = mutableListOf<String>()
    val trackingReader =
      object : DuplicateByteReader {
        override suspend fun computePartialHash(uriString: String, windowBytes: Int): String? {
          partialCalls.add(uriString)
          return realReader.computePartialHash(uriString, windowBytes)
        }

        override suspend fun computeFullHash(uriString: String): String? {
          fullCalls.add(uriString)
          return realReader.computeFullHash(uriString)
        }
      }

    val engine = DuplicateFinderEngine(dao, trackingReader)
    val result = engine.scanActiveDuplicates()

    // Unique size file (#5) was skipped in Stage 2; only 4 size-colliding files were partial-hashed
    assertEquals(4, result.sizeCandidateCount)
    assertEquals(4, partialCalls.size)
    assertFalse(partialCalls.contains(Uri.fromFile(uniqueSizeFile).toString()))

    // Head-diff file (#4) was eliminated in Stage 2; only 3 partial-colliding files were full-hashed
    assertEquals(3, result.partialCandidateCount)
    assertEquals(3, fullCalls.size)
    assertFalse(fullCalls.contains(Uri.fromFile(headDiffFile).toString()))

    // Middle-diff file (#3) had different SHA-256 so only #1 and #2 form the confirmed duplicate group
    assertEquals(1, result.groups.size)
    val group = result.groups.single()
    assertEquals(listOf(2L, 1L), group.documents.map { it.id }) // newest first
    assertEquals(2L, group.newestToKeep.id)
    assertEquals(listOf(1L), group.duplicatesToTrash.map { it.id })
    assertEquals(totalSize.toLong(), group.reclaimableBytes)
  }

  @Test
  fun localAndStreamPartialSha256ProduceIdenticalDigestsAcrossSizes() {
    val sizes = listOf(0, 512, 65_536, 100_000, 131_072, 220_000)
    for ((idx, size) in sizes.withIndex()) {
      val bytes = ByteArray(size) { pos -> ((pos * 31 + idx) and 0xFF).toByte() }
      val file = tempFolder.newFile("sample_$size.bin").apply { writeBytes(bytes) }
      val localDigest = computeLocalPartialSha256(file, DuplicateFinderEngine.PARTIAL_HASH_WINDOW_BYTES)
      val streamDigest =
        computeStreamPartialSha256(
          ByteArrayInputStream(bytes),
          DuplicateFinderEngine.PARTIAL_HASH_WINDOW_BYTES,
        )
      assertEquals("Partial digest mismatch for size=$size", localDigest, streamDigest)
    }
  }

  @Test
  fun keepNewestMovesDuplicatesToRecycleBinAndNeverPermanentlyDeletes() = runTest {
    val payload = ByteArray(32_768) { 42 }
    val oldest = tempFolder.newFile("invoice_oldest.pdf").apply { writeBytes(payload) }
    val middle = tempFolder.newFile("invoice_middle.pdf").apply { writeBytes(payload) }
    val newest = tempFolder.newFile("invoice_newest.pdf").apply { writeBytes(payload) }

    val dao = database.documentIndexDao()
    dao.upsertAll(
      listOf(
        makeEntity(10L, oldest, lastModified = 1_000L),
        makeEntity(11L, middle, lastModified = 2_000L),
        makeEntity(12L, newest, lastModified = 9_000L),
      )
    )

    val engine = DuplicateFinderEngine(dao, realReader)
    val result = engine.scanActiveDuplicates()
    assertEquals(1, result.groups.size)

    val fakeGateway =
      object : DocumentStorageGateway {
        override suspend fun currentAccessState() =
          com.prism.docs.core.data.model.StorageAccessState(
            level = com.prism.docs.core.data.model.StorageAccessLevel.FULL_ALL_FILES,
            sdkInt = 36,
            hasAllFilesPermission = true,
            safFolders = emptyList(),
          )
        override suspend fun listStorageRoots(): List<StorageVolumeRoot> = emptyList()
        override suspend fun listDirectoryChildren(directoryPathOrUri: String): List<ScannedDocumentMetadata> =
          emptyList()
        override suspend fun scanAccessibleDocuments(
          batchSize: Int,
          onBatch: suspend (List<ScannedDocumentMetadata>) -> Unit,
        ): Int = 0
        override fun observeOpenDirectory(directoryPathOrUri: String): Flow<DirectoryChangeEvent> =
          emptyFlow()
      }
    val volumeRoot = tempFolder.root
    val opsManager =
      FileOperationsManager(
        context = context,
        database = database,
        documentIndexDao = dao,
        recycleBinDao = database.recycleBinDao(),
        storageGateway = fakeGateway,
        volumeRootResolver = { volumeRoot },
        customVerifier = null,
      )

    val idsToTrash = DuplicateFinderEngine.selectDocumentsToTrash(result.groups)
    assertEquals(listOf(11L, 10L), idsToTrash)

    val trashResult = opsManager.moveToRecycleBin(idsToTrash)
    assertTrue(trashResult.success)
    assertEquals(2, trashResult.affectedCount)

    // Newest copy stays untouched on disk and active in DB
    assertTrue(newest.exists())
    assertFalse(oldest.exists())
    assertFalse(middle.exists())

    // Both duplicates are in Recycle Bin (.PrismTrash), NOT permanently deleted
    val trashEntries = database.recycleBinDao().getAllEntries()
    assertEquals(2, trashEntries.size)
    assertTrue(trashEntries.all { File(it.trashedFilePath).exists() })

    // Reactive duplicate groups immediately empty once duplicates are binned
    val remainingGroups = engine.observeConfirmedDuplicateGroups().first()
    assertTrue(remainingGroups.isEmpty())
  }

  private fun makeEntity(
    id: Long,
    file: File,
    lastModified: Long,
  ): IndexedDocumentEntity =
    IndexedDocumentEntity(
      id = id,
      uriString = Uri.fromFile(file).toString(),
      displayName = file.name,
      normalizedName = file.name.lowercase(),
      parentPath = file.parent.orEmpty(),
      mimeType = "application/pdf",
      extension = "pdf",
      category = DocumentCategory.PDF.name,
      sizeBytes = file.length(),
      lastModifiedEpochMs = lastModified,
    )
}
