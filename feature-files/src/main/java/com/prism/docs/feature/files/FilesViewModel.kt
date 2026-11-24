package com.prism.docs.feature.files

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.prism.docs.core.data.db.CategoryCountRow
import com.prism.docs.core.data.db.CollectionEntity
import com.prism.docs.core.data.db.CollectionWithCountRow
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.RecycleBinEntryEntity
import com.prism.docs.core.data.db.TagEntity
import com.prism.docs.core.data.db.TagWithCountRow
import com.prism.docs.core.data.indexer.DocumentIndexScheduler
import com.prism.docs.core.data.indexer.IncrementalDocumentIndexer
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.FileListQuery
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.IndexWorkStatus
import com.prism.docs.core.data.model.NameCollisionPolicy
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.SortDirection
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.model.TransferOperationType
import com.prism.docs.core.data.model.TransferProgress
import com.prism.docs.core.data.paging.DocumentRepository
import com.prism.docs.core.data.storage.DebugSampleFilesGenerator
import com.prism.docs.core.data.storage.FileOperationsManager
import com.prism.docs.core.data.storage.FileTransferScheduler
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FilesSection {
  LIBRARY,
  STORAGE,
  RECYCLE_BIN,
}

data class BreadcrumbSegment(
  val label: String,
  val pathOrUri: String,
  val isSaf: Boolean,
)

/**
 * Live SAF directory file merged with its Room entity (matched strictly by exact URI, never label).
 */
data class LiveDirectoryFile(
  val meta: ScannedDocumentMetadata,
  val indexedEntity: IndexedDocumentEntity?,
)

data class StorageBrowserState(
  val roots: List<StorageVolumeRoot> = emptyList(),
  val breadcrumbs: List<BreadcrumbSegment> = emptyList(),
  val subfolders: List<ScannedDocumentMetadata> = emptyList(),
  val liveSafFiles: List<LiveDirectoryFile> = emptyList(),
  val isLoadingDirectory: Boolean = false,
  val isWatchingLiveDirectory: Boolean = false,
) {
  val currentDirectory: BreadcrumbSegment?
    get() = breadcrumbs.lastOrNull()
}

data class DestinationPickerState(
  val operationType: TransferOperationType,
  val targetDocumentIds: List<Long>,
  val collisionPolicy: NameCollisionPolicy = NameCollisionPolicy.AUTO_RENAME,
  val roots: List<StorageVolumeRoot> = emptyList(),
  val breadcrumbs: List<BreadcrumbSegment> = emptyList(),
  val subfolders: List<ScannedDocumentMetadata> = emptyList(),
) {
  val currentPathOrUri: String?
    get() = breadcrumbs.lastOrNull()?.pathOrUri
}

data class AssignSheetState(
  val targetDocumentIds: List<Long>,
  val collectionAssignedCounts: Map<Long, Int>,
  val tagAssignedCounts: Map<Long, Int>,
)

