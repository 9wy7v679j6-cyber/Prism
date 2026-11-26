package com.prism.docs.core.data.paging

import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.prism.docs.core.data.db.PrismDatabase
import com.prism.docs.core.data.indexer.IncrementalDocumentIndexer
import com.prism.docs.core.data.model.DirectoryChangeEvent
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.FileListQuery
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.SortDirection
import com.prism.docs.core.data.model.StorageAccessLevel
import com.prism.docs.core.data.model.StorageAccessState
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.storage.DocumentCategoryClassifier
import com.prism.docs.core.data.storage.DocumentStorageGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentRepositoryAndIndexerTest {

  private lateinit var db: PrismDatabase
  private lateinit var fakeGateway: FakeStorageGateway
  private lateinit var repository: DocumentRepository
  private lateinit var indexer: IncrementalDocumentIndexer

  @Before
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext(),
          PrismDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
    fakeGateway = FakeStorageGateway()
    repository =
      DocumentRepository(
        database = db,
        documentIndexDao = db.documentIndexDao(),
        collectionDao = db.collectionDao(),
        tagDao = db.tagDao(),
        recycleBinDao = db.recycleBinDao(),
        storageGateway = fakeGateway,
      )
    indexer =
      IncrementalDocumentIndexer(
        database = db,
        documentIndexDao = db.documentIndexDao(),
        indexerStateDao = db.indexerStateDao(),
        documentRepository = repository,
        storageGateway = fakeGateway,
      )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun categoryClassifierCoversAllElevenDocumentTypes() {
    assertEquals(DocumentCategory.PDF, DocumentCategoryClassifier.classify("application/pdf", "a.pdf"))
    assertEquals(DocumentCategory.DOCUMENTS, DocumentCategoryClassifier.classify(null, "letter.docx"))
    assertEquals(DocumentCategory.SPREADSHEETS, DocumentCategoryClassifier.classify(null, "budget.xlsx"))
    assertEquals(DocumentCategory.SLIDES, DocumentCategoryClassifier.classify(null, "deck.pptx"))
    assertEquals(DocumentCategory.IMAGES, DocumentCategoryClassifier.classify(null, "photo.heic"))
    assertEquals(DocumentCategory.VIDEOS, DocumentCategoryClassifier.classify(null, "clip.mp4"))
    assertEquals(DocumentCategory.AUDIO, DocumentCategoryClassifier.classify(null, "memo.flac"))
    assertEquals(DocumentCategory.ARCHIVES, DocumentCategoryClassifier.classify(null, "backup.7z"))
    assertEquals(DocumentCategory.EBOOKS, DocumentCategoryClassifier.classify(null, "book.epub"))
    assertEquals(DocumentCategory.CODE_TEXT, DocumentCategoryClassifier.classify(null, "Main.kt"))
    assertEquals(DocumentCategory.OTHER, DocumentCategoryClassifier.classify("application/octet-stream", "blob.bin"))
  }

  @Test
  fun paging3SortsAndFiltersByNameDateSizeTypeCollectionTagAndFavorites() = runTest {
    fakeGateway.documents =
      listOf(
        sampleMeta("file:///docs/zeta.pdf", "Zeta.pdf", "/docs", DocumentCategory.PDF, 300L, 1_000L),
        sampleMeta("file:///docs/alpha.docx", "alpha.docx", "/docs", DocumentCategory.DOCUMENTS, 1_500L, 3_000L),
        sampleMeta("file:///work/budget.xlsx", "Budget.xlsx", "/work", DocumentCategory.SPREADSHEETS, 900L, 2_000L),
      )

    val sync = indexer.runIncrementalSync(nowEpochMs = 10_000L)
    assertEquals(3, sync.insertedCount)

    // Sort by NAME ASC (case-insensitive via normalizedName)
    val byNameAsc =
      repository
        .pagedDocuments(FileListQuery(sortField = FileSortField.NAME, sortDirection = SortDirection.ASC))
        .asSnapshot()
    assertEquals(listOf("alpha.docx", "Budget.xlsx", "Zeta.pdf"), byNameAsc.map { it.displayName })

    // Sort by SIZE DESC
    val bySizeDesc =
      repository
        .pagedDocuments(FileListQuery(sortField = FileSortField.SIZE, sortDirection = SortDirection.DESC))
        .asSnapshot()
    assertEquals(listOf("alpha.docx", "Budget.xlsx", "Zeta.pdf"), bySizeDesc.map { it.displayName })

    // Sort by DATE_MODIFIED ASC
    val byDateAsc =
      repository
        .pagedDocuments(
          FileListQuery(sortField = FileSortField.DATE_MODIFIED, sortDirection = SortDirection.ASC)
        )
        .asSnapshot()
    assertEquals(listOf("Zeta.pdf", "Budget.xlsx", "alpha.docx"), byDateAsc.map { it.displayName })

    // Filter by Category
    val onlyPdfs =
      repository.pagedDocuments(FileListQuery(category = DocumentCategory.PDF)).asSnapshot()
    assertEquals(listOf("Zeta.pdf"), onlyPdfs.map { it.displayName })

    // Filter by Parent Folder + Favorites + Collection + Tag
    val alphaId = byNameAsc.first { it.displayName == "alpha.docx" }.id
    repository.setFavorite(alphaId, true)
    repository.recordDocumentOpened(alphaId, 50_000L)
    val colId = repository.createCollection("Q4 Review", "#0066E6")
    repository.addDocumentToCollection(alphaId, colId)
    val tagId = repository.createTag("Important", "#E5484D")
    repository.attachTag(alphaId, tagId)

    val favs = repository.pagedDocuments(FileListQuery(favoritesOnly = true)).asSnapshot()
    assertEquals(listOf("alpha.docx"), favs.map { it.displayName })

    val inCollection = repository.pagedDocuments(FileListQuery(collectionId = colId)).asSnapshot()
    assertEquals(listOf("alpha.docx"), inCollection.map { it.displayName })

    val withTag = repository.pagedDocuments(FileListQuery(tagId = tagId)).asSnapshot()
    assertEquals(listOf("alpha.docx"), withTag.map { it.displayName })

    val recents = repository.pagedDocuments(FileListQuery(recentsOnly = true)).asSnapshot()
    assertEquals(listOf("alpha.docx"), recents.map { it.displayName })
  }

  @Test
  fun incrementalSyncPreservesFavoritesUpdatesModifiedAndEnforces30DayRecycleBin() = runTest {
    val t0 = 1_000_000L
    fakeGateway.documents =
      listOf(
        sampleMeta("file:///docs/a.pdf", "a.pdf", "/docs", DocumentCategory.PDF, 100L, 500L),
        sampleMeta("file:///docs/b.pdf", "b.pdf", "/docs", DocumentCategory.PDF, 200L, 600L),
        sampleMeta("file:///docs/c.pdf", "c.pdf", "/docs", DocumentCategory.PDF, 300L, 700L),
      )
    indexer.runIncrementalSync(nowEpochMs = t0)

    val aDoc = db.documentIndexDao().getByUri("file:///docs/a.pdf")!!
    val cDoc = db.documentIndexDao().getByUri("file:///docs/c.pdf")!!
    repository.setFavorite(aDoc.id, true)
    repository.recordDocumentOpened(aDoc.id, t0 + 10L)

    // Move c.pdf to 30-day Recycle Bin
    assertTrue(repository.moveToRecycleBin(cDoc.id, "/trash/c.pdf", nowEpochMs = t0))
    assertEquals(1, repository.observeRecycleBin().first().size)

    // Simulate file system delta: a.pdf modified in place, b.pdf deleted on disk, d.pdf added
    fakeGateway.documents =
      listOf(
        sampleMeta("file:///docs/a.pdf", "a.pdf", "/docs", DocumentCategory.PDF, 180L, 900L),
        sampleMeta("file:///docs/d.pdf", "d.pdf", "/docs", DocumentCategory.PDF, 400L, 950L),
      )
    val delta = indexer.runIncrementalSync(nowEpochMs = t0 + 1_000L)
    assertEquals(1, delta.insertedCount)
    assertEquals(1, delta.updatedCount)
    assertEquals(1, delta.removedCount)
    assertEquals(0, delta.expiredTrashPurged)

    // User metadata on a.pdf must survive incremental update
    val updatedA = db.documentIndexDao().getByUri("file:///docs/a.pdf")!!
    assertEquals(180L, updatedA.sizeBytes)
    assertTrue(updatedA.isFavorite)
    assertEquals(t0 + 10L, updatedA.lastOpenedEpochMs)

    // Trashed c.pdf must NOT be deleted before 30 days elapse
    assertTrue(db.documentIndexDao().getById(cDoc.id)!!.isTrashed)
    assertEquals(1, repository.observeRecycleBin().first().size)

    // Advance clock past 30 days -> incremental sync purges expired Recycle Bin entry
    val after31Days = t0 + DocumentRepository.RECYCLE_BIN_RETENTION_MS + 1_000L
    val expiredSync = indexer.runIncrementalSync(nowEpochMs = after31Days)
    assertEquals(1, expiredSync.expiredTrashPurged)
    assertTrue(repository.observeRecycleBin().first().isEmpty())
    assertFalse(db.documentIndexDao().getById(cDoc.id) != null)
  }

  @Test
  fun indexerSkipsDirectoriesAcrossLocalNestedFoldersMediaStoreAndSaf() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val root = java.io.File(context.cacheDir, "nested_index_test_${System.nanoTime()}")
    try {
      // Build nested directory tree including "Android/media", empty folders, hidden folders, and real files
      val androidMediaNested = java.io.File(root, "Android/media/com.example/nested").apply { mkdirs() }
      val androidMediaEmpty = java.io.File(root, "Android/media/empty_dir").apply { mkdirs() }
      val androidDataPkg = java.io.File(root, "Android/data/com.other/files").apply { mkdirs() }
      val workQ4 = java.io.File(root, "Work/2026/Q4").apply { mkdirs() }
      val workEmpty = java.io.File(root, "Work/2026/empty_sub").apply { mkdirs() }
      val trashDir = java.io.File(root, ".PrismTrash").apply { mkdirs() }

      val reportPdf = java.io.File(androidMediaNested, "report.pdf").apply { writeText("PDF payload") }
      val budgetXlsx = java.io.File(workQ4, "budget.xlsx").apply { writeText("XLSX payload") }
      java.io.File(androidDataPkg, "private.txt").apply { writeText("Restricted app data") }
      java.io.File(trashDir, "trashed.pdf").apply { writeText("Trashed file") }
      java.io.File(workQ4, ".hidden.txt").apply { writeText("Hidden dot file") }

      assertTrue(androidMediaEmpty.isDirectory)
      assertTrue(workEmpty.isDirectory)

      // 1. Walk local nested folders via real UnifiedDocumentStorageGateway.walkFilesystemDirectory
      val gateway =
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway(
          context = context,
          permissionManager = com.prism.docs.core.data.storage.StoragePermissionManager(context),
          preferencesRepository = com.prism.docs.core.data.preferences.PrismPreferencesRepository(context),
        )
      val walked = mutableListOf<ScannedDocumentMetadata>()
      gateway.walkFilesystemDirectory(root) { walked += it }

      assertEquals(listOf("budget.xlsx", "report.pdf"), walked.map { it.displayName }.sorted())
      assertTrue(walked.none { it.isDirectory })

      // 2. Verify MediaStore row filtering skips "Android", "media", folder MIMEs, pending/trashed, and allows real files
      val androidDir = java.io.File(root, "Android")
      val mediaDir = java.io.File(root, "Android/media")
      assertTrue(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "Android",
          rawMimeType = null,
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_NONE,
          dataPath = androidDir.absolutePath,
          relativePath = "",
          isPending = false,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )
      assertTrue(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "media",
          rawMimeType = null,
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_NONE,
          dataPath = mediaDir.absolutePath,
          relativePath = "Android/",
          isPending = false,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )
      assertTrue(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "media",
          rawMimeType = null,
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_NONE,
          dataPath = "",
          relativePath = "Android/",
          isPending = false,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )
      assertTrue(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "Slides",
          rawMimeType = android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_NONE,
          dataPath = "",
          relativePath = "Documents/",
          isPending = false,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )
      assertTrue(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "draft.pdf",
          rawMimeType = "application/pdf",
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT,
          dataPath = reportPdf.absolutePath,
          relativePath = "Android/media/com.example/nested/",
          isPending = true,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )
      assertFalse(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "report.pdf",
          rawMimeType = "application/pdf",
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT,
          dataPath = reportPdf.absolutePath,
          relativePath = "Android/media/com.example/nested/",
          isPending = false,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )
      assertFalse(
        com.prism.docs.core.data.storage.UnifiedDocumentStorageGateway.shouldSkipMediaStoreRow(
          displayName = "budget.xlsx",
          rawMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          mediaType = android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_NONE,
          dataPath = budgetXlsx.absolutePath,
          relativePath = "Work/2026/Q4/",
          isPending = false,
          isTrashed = false,
          primaryExternalRoot = root,
        )
      )

      // 3. Index walked local files alongside SAF directory items + a valid SAF file, plus a pre-existing stale folder row
      db.documentIndexDao().upsert(
        com.prism.docs.core.data.db.IndexedDocumentEntity(
          uriString = "content://media/external/file/999",
          displayName = "Android",
          normalizedName = "android",
          parentPath = "/storage/emulated/0",
          mimeType = "application/octet-stream",
          extension = "",
          category = DocumentCategory.OTHER.name,
          sizeBytes = 4096L,
          lastModifiedEpochMs = 1_000L,
          sourceType = StorageSourceType.MEDIASTORE.name,
        )
      )

      val safDirectoryMeta =
        ScannedDocumentMetadata(
          uriString = "content://com.android.externalstorage.documents/tree/primary%3AWork/document/primary%3AWork%2FSubfolder",
          displayName = "Subfolder",
          parentPath = "content://com.android.externalstorage.documents/tree/primary%3AWork",
          mimeType = android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
          extension = "",
          category = DocumentCategory.OTHER,
          sizeBytes = 0L,
          lastModifiedEpochMs = 2_000L,
          isDirectory = true,
          sourceType = StorageSourceType.SAF,
        )
      val safSpoofedDirMimeMeta =
        safDirectoryMeta.copy(
          uriString = "content://com.android.externalstorage.documents/tree/primary%3AWork/document/primary%3AWork%2FMedia",
          displayName = "media",
          isDirectory = false,
        )
      val safRealFileMeta =
        ScannedDocumentMetadata(
          uriString = "content://com.android.externalstorage.documents/tree/primary%3AWork/document/primary%3AWork%2Fdeck.pptx",
          displayName = "deck.pptx",
          parentPath = "content://com.android.externalstorage.documents/tree/primary%3AWork",
          mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          extension = "pptx",
          category = DocumentCategory.SLIDES,
          sizeBytes = 12_400L,
          lastModifiedEpochMs = 3_000L,
          isDirectory = false,
          sourceType = StorageSourceType.SAF,
        )

      fakeGateway.documents = walked + listOf(safDirectoryMeta, safSpoofedDirMimeMeta, safRealFileMeta)
      val summary = indexer.runIncrementalSync(nowEpochMs = 50_000L)
      assertEquals(3, summary.insertedCount)
      assertEquals(1, summary.removedCount) // Stale pre-existing "Android" row pruned

      val librarySnapshot =
        repository
          .pagedDocuments(FileListQuery(sortField = FileSortField.NAME, sortDirection = SortDirection.ASC))
          .asSnapshot()
      assertEquals(
        listOf("budget.xlsx", "deck.pptx", "report.pdf"),
        librarySnapshot.map { it.displayName },
      )
      val disallowedNames = setOf("Android", "media", "Work", "2026", "Q4", "empty_dir", "empty_sub", "Subfolder", ".PrismTrash", "private.txt", "trashed.pdf")
      assertTrue(librarySnapshot.none { it.displayName in disallowedNames })
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun incrementalSyncDeduplicatesCanonicalPathsAndFiltersDocumentsOnly() = runTest {
    // Pre-existing MediaStore row for the exact same on-disk Download file
    db.documentIndexDao().upsert(
      com.prism.docs.core.data.db.IndexedDocumentEntity(
        uriString = "content://media/external/file/4201",
        displayName = "prism-debug-apk (5).zip",
        normalizedName = "prism-debug-apk (5).zip",
        parentPath = "/storage/emulated/0/Download",
        mimeType = "application/zip",
        extension = "zip",
        category = DocumentCategory.ARCHIVES.name,
        sizeBytes = 6_410_000L,
        lastModifiedEpochMs = 5_000L,
        sourceType = StorageSourceType.MEDIASTORE.name,
      )
    )

    val fsZip =
      ScannedDocumentMetadata(
        uriString = "file:///storage/emulated/0/Download/prism-debug-apk%20(5).zip",
        displayName = "prism-debug-apk (5).zip",
        parentPath = "/storage/emulated/0/Download",
        mimeType = "application/zip",
        extension = "zip",
        category = DocumentCategory.ARCHIVES,
        sizeBytes = 6_410_000L,
        lastModifiedEpochMs = 5_000L,
        isDirectory = false,
        sourceType = StorageSourceType.FILESYSTEM,
      )
    val msZipDuplicate =
      fsZip.copy(
        uriString = "content://media/external/file/4201",
        sourceType = StorageSourceType.MEDIASTORE,
      )
    val reportPdf =
      sampleMeta("file:///storage/emulated/0/Documents/Q4.pdf", "Q4.pdf", "/storage/emulated/0/Documents", DocumentCategory.PDF, 420_000L, 6_000L)
    val readmeMd =
      sampleMeta("file:///storage/emulated/0/Documents/notes.md", "notes.md", "/storage/emulated/0/Documents", DocumentCategory.CODE_TEXT, 4_096L, 7_000L)
    val snapshotJpg =
      sampleMeta("file:///storage/emulated/0/Pictures/shot.jpg", "shot.jpg", "/storage/emulated/0/Pictures", DocumentCategory.IMAGES, 1_800_000L, 8_000L)

    // Gateway emits both the Filesystem and MediaStore entries for the same zip path
    fakeGateway.documents = listOf(fsZip, msZipDuplicate, reportPdf, readmeMd, snapshotJpg)
    indexer.runIncrementalSync(nowEpochMs = 20_000L)

    // All files view must contain prism-debug-apk (5).zip ONCE (never duplicated)
    val allIndexed =
      repository
        .pagedDocuments(
          FileListQuery(
            documentsOnly = false,
            sortField = FileSortField.NAME,
            sortDirection = SortDirection.ASC,
          )
        )
        .asSnapshot()
    assertEquals(
      listOf("notes.md", "prism-debug-apk (5).zip", "Q4.pdf", "shot.jpg"),
      allIndexed.map { it.displayName },
    )
    assertEquals(1, allIndexed.count { it.displayName == "prism-debug-apk (5).zip" })

    // Default Library query (documentsOnly = true) shows PDF + Markdown only, hiding ZIP and JPG
    val docsOnly =
      repository
        .pagedDocuments(
          FileListQuery(
            documentsOnly = true,
            sortField = FileSortField.NAME,
            sortDirection = SortDirection.ASC,
          )
        )
        .asSnapshot()
    assertEquals(listOf("notes.md", "Q4.pdf"), docsOnly.map { it.displayName })
  }

  private fun sampleMeta(
    uri: String,
    name: String,
    parent: String,
    category: DocumentCategory,
    size: Long,
    modified: Long,
  ) =
    ScannedDocumentMetadata(
      uriString = uri,
      displayName = name,
      parentPath = parent,
      mimeType = DocumentCategoryClassifier.inferMimeType(name, null),
      extension = DocumentCategoryClassifier.extractExtension(name),
      category = category,
      sizeBytes = size,
      lastModifiedEpochMs = modified,
      isDirectory = false,
      sourceType = StorageSourceType.FILESYSTEM,
    )

  private class FakeStorageGateway : DocumentStorageGateway {
    var documents: List<ScannedDocumentMetadata> = emptyList()

    override suspend fun currentAccessState(): StorageAccessState =
      StorageAccessState(
        level = StorageAccessLevel.FULL_ALL_FILES,
        sdkInt = 36,
        hasAllFilesPermission = true,
        safFolders = emptyList(),
      )

    override suspend fun listStorageRoots(): List<StorageVolumeRoot> = emptyList()

    override suspend fun listDirectoryChildren(
      directoryPathOrUri: String,
    ): List<ScannedDocumentMetadata> = documents.filter { it.parentPath == directoryPathOrUri }

    override suspend fun scanAccessibleDocuments(
      batchSize: Int,
      onBatch: suspend (List<ScannedDocumentMetadata>) -> Unit,
    ): Int {
      documents.chunked(batchSize).forEach { onBatch(it) }
      return documents.size
    }

    override fun observeOpenDirectory(directoryPathOrUri: String): Flow<DirectoryChangeEvent> =
      emptyFlow()
  }
}
