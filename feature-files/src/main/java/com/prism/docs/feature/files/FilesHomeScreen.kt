package com.prism.docs.feature.files

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.model.PrismPreferences
import com.prism.docs.core.data.model.SortDirection
import com.prism.docs.core.data.model.StorageAccessState
import com.prism.docs.core.data.model.TransferOperationType
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.ObserveScrollCollapse
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.glassEntrance
import com.prism.docs.core.ui.glass.glassPress
import com.prism.docs.core.ui.glass.horizontalRightEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismColors
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

@Composable
fun FilesHomeScreen(
  preferences: PrismPreferences,
  storageAccessState: StorageAccessState,
  onRequestAllFilesAccess: () -> Unit,
  onAddSafFolder: () -> Unit,
  onRemoveSafFolder: (String) -> Unit,
  onDismissAccessBanner: () -> Unit,
  onOpenGlassSettings: () -> Unit,
  onOpenSearch: () -> Unit,
  onOpenDocument: (IncomingDocumentRequest) -> Unit,
  onTabBarCollapsedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isDebugBuild: Boolean = false,
  viewModel: FilesViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val libraryItems = viewModel.libraryPagingData.collectAsLazyPagingItems()
  val storageDirectoryItems = viewModel.storageDirectoryPagingData.collectAsLazyPagingItems()
  val colorScheme = MaterialTheme.colorScheme

  var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
  var optionsDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }

  val hasActiveSecondaryFilters =
    uiState.query.favoritesOnly ||
      uiState.query.recentsOnly ||
      uiState.query.collectionId != null ||
      uiState.query.tagId != null ||
      !uiState.query.namePrefix.isNullOrBlank()

  LaunchedEffect(storageAccessState) { viewModel.refreshStorageRoots() }

  LaunchedEffect(uiState.statusMessage) {
    if (uiState.statusMessage != null) {
      delay(3_200L)
      viewModel.clearStatusMessage()
    }
  }

  BackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }

  val openIndexedDoc: (IndexedDocumentEntity) -> Unit = { doc ->
    viewModel.recordDocumentOpened(doc.id)
    onOpenDocument(
      IncomingDocumentRequest(
        uriString = doc.uriString,
        displayName = doc.displayName,
        mimeType = doc.mimeType,
        sizeBytes = doc.sizeBytes,
        sourceAction = "LIBRARY",
      )
    )
  }

  val shareIds: (List<Long>) -> Unit = { ids ->
    viewModel.buildShareIntentForIds(ids) { intent ->
      if (intent != null) {
        runCatching { context.startActivity(Intent.createChooser(intent, "Share document")) }
      }
    }
  }

  val sectionTitle =
    when (uiState.section) {
      FilesSection.LIBRARY -> "Library"
      FilesSection.STORAGE -> "Storage"
      FilesSection.RECYCLE_BIN -> "Recycle Bin"
    }

  val headerSubtitle =
    when {
      uiState.indexWorkStatus.isRunning -> "Indexing documents…"
      uiState.section == FilesSection.RECYCLE_BIN ->
        "${formatFileCount(uiState.recycleBinCount)} trashed, 30-day retention"
      uiState.totalIndexedCount == 1 -> "1 file, all indexed"
      else -> "${formatFileCount(uiState.totalIndexedCount)} files, all indexed"
    }

  val libraryListState = rememberLazyListState()
  val storageRootsListState = rememberLazyListState()
  val storageDirListState = rememberLazyListState()
  val recycleBinListState = rememberLazyListState()
  val activeHeaderListState =
    when (uiState.section) {
      FilesSection.LIBRARY -> libraryListState
      FilesSection.STORAGE ->
        if (uiState.storageBrowser.currentDirectory == null) storageRootsListState
        else storageDirListState
      FilesSection.RECYCLE_BIN -> recycleBinListState
    }
  val headerScrollProgress = rememberHeaderScrollProgress(activeHeaderListState)

  Box(modifier = modifier.fillMaxSize()) {
    when (uiState.section) {
      FilesSection.LIBRARY ->
        LibraryPagingSection(
          uiState = uiState,
          listState = libraryListState,
          sectionTitle = sectionTitle,
          headerSubtitle = headerSubtitle,
          hasActiveSecondaryFilters = hasActiveSecondaryFilters,
          preferences = preferences,
          storageAccessState = storageAccessState,
          pagedItems = libraryItems,
          thumbnailPipeline = viewModel.thumbnailPipeline,
          onOpenOptionsSheet = {
            optionsDetent = GlassSheetDetent.HALF
            showOptionsSheet = true
          },
          onOpenSearch = onOpenSearch,
          onSelectSection = viewModel::setSection,
          onRequestAllFilesAccess = onRequestAllFilesAccess,
          onAddSafFolder = onAddSafFolder,
          onDismissAccessBanner = onDismissAccessBanner,
          onTriggerSync = viewModel::triggerManualSync,
          onCategorySelected = viewModel::setCategoryFilter,
          onSeeAllRecents = {
            if (!uiState.query.recentsOnly) {
              viewModel.toggleRecentsOnly()
            }
          },
          onClearFilters = viewModel::clearAllFilters,
          onOpenDocument = openIndexedDoc,
          onToggleSelection = viewModel::toggleSelection,
          onToggleFavorite = viewModel::toggleFavorite,
          onRenameDocument = viewModel::openRenameDialog,
          onCopyDocument = {
            viewModel.openDestinationPicker(TransferOperationType.COPY, listOf(it.id))
          },
          onMoveDocument = {
            viewModel.openDestinationPicker(TransferOperationType.MOVE, listOf(it.id))
          },
          onShareDocument = { shareIds(listOf(it.id)) },
          onTrashDocument = { viewModel.moveToRecycleBin(listOf(it.id)) },
          onTabBarCollapsedChange = onTabBarCollapsedChange,
        )

      FilesSection.STORAGE ->
        StorageBrowserSection(
          storageState = uiState.storageBrowser,
          isGridMode = uiState.isGridMode,
          sortField = uiState.query.sortField,
          sortDirection = uiState.query.sortDirection,
          selectedIds = uiState.selectedIds,
          isSelectionMode = uiState.isSelectionMode,
          pagedLocalFiles = storageDirectoryItems,
          thumbnailPipeline = viewModel.thumbnailPipeline,
          onOpenRoot = viewModel::openStorageRoot,
          onOpenSubfolder = viewModel::openSubfolder,
          onNavigateToBreadcrumb = viewModel::navigateToBreadcrumb,
          onNavigateUp = viewModel::navigateUpDirectory,
          onCreateFolderClick = viewModel::openCreateFolderDialog,
          onOpenIndexedDocument = openIndexedDoc,
          onOpenRawDocument = onOpenDocument,
          onToggleSelection = viewModel::toggleSelection,
          onToggleFavorite = viewModel::toggleFavorite,
          onRenameDocument = viewModel::openRenameDialog,
          onCopyDocument = {
            viewModel.openDestinationPicker(TransferOperationType.COPY, listOf(it.id))
          },
          onMoveDocument = {
            viewModel.openDestinationPicker(TransferOperationType.MOVE, listOf(it.id))
          },
          onShareDocument = { shareIds(listOf(it.id)) },
          onTrashDocument = { viewModel.moveToRecycleBin(listOf(it.id)) },
          onSortFieldSelected = viewModel::setSortField,
          onToggleSortDirection = viewModel::toggleSortDirection,
          onAddSafFolder = onAddSafFolder,
          onRequestAllFilesAccess = onRequestAllFilesAccess,
          hasFullAccess = storageAccessState.canBrowseFullDevice,
          rootsListState = storageRootsListState,
          directoryListState = storageDirListState,
        )

      FilesSection.RECYCLE_BIN ->
        RecycleBinSection(
          entries = uiState.recycleBinEntries,
          totalBytes = uiState.recycleBinBytes,
          onNavigateBack = { viewModel.setSection(FilesSection.LIBRARY) },
          onRestoreEntry = { viewModel.restoreFromRecycleBin(listOf(it)) },
          onDeleteEntryPermanently = { viewModel.permanentlyDeleteFromRecycleBin(listOf(it)) },
          onRestoreAll = viewModel::restoreAllFromRecycleBin,
          onEmptyRecycleBin = viewModel::emptyRecycleBin,
          listState = recycleBinListState,
        )
    }

    if (uiState.section == FilesSection.LIBRARY) {
      // Subtle status-bar fade that firms up as Library content scrolls under status icons
      ScrollEdgeFade(
        isTop = true,
        height = 52.dp,
        scrollProgress = headerScrollProgress,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    } else {
      // Top header scrim that firms up as Storage & Bin content scrolls under the pinned title
      ScrollEdgeFade(
        isTop = true,
        height = 204.dp,
        strongTint = true,
        scrollProgress = headerScrollProgress,
        modifier = Modifier.align(Alignment.TopCenter),
      )
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        LibraryTitleRow(
          subtitle = headerSubtitle,
          title = sectionTitle,
          isIndexing = uiState.indexWorkStatus.isRunning,
          hasActiveFilters = hasActiveSecondaryFilters,
          onSettingsClick = {
            optionsDetent = GlassSheetDetent.HALF
            showOptionsSheet = true
          },
        )
        SectionSwitcherBar(
          section = uiState.section,
          recycleBinCount = uiState.recycleBinCount,
          onSelectSection = viewModel::setSection,
        )
      }
    }

    // Bottom overlay stack: Transfer progress banner, Multi-select batch bar, Status toast
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(bottom = 112.dp, start = 16.dp, end = 16.dp)
          .widthIn(max = 600.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      uiState.transferProgress?.let { progress ->
        TransferProgressBanner(
          progress = progress,
          onCancel = viewModel::cancelActiveTransfer,
          onDismiss = viewModel::dismissTransferProgress,
        )
      }

      if (uiState.isSelectionMode) {
        val ids = uiState.selectedIds.toList()
        MultiSelectBatchBar(
          selectedCount = ids.size,
          onCopy = { viewModel.openDestinationPicker(TransferOperationType.COPY, ids) },
          onMove = { viewModel.openDestinationPicker(TransferOperationType.MOVE, ids) },
          onShare = { shareIds(ids) },
          onTrash = { viewModel.moveToRecycleBin(ids) },
          onClear = viewModel::clearSelection,
        )
      }

      uiState.statusMessage?.let { msg ->
        GlassSurface(
          cornerRadius = 20.dp,
          elevation = GlassElevation.FLOATING,
          modifier = Modifier.testTag("browser_status_toast"),
        ) {
          Text(
            text = msg,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          )
        }
      }
    }

    ScrollEdgeFade(isTop = false, height = 104.dp, modifier = Modifier.align(Alignment.BottomCenter))

    // Sliders / Options Glass Bottom Sheet (Name filter, Sort, Layout, Settings, Debug samples)
    LibraryOptionsGlassSheet(
      visible = showOptionsSheet,
      detent = optionsDetent,
      onDetentChange = { optionsDetent = it },
      onDismiss = { showOptionsSheet = false },
      uiState = uiState,
      isDebugBuild = isDebugBuild,
      hasActiveSecondaryFilters = hasActiveSecondaryFilters,
      onNamePrefixChange = viewModel::setNamePrefix,
      onSortFieldSelected = viewModel::setSortField,
      onToggleSortDirection = viewModel::toggleSortDirection,
      onSetGridMode = { grid ->
        if (grid != uiState.isGridMode) viewModel.toggleGridMode()
      },
      onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
      onToggleRecentsOnly = viewModel::toggleRecentsOnly,
      onCollectionSelected = viewModel::setCollectionFilter,
      onTagSelected = viewModel::setTagFilter,
      onClearFilters = viewModel::clearAllFilters,
      onOpenGlassSettings = onOpenGlassSettings,
      onTriggerSync = viewModel::triggerManualSync,
      onAddSafFolder = onAddSafFolder,
      onClearThumbnailCache = viewModel::clearThumbnailCache,
      onCreateDebugSampleFiles = viewModel::createDebugSampleFiles,
    )
  }

  // Modal Dialogs for File Operations
  if (uiState.showCreateFolderDialog) {
    CreateFolderDialog(
      onDismiss = viewModel::dismissCreateFolderDialog,
      onCreate = viewModel::createFolderInCurrentDirectory,
    )
  }

  uiState.renameTarget?.let { target ->
    RenameDocumentDialog(
      document = target,
      onDismiss = viewModel::dismissRenameDialog,
      onConfirm = viewModel::confirmRename,
    )
  }

  uiState.destinationPicker?.let { picker ->
    DestinationFolderPickerDialog(
      state = picker,
      onSelectRoot = viewModel::openPickerRoot,
      onSelectSubfolder = viewModel::openPickerSubfolder,
      onSelectBreadcrumb = viewModel::navigatePickerBreadcrumb,
      onCollisionPolicyChange = viewModel::setPickerCollisionPolicy,
      onDismiss = viewModel::dismissDestinationPicker,
      onConfirm = viewModel::confirmDestinationPicker,
    )
  }
}