data class FilesUiState(
  val section: FilesSection = FilesSection.LIBRARY,
  val isGridMode: Boolean = false,
  val query: FileListQuery = FileListQuery(),
  val selectedIds: Set<Long> = emptySet(),
  val isSelectionModeExplicit: Boolean = false,
  val totalIndexedCount: Int = 0,
  val totalIndexedBytes: Long = 0L,
  val favoritesCount: Int = 0,
  val recentsCount: Int = 0,
  val recycleBinCount: Int = 0,
  val recycleBinBytes: Long = 0L,
  val recycleBinEntries: List<RecycleBinEntryEntity> = emptyList(),
  val categoryCounts: Map<DocumentCategory, CategoryCountRow> = emptyMap(),
  val recentDocuments: List<IndexedDocumentEntity> = emptyList(),
  val collections: List<CollectionEntity> = emptyList(),
  val tags: List<TagEntity> = emptyList(),
  val collectionsWithStats: List<CollectionWithCountRow> = emptyList(),
  val tagsWithStats: List<TagWithCountRow> = emptyList(),
  val assignSheetState: AssignSheetState? = null,
  val showManageTagsCollectionsSheet: Boolean = false,
  val indexWorkStatus: IndexWorkStatus = IndexWorkStatus(),
  val transferProgress: TransferProgress? = null,
  val storageBrowser: StorageBrowserState = StorageBrowserState(),
  val destinationPicker: DestinationPickerState? = null,
  val renameTarget: IndexedDocumentEntity? = null,
  val showCreateFolderDialog: Boolean = false,
  val statusMessage: String? = null,
) {
  val isSelectionMode: Boolean
    get() = isSelectionModeExplicit || selectedIds.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FilesViewModel
@Inject
constructor(
  private val documentRepository: DocumentRepository,
  private val indexScheduler: DocumentIndexScheduler,
  private val incrementalIndexer: IncrementalDocumentIndexer,
  val thumbnailPipeline: DocumentThumbnailPipeline,
  private val fileOperationsManager: FileOperationsManager,
  private val fileTransferScheduler: FileTransferScheduler,
  private val debugSampleFilesGenerator: DebugSampleFilesGenerator,
) : ViewModel() {

  private val sectionFlow = MutableStateFlow(FilesSection.LIBRARY)
  private val gridModeFlow = MutableStateFlow(false)
  private val queryFlow = MutableStateFlow(FileListQuery())
  private val selectedIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
  private val selectionModeActiveFlow = MutableStateFlow(false)
  private val storageBrowserFlow = MutableStateFlow(StorageBrowserState())
  private val destinationPickerFlow = MutableStateFlow<DestinationPickerState?>(null)
  private val renameTargetFlow = MutableStateFlow<IndexedDocumentEntity?>(null)
  private val showCreateFolderFlow = MutableStateFlow(false)
  private val statusMessageFlow = MutableStateFlow<String?>(null)
  private val assignTargetDocIdsFlow = MutableStateFlow<List<Long>?>(null)
  private val showManageLabelsSheetFlow = MutableStateFlow(false)

  private var openDirectoryObserverJob: Job? = null

  private val countsFlow =
    combine(
      documentRepository.observeActiveCount(),
      documentRepository.observeTotalIndexedBytes(),
      documentRepository.observeFavoriteCount(),
      documentRepository.observeRecentCount(),
      documentRepository.observeCategoryCounts(),
    ) { active, bytes, favs, recents, catRows ->
      val byCategory =
        catRows.mapNotNull { row ->
            val cat = runCatching { DocumentCategory.valueOf(row.category) }.getOrNull()
            if (cat != null) cat to row else null
          }
          .toMap()
      LibraryCounts(active, bytes, favs, recents, byCategory)
    }

  private val trashFlow =
    combine(
      documentRepository.observeRecycleBinCount(),
      documentRepository.observeRecycleBinBytes(),
      documentRepository.observeRecycleBin(),
    ) { count, bytes, entries ->
      Triple(count, bytes, entries)
    }

  private val labelsFlow =
    combine(
      documentRepository.observeCollections(),
      documentRepository.observeTags(),
      documentRepository.observeCollectionsWithStats(),
      documentRepository.observeTagsWithStats(),
    ) { collections, tags, colStats, tagStats ->
      LabelsSlice(collections, tags, colStats, tagStats)
    }

  private val assignSheetStateFlow: Flow<AssignSheetState?> =
    assignTargetDocIdsFlow.flatMapLatest { targetIds ->
      if (targetIds.isNullOrEmpty()) {
        flowOf(null)
      } else {
        combine(
          documentRepository.observeCollectionCrossRefsForDocuments(targetIds),
          documentRepository.observeTagCrossRefsForDocuments(targetIds),
        ) { colRefs, tagRefs ->
          AssignSheetState(
            targetDocumentIds = targetIds,
            collectionAssignedCounts = colRefs.groupingBy { it.collectionId }.eachCount(),
            tagAssignedCounts = tagRefs.groupingBy { it.tagId }.eachCount(),
          )
        }
      }
    }

  private val metaFlow =
    combine(
      documentRepository.observeRecents(limit = 12),
      labelsFlow,
      indexScheduler.observeIndexStatus(),
      fileTransferScheduler.transferProgress,
    ) { recents, labels, workStatus, transfer ->
      MetaSlice(recents, labels, workStatus, transfer)
    }

  private val labelDialogsFlow =
    combine(assignSheetStateFlow, showManageLabelsSheetFlow) { assignState, manageOpen ->
      assignState to manageOpen
    }

  private val dialogsFlow =
    combine(
      destinationPickerFlow,
      renameTargetFlow,
      showCreateFolderFlow,
      statusMessageFlow,
      labelDialogsFlow,
    ) { picker, rename, createFolder, msg, (assignState, manageOpen) ->
      DialogSlice(picker, rename, createFolder, msg, assignState, manageOpen)
    }

  private val selectionStateFlow =
    combine(selectedIdsFlow, selectionModeActiveFlow) { selected, explicit -> selected to explicit }

  val uiState: StateFlow<FilesUiState> =
    combine(
        combine(sectionFlow, gridModeFlow, queryFlow, selectionStateFlow, storageBrowserFlow) {
          section,
          grid,
          query,
          (selected, explicitSelection),
          storage ->
          ControlSlice(section, grid, query, selected, explicitSelection, storage)
        },
        countsFlow,
        trashFlow,
        metaFlow,
        dialogsFlow,
      ) { controls, counts, (trashCount, trashBytes, trashEntries), meta, dialogs ->
        FilesUiState(
          section = controls.section,
          isGridMode = controls.isGridMode,
          query = controls.query,
          selectedIds = controls.selectedIds,
          isSelectionModeExplicit = controls.isSelectionModeExplicit,
          totalIndexedCount = counts.activeCount,
          totalIndexedBytes = counts.totalBytes,
          favoritesCount = counts.favoritesCount,
          recentsCount = counts.recentsCount,
          recycleBinCount = trashCount,
          recycleBinBytes = trashBytes,
          recycleBinEntries = trashEntries,
          categoryCounts = counts.categoryCounts,
          recentDocuments = meta.recentDocuments,
          collections = meta.labels.collections,
          tags = meta.labels.tags,
          collectionsWithStats = meta.labels.collectionsWithStats,
          tagsWithStats = meta.labels.tagsWithStats,
          assignSheetState = dialogs.assignSheetState,
          showManageTagsCollectionsSheet = dialogs.showManageTagsCollectionsSheet,
          indexWorkStatus = meta.workStatus,
          transferProgress = meta.transferProgress,
          storageBrowser = controls.storageBrowser,
          destinationPicker = dialogs.destinationPicker,
          renameTarget = dialogs.renameTarget,
          showCreateFolderDialog = dialogs.showCreateFolderDialog,
          statusMessage = dialogs.statusMessage,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = FilesUiState(),
      )

  /** Paging 3 stream backing Home / Library with sort and filter. */
  val libraryPagingData: Flow<PagingData<IndexedDocumentEntity>> =
    queryFlow
      .flatMapLatest { query -> documentRepository.pagedDocuments(query.copy(parentPath = null)) }
      .cachedIn(viewModelScope)

  /**
   * Paging 3 stream backing local filesystem directories inside the Storage Browser.
   * SAF directories are listed live via DocumentsContract (never matched by display label).
   */
  val storageDirectoryPagingData: Flow<PagingData<IndexedDocumentEntity>> =
    combine(
        storageBrowserFlow.map { it.currentDirectory }.distinctUntilChanged(),
        queryFlow.map { Triple(it.sortField, it.sortDirection, it.category) }.distinctUntilChanged(),
      ) { currentDir, (sortField, sortDirection, category) ->
        currentDir to Triple(sortField, sortDirection, category)
      }
      .flatMapLatest { (currentDir, sortAndCategory) ->
        if (currentDir == null || currentDir.isSaf) {
          flowOf(PagingData.empty())
        } else {
          val (sortField, sortDirection, category) = sortAndCategory
          documentRepository.pagedDocuments(
            FileListQuery(
              parentPath = currentDir.pathOrUri,
              category = category,
              sortField = sortField,
              sortDirection = sortDirection,
            )
          )
        }
      }
      .cachedIn(viewModelScope)

  init {
    refreshStorageRoots()
  }

  fun setSection(section: FilesSection) {
    sectionFlow.value = section
    if (section != FilesSection.LIBRARY) {
      clearSelection()
    }
    if (section == FilesSection.STORAGE) {
      refreshStorageRoots()
    }
  }

  fun toggleGridMode() {
    gridModeFlow.update { !it }
  }

  fun setSortField(field: FileSortField) {
    queryFlow.update { current ->
      if (current.sortField == field) {
        val flipped =
          if (current.sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
        current.copy(sortDirection = flipped)
      } else {
        val defaultDir =
          when (field) {
            FileSortField.NAME, FileSortField.TYPE -> SortDirection.ASC
            FileSortField.DATE_MODIFIED, FileSortField.SIZE -> SortDirection.DESC
          }
        current.copy(sortField = field, sortDirection = defaultDir)
      }
    }
    resortLiveSafFiles()
  }

  fun toggleSortDirection() {
    queryFlow.update { current ->
      current.copy(
        sortDirection =
          if (current.sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
      )
    }
    resortLiveSafFiles()
  }

  fun setCategoryFilter(category: DocumentCategory?) {
    queryFlow.update { current ->
      current.copy(
        category = if (current.category == category && !current.otherCategoriesOnly) null else category,
        otherCategoriesOnly = false,
      )
    }
  }

  fun applyAnalyzerCategoryFilter(category: DocumentCategory?, otherOnly: Boolean) {
    sectionFlow.value = FilesSection.LIBRARY
    clearSelection()
    queryFlow.update { current ->
      current.copy(
        category = if (otherOnly) null else category,
        otherCategoriesOnly = otherOnly,
      )
    }
  }

  fun toggleFavoritesOnly() {
    queryFlow.update { current ->
      current.copy(favoritesOnly = !current.favoritesOnly, recentsOnly = false)
    }
  }

  fun toggleRecentsOnly() {
    queryFlow.update { current ->
      current.copy(recentsOnly = !current.recentsOnly, favoritesOnly = false)
    }
  }

  fun setCollectionFilter(collectionId: Long?) {
    queryFlow.update { current ->
      current.copy(collectionId = if (current.collectionId == collectionId) null else collectionId)
    }
  }

  fun setTagFilter(tagId: Long?) {
    queryFlow.update { current ->
      current.copy(tagId = if (current.tagId == tagId) null else tagId)
    }
  }

  fun setNamePrefix(prefix: String) {
    queryFlow.update { current -> current.copy(namePrefix = prefix.ifBlank { null }) }
  }

  fun clearAllFilters() {
    queryFlow.update { current ->
      FileListQuery(sortField = current.sortField, sortDirection = current.sortDirection)
    }
  }

  // Tags & Collections management & assignment
  fun openAssignSheet(documentIds: List<Long>) {
    if (documentIds.isEmpty()) return
    assignTargetDocIdsFlow.value = documentIds.distinct()
  }

  fun dismissAssignSheet() {
    assignTargetDocIdsFlow.value = null
  }

  fun openManageTagsCollectionsSheet() {
    showManageLabelsSheetFlow.value = true
  }

  fun dismissManageTagsCollectionsSheet() {
    showManageLabelsSheetFlow.value = false
  }

  fun createCollection(name: String, accentHex: String, assignToTargets: Boolean = false) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      val colId = documentRepository.createCollection(clean, accentHex)
      if (assignToTargets) {
        val targets = assignTargetDocIdsFlow.value.orEmpty()
        if (targets.isNotEmpty()) {
          documentRepository.addDocumentsToCollection(targets, colId)
        }
      }
      statusMessageFlow.value = "Created collection \"$clean\""
    }
  }

  fun renameCollection(collectionId: Long, newName: String) {
    viewModelScope.launch {
      val ok = documentRepository.renameCollection(collectionId, newName)
      statusMessageFlow.value =
        if (ok) "Renamed collection to \"${newName.trim()}\"" else "Enter a valid collection name"
    }
  }

  fun deleteCollection(collectionId: Long) {
    viewModelScope.launch {
      documentRepository.deleteCollection(collectionId)
      queryFlow.update { q -> if (q.collectionId == collectionId) q.copy(collectionId = null) else q }
      statusMessageFlow.value = "Collection deleted"
    }
  }

  fun toggleCollectionForTargets(collectionId: Long, assignAll: Boolean) {
    val targets = assignTargetDocIdsFlow.value ?: return
    if (targets.isEmpty()) return
    viewModelScope.launch {
      if (assignAll) {
        documentRepository.addDocumentsToCollection(targets, collectionId)
      } else {
        documentRepository.removeDocumentsFromCollection(targets, collectionId)
      }
    }
  }

  fun createTag(name: String, colorHex: String, assignToTargets: Boolean = false) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      val tagId = documentRepository.createTag(clean, colorHex)
      if (assignToTargets) {
        val targets = assignTargetDocIdsFlow.value.orEmpty()
        if (targets.isNotEmpty()) {
          documentRepository.attachTagToDocuments(targets, tagId)
        }
      }
      statusMessageFlow.value = "Created tag #$clean"
    }
  }

  fun renameTag(tagId: Long, newName: String) {
    viewModelScope.launch {
      val ok = documentRepository.renameTag(tagId, newName)
      statusMessageFlow.value =
        if (ok) "Renamed tag to #${newName.trim()}" else "Tag name already exists or is invalid"
    }
  }

  fun deleteTag(tagId: Long) {
    viewModelScope.launch {
      documentRepository.deleteTag(tagId)
      queryFlow.update { q -> if (q.tagId == tagId) q.copy(tagId = null) else q }
      statusMessageFlow.value = "Tag deleted"
    }
  }

  fun toggleTagForTargets(tagId: Long, assignAll: Boolean) {
    val targets = assignTargetDocIdsFlow.value ?: return
    if (targets.isEmpty()) return
    viewModelScope.launch {
      if (assignAll) {
        documentRepository.attachTagToDocuments(targets, tagId)
      } else {
        documentRepository.detachTagFromDocuments(targets, tagId)
      }
    }
  }

  // Multi-select
  fun enterSelectionMode() {
    selectionModeActiveFlow.value = true
  }

  fun toggleSelection(documentId: Long) {
    selectedIdsFlow.update { current ->
      if (documentId in current) current - documentId else current + documentId
    }
  }

  fun selectAll(documentIds: Collection<Long>) {
    selectionModeActiveFlow.value = true
    selectedIdsFlow.value = documentIds.toSet()
  }

  fun clearSelection() {
    selectionModeActiveFlow.value = false
    selectedIdsFlow.value = emptySet()
  }

  fun toggleFavorite(documentId: Long, currentlyFavorite: Boolean) {
    viewModelScope.launch {
      documentRepository.setFavorite(documentId, !currentlyFavorite)
      storageBrowserFlow.value.currentDirectory?.takeIf { it.isSaf }?.let {
        refreshDirectoryContents(it)
      }
    }
  }

  fun recordDocumentOpened(documentId: Long) {
    viewModelScope.launch { documentRepository.recordDocumentOpened(documentId) }
  }

  fun triggerManualSync() {
    indexScheduler.triggerImmediateSync()
    statusMessageFlow.value = "Syncing device documents..."
  }

  fun clearThumbnailCache() {
    viewModelScope.launch {
      val freed = thumbnailPipeline.clearCaches()
      statusMessageFlow.value =
        if (freed > 0L) "Cleared ${formatByteSize(freed)} of cached thumbnails"
        else "Thumbnail cache is already empty"
    }
  }

  fun createDebugSampleFiles() {
    viewModelScope.launch {
      statusMessageFlow.value = "Creating sample files in Documents/PrismSamples…"
      val result = debugSampleFilesGenerator.createSamplesAndReindex()
      statusMessageFlow.value = result.message
      refreshStorageRoots()
    }
  }

  fun clearStatusMessage() {
    statusMessageFlow.value = null
  }

  // Create Folder
  fun openCreateFolderDialog() {
    showCreateFolderFlow.value = true
  }

  fun dismissCreateFolderDialog() {
    showCreateFolderFlow.value = false
  }

  fun createFolderInCurrentDirectory(folderName: String) {
    val currentDir = storageBrowserFlow.value.currentDirectory ?: return
    viewModelScope.launch {
      val result = fileOperationsManager.createFolder(currentDir.pathOrUri, folderName)
      showCreateFolderFlow.value = false
      statusMessageFlow.value = result.message
      if (result.success) {
        refreshDirectoryContents(currentDir)
      }
    }
  }

  // Rename
  fun openRenameDialog(document: IndexedDocumentEntity) {
    renameTargetFlow.value = document
  }

  fun dismissRenameDialog() {
    renameTargetFlow.value = null
  }

  fun confirmRename(newName: String) {
    val target = renameTargetFlow.value ?: return
    viewModelScope.launch {
      val result = fileOperationsManager.renameDocument(target.id, newName)
      renameTargetFlow.value = null
      statusMessageFlow.value = result.message
      storageBrowserFlow.value.currentDirectory?.let { refreshDirectoryContents(it) }
    }
  }

  // Copy / Move Destination Picker
  fun openDestinationPicker(operationType: TransferOperationType, documentIds: List<Long>) {
    if (documentIds.isEmpty()) return
    viewModelScope.launch {
      val roots = documentRepository.listStorageRoots()
      destinationPickerFlow.value =
        DestinationPickerState(
          operationType = operationType,
          targetDocumentIds = documentIds,
          roots = roots,
        )
    }
  }

  fun dismissDestinationPicker() {
    destinationPickerFlow.value = null
  }

  fun setPickerCollisionPolicy(policy: NameCollisionPolicy) {
    destinationPickerFlow.update { it?.copy(collisionPolicy = policy) }
  }

  fun openPickerRoot(root: StorageVolumeRoot) {
    val seg =
      BreadcrumbSegment(
        label = root.displayName,
        pathOrUri = root.pathOrUri,
        isSaf = root.sourceType == StorageSourceType.SAF,
      )
    navigatePickerBreadcrumbs(listOf(seg))
  }

  fun openPickerSubfolder(folder: ScannedDocumentMetadata) {
    val picker = destinationPickerFlow.value ?: return
    val isSaf = folder.sourceType == StorageSourceType.SAF
    val pathOrUri =
      if (isSaf) folder.uriString
      else Uri.parse(folder.uriString).path ?: File(folder.parentPath, folder.displayName).absolutePath
    navigatePickerBreadcrumbs(picker.breadcrumbs + BreadcrumbSegment(folder.displayName, pathOrUri, isSaf))
  }

  fun navigatePickerBreadcrumb(index: Int) {
    val picker = destinationPickerFlow.value ?: return
    if (index < 0) {
      destinationPickerFlow.update { it?.copy(breadcrumbs = emptyList(), subfolders = emptyList()) }
    } else if (index < picker.breadcrumbs.size) {
      navigatePickerBreadcrumbs(picker.breadcrumbs.take(index + 1))
    }
  }

  private fun navigatePickerBreadcrumbs(breadcrumbs: List<BreadcrumbSegment>) {
    val target = breadcrumbs.lastOrNull() ?: return
    destinationPickerFlow.update { it?.copy(breadcrumbs = breadcrumbs, subfolders = emptyList()) }
    viewModelScope.launch {
      val folders =
        documentRepository.listDirectoryChildren(target.pathOrUri)
          .filter { it.isDirectory }
          .sortedBy { it.displayName.lowercase() }
      destinationPickerFlow.update { it?.copy(subfolders = folders) }
    }
  }

  fun confirmDestinationPicker() {
    val picker = destinationPickerFlow.value ?: return
    val dest = picker.currentPathOrUri ?: return
    destinationPickerFlow.value = null
    clearSelection()

    fileTransferScheduler.startCopyOrMove(
      documentIds = picker.targetDocumentIds,
      destinationDirPathOrUri = dest,
      operationType = picker.operationType,
      collisionPolicy = picker.collisionPolicy,
      onCompleted = { result ->
        statusMessageFlow.value = result.message
        storageBrowserFlow.value.currentDirectory?.let { dir ->
          viewModelScope.launch { refreshDirectoryContents(dir) }
        }
      },
    )
  }

  fun cancelActiveTransfer() {
    fileTransferScheduler.cancelActiveTransfer()
  }

  fun dismissTransferProgress() {
    fileTransferScheduler.dismissCompletedProgress()
  }

  // Share
  fun buildShareIntentForIds(documentIds: List<Long>, onReady: (Intent?) -> Unit) {
    viewModelScope.launch {
      val docs = documentRepository.getDocumentsByIds(documentIds)
      onReady(fileOperationsManager.buildShareIntent(docs))
    }
  }

  // Recycle Bin
  fun moveToRecycleBin(documentIds: List<Long>) {
    if (documentIds.isEmpty()) return
    viewModelScope.launch {
      val result = fileOperationsManager.moveToRecycleBin(documentIds)
      clearSelection()
      statusMessageFlow.value = result.message
      storageBrowserFlow.value.currentDirectory?.let { refreshDirectoryContents(it) }
    }
  }

  fun restoreFromRecycleBin(
    documentIds: List<Long>,
    collisionPolicy: NameCollisionPolicy = NameCollisionPolicy.AUTO_RENAME,
  ) {
    if (documentIds.isEmpty()) return
    viewModelScope.launch {
      val result = fileOperationsManager.restoreFromRecycleBin(documentIds, collisionPolicy)
      statusMessageFlow.value = result.message
    }
  }

  fun restoreAllFromRecycleBin() {
    val allIds = uiState.value.recycleBinEntries.map { it.documentId }
    restoreFromRecycleBin(allIds)
  }

  fun permanentlyDeleteFromRecycleBin(documentIds: List<Long>) {
    if (documentIds.isEmpty()) return
    viewModelScope.launch {
      val result = fileOperationsManager.permanentlyDeleteFromRecycleBin(documentIds)
      statusMessageFlow.value = result.message
    }
  }

  fun emptyRecycleBin() {
    viewModelScope.launch {
      val result = fileOperationsManager.emptyRecycleBin()
      statusMessageFlow.value = result.message
    }
  }

  // Storage Browser Navigation
  fun refreshStorageRoots() {
    viewModelScope.launch {
      val roots = documentRepository.listStorageRoots()
      storageBrowserFlow.update { it.copy(roots = roots) }
    }
  }

  fun openStorageRoot(root: StorageVolumeRoot) {
    val segment =
      BreadcrumbSegment(
        label = root.displayName,
        pathOrUri = root.pathOrUri,
        isSaf = root.sourceType == StorageSourceType.SAF,
      )
    navigateToBreadcrumbs(listOf(segment))
  }

  fun openSubfolder(folder: ScannedDocumentMetadata) {
    val isSaf = folder.sourceType == StorageSourceType.SAF
    val pathOrUri =
      if (isSaf) folder.uriString
      else Uri.parse(folder.uriString).path ?: File(folder.parentPath, folder.displayName).absolutePath
    val next =
      storageBrowserFlow.value.breadcrumbs +
        BreadcrumbSegment(label = folder.displayName, pathOrUri = pathOrUri, isSaf = isSaf)
    navigateToBreadcrumbs(next)
  }

  fun navigateToBreadcrumb(index: Int) {
    if (index < 0) {
      closeOpenDirectory()
      return
    }
    val current = storageBrowserFlow.value.breadcrumbs
    if (index >= current.size) return
    navigateToBreadcrumbs(current.take(index + 1))
  }

  fun navigateUpDirectory(): Boolean {
    val current = storageBrowserFlow.value.breadcrumbs
    if (current.isEmpty()) return false
    if (current.size == 1) closeOpenDirectory()
    else navigateToBreadcrumbs(current.dropLast(1))
    return true
  }

  private fun closeOpenDirectory() {
    openDirectoryObserverJob?.cancel()
    openDirectoryObserverJob = null
    storageBrowserFlow.update {
      it.copy(
        breadcrumbs = emptyList(),
        subfolders = emptyList(),
        liveSafFiles = emptyList(),
        isLoadingDirectory = false,
        isWatchingLiveDirectory = false,
      )
    }
  }

  private fun navigateToBreadcrumbs(breadcrumbs: List<BreadcrumbSegment>) {
    val target = breadcrumbs.lastOrNull() ?: return
    openDirectoryObserverJob?.cancel()
    storageBrowserFlow.update {
      it.copy(
        breadcrumbs = breadcrumbs,
        subfolders = emptyList(),
        liveSafFiles = emptyList(),
        isLoadingDirectory = true,
        isWatchingLiveDirectory = false,
      )
    }

    viewModelScope.launch { refreshDirectoryContents(target) }

    openDirectoryObserverJob =
      viewModelScope.launch {
        storageBrowserFlow.update { it.copy(isWatchingLiveDirectory = true) }
        documentRepository
          .observeOpenDirectory(target.pathOrUri)
          .debounce(350L)
          .collect { refreshDirectoryContents(target) }
      }
  }

  private suspend fun refreshDirectoryContents(target: BreadcrumbSegment) {
    val children = documentRepository.listDirectoryChildren(target.pathOrUri)
    val folders = children.filter { it.isDirectory }.sortedBy { it.displayName.lowercase() }

    if (target.isSaf) {
      // Live DocumentsContract listing for SAF folders; match Room metadata strictly by exact URI
      val rawFiles = children.filterNot { it.isDirectory }
      val indexedByUri =
        documentRepository.getDocumentsByUris(rawFiles.map { it.uriString }).associateBy {
          it.uriString
        }
      val merged = rawFiles.map { LiveDirectoryFile(it, indexedByUri[it.uriString]) }
      storageBrowserFlow.update {
        it.copy(
          subfolders = folders,
          liveSafFiles = sortLiveSafFiles(merged, queryFlow.value.sortField, queryFlow.value.sortDirection),
          isLoadingDirectory = false,
        )
      }
      runCatching { incrementalIndexer.syncSingleDirectory(target.pathOrUri) }
    } else {
      storageBrowserFlow.update {
        it.copy(subfolders = folders, liveSafFiles = emptyList(), isLoadingDirectory = false)
      }
      runCatching { incrementalIndexer.syncSingleDirectory(target.pathOrUri) }
    }
  }

  private fun resortLiveSafFiles() {
    val q = queryFlow.value
    storageBrowserFlow.update { state ->
      if (state.liveSafFiles.isEmpty()) state
      else state.copy(liveSafFiles = sortLiveSafFiles(state.liveSafFiles, q.sortField, q.sortDirection))
    }
  }

  private fun sortLiveSafFiles(
    items: List<LiveDirectoryFile>,
    field: FileSortField,
    dir: SortDirection,
  ): List<LiveDirectoryFile> {
    val comparator =
      when (field) {
        FileSortField.NAME -> compareBy<LiveDirectoryFile> { it.meta.displayName.lowercase() }
        FileSortField.DATE_MODIFIED -> compareBy { it.meta.lastModifiedEpochMs }
        FileSortField.SIZE -> compareBy { it.meta.sizeBytes }
        FileSortField.TYPE ->
          compareBy<LiveDirectoryFile> { it.meta.extension.lowercase() }
            .thenBy { it.meta.displayName.lowercase() }
      }
    return if (dir == SortDirection.ASC) items.sortedWith(comparator)
    else items.sortedWith(comparator.reversed())
  }

  override fun onCleared() {
    openDirectoryObserverJob?.cancel()
    super.onCleared()
  }

  private data class LibraryCounts(
    val activeCount: Int,
    val totalBytes: Long,
    val favoritesCount: Int,
    val recentsCount: Int,
    val categoryCounts: Map<DocumentCategory, CategoryCountRow>,
  )

  private data class LabelsSlice(
    val collections: List<CollectionEntity>,
    val tags: List<TagEntity>,
    val collectionsWithStats: List<CollectionWithCountRow>,
    val tagsWithStats: List<TagWithCountRow>,
  )

  private data class MetaSlice(
    val recentDocuments: List<IndexedDocumentEntity>,
    val labels: LabelsSlice,
    val workStatus: IndexWorkStatus,
    val transferProgress: TransferProgress?,
  )

  private data class DialogSlice(
    val destinationPicker: DestinationPickerState?,
    val renameTarget: IndexedDocumentEntity?,
    val showCreateFolderDialog: Boolean,
    val statusMessage: String?,
    val assignSheetState: AssignSheetState?,
    val showManageTagsCollectionsSheet: Boolean,
  )

  private data class ControlSlice(
    val section: FilesSection,
    val isGridMode: Boolean,
    val query: FileListQuery,
    val selectedIds: Set<Long>,
    val isSelectionModeExplicit: Boolean,
    val storageBrowser: StorageBrowserState,
  )
}
