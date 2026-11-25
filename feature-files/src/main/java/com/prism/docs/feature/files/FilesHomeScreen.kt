package com.prism.docs.feature.files

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.FileCopy
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.prism.docs.core.data.db.CollectionWithCountRow
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.db.TagWithCountRow
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
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.ObserveScrollCollapse
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.glassEntrance
import com.prism.docs.core.ui.glass.glassPress
import com.prism.docs.core.ui.glass.horizontalRightEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
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
  onOpenDuplicateFinder: () -> Unit = {},
  onOpenStorageAnalyzer: () -> Unit = {},
  onOpenCollectionDetail: (Long) -> Unit = {},
  isDebugBuild: Boolean = false,
  hasBottomTabBar: Boolean = true,
  viewModel: FilesViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val haptics = rememberPrismHaptics()
  val motionEnabled = LocalGlassMotion.current
  val density = LocalDensity.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val libraryItems = viewModel.libraryPagingData.collectAsLazyPagingItems()
  val storageDirectoryItems = viewModel.storageDirectoryPagingData.collectAsLazyPagingItems()
  val colorScheme = MaterialTheme.colorScheme

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var libraryTopHeightPx by remember { mutableIntStateOf(0) }
  var secondaryTopHeightPx by remember { mutableIntStateOf(0) }
  val currentSectionIndex =
    when (uiState.section) {
      FilesSection.LIBRARY -> 0
      FilesSection.STORAGE -> 1
      FilesSection.RECYCLE_BIN -> 2
    }
  var lastSectionIndex by rememberSaveable { mutableIntStateOf(currentSectionIndex) }
  LaunchedEffect(currentSectionIndex) {
    lastSectionIndex = currentSectionIndex
  }

  var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
  var optionsDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }
  var assignDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }
  var manageLabelsDetent by remember { mutableStateOf(GlassSheetDetent.HALF) }

  val hasActiveSecondaryFilters =
    uiState.query.favoritesOnly ||
      uiState.query.recentsOnly ||
      uiState.query.collectionId != null ||
      uiState.query.tagId != null ||
      uiState.query.otherCategoriesOnly ||
      !uiState.query.namePrefix.isNullOrBlank()

  LaunchedEffect(storageAccessState) { viewModel.refreshStorageRoots() }

  LaunchedEffect(uiState.statusMessage) {
    if (uiState.statusMessage != null) {
      delay(3_200L)
      viewModel.clearStatusMessage()
    }
  }

  BackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }

  val toggleSelectionWithHaptics: (Long) -> Unit = { docId ->
    val wasEmpty = uiState.selectedIds.isEmpty()
    val adding = docId !in uiState.selectedIds
    if (adding && wasEmpty) {
      haptics.longPress()
    } else {
      haptics.segmentTick()
    }
    viewModel.toggleSelection(docId)
  }

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

  val shareChooserTitle = stringResource(R.string.action_share_chooser_title)
  val shareIds: (List<Long>) -> Unit = { ids ->
    viewModel.buildShareIntentForIds(ids) { intent ->
      if (intent != null) {
        runCatching { context.startActivity(Intent.createChooser(intent, shareChooserTitle)) }
      }
    }
  }

  val sectionTitle =
    when (uiState.section) {
      FilesSection.LIBRARY -> stringResource(R.string.section_library)
      FilesSection.STORAGE -> stringResource(R.string.section_storage)
      FilesSection.RECYCLE_BIN -> stringResource(R.string.section_recycle_bin)
    }

  val headerSubtitle =
    when {
      uiState.indexWorkStatus.isRunning -> stringResource(R.string.library_subtitle_indexing)
      uiState.section == FilesSection.RECYCLE_BIN ->
        pluralStringResource(
          R.plurals.recycle_bin_header_subtitle,
          uiState.recycleBinCount,
          formatFileCount(uiState.recycleBinCount),
        )
      else ->
        pluralStringResource(
          R.plurals.library_indexed_files_subtitle,
          uiState.totalIndexedCount,
          formatFileCount(uiState.totalIndexedCount),
        )
    }

  val libraryListState = rememberLazyListState()
  val libraryGridState = rememberLazyGridState()
  val storageRootsListState = rememberLazyListState()
  val storageDirListState = rememberLazyListState()
  val recycleBinListState = rememberLazyListState()

  val libraryListScroll = rememberHeaderScrollProgress(libraryListState)
  val libraryGridScroll = rememberHeaderScrollProgress(libraryGridState)
  val storageRootsScroll = rememberHeaderScrollProgress(storageRootsListState)
  val storageDirScroll = rememberHeaderScrollProgress(storageDirListState)
  val recycleBinScroll = rememberHeaderScrollProgress(recycleBinListState)

  LaunchedEffect(uiState.section, uiState.isSelectionMode, uiState.isGridMode) {
    onTabBarCollapsedChange(false)
  }

  when (uiState.section) {
    FilesSection.STORAGE -> {
      val activeStorageList =
        if (uiState.storageBrowser.currentDirectory == null) storageRootsListState
        else storageDirListState
      ObserveScrollCollapse(
        listState = activeStorageList,
        onCollapsedChanged = onTabBarCollapsedChange,
      )
    }
    FilesSection.RECYCLE_BIN -> {
      ObserveScrollCollapse(
        listState = recycleBinListState,
        onCollapsedChanged = onTabBarCollapsedChange,
      )
    }
    FilesSection.LIBRARY -> Unit
  }

  val headerScrollProgress =
    when (uiState.section) {
      FilesSection.LIBRARY -> if (uiState.isGridMode) libraryGridScroll else libraryListScroll
      FilesSection.STORAGE ->
        if (uiState.storageBrowser.currentDirectory == null) storageRootsScroll
        else storageDirScroll
      FilesSection.RECYCLE_BIN -> recycleBinScroll
    }

  val measuredTopHeightDp =
    if (uiState.section == FilesSection.LIBRARY) {
      if (libraryTopHeightPx > 0) with(density) { libraryTopHeightPx.toDp() }
      else statusBarTop + 68.dp
    } else {
      if (secondaryTopHeightPx > 0) with(density) { secondaryTopHeightPx.toDp() }
      else statusBarTop + 148.dp
    }
  val animatedTopPadding by
    animateDpAsState(
      targetValue = measuredTopHeightDp + 4.dp,
      label = "files_top_content_padding",
    )

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedContent(
      targetState = uiState.section,
      transitionSpec = {
        if (motionEnabled) {
          val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
          (fadeIn(animationSpec = tween(durationMillis = 170)) +
            slideInHorizontally(
              animationSpec = spring(dampingRatio = 0.86f, stiffness = 460f),
              initialOffsetX = { fullWidth -> direction * (fullWidth / 12) },
            ))
            .togetherWith(
              fadeOut(animationSpec = tween(durationMillis = 115)) +
                slideOutHorizontally(
                  animationSpec = tween(durationMillis = 130),
                  targetOffsetX = { fullWidth -> -direction * (fullWidth / 14) },
                )
            )
        } else {
          EnterTransition.None togetherWith ExitTransition.None
        }
      },
      modifier = Modifier.fillMaxSize(),
      label = "files_section_transition",
    ) { activeSection ->
      when (activeSection) {
        FilesSection.LIBRARY ->
          LibraryPagingSection(
            uiState = uiState,
            listState = libraryListState,
            gridState = libraryGridState,
            sectionTitle = sectionTitle,
            animateSwitcherFromIndex = lastSectionIndex,
            topBarHeightDp = measuredTopHeightDp,
            topContentPadding = animatedTopPadding,
            headerScrollProgress = headerScrollProgress,
            hasActiveSecondaryFilters = hasActiveSecondaryFilters,
            isDebugBuild = isDebugBuild,
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
            onTagSelected = viewModel::setTagFilter,
            onOpenDuplicateFinder = onOpenDuplicateFinder,
            onOpenStorageAnalyzer = onOpenStorageAnalyzer,
            onOpenCollectionDetail = onOpenCollectionDetail,
            onOpenManageTagsCollections = viewModel::openManageTagsCollectionsSheet,
            onManageDocumentTagsCollections = { doc -> viewModel.openAssignSheet(listOf(doc.id)) },
            onSeeAllRecents = {
              if (!uiState.query.recentsOnly) {
                viewModel.toggleRecentsOnly()
              }
            },
            onClearFilters = viewModel::clearAllFilters,
            onOpenDocument = openIndexedDoc,
            onToggleSelection = toggleSelectionWithHaptics,
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
            topContentPadding = animatedTopPadding,
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
            onToggleSelection = toggleSelectionWithHaptics,
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
            topContentPadding = animatedTopPadding,
            listState = recycleBinListState,
          )
      }
    }

    if (uiState.section == FilesSection.LIBRARY) {
      ScrollEdgeFade(
        isTop = true,
        height = measuredTopHeightDp + 14.dp,
        scrollProgress = headerScrollProgress,
        modifier = Modifier.align(Alignment.TopCenter),
      )

      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { libraryTopHeightPx = it.height }
            .statusBarsPadding()
            .padding(
              horizontal = PrismDimens.ScreenHorizontalPadding,
              vertical = PrismDimens.SpaceSmall,
            ),
        contentAlignment = Alignment.Center,
      ) {
        val motion = LocalGlassMotion.current
        AnimatedContent(
          targetState = uiState.isSelectionMode,
          contentAlignment = Alignment.TopCenter,
          transitionSpec = {
            if (motion) {
              (fadeIn(animationSpec = tween(durationMillis = 180)) +
                slideInVertically(
                  animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
                  initialOffsetY = { -it / 4 },
                ) +
                expandVertically(
                  animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                  expandFrom = Alignment.Top,
                ))
                .togetherWith(
                  fadeOut(animationSpec = tween(durationMillis = 130)) +
                    slideOutVertically(
                      animationSpec = tween(durationMillis = 140),
                      targetOffsetY = { -it / 5 },
                    ) +
                    shrinkVertically(
                      animationSpec = spring(dampingRatio = 0.88f, stiffness = 460f),
                      shrinkTowards = Alignment.Top,
                    )
                )
                .using(
                  SizeTransform(
                    clip = false,
                    sizeAnimationSpec = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 420f) },
                  )
                )
            } else {
              (EnterTransition.None togetherWith ExitTransition.None)
                .using(SizeTransform(clip = false))
            }
          },
          modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          label = "library_top_bar_mode",
        ) { selectionActive ->
          if (selectionActive) {
            val selectedIdsList = uiState.selectedIds.toList()
            SelectionGlassTopBar(
              selectedCount = uiState.selectedIds.size,
              onClearSelection = viewModel::clearSelection,
              onSelectAll = {
                haptics.segmentTick()
                val loadedIds =
                  buildSet {
                    libraryItems.itemSnapshotList.items.forEach { add(it.id) }
                    uiState.recentDocuments.forEach { add(it.id) }
                  }
                viewModel.selectAll(loadedIds)
              },
              onTagsCollections = { viewModel.openAssignSheet(selectedIdsList) },
              onShare = { shareIds(selectedIdsList) },
              onMove = {
                viewModel.openDestinationPicker(TransferOperationType.MOVE, selectedIdsList)
              },
              onDelete = { viewModel.moveToRecycleBin(selectedIdsList) },
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            LibraryCollapsingTopBar(
              title = sectionTitle,
              subtitle = headerSubtitle,
              isIndexing = uiState.indexWorkStatus.isRunning,
              hasActiveFilters = hasActiveSecondaryFilters,
              scrollProgress = headerScrollProgress,
              onEnterSelectionMode = {
                haptics.segmentTick()
                viewModel.enterSelectionMode()
              },
              onSettingsClick = {
                optionsDetent = GlassSheetDetent.HALF
                showOptionsSheet = true
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    } else {
      ScrollEdgeFade(
        isTop = true,
        height = measuredTopHeightDp + 14.dp,
        strongTint = false,
        scrollProgress = headerScrollProgress,
        modifier = Modifier.align(Alignment.TopCenter),
      )
      Column(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .widthIn(max = PrismDimens.ContentMaxWidth)
            .fillMaxWidth()
            .onSizeChanged { secondaryTopHeightPx = it.height }
            .statusBarsPadding()
            .padding(
              horizontal = PrismDimens.ScreenHorizontalPadding,
              vertical = PrismDimens.SpaceMediumLarge,
            ),
        verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      ) {
        SecondarySectionTitleRow(
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
          animateFromIndex = lastSectionIndex,
          onSelectSection = viewModel::setSection,
        )
      }
    }

    val overlayBottomClearance =
      if (hasBottomTabBar) PrismDimens.OverlayBottomPadding else PrismDimens.SpaceLarge

    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(
            bottom = overlayBottomClearance,
            start = PrismDimens.SpaceSection,
            end = PrismDimens.SpaceSection,
          )
          .widthIn(max = PrismDimens.OverlayMaxWidth),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    ) {
      AnimatedVisibility(
        visible = uiState.transferProgress != null,
        enter =
          slideInVertically(
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            initialOffsetY = { it / 2 },
          ) + fadeIn(tween(150)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(120)),
      ) {
        uiState.transferProgress?.let { progress ->
          TransferProgressBanner(
            progress = progress,
            onCancel = viewModel::cancelActiveTransfer,
            onDismiss = viewModel::dismissTransferProgress,
          )
        }
      }

      AnimatedVisibility(
        visible = uiState.isSelectionMode && uiState.section != FilesSection.LIBRARY,
        enter =
          slideInVertically(
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            initialOffsetY = { it / 2 },
          ) + fadeIn(tween(150)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(120)),
      ) {
        val ids = uiState.selectedIds.toList()
        MultiSelectBatchBar(
          selectedCount = ids.size,
          onTagsCollections = { viewModel.openAssignSheet(ids) },
          onCopy = { viewModel.openDestinationPicker(TransferOperationType.COPY, ids) },
          onMove = { viewModel.openDestinationPicker(TransferOperationType.MOVE, ids) },
          onShare = { shareIds(ids) },
          onTrash = { viewModel.moveToRecycleBin(ids) },
          onClear = viewModel::clearSelection,
        )
      }

      AnimatedVisibility(
        visible = uiState.statusMessage != null,
        enter =
          slideInVertically(
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
            initialOffsetY = { it / 2 },
          ) + fadeIn(tween(140)) + scaleIn(initialScale = 0.92f),
        exit =
          slideOutVertically(targetOffsetY = { it / 3 }) +
            fadeOut(tween(120)) +
            scaleOut(targetScale = 0.94f),
      ) {
        uiState.statusMessage?.let { msg ->
          GlassSurface(
            cornerRadius = PrismDimens.ToastCornerRadius,
            elevation = GlassElevation.FLOATING,
            modifier = Modifier.testTag("browser_status_toast"),
          ) {
            Text(
              text = msg,
              style = MaterialTheme.typography.labelLarge,
              color = colorScheme.onSurface,
              modifier =
                Modifier.padding(
                  horizontal = PrismDimens.SpaceSection,
                  vertical = PrismDimens.SpaceMediumLarge,
                ),
            )
          }
        }
      }
    }

    ScrollEdgeFade(
      isTop = false,
      height = navBarBottom + PrismDimens.BottomScrimHeight,
      modifier = Modifier.align(Alignment.BottomCenter),
    )

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
      onOpenDuplicateFinder = onOpenDuplicateFinder,
      onOpenStorageAnalyzer = onOpenStorageAnalyzer,
      onOpenManageTagsCollections = viewModel::openManageTagsCollectionsSheet,
      onOpenGlassSettings = onOpenGlassSettings,
      onTriggerSync = viewModel::triggerManualSync,
      onAddSafFolder = onAddSafFolder,
      onClearThumbnailCache = viewModel::clearThumbnailCache,
      onCreateDebugSampleFiles = viewModel::createDebugSampleFiles,
    )

    val assignState = uiState.assignSheetState
    AssignTagsCollectionsSheet(
      visible = assignState != null,
      detent = assignDetent,
      onDetentChange = { assignDetent = it },
      onDismiss = viewModel::dismissAssignSheet,
      targetDocIds = assignState?.targetDocumentIds.orEmpty(),
      collections = uiState.collectionsWithStats,
      tags = uiState.tagsWithStats,
      collectionAssignedCounts = assignState?.collectionAssignedCounts.orEmpty(),
      tagAssignedCounts = assignState?.tagAssignedCounts.orEmpty(),
      onToggleCollection = viewModel::toggleCollectionForTargets,
      onToggleTag = viewModel::toggleTagForTargets,
      onCreateCollection = { name, hex ->
        viewModel.createCollection(name, hex, assignToTargets = true)
      },
      onCreateTag = { name, hex ->
        viewModel.createTag(name, hex, assignToTargets = true)
      },
    )

    ManageTagsCollectionsSheet(
      visible = uiState.showManageTagsCollectionsSheet,
      detent = manageLabelsDetent,
      onDetentChange = { manageLabelsDetent = it },
      onDismiss = viewModel::dismissManageTagsCollectionsSheet,
      collections = uiState.collectionsWithStats,
      tags = uiState.tagsWithStats,
      onOpenCollection = onOpenCollectionDetail,
      onCreateCollection = { name, hex -> viewModel.createCollection(name, hex) },
      onRenameCollection = viewModel::renameCollection,
      onDeleteCollection = viewModel::deleteCollection,
      onCreateTag = { name, hex -> viewModel.createTag(name, hex) },
      onRenameTag = viewModel::renameTag,
      onDeleteTag = viewModel::deleteTag,
    )
  }

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
private fun SecondarySectionTitleRow(
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
      modifier = Modifier.weight(1f).padding(end = PrismDimens.SpaceStandard),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
      ) {
        if (isIndexing) {
          CircularProgressIndicator(
            modifier = Modifier.size(PrismDimens.HeaderIndexingSpinnerSize),
            strokeWidth = 1.8.dp,
          )
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

    Box {
      GlassSurface(
        cornerRadius = PrismDimens.CategoryChipCornerRadius,
        shape = CircleShape,
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = true,
        onClick = onSettingsClick,
        clickRole = Role.Button,
        modifier =
          Modifier.size(PrismDimens.HeaderIconButtonSize)
            .defaultMinSize(
              minWidth = PrismDimens.TouchTargetMin,
              minHeight = PrismDimens.TouchTargetMin,
            )
            .testTag("library_sliders_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Tune,
          contentDescription = stringResource(R.string.library_sliders_cd),
          tint = colorScheme.onSurface,
          modifier = Modifier.align(Alignment.Center).size(PrismDimens.HeaderIconSize),
        )
      }
      if (hasActiveFilters) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(PrismDimens.HeaderFilterDotPadding)
              .size(PrismDimens.HeaderFilterDotSize)
              .clip(CircleShape)
              .background(colorScheme.primary)
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
  val tokens = LocalPrismVisualTokens.current
  val placeholder =
    if (totalIndexedCount > 0) {
      stringResource(R.string.library_search_pill_count, formatFileCount(totalIndexedCount))
    } else {
      stringResource(R.string.library_search_pill_empty)
    }
  val boltGradient = Brush.linearGradient(colors = tokens.brandGradient)

  GlassSurface(
    cornerRadius = PrismDimens.SearchPillCornerRadius,
    elevation = GlassElevation.FLOATING,
    useBackdropBlur = false,
    onClick = onClick,
    clickRole = Role.Button,
    modifier =
      modifier
        .fillMaxWidth()
        .height(PrismDimens.SearchPillHeight)
        .testTag("library_search_pill"),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = PrismDimens.SpaceMedium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = colorScheme.onSurfaceVariant,
        modifier = Modifier.size(PrismDimens.SearchIconSize),
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
          Modifier.size(PrismDimens.SearchBoltSize)
            .shadow(
              elevation = PrismDimens.ShadowSmall,
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
          contentDescription = null,
          tint = PrismColors.OnAccent,
          modifier = Modifier.size(PrismDimens.SearchIconSize),
        )
      }
    }
  }
}

@Composable
private fun SectionSwitcherBar(
  section: FilesSection,
  recycleBinCount: Int,
  animateFromIndex: Int? = null,
  onSelectSection: (FilesSection) -> Unit,
  modifier: Modifier = Modifier,
) {
  val binLabel =
    if (recycleBinCount > 0) stringResource(R.string.switcher_bin_count, recycleBinCount)
    else stringResource(R.string.switcher_bin)

  GlassSegmentedControl(
    options =
      listOf(
        stringResource(R.string.section_library),
        stringResource(R.string.section_storage),
        binLabel,
      ),
    selectedIndex =
      when (section) {
        FilesSection.LIBRARY -> 0
        FilesSection.STORAGE -> 1
        FilesSection.RECYCLE_BIN -> 2
      },
    animateFromIndex = animateFromIndex,
    onSelect = { idx ->
      onSelectSection(
        when (idx) {
          1 -> FilesSection.STORAGE
          2 -> FilesSection.RECYCLE_BIN
          else -> FilesSection.LIBRARY
        }
      )
    },
    modifier = modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
  )
}

private data class PinnedLibrarySectionOverlay(
  val bucket: DateBucket,
  val pushUpOffsetPx: Float,
)

private data class LibraryLazySectionRange(
  val bucket: DateBucket,
  val headerIndex: Int?,
  val startLazyIndex: Int,
  val endLazyIndex: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPagingSection(
  uiState: FilesUiState,
  listState: LazyListState,
  gridState: LazyGridState,
  sectionTitle: String,
  animateSwitcherFromIndex: Int,
  topBarHeightDp: Dp,
  topContentPadding: Dp,
  headerScrollProgress: androidx.compose.runtime.State<Float>,
  hasActiveSecondaryFilters: Boolean,
  isDebugBuild: Boolean,
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
  onTagSelected: (Long?) -> Unit,
  onOpenDuplicateFinder: () -> Unit,
  onOpenStorageAnalyzer: () -> Unit,
  onOpenCollectionDetail: (Long) -> Unit,
  onOpenManageTagsCollections: () -> Unit,
  onManageDocumentTagsCollections: (IndexedDocumentEntity) -> Unit,
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
  val density = LocalDensity.current
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val showAccessBanner = storageAccessState.isDegradedMode && !preferences.fullAccessBannerDismissed
  val hasAnyFilter = hasActiveSecondaryFilters || uiState.query.category != null
  val isInitialLoading =
    pagedItems.loadState.refresh is LoadState.Loading && pagedItems.itemCount == 0
  val isAppending = pagedItems.loadState.append is LoadState.Loading
  val dateBoundaries = remember(pagedItems.itemCount) { DateBucketBoundaries.current() }
  val sections =
    buildLibrarySections(
      pagedItems = pagedItems,
      sortField = uiState.query.sortField,
      boundaries = dateBoundaries,
    )

  val motionEnabled = LocalGlassMotion.current
  AnimatedContent(
    targetState = uiState.isGridMode,
    transitionSpec = {
      if (motionEnabled) {
        (fadeIn(tween(180)) + scaleIn(initialScale = 0.97f, animationSpec = spring(0.84f, 440f)))
          .togetherWith(fadeOut(tween(120)))
      } else {
        EnterTransition.None togetherWith ExitTransition.None
      }
    },
    modifier = Modifier.fillMaxSize(),
    label = "library_layout_mode",
  ) { isGrid ->
  if (isGrid) {
    ObserveScrollCollapse(gridState = gridState, onCollapsedChanged = onTabBarCollapsedChange)
    LazyVerticalGrid(
      state = gridState,
      columns = GridCells.Adaptive(minSize = PrismDimens.GridCardMinSize),
      contentPadding =
        PaddingValues(
          top = topContentPadding,
          bottom = navBarBottom + PrismDimens.ListBottomContentPadding,
          start = PrismDimens.ScreenHorizontalPadding,
          end = PrismDimens.ScreenHorizontalPadding,
        ),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      modifier =
        Modifier.fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("library_grid"),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }, key = "library_hero_stack") {
        LibraryHeroStack(
          uiState = uiState,
          sectionTitle = sectionTitle,
          animateSwitcherFromIndex = animateSwitcherFromIndex,
          headerScrollProgress = headerScrollProgress,
          isInitialLoading = isInitialLoading,
          hasActiveSecondaryFilters = hasActiveSecondaryFilters,
          showAccessBanner = showAccessBanner,
          thumbnailPipeline = thumbnailPipeline,
          onOpenSearch = onOpenSearch,
          onSelectSection = onSelectSection,
          onRequestAllFilesAccess = onRequestAllFilesAccess,
          onAddSafFolder = onAddSafFolder,
          onDismissAccessBanner = onDismissAccessBanner,
          onCategorySelected = onCategorySelected,
          onTagSelected = onTagSelected,
          onOpenDuplicateFinder = onOpenDuplicateFinder,
          onOpenStorageAnalyzer = onOpenStorageAnalyzer,
          onOpenCollectionDetail = onOpenCollectionDetail,
          onOpenManageTagsCollections = onOpenManageTagsCollections,
          onManageDocumentTagsCollections = onManageDocumentTagsCollections,
          onSeeAllRecents = onSeeAllRecents,
          onClearFilters = onClearFilters,
          onOpenDocument = onOpenDocument,
          onToggleSelection = onToggleSelection,
          onToggleFavorite = onToggleFavorite,
          onRenameDocument = onRenameDocument,
          onCopyDocument = onCopyDocument,
          onMoveDocument = onMoveDocument,
          onShareDocument = onShareDocument,
          onTrashDocument = onTrashDocument,
        )
      }

      if (isInitialLoading) {
        items(6, key = { "grid_skeleton_$it" }) { idx ->
          LibraryGridSkeletonCard(
            modifier = if (idx == 0) Modifier.testTag("library_grid_skeleton") else Modifier
          )
        }
      } else {
        sections.forEachIndexed { sectionIndex, section ->
          if (section.bucket != DateBucket.ALL || uiState.query.sortField == FileSortField.DATE_MODIFIED) {
            item(
              span = { GridItemSpan(maxLineSpan) },
              key = "grid_header_${sectionIndex}_${section.bucket.name}",
              contentType = "date_section_header",
            ) {
              StickyDateSectionHeader(bucket = section.bucket)
            }
          }

          items(
            count = section.count,
            key = { localIdx ->
              val globalIdx = section.startIndex + localIdx
              pagedItems.peek(globalIdx)?.id ?: "lib_grid_placeholder_$globalIdx"
            },
            contentType = { "doc_grid_card" },
          ) { localIdx ->
            val globalIdx = section.startIndex + localIdx
            val doc = pagedItems[globalIdx] ?: return@items
            DocumentGridCard(
              document = doc,
              thumbnailPipeline = thumbnailPipeline,
              isSelected = doc.id in uiState.selectedIds,
              isSelectionMode = uiState.isSelectionMode,
              onClick = {
                if (uiState.isSelectionMode) onToggleSelection(doc.id) else onOpenDocument(doc)
              },
              onLongClick = { onToggleSelection(doc.id) },
              onSelectItem = { onToggleSelection(doc.id) },
              onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
              onRename = { onRenameDocument(doc) },
              onCopy = { onCopyDocument(doc) },
              onMove = { onMoveDocument(doc) },
              onShare = { onShareDocument(doc) },
              onTrash = { onTrashDocument(doc) },
              onManageTagsCollections = { onManageDocumentTagsCollections(doc) },
              modifier = if (globalIdx < 8) Modifier.glassEntrance(index = globalIdx) else Modifier,
            )
          }
        }

        if (isAppending) {
          items(2, key = { "grid_append_skeleton_$it" }) { LibraryGridSkeletonCard() }
        }
      }

      if (pagedItems.itemCount == 0 && pagedItems.loadState.refresh is LoadState.NotLoading) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "library_empty_card") {
          LibraryEmptyStateCard(
            hasActiveFilters = hasAnyFilter,
            hasFullAccess = storageAccessState.canBrowseFullDevice,
            isDebugBuild = isDebugBuild,
            onClearFilters = onClearFilters,
            onOpenOptions = onOpenOptionsSheet,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onAddSafFolder = onAddSafFolder,
            onTriggerSync = onTriggerSync,
          )
        }
      }
    }
  } else {
    ObserveScrollCollapse(listState = listState, onCollapsedChanged = onTabBarCollapsedChange)

    val showDateHeaders = uiState.query.sortField == FileSortField.DATE_MODIFIED
    val sectionRanges =
      remember(sections, showDateHeaders) {
        var cursor = 1 // 0 is library_hero_stack
        sections.map { section ->
          val hasHeader = section.bucket != DateBucket.ALL || showDateHeaders
          val startIdx = cursor
          val headerIdx = if (hasHeader) cursor++ else null
          cursor += section.count
          cursor++ // trailing section_gap spacer
          LibraryLazySectionRange(
            bucket = section.bucket,
            headerIndex = headerIdx,
            startLazyIndex = startIdx,
            endLazyIndex = cursor - 1,
          )
        }
      }

    var pinnedHeaderHeightPx by remember { mutableIntStateOf(0) }
    val dockAnchorPx = with(density) { topBarHeightDp.toPx() }
    val pinnedOverlay by
      remember(sectionRanges, dockAnchorPx, isInitialLoading) {
        derivedStateOf {
          if (isInitialLoading || sectionRanges.isEmpty()) return@derivedStateOf null
          val visibleItems = listState.layoutInfo.visibleItemsInfo
          if (visibleItems.isEmpty()) return@derivedStateOf null

          val anchorInt = dockAnchorPx.toInt()
          val firstRange = sectionRanges.first()
          val firstAnchorIdx = firstRange.headerIndex ?: firstRange.startLazyIndex
          val firstVisible = visibleItems.first()
          if (firstVisible.index < firstAnchorIdx) {
            val upcomingHeader = visibleItems.firstOrNull { it.index == firstAnchorIdx }
            if (upcomingHeader == null || upcomingHeader.offset > anchorInt) {
              return@derivedStateOf null
            }
          } else if (firstVisible.index == firstAnchorIdx && firstVisible.offset > anchorInt) {
            return@derivedStateOf null
          }

          val anchorItem =
            visibleItems.lastOrNull { it.offset <= anchorInt } ?: visibleItems.first()
          val activeRangeIdx =
            sectionRanges.indexOfFirst {
              anchorItem.index in it.startLazyIndex..it.endLazyIndex
            }
          if (activeRangeIdx < 0) return@derivedStateOf null
          val activeRange = sectionRanges[activeRangeIdx]
          if (activeRange.headerIndex == null) return@derivedStateOf null

          val headerHeight =
            if (pinnedHeaderHeightPx > 0) pinnedHeaderHeightPx
            else with(density) { 54.dp.roundToPx() }
          val nextHeaderIdx = sectionRanges.getOrNull(activeRangeIdx + 1)?.headerIndex
          val nextHeaderItem =
            if (nextHeaderIdx != null) visibleItems.firstOrNull { it.index == nextHeaderIdx }
            else null
          val pushUpPx =
            if (nextHeaderItem != null && nextHeaderItem.offset < anchorInt + headerHeight) {
              (nextHeaderItem.offset - (anchorInt + headerHeight)).toFloat().coerceAtMost(0f)
            } else {
              0f
            }

          PinnedLibrarySectionOverlay(bucket = activeRange.bucket, pushUpOffsetPx = pushUpPx)
        }
      }

    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        state = listState,
        contentPadding =
          PaddingValues(
            top = topContentPadding,
            bottom = navBarBottom + PrismDimens.ListBottomContentPadding,
            start = PrismDimens.ScreenHorizontalPadding,
            end = PrismDimens.ScreenHorizontalPadding,
          ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
          Modifier.fillMaxSize()
            .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
            .testTag("library_list"),
      ) {
        item(key = "library_hero_stack", contentType = "library_hero") {
          LibraryHeroStack(
            uiState = uiState,
            sectionTitle = sectionTitle,
            animateSwitcherFromIndex = animateSwitcherFromIndex,
            headerScrollProgress = headerScrollProgress,
            isInitialLoading = isInitialLoading,
            hasActiveSecondaryFilters = hasActiveSecondaryFilters,
            showAccessBanner = showAccessBanner,
            thumbnailPipeline = thumbnailPipeline,
            onOpenSearch = onOpenSearch,
            onSelectSection = onSelectSection,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onAddSafFolder = onAddSafFolder,
            onDismissAccessBanner = onDismissAccessBanner,
            onCategorySelected = onCategorySelected,
            onTagSelected = onTagSelected,
            onOpenDuplicateFinder = onOpenDuplicateFinder,
            onOpenStorageAnalyzer = onOpenStorageAnalyzer,
            onOpenCollectionDetail = onOpenCollectionDetail,
            onOpenManageTagsCollections = onOpenManageTagsCollections,
            onManageDocumentTagsCollections = onManageDocumentTagsCollections,
            onSeeAllRecents = onSeeAllRecents,
            onClearFilters = onClearFilters,
            onOpenDocument = onOpenDocument,
            onToggleSelection = onToggleSelection,
            onToggleFavorite = onToggleFavorite,
            onRenameDocument = onRenameDocument,
            onCopyDocument = onCopyDocument,
            onMoveDocument = onMoveDocument,
            onShareDocument = onShareDocument,
            onTrashDocument = onTrashDocument,
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
          Spacer(Modifier.height(PrismDimens.SpaceMedium))
        }

        if (isInitialLoading) {
          items(6, key = { "list_skeleton_$it" }, contentType = { "skeleton_row" }) { idx ->
            LibraryListSkeletonRow(
              isFirst = idx == 0,
              isLast = idx == 5,
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }
        } else {
          sections.forEachIndexed { sectionIndex, section ->
            if (section.bucket != DateBucket.ALL || showDateHeaders) {
              item(
                key = "sticky_${sectionIndex}_${section.bucket.name}",
                contentType = "sticky_date_header",
              ) {
                StickyDateSectionHeader(
                  bucket = section.bucket,
                  modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
                )
              }
            }

            items(
              count = section.count,
              key = { localIdx ->
                val globalIdx = section.startIndex + localIdx
                pagedItems.peek(globalIdx)?.id ?: "lib_list_placeholder_$globalIdx"
              },
              contentType = { "doc_list_row" },
            ) { localIdx ->
              val globalIdx = section.startIndex + localIdx
              val doc = pagedItems[globalIdx] ?: return@items
              LibraryCardDocumentRow(
                document = doc,
                isFirst = localIdx == 0,
                isLast = localIdx == section.count - 1,
                isSelected = doc.id in uiState.selectedIds,
                isSelectionMode = uiState.isSelectionMode,
                entranceIndex = if (globalIdx < 8) globalIdx else null,
                onClick = {
                  if (uiState.isSelectionMode) onToggleSelection(doc.id) else onOpenDocument(doc)
                },
                onLongClick = { onToggleSelection(doc.id) },
                onSelectItem = { onToggleSelection(doc.id) },
                onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
                onRename = { onRenameDocument(doc) },
                onCopy = { onCopyDocument(doc) },
                onMove = { onMoveDocument(doc) },
                onShare = { onShareDocument(doc) },
                onTrash = { onTrashDocument(doc) },
                onManageTagsCollections = { onManageDocumentTagsCollections(doc) },
                modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
              )
            }

            item(key = "section_gap_$sectionIndex", contentType = "spacer") {
              Spacer(Modifier.height(PrismDimens.SpaceStandard))
            }
          }

          if (isAppending) {
            items(2, key = { "list_append_skeleton_$it" }, contentType = { "skeleton_row" }) { idx ->
              LibraryListSkeletonRow(
                isFirst = idx == 0,
                isLast = idx == 1,
                modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
              )
            }
          }
        }

        if (pagedItems.itemCount == 0 && pagedItems.loadState.refresh is LoadState.NotLoading) {
          item(key = "library_empty_card", contentType = "empty_card") {
            LibraryEmptyStateCard(
              hasActiveFilters = hasAnyFilter,
              hasFullAccess = storageAccessState.canBrowseFullDevice,
              isDebugBuild = isDebugBuild,
              onClearFilters = onClearFilters,
              onOpenOptions = onOpenOptionsSheet,
              onRequestAllFilesAccess = onRequestAllFilesAccess,
              onAddSafFolder = onAddSafFolder,
              onTriggerSync = onTriggerSync,
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }
        }
      }

      pinnedOverlay?.let { pinned ->
        Box(
          modifier =
            Modifier.align(Alignment.TopCenter)
              .padding(top = topBarHeightDp)
              .fillMaxWidth()
              .clipToBounds(),
          contentAlignment = Alignment.TopCenter,
        ) {
          StickyDateSectionHeader(
            bucket = pinned.bucket,
            useBackdropBlur = true,
            headerTestTag = "pinned_date_section_header_${pinned.bucket.name}",
            modifier =
              Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = PrismDimens.ScreenHorizontalPadding)
                .onSizeChanged { pinnedHeaderHeightPx = it.height }
                .graphicsLayer { translationY = pinned.pushUpOffsetPx },
          )
        }
      }
    }
  }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryHeroStack(
  uiState: FilesUiState,
  sectionTitle: String,
  animateSwitcherFromIndex: Int,
  headerScrollProgress: androidx.compose.runtime.State<Float>,
  isInitialLoading: Boolean,
  hasActiveSecondaryFilters: Boolean,
  showAccessBanner: Boolean,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onOpenSearch: () -> Unit,
  onSelectSection: (FilesSection) -> Unit,
  onRequestAllFilesAccess: () -> Unit,
  onAddSafFolder: () -> Unit,
  onDismissAccessBanner: () -> Unit,
  onCategorySelected: (DocumentCategory?) -> Unit,
  onTagSelected: (Long?) -> Unit,
  onOpenDuplicateFinder: () -> Unit,
  onOpenStorageAnalyzer: () -> Unit,
  onOpenCollectionDetail: (Long) -> Unit,
  onOpenManageTagsCollections: () -> Unit,
  onManageDocumentTagsCollections: (IndexedDocumentEntity) -> Unit,
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
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection)) {
    // Large "Library" title collapsing smoothly into the centered small glass top bar
    Text(
      text = sectionTitle,
      style = MaterialTheme.typography.displaySmall,
      color = colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier =
        Modifier.graphicsLayer {
            val p = headerScrollProgress.value
            alpha = (1f - p * 1.45f).coerceIn(0f, 1f)
            translationY = -p * 8.dp.toPx()
          }
          .testTag("library_expanded_title"),
    )

    LibrarySearchPill(
      totalIndexedCount = uiState.totalIndexedCount,
      onClick = onOpenSearch,
    )

    SectionSwitcherBar(
      section = uiState.section,
      recycleBinCount = uiState.recycleBinCount,
      animateFromIndex = animateSwitcherFromIndex,
      onSelectSection = onSelectSection,
    )

    if (showAccessBanner) {
      GlassSurface(
        cornerRadius = PrismDimens.BannerCornerRadius,
        elevation = GlassElevation.SUBTLE,
        accentTint = colorScheme.primary,
        modifier = Modifier.fillMaxWidth().testTag("grant_full_access_banner"),
      ) {
        Column(
          modifier = Modifier.padding(PrismDimens.SpaceSection),
          verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
          ) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              tint = colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.access_banner_title),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.access_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
            TextButton(
              onClick = onDismissAccessBanner,
              modifier = Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin),
            ) {
              Text(stringResource(R.string.access_banner_hide))
            }
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
            verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
          ) {
            Button(
              onClick = onRequestAllFilesAccess,
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .testTag("banner_grant_full_access_button"),
            ) {
              Text(stringResource(R.string.access_banner_grant))
            }
            OutlinedButton(
              onClick = onAddSafFolder,
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .testTag("banner_add_saf_folder_button"),
            ) {
              Text(stringResource(R.string.access_banner_add_folder))
            }
          }
        }
      }
    }

    if (uiState.recentDocuments.isNotEmpty()) {
      RecentDocumentsCarousel(
        recentDocuments = uiState.recentDocuments,
        selectedIds = uiState.selectedIds,
        isSelectionMode = uiState.isSelectionMode,
        thumbnailPipeline = thumbnailPipeline,
        onSeeAllClick = onSeeAllRecents,
        onOpenDocument = onOpenDocument,
        onToggleSelection = onToggleSelection,
        onToggleFavorite = onToggleFavorite,
        onRenameDocument = onRenameDocument,
        onCopyDocument = onCopyDocument,
        onMoveDocument = onMoveDocument,
        onShareDocument = onShareDocument,
        onTrashDocument = onTrashDocument,
        onManageDocumentTagsCollections = onManageDocumentTagsCollections,
      )
    } else if (isInitialLoading) {
      LibraryRecentSkeletonRow()
    }

    PowerToolsQuickRow(
      onOpenDuplicateFinder = onOpenDuplicateFinder,
      onOpenStorageAnalyzer = onOpenStorageAnalyzer,
    )

    CollectionsGlassSection(
      collections = uiState.collectionsWithStats,
      onOpenCollection = onOpenCollectionDetail,
      onManageCollections = onOpenManageTagsCollections,
    )

    CategoryGlassChipRow(
      selectedCategory = uiState.query.category,
      onCategorySelected = onCategorySelected,
    )

    TagFilterChipRow(
      tags = uiState.tagsWithStats,
      selectedTagId = uiState.query.tagId,
      onTagSelected = onTagSelected,
      onManageTags = onOpenManageTagsCollections,
    )

    AnimatedVisibility(
      visible = hasActiveSecondaryFilters,
      enter =
        expandVertically(animationSpec = spring(dampingRatio = 0.84f, stiffness = 440f)) +
          fadeIn(tween(150)),
      exit =
        shrinkVertically(animationSpec = spring(dampingRatio = 0.88f, stiffness = 480f)) +
          fadeOut(tween(120)),
    ) {
      ActiveFilterSummaryBar(
        uiState = uiState,
        onClearFilters = onClearFilters,
      )
    }
  }
}