@Composable
private fun LibraryTitleRow(
  subtitle: String,
  title: String,
  isIndexing: Boolean,
  hasActiveFilters: Boolean,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f).padding(end = 12.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (isIndexing) {
          CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp)
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("library_header_subtitle"),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.displaySmall,
        color = colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    // One round glass sliders / settings button on the right
    Box {
      GlassSurface(
        cornerRadius = 24.dp,
        shape = CircleShape,
        elevation = GlassElevation.FLOATING,
        onClick = onSettingsClick,
        clickRole = Role.Button,
        modifier =
          Modifier.size(48.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("library_sliders_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Tune,
          contentDescription = "Library filters and settings",
          tint = colorScheme.onSurface,
          modifier = Modifier.align(Alignment.Center).size(22.dp),
        )
      }
      if (hasActiveFilters) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(3.dp)
              .size(10.dp)
              .clip(CircleShape)
              .background(PrismColors.IndigoBlue)
        )
      }
    }
  }
}

@Composable
private fun LibrarySearchPill(
  totalIndexedCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val placeholder =
    if (totalIndexedCount > 0) {
      "Search inside ${formatFileCount(totalIndexedCount)} documents"
    } else {
      "Search documents"
    }
  val boltGradient =
    Brush.linearGradient(
      colors = listOf(PrismColors.IndigoBlue, PrismColors.VioletAccent),
    )

  GlassSurface(
    cornerRadius = 29.dp,
    elevation = GlassElevation.FLOATING,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = modifier.fillMaxWidth().height(58.dp).testTag("library_search_pill"),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = placeholder,
        style = MaterialTheme.typography.bodyLarge,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Box(
        modifier =
          Modifier.size(42.dp)
            .shadow(
              elevation = 6.dp,
              shape = CircleShape,
              ambientColor = PrismColors.VioletAccent.copy(alpha = 0.36f),
              spotColor = PrismColors.VioletAccent.copy(alpha = 0.42f),
            )
            .clip(CircleShape)
            .background(boltGradient),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Bolt,
          contentDescription = "Instant search",
          tint = Color.White,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

@Composable
private fun SectionSwitcherBar(
  section: FilesSection,
  recycleBinCount: Int,
  onSelectSection: (FilesSection) -> Unit,
  modifier: Modifier = Modifier,
) {
  val binLabel = if (recycleBinCount > 0) "Bin ($recycleBinCount)" else "Bin"
  GlassSegmentedControl(
    options = listOf("Library", "Storage", binLabel),
    selectedIndex =
      when (section) {
        FilesSection.LIBRARY -> 0
        FilesSection.STORAGE -> 1
        FilesSection.RECYCLE_BIN -> 2
      },
    onSelect = { idx ->
      onSelectSection(
        when (idx) {
          1 -> FilesSection.STORAGE
          2 -> FilesSection.RECYCLE_BIN
          else -> FilesSection.LIBRARY
        }
      )
    },
    modifier = modifier.fillMaxWidth().widthIn(max = 760.dp),
  )
}

@Composable
private fun LibraryPagingSection(
  uiState: FilesUiState,
  listState: LazyListState,
  sectionTitle: String,
  headerSubtitle: String,
  hasActiveSecondaryFilters: Boolean,
  preferences: PrismPreferences,
  storageAccessState: StorageAccessState,
  pagedItems: LazyPagingItems<IndexedDocumentEntity>,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onOpenOptionsSheet: () -> Unit,
  onOpenSearch: () -> Unit,
  onSelectSection: (FilesSection) -> Unit,
  onRequestAllFilesAccess: () -> Unit,
  onAddSafFolder: () -> Unit,
  onDismissAccessBanner: () -> Unit,
  onTriggerSync: () -> Unit,
  onCategorySelected: (DocumentCategory?) -> Unit,
  onSeeAllRecents: () -> Unit,
  onClearFilters: () -> Unit,
  onOpenDocument: (IndexedDocumentEntity) -> Unit,
  onToggleSelection: (Long) -> Unit,
  onToggleFavorite: (Long, Boolean) -> Unit,
  onRenameDocument: (IndexedDocumentEntity) -> Unit,
  onCopyDocument: (IndexedDocumentEntity) -> Unit,
  onMoveDocument: (IndexedDocumentEntity) -> Unit,
  onShareDocument: (IndexedDocumentEntity) -> Unit,
  onTrashDocument: (IndexedDocumentEntity) -> Unit,
  onTabBarCollapsedChange: (Boolean) -> Unit,
) {
  val hazeState = LocalHazeState.current
  val showAccessBanner = storageAccessState.isDegradedMode && !preferences.fullAccessBannerDismissed
  val hasAnyFilter = hasActiveSecondaryFilters || uiState.query.category != null

  if (uiState.isGridMode) {
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
      state = gridState,
      columns = GridCells.Adaptive(minSize = 164.dp),
      contentPadding = PaddingValues(top = 12.dp, bottom = 144.dp, start = 20.dp, end = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("library_grid"),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        LibraryHeroStack(
          uiState = uiState,
          sectionTitle = sectionTitle,
          headerSubtitle = headerSubtitle,
          hasActiveSecondaryFilters = hasActiveSecondaryFilters,
          showAccessBanner = showAccessBanner,
          thumbnailPipeline = thumbnailPipeline,
          onOpenOptionsSheet = onOpenOptionsSheet,
          onOpenSearch = onOpenSearch,
          onSelectSection = onSelectSection,
          onRequestAllFilesAccess = onRequestAllFilesAccess,
          onAddSafFolder = onAddSafFolder,
          onDismissAccessBanner = onDismissAccessBanner,
          onCategorySelected = onCategorySelected,
          onSeeAllRecents = onSeeAllRecents,
          onClearFilters = onClearFilters,
          onOpenDocument = onOpenDocument,
          onToggleSelection = onToggleSelection,
        )
      }

      items(
        count = pagedItems.itemCount,
        key = { idx -> pagedItems.peek(idx)?.id ?: "lib_grid_placeholder_$idx" },
      ) { idx ->
        val doc = pagedItems[idx] ?: return@items
        DocumentGridCard(
          document = doc,
          thumbnailPipeline = thumbnailPipeline,
          isSelected = doc.id in uiState.selectedIds,
          onClick = {
            if (uiState.isSelectionMode) onToggleSelection(doc.id) else onOpenDocument(doc)
          },
          onLongClick = { onToggleSelection(doc.id) },
          onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
          onRename = { onRenameDocument(doc) },
          onCopy = { onCopyDocument(doc) },
          onMove = { onMoveDocument(doc) },
          onShare = { onShareDocument(doc) },
          onTrash = { onTrashDocument(doc) },
          modifier = if (idx < 8) Modifier.glassEntrance(index = idx) else Modifier,
        )
      }

      if (pagedItems.itemCount == 0 && pagedItems.loadState.refresh is LoadState.NotLoading) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          LibraryEmptyStateCard(
            hasActiveFilters = hasAnyFilter,
            hasFullAccess = storageAccessState.canBrowseFullDevice,
            onClearFilters = onClearFilters,
            onAddSafFolder = onAddSafFolder,
            onTriggerSync = onTriggerSync,
          )
        }
      }
    }
  } else {
    ObserveScrollCollapse(listState = listState, onCollapsedChanged = onTabBarCollapsedChange)

    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(top = 12.dp, bottom = 144.dp, start = 20.dp, end = 20.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("library_list"),
    ) {
      item(key = "library_hero_stack") {
        LibraryHeroStack(
          uiState = uiState,
          sectionTitle = sectionTitle,
          headerSubtitle = headerSubtitle,
          hasActiveSecondaryFilters = hasActiveSecondaryFilters,
          showAccessBanner = showAccessBanner,
          thumbnailPipeline = thumbnailPipeline,
          onOpenOptionsSheet = onOpenOptionsSheet,
          onOpenSearch = onOpenSearch,
          onSelectSection = onSelectSection,
          onRequestAllFilesAccess = onRequestAllFilesAccess,
          onAddSafFolder = onAddSafFolder,
          onDismissAccessBanner = onDismissAccessBanner,
          onCategorySelected = onCategorySelected,
          onSeeAllRecents = onSeeAllRecents,
          onClearFilters = onClearFilters,
          onOpenDocument = onOpenDocument,
          onToggleSelection = onToggleSelection,
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        )
        Spacer(Modifier.height(16.dp))
      }

      // One big rounded glass card holding the virtualized Paging 3 file list
      val totalCount = pagedItems.itemCount
      items(
        count = totalCount,
        key = { idx -> pagedItems.peek(idx)?.id ?: "lib_list_placeholder_$idx" },
      ) { idx ->
        val doc = pagedItems[idx] ?: return@items
        LibraryCardDocumentRow(
          document = doc,
          isFirst = idx == 0,
          isLast = idx == totalCount - 1,
          isSelected = doc.id in uiState.selectedIds,
          onClick = {
            if (uiState.isSelectionMode) onToggleSelection(doc.id) else onOpenDocument(doc)
          },
          onLongClick = { onToggleSelection(doc.id) },
          onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
          onRename = { onRenameDocument(doc) },
          onCopy = { onCopyDocument(doc) },
          onMove = { onMoveDocument(doc) },
          onShare = { onShareDocument(doc) },
          onTrash = { onTrashDocument(doc) },
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 760.dp)
              .then(if (idx < 8) Modifier.glassEntrance(index = idx) else Modifier),
        )
      }

      if (totalCount == 0 && pagedItems.loadState.refresh is LoadState.NotLoading) {
        item(key = "library_empty_card") {
          LibraryEmptyStateCard(
            hasActiveFilters = hasAnyFilter,
            hasFullAccess = storageAccessState.canBrowseFullDevice,
            onClearFilters = onClearFilters,
            onAddSafFolder = onAddSafFolder,
            onTriggerSync = onTriggerSync,
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun LibraryHeroStack(
  uiState: FilesUiState,
  sectionTitle: String,
  headerSubtitle: String,
  hasActiveSecondaryFilters: Boolean,
  showAccessBanner: Boolean,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onOpenOptionsSheet: () -> Unit,
  onOpenSearch: () -> Unit,
  onSelectSection: (FilesSection) -> Unit,
  onRequestAllFilesAccess: () -> Unit,
  onAddSafFolder: () -> Unit,
  onDismissAccessBanner: () -> Unit,
  onCategorySelected: (DocumentCategory?) -> Unit,
  onSeeAllRecents: () -> Unit,
  onClearFilters: () -> Unit,
  onOpenDocument: (IndexedDocumentEntity) -> Unit,
  onToggleSelection: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // 1. Small subtitle above heavy 36sp "Library" title + single round glass sliders button
    LibraryTitleRow(
      subtitle = headerSubtitle,
      title = sectionTitle,
      isIndexing = uiState.indexWorkStatus.isRunning,
      hasActiveFilters = hasActiveSecondaryFilters,
      onSettingsClick = onOpenOptionsSheet,
    )

    // 2. Large glass search pill with magnifier + blue-violet round bolt button -> opens Search
    LibrarySearchPill(
      totalIndexedCount = uiState.totalIndexedCount,
      onClick = onOpenSearch,
    )

    // 3. Slim glass Library | Storage | Bin switcher
    SectionSwitcherBar(
      section = uiState.section,
      recycleBinCount = uiState.recycleBinCount,
      onSelectSection = onSelectSection,
    )

    if (showAccessBanner) {
      GlassSurface(
        cornerRadius = 24.dp,
        elevation = GlassElevation.SUBTLE,
        accentTint = colorScheme.primary,
        modifier = Modifier.fillMaxWidth().testTag("grant_full_access_banner"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(imageVector = Icons.Rounded.Shield, contentDescription = null, tint = colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Limited folder visibility",
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
              )
              Text(
                text = "Grant All Files Access or link SAF folders so every document appears here.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
            TextButton(onClick = onDismissAccessBanner) { Text("Hide") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = onRequestAllFilesAccess,
              modifier = Modifier.testTag("banner_grant_full_access_button"),
            ) {
              Text("Grant Full Access")
            }
            OutlinedButton(
              onClick = onAddSafFolder,
              modifier = Modifier.testTag("banner_add_saf_folder_button"),
            ) {
              Text("Add Folder")
            }
          }
        }
      }
    }

    // 4. "Recent" title + "See all" link + 158dp glass cards (hidden when empty)
    if (uiState.recentDocuments.isNotEmpty()) {
      RecentDocumentsCarousel(
        recentDocuments = uiState.recentDocuments,
        selectedIds = uiState.selectedIds,
        isSelectionMode = uiState.isSelectionMode,
        thumbnailPipeline = thumbnailPipeline,
        onSeeAllClick = onSeeAllRecents,
        onOpenDocument = onOpenDocument,
        onToggleSelection = onToggleSelection,
      )
    }

    // 5. Category chips: selected = blue-violet gradient pill with white text; others = glass pills
    CategoryGlassChipRow(
      selectedCategory = uiState.query.category,
      onCategorySelected = onCategorySelected,
    )

    // Compact active filter indicator bar only when user filtered via sliders sheet or "See all"
    if (hasActiveSecondaryFilters) {
      ActiveFilterSummaryBar(
        uiState = uiState,
        onClearFilters = onClearFilters,
      )
    }
  }
}

@Composable
private fun CategoryGlassChipRow(
  selectedCategory: DocumentCategory?,
  onCategorySelected: (DocumentCategory?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val rowState = rememberLazyListState()
  val canScrollForward by remember { derivedStateOf { rowState.canScrollForward } }
  val fadeAlpha by animateFloatAsState(
    targetValue = if (canScrollForward) 1f else 0f,
    label = "category_chip_fade",
  )

  val categories = remember {
    listOf<Pair<DocumentCategory?, String>>(
      null to "All files",
      DocumentCategory.PDF to "PDF",
      DocumentCategory.DOCUMENTS to "Word",
      DocumentCategory.SPREADSHEETS to "Excel",
      DocumentCategory.SLIDES to "Slides",
      DocumentCategory.IMAGES to "Images",
      DocumentCategory.VIDEOS to "Video",
      DocumentCategory.AUDIO to "Audio",
      DocumentCategory.ARCHIVES to "ZIP",
      DocumentCategory.EBOOKS to "Books",
      DocumentCategory.CODE_TEXT to "Code",
    )
  }

  LazyRow(
    state = rowState,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(end = 30.dp),
    modifier = modifier.fillMaxWidth().horizontalRightEdgeFade(fadeFraction = fadeAlpha),
  ) {
    items(categories, key = { it.first?.name ?: "ALL" }) { (category, label) ->
      val isSelected = selectedCategory == category
      CategoryGlassPill(
        label = label,
        selected = isSelected,
        testTag = if (category == null) "category_chip_ALL" else "category_chip_${category.name}",
        onClick = { onCategorySelected(category) },
      )
    }
  }
}

@Composable
private fun CategoryGlassPill(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val gradientBrush =
    Brush.linearGradient(
      colors = listOf(PrismColors.IndigoBlue, PrismColors.VioletAccent),
    )

  if (selected) {
    Box(
      modifier =
        Modifier.height(44.dp)
          .defaultMinSize(minWidth = 64.dp)
          .glassClickable(role = Role.Tab, onClick = onClick)
          .shadow(
            elevation = 8.dp,
            shape = CircleShape,
            ambientColor = PrismColors.IndigoBlue.copy(alpha = 0.34f),
            spotColor = PrismColors.VioletAccent.copy(alpha = 0.42f),
          )
          .clip(CircleShape)
          .background(gradientBrush)
          .padding(horizontal = 20.dp)
          .testTag(testTag),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
      )
    }
  } else {
    GlassSurface(
      cornerRadius = 22.dp,
      shape = CircleShape,
      elevation = GlassElevation.SUBTLE,
      enableRefraction = false,
      onClick = onClick,
      clickRole = Role.Tab,
      modifier = Modifier.height(44.dp).defaultMinSize(minWidth = 64.dp).testTag(testTag),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label,
          fontFamily = FontFamily.SansSerif,
          fontSize = 14.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = colorScheme.onSurface,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun ActiveFilterSummaryBar(
  uiState: FilesUiState,
  onClearFilters: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val summaryParts = buildList {
    if (!uiState.query.namePrefix.isNullOrBlank()) add("Name: \"${uiState.query.namePrefix}\"")
    if (uiState.query.favoritesOnly) add("Favorites")
    if (uiState.query.recentsOnly) add("All Recents")
    uiState.query.collectionId?.let { id ->
      uiState.collections.firstOrNull { it.id == id }?.let { add(it.name) }
    }
    uiState.query.tagId?.let { id ->
      uiState.tags.firstOrNull { it.id == id }?.let { add("#${it.name}") }
    }
  }
  if (summaryParts.isEmpty()) return

  GlassSurface(
    cornerRadius = 18.dp,
    elevation = GlassElevation.SUBTLE,
    enableRefraction = false,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "Filtered: ${summaryParts.joinToString(" • ")}",
        style = MaterialTheme.typography.labelLarge,
        color = colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onClearFilters, modifier = Modifier.testTag("active_filter_reset_button")) {
        Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("Clear")
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentDocumentsCarousel(
  recentDocuments: List<IndexedDocumentEntity>,
  selectedIds: Set<Long>,
  isSelectionMode: Boolean,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onSeeAllClick: () -> Unit,
  onOpenDocument: (IndexedDocumentEntity) -> Unit,
  onToggleSelection: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val rowState = rememberLazyListState()
  val canScrollForward by remember { derivedStateOf { rowState.canScrollForward } }
  val fadeAlpha by animateFloatAsState(
    targetValue = if (canScrollForward) 1f else 0f,
    label = "recent_row_fade",
  )
  val linkBlue = if (env.isDark) colorScheme.primary else PrismColors.IndigoBlue

  Column(
    modifier = modifier.fillMaxWidth().testTag("library_recent_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Recent",
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
        color = colorScheme.onSurface,
      )
      Text(
        text = "See all",
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = linkBlue,
        modifier =
          Modifier.clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onSeeAllClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("recent_see_all_button"),
      )
    }

    LazyRow(
      state = rowState,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(end = 28.dp),
      modifier = Modifier.fillMaxWidth().horizontalRightEdgeFade(fadeFraction = fadeAlpha),
    ) {
      itemsIndexed(recentDocuments, key = { _, doc -> "recent_${doc.id}" }) { idx, doc ->
        val isSelected = doc.id in selectedIds
        val request = doc.toThumbnailRequest(targetSizePx = 380)
        val caption =
          remember(doc.sizeBytes, doc.lastOpenedEpochMs, doc.lastModifiedEpochMs) {
            "${formatByteSize(doc.sizeBytes)}, ${formatRelativeDay(doc.lastOpenedEpochMs ?: doc.lastModifiedEpochMs)}"
          }
        val interactionSource = remember { MutableInteractionSource() }

        GlassSurface(
          cornerRadius = 26.dp,
          elevation = GlassElevation.SUBTLE,
          accentTint = if (isSelected) colorScheme.primary else null,
          modifier =
            Modifier.width(158.dp)
              .then(if (idx < 8) Modifier.glassEntrance(index = idx) else Modifier)
              .glassPress(interactionSource)
              .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                  if (isSelectionMode) onToggleSelection(doc.id) else onOpenDocument(doc)
                },
                onLongClick = { onToggleSelection(doc.id) },
              )
              .testTag("recent_doc_card_${doc.id}"),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            DocumentThumbnailView(
              request = request,
              pipeline = thumbnailPipeline,
              cornerRadius = 18.dp,
              compact = false,
              showCornerBadge = true,
              modifier = Modifier.fillMaxWidth().height(134.dp),
            )
            Column(
              modifier = Modifier.padding(horizontal = 2.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = doc.displayName.substringBeforeLast('.').ifBlank { doc.displayName },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun SortControlRow(
  sortField: FileSortField,
  sortDirection: SortDirection,
  onSortFieldSelected: (FileSortField) -> Unit,
  onToggleSortDirection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val rowState = rememberLazyListState()
  val canScrollForward by remember { derivedStateOf { rowState.canScrollForward } }
  val fadeAlpha by animateFloatAsState(
    targetValue = if (canScrollForward) 1f else 0f,
    label = "sort_chip_fade",
  )

  val options =
    listOf(
      FileSortField.DATE_MODIFIED to "Date",
      FileSortField.NAME to "Name",
      FileSortField.SIZE to "Size",
      FileSortField.TYPE to "Type",
    )
  LazyRow(
    state = rowState,
    modifier = modifier.horizontalRightEdgeFade(fadeFraction = fadeAlpha),
    contentPadding = PaddingValues(end = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items(options) { (field, label) ->
      val selected = sortField == field
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
          Modifier.clip(RoundedCornerShape(16.dp))
            .background(
              if (selected) colorScheme.primaryContainer
              else colorScheme.surface.copy(alpha = 0.72f)
            )
            .clickable { onSortFieldSelected(field) }
            .padding(horizontal = 13.dp, vertical = 8.dp)
            .testTag("sort_chip_${field.name}"),
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
          Icon(
            imageVector =
              if (sortDirection == SortDirection.ASC) Icons.Rounded.ArrowUpward
              else Icons.Rounded.ArrowDownward,
            contentDescription = if (sortDirection == SortDirection.ASC) "Ascending" else "Descending",
            tint = colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp).clickable { onToggleSortDirection() },
          )
        }
      }
    }
  }
}

@Composable
private fun LibraryEmptyStateCard(
  hasActiveFilters: Boolean,
  hasFullAccess: Boolean,
  onClearFilters: () -> Unit,
  onAddSafFolder: () -> Unit,
  onTriggerSync: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 26.dp,
    elevation = GlassElevation.SUBTLE,
    modifier = modifier.fillMaxWidth().testTag("library_empty_card"),
  ) {
    Column(
      modifier = Modifier.padding(24.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = if (hasActiveFilters) "No matching documents" else "No indexed documents yet",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
      )
      Text(
        text =
          if (hasActiveFilters) "Try clearing your active category, favorite, or name filters."
          else if (hasFullAccess)
            "Tap Sync to scan your device storage, or open the sliders menu to create sample files."
          else
            "Grant All Files Access or pick a folder via SAF to bring your documents into Prism.",
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (hasActiveFilters) {
          Button(onClick = onClearFilters) { Text("Clear Filters") }
        } else {
          Button(onClick = onTriggerSync) { Text("Sync Now") }
          if (!hasFullAccess) {
            OutlinedButton(onClick = onAddSafFolder) { Text("Add Folder") }
          }
        }
      }
    }
  }
}