@Composable
private fun PowerToolsQuickRow(
  onOpenDuplicateFinder: () -> Unit,
  onOpenStorageAnalyzer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Row(
    modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
  ) {
    GlassSurface(
      cornerRadius = PrismDimens.FilterBarCornerRadius,
      elevation = GlassElevation.SUBTLE,
      useBackdropBlur = false,
      onClick = onOpenDuplicateFinder,
      clickRole = Role.Button,
      modifier =
        Modifier.weight(1f)
          .fillMaxHeight()
          .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
          .testTag("library_duplicate_finder_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
      ) {
        Box(
          modifier =
            Modifier.size(32.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(colorScheme.primary.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.FileCopy,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = stringResource(R.string.tool_duplicate_finder_title),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
    }

    GlassSurface(
      cornerRadius = PrismDimens.FilterBarCornerRadius,
      elevation = GlassElevation.SUBTLE,
      useBackdropBlur = false,
      onClick = onOpenStorageAnalyzer,
      clickRole = Role.Button,
      modifier =
        Modifier.weight(1f)
          .fillMaxHeight()
          .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
          .testTag("library_storage_analyzer_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
      ) {
        Box(
          modifier =
            Modifier.size(32.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(colorScheme.secondary.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.DonutLarge,
            contentDescription = null,
            tint = colorScheme.secondary,
            modifier = Modifier.size(18.dp),
          )
        }
        Text(
          text = stringResource(R.string.tool_storage_analyzer_title),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun CollectionsGlassSection(
  collections: List<CollectionWithCountRow>,
  onOpenCollection: (Long) -> Unit,
  onManageCollections: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.library_collections_title),
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = PrismDimens.FontSizeSectionTitle,
            fontWeight = FontWeight.ExtraBold,
          ),
        color = colorScheme.onSurface,
      )
      TextButton(
        onClick = onManageCollections,
        modifier =
          Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
            .testTag("library_manage_collections_button"),
      ) {
        Text(
          text = stringResource(R.string.library_collections_manage),
          color = tokens.linkAccent,
        )
      }
    }

    if (collections.isNotEmpty()) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
        contentPadding = PaddingValues(end = PrismDimens.SpaceStandard),
        modifier = Modifier.fillMaxWidth().testTag("library_collections_row"),
      ) {
        items(collections, key = { "lib_col_${it.id}" }) { col ->
          val accent = parseHexColorOrDefault(col.accentHex, colorScheme.primary)
          GlassSurface(
            cornerRadius = PrismDimens.FilterBarCornerRadius,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            accentTint = accent.copy(alpha = 0.14f),
            onClick = { onOpenCollection(col.id) },
            clickRole = Role.Button,
            modifier =
              Modifier.defaultMinSize(minWidth = 138.dp, minHeight = PrismDimens.TouchTargetMin)
                .testTag("library_collection_card_${col.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
            ) {
              Box(
                modifier =
                  Modifier.size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.CollectionsBookmark,
                  contentDescription = null,
                  tint = accent,
                  modifier = Modifier.size(16.dp),
                )
              }
              Column {
                Text(
                  text = col.name,
                  style = MaterialTheme.typography.titleSmall,
                  color = colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    pluralStringResource(
                      R.plurals.collection_doc_count,
                      col.documentCount,
                      col.documentCount,
                    ),
                  style = MaterialTheme.typography.labelSmall,
                  color = colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TagFilterChipRow(
  tags: List<TagWithCountRow>,
  selectedTagId: Long?,
  onTagSelected: (Long?) -> Unit,
  onManageTags: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val stateSelected = stringResource(R.string.state_selected)
  val stateNotSelected = stringResource(R.string.state_not_selected)

  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    contentPadding = PaddingValues(end = PrismDimens.SpaceStandard),
    modifier = modifier.fillMaxWidth().testTag("library_tag_filter_row"),
  ) {
    items(tags, key = { "lib_tag_${it.id}" }) { tag ->
      val isSelected = selectedTagId == tag.id
      val tagAccent = parseHexColorOrDefault(tag.colorHex, colorScheme.primary)
      val cd = stringResource(R.string.library_tags_filter_cd, tag.name)
      val haptics = rememberPrismHaptics()
      val labelColor by
        animateColorAsState(
          targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurface,
          label = "tag_chip_color_${tag.id}",
        )

      GlassSurface(
        cornerRadius = 18.dp,
        shape = CircleShape,
        elevation = GlassElevation.SUBTLE,
        useBackdropBlur = false,
        accentTint = if (isSelected) tagAccent else null,
        onClick = {
          haptics.segmentTick()
          onTagSelected(tag.id)
        },
        hapticOnClick = false,
        clickRole = Role.Tab,
        modifier =
          Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .semantics {
              selected = isSelected
              stateDescription = if (isSelected) stateSelected else stateNotSelected
              contentDescription = cd
            }
            .testTag("library_tag_chip_${tag.id}"),
      ) {
        Row(
          modifier =
            Modifier.align(Alignment.Center)
              .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(tagAccent))
          Text(
            text = stringResource(R.string.filter_part_tag, tag.name),
            style =
              MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
              ),
            color = labelColor,
            maxLines = 1,
          )
        }
      }
    }

    item(key = "manage_tags_chip") {
      GlassSurface(
        cornerRadius = 18.dp,
        shape = CircleShape,
        elevation = GlassElevation.SUBTLE,
        useBackdropBlur = false,
        onClick = onManageTags,
        clickRole = Role.Button,
        modifier =
          Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .testTag("library_manage_tags_chip"),
      ) {
        Box(
          modifier =
            Modifier.align(Alignment.Center)
              .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.library_tags_manage_chip),
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
    }
  }
}

private data class CategoryChipSpec(
  val category: DocumentCategory?,
  @StringRes val labelRes: Int,
)

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
    listOf(
      CategoryChipSpec(null, R.string.category_all_files),
      CategoryChipSpec(DocumentCategory.PDF, R.string.category_pdf),
      CategoryChipSpec(DocumentCategory.DOCUMENTS, R.string.category_word),
      CategoryChipSpec(DocumentCategory.SPREADSHEETS, R.string.category_excel),
      CategoryChipSpec(DocumentCategory.SLIDES, R.string.category_slides),
      CategoryChipSpec(DocumentCategory.IMAGES, R.string.category_images),
      CategoryChipSpec(DocumentCategory.VIDEOS, R.string.category_video),
      CategoryChipSpec(DocumentCategory.AUDIO, R.string.category_audio),
      CategoryChipSpec(DocumentCategory.ARCHIVES, R.string.category_zip),
      CategoryChipSpec(DocumentCategory.EBOOKS, R.string.category_books),
      CategoryChipSpec(DocumentCategory.CODE_TEXT, R.string.category_code),
    )
  }

  LazyRow(
    state = rowState,
    horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    contentPadding =
      PaddingValues(
        top = PrismDimens.SpaceSmallMedium,
        bottom = PrismDimens.SpaceMediumLarge,
        end = PrismDimens.ChipFadeWidth,
      ),
    modifier = modifier.fillMaxWidth().horizontalRightEdgeFade(fadeFraction = fadeAlpha),
  ) {
    items(categories, key = { it.category?.name ?: "ALL" }) { spec ->
      val isSelected = selectedCategory == spec.category
      CategoryGlassPill(
        label = stringResource(spec.labelRes),
        selected = isSelected,
        testTag =
          if (spec.category == null) "category_chip_ALL"
          else "category_chip_${spec.category.name}",
        onClick = { onCategorySelected(spec.category) },
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
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val gradientBrush = remember(tokens.brandGradient) { Brush.linearGradient(colors = tokens.brandGradient) }
  val chipCd = stringResource(R.string.category_chip_cd, label)
  val stateDesc =
    stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)

  val selectedProgress =
    animateFloatAsState(
      targetValue = if (selected) 1f else 0f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.78f, stiffness = 460f) else snap(),
      label = "category_pill_progress_$testTag",
    )
  val labelColor by
    animateColorAsState(
      targetValue = if (selected) PrismColors.OnAccent else colorScheme.onSurface,
      label = "category_pill_label_$testTag",
    )

  GlassSurface(
    cornerRadius = PrismDimens.CategoryChipCornerRadius,
    shape = CircleShape,
    elevation = GlassElevation.SUBTLE,
    enableRefraction = false,
    onClick = {
      haptics.segmentTick()
      onClick()
    },
    hapticOnClick = false,
    clickRole = Role.Tab,
    modifier =
      Modifier.height(PrismDimens.CategoryChipHeight)
        .defaultMinSize(
          minWidth = PrismDimens.CategoryChipMinWidth,
          minHeight = PrismDimens.TouchTargetMin,
        )
        .graphicsLayer {
          val s = 1f + 0.03f * selectedProgress.value
          scaleX = s
          scaleY = s
        }
        .semantics {
          role = Role.Tab
          this.selected = selected
          stateDescription = stateDesc
          contentDescription = chipCd
        }
        .testTag(testTag),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .drawBehind {
            val alpha = selectedProgress.value.coerceIn(0f, 1f)
            if (alpha > 0.01f) {
              drawRect(brush = gradientBrush, alpha = alpha)
            }
          }
          .padding(horizontal = PrismDimens.CategoryChipHorizontalPadding),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        fontFamily = FontFamily.SansSerif,
        fontSize = PrismDimens.FontSizeCategoryChip,
        fontWeight = FontWeight.SemiBold,
        color = labelColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ActiveFilterSummaryBar(
  uiState: FilesUiState,
  onClearFilters: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val namePrefixText =
    uiState.query.namePrefix?.takeIf { it.isNotBlank() }?.let {
      stringResource(R.string.filter_part_name, it)
    }
  val favoritesText =
    if (uiState.query.favoritesOnly) stringResource(R.string.filter_part_favorites) else null
  val recentsText =
    if (uiState.query.recentsOnly) stringResource(R.string.filter_part_recents) else null
  val collectionText =
    uiState.query.collectionId?.let { id ->
      uiState.collections.firstOrNull { it.id == id }?.name
    }
  val tagText =
    uiState.query.tagId?.let { id ->
      uiState.tags.firstOrNull { it.id == id }?.let {
        stringResource(R.string.filter_part_tag, it.name)
      }
    }
  val otherCatText =
    if (uiState.query.otherCategoriesOnly) stringResource(R.string.analyzer_category_other) else null

  val summaryParts =
    listOfNotNull(namePrefixText, favoritesText, recentsText, collectionText, tagText, otherCatText)
  if (summaryParts.isEmpty()) return

  GlassSurface(
    cornerRadius = PrismDimens.FilterBarCornerRadius,
    elevation = GlassElevation.SUBTLE,
    enableRefraction = false,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(
            start = PrismDimens.SpaceRow,
            end = PrismDimens.SpaceSmallMedium,
            top = PrismDimens.SpaceSmall,
            bottom = PrismDimens.SpaceSmall,
          ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = stringResource(R.string.filter_summary_prefix, summaryParts.joinToString(" • ")),
        style = MaterialTheme.typography.labelLarge,
        color = colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      val haptics = rememberPrismHaptics()
      TextButton(
        onClick = {
          haptics.tap()
          onClearFilters()
        },
        modifier =
          Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .testTag("active_filter_reset_button"),
      ) {
        Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(PrismDimens.SpaceSmall))
        Text(stringResource(R.string.filter_clear))
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
  onToggleFavorite: (Long, Boolean) -> Unit,
  onRenameDocument: (IndexedDocumentEntity) -> Unit,
  onCopyDocument: (IndexedDocumentEntity) -> Unit,
  onMoveDocument: (IndexedDocumentEntity) -> Unit,
  onShareDocument: (IndexedDocumentEntity) -> Unit,
  onTrashDocument: (IndexedDocumentEntity) -> Unit,
  onManageDocumentTagsCollections: (IndexedDocumentEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tokens = LocalPrismVisualTokens.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val rowState = rememberLazyListState()
  val canScrollForward by remember { derivedStateOf { rowState.canScrollForward } }
  val fadeAlpha by animateFloatAsState(
    targetValue = if (canScrollForward) 1f else 0f,
    label = "recent_row_fade",
  )

  Column(
    modifier = modifier.fillMaxWidth().testTag("library_recent_section"),
    verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.library_recent_title),
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontSize = PrismDimens.FontSizeSectionTitle,
            fontWeight = FontWeight.ExtraBold,
          ),
        color = colorScheme.onSurface,
      )
      Box(
        modifier =
          Modifier.defaultMinSize(
              minWidth = PrismDimens.TouchTargetMin,
              minHeight = PrismDimens.TouchTargetMin,
            )
            .clip(RoundedCornerShape(PrismDimens.SpaceMediumLarge))
            .glassClickable(role = Role.Button, onClick = onSeeAllClick)
            .padding(horizontal = PrismDimens.SpaceMedium)
            .testTag("recent_see_all_button"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.library_recent_see_all),
          fontFamily = FontFamily.SansSerif,
          fontSize = PrismDimens.FontSizeTextLink,
          fontWeight = FontWeight.SemiBold,
          color = tokens.linkAccent,
        )
      }
    }

    LazyRow(
      state = rowState,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      contentPadding =
        PaddingValues(
          top = PrismDimens.SpaceSmallMedium,
          bottom = PrismDimens.SpaceStandard,
          end = PrismDimens.RecentFadeWidth,
        ),
      modifier = Modifier.fillMaxWidth().horizontalRightEdgeFade(fadeFraction = fadeAlpha),
    ) {
      itemsIndexed(recentDocuments, key = { _, doc -> "recent_${doc.id}" }) { idx, doc ->
        val isSelected = doc.id in selectedIds
        val request = doc.toThumbnailRequest(targetSizePx = 380)
        val sizeText = rememberFormattedByteSize(doc.sizeBytes)
        val dayText = rememberFormattedRelativeDay(doc.lastOpenedEpochMs ?: doc.lastModifiedEpochMs)
        val caption = stringResource(R.string.doc_row_caption_format, sizeText, dayText)
        val a11yDescription = stringResource(R.string.recent_card_a11y_cd, doc.displayName, caption)
        val selectedStateDesc =
          stringResource(if (isSelected) R.string.state_selected else R.string.state_not_selected)
        val interactionSource = remember { MutableInteractionSource() }
        var menuExpanded by remember { mutableStateOf(false) }

        Box {
          GlassSurface(
            cornerRadius = PrismDimens.RecentCardCornerRadius,
            elevation = GlassElevation.SUBTLE,
            accentTint = if (isSelected) colorScheme.primary else null,
            modifier =
              Modifier.width(PrismDimens.RecentCardWidth)
                .then(if (idx < 8) Modifier.glassEntrance(index = idx) else Modifier)
                .semantics(mergeDescendants = true) {
                  role = if (isSelectionMode) Role.Checkbox else Role.Button
                  selected = isSelected
                  stateDescription = selectedStateDesc
                  contentDescription = a11yDescription
                }
                .glassPress(interactionSource)
                .combinedClickable(
                  interactionSource = interactionSource,
                  indication = null,
                  onClick = {
                    if (isSelectionMode) {
                      onToggleSelection(doc.id)
                    } else {
                      haptics.tap()
                      onOpenDocument(doc)
                    }
                  },
                  onLongClick = {
                    if (isSelectionMode) {
                      onToggleSelection(doc.id)
                    } else {
                      haptics.longPress()
                      menuExpanded = true
                    }
                  },
                )
                .testTag("recent_doc_card_${doc.id}"),
          ) {
            Column(
              modifier = Modifier.padding(PrismDimens.SpaceMediumLarge),
              verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
            ) {
              Box {
                DocumentThumbnailView(
                  request = request,
                  pipeline = thumbnailPipeline,
                  cornerRadius = PrismDimens.RecentThumbnailCornerRadius,
                  compact = false,
                  showCornerBadge = true,
                  modifier = Modifier.fillMaxWidth().height(PrismDimens.RecentThumbnailHeight),
                )
                androidx.compose.animation.AnimatedVisibility(
                  visible = isSelected,
                  enter =
                    scaleIn(
                      initialScale = 0.45f,
                      animationSpec = spring(dampingRatio = 0.56f, stiffness = 520f),
                    ) + fadeIn(tween(120)),
                  exit =
                    scaleOut(targetScale = 0.45f, animationSpec = tween(110)) + fadeOut(tween(100)),
                  modifier = Modifier.align(Alignment.TopStart).padding(PrismDimens.SpaceSmallMedium),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier =
                      Modifier.size(22.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface),
                  )
                }
              }
              Column(
                modifier = Modifier.padding(horizontal = PrismDimens.SpaceXSmall),
                verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceXSmall),
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

          DocumentItemContextMenu(
            expanded = menuExpanded,
            isFavorite = doc.isFavorite,
            showSelectOption = !isSelectionMode,
            onSelectItem = { onToggleSelection(doc.id) },
            onDismiss = { menuExpanded = false },
            onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
            onRename = { onRenameDocument(doc) },
            onCopy = { onCopyDocument(doc) },
            onMove = { onMoveDocument(doc) },
            onShare = { onShareDocument(doc) },
            onTrash = { onTrashDocument(doc) },
            onManageTagsCollections = { onManageDocumentTagsCollections(doc) },
          )
        }
      }
    }
  }
}

private data class SortFieldSpec(
  val field: FileSortField,
  @StringRes val labelRes: Int,
)

@Composable
fun SortControlRow(
  sortField: FileSortField,
  sortDirection: SortDirection,
  onSortFieldSelected: (FileSortField) -> Unit,
  onToggleSortDirection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val rowState = rememberLazyListState()
  val canScrollForward by remember { derivedStateOf { rowState.canScrollForward } }
  val fadeAlpha by animateFloatAsState(
    targetValue = if (canScrollForward) 1f else 0f,
    label = "sort_chip_fade",
  )
  val arrowRotation =
    animateFloatAsState(
      targetValue = if (sortDirection == SortDirection.ASC) 0f else 180f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.68f, stiffness = 440f) else snap(),
      label = "sort_arrow_rotation",
    )

  val options = remember {
    listOf(
      SortFieldSpec(FileSortField.DATE_MODIFIED, R.string.sort_date),
      SortFieldSpec(FileSortField.NAME, R.string.sort_name),
      SortFieldSpec(FileSortField.SIZE, R.string.sort_size),
      SortFieldSpec(FileSortField.TYPE, R.string.sort_type),
    )
  }
  LazyRow(
    state = rowState,
    modifier = modifier.horizontalRightEdgeFade(fadeFraction = fadeAlpha),
    contentPadding = PaddingValues(end = PrismDimens.SpaceXLarge),
    horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items(options) { spec ->
      val selected = sortField == spec.field
      val label = stringResource(spec.labelRes)
      val dirCd =
        stringResource(
          if (sortDirection == SortDirection.ASC) R.string.sort_ascending_cd
          else R.string.sort_descending_cd
        )
      val chipCd = stringResource(R.string.sort_chip_cd, label)
      val selectedState =
        stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)

      val chipBg by
        animateColorAsState(
          targetValue =
            if (selected) colorScheme.primaryContainer
            else colorScheme.surface.copy(alpha = 0.72f),
          label = "sort_bg_${spec.field.name}",
        )
      val chipFg by
        animateColorAsState(
          targetValue =
            if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
          label = "sort_fg_${spec.field.name}",
        )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
        modifier =
          Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .clip(RoundedCornerShape(PrismDimens.SpaceSection))
            .background(chipBg)
            .semantics {
              role = Role.Tab
              this.selected = selected
              stateDescription = if (selected) "$selectedState, $dirCd" else selectedState
              contentDescription = chipCd
            }
            .glassClickable(role = Role.Tab, hapticOnClick = false) {
              haptics.segmentTick()
              if (selected) onToggleSortDirection() else onSortFieldSelected(spec.field)
            }
            .padding(horizontal = 14.dp, vertical = PrismDimens.SpaceMedium)
            .testTag("sort_chip_${spec.field.name}"),
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = chipFg,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        AnimatedVisibility(
          visible = selected,
          enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.6f),
          exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.6f),
        ) {
          Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = null,
            tint = chipFg,
            modifier =
              Modifier.size(14.dp).graphicsLayer {
                rotationZ = arrowRotation.value
              },
          )
        }
      }
    }
  }
}
