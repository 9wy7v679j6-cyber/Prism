package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.FileSortField
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.model.ScannedDocumentMetadata
import com.prism.docs.core.data.model.SortDirection
import com.prism.docs.core.data.model.StorageSourceType
import com.prism.docs.core.data.model.StorageVolumeRoot
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import com.prism.docs.core.data.thumbnail.ThumbnailRequest
import com.prism.docs.core.ui.glass.ConnectedGlassCardSlice
import com.prism.docs.core.ui.glass.GlassContextMenu
import com.prism.docs.core.ui.glass.GlassContextMenuItem
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.glassEntrance
import com.prism.docs.core.ui.glass.glassPress
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import com.prism.docs.core.ui.theme.PrismMotion
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun StorageBrowserSection(
  storageState: StorageBrowserState,
  isGridMode: Boolean,
  sortField: FileSortField,
  sortDirection: SortDirection,
  selectedIds: Set<Long>,
  isSelectionMode: Boolean,
  pagedLocalFiles: LazyPagingItems<IndexedDocumentEntity>,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onOpenRoot: (StorageVolumeRoot) -> Unit,
  onOpenSubfolder: (ScannedDocumentMetadata) -> Unit,
  onNavigateToBreadcrumb: (Int) -> Unit,
  onNavigateUp: () -> Boolean,
  onCreateFolderClick: () -> Unit,
  onOpenIndexedDocument: (IndexedDocumentEntity) -> Unit,
  onOpenRawDocument: (IncomingDocumentRequest) -> Unit,
  onToggleSelection: (Long) -> Unit,
  onToggleFavorite: (Long, Boolean) -> Unit,
  onRenameDocument: (IndexedDocumentEntity) -> Unit,
  onCopyDocument: (IndexedDocumentEntity) -> Unit,
  onMoveDocument: (IndexedDocumentEntity) -> Unit,
  onShareDocument: (IndexedDocumentEntity) -> Unit,
  onTrashDocument: (IndexedDocumentEntity) -> Unit,
  onSortFieldSelected: (FileSortField) -> Unit,
  onToggleSortDirection: () -> Unit,
  onAddSafFolder: () -> Unit,
  onRequestAllFilesAccess: () -> Unit,
  hasFullAccess: Boolean,
  modifier: Modifier = Modifier,
  topContentPadding: Dp = 148.dp,
  rootsListState: LazyListState = rememberLazyListState(),
  directoryListState: LazyListState = rememberLazyListState(),
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val currentDir = storageState.currentDirectory
  val isSafOpen = currentDir?.isSaf == true

  BackHandler(enabled = storageState.breadcrumbs.isNotEmpty()) { onNavigateUp() }

  if (currentDir == null) {
    LazyColumn(
      state = rootsListState,
      contentPadding =
        PaddingValues(
          top = topContentPadding,
          bottom = navBarBottom + PrismDimens.ListBottomContentPadding,
          start = 16.dp,
          end = 16.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        modifier
          .fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("storage_roots_list"),
    ) {
      item {
        Text(
          text = stringResource(R.string.storage_volumes_header),
          style = MaterialTheme.typography.titleMedium,
          color = colorScheme.onSurface,
          modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
        )
      }

      if (storageState.roots.isEmpty()) {
        item {
          GlassSurface(
            cornerRadius = PrismDimens.BannerCornerRadius,
            elevation = GlassElevation.SUBTLE,
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(PrismDimens.SpaceLarge),
              verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
            ) {
              Text(
                text = stringResource(R.string.storage_empty_roots_title),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.storage_empty_roots_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge)) {
                if (!hasFullAccess) {
                  Button(
                    onClick = onRequestAllFilesAccess,
                    modifier =
                      Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                        .testTag("storage_grant_all_files_button"),
                  ) {
                    Text(stringResource(R.string.access_banner_grant))
                  }
                }
                OutlinedButton(
                  onClick = onAddSafFolder,
                  modifier =
                    Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                      .testTag("storage_pick_saf_folder_button"),
                ) {
                  Text(stringResource(R.string.storage_add_saf_folder))
                }
              }
            }
          }
        }
      } else {
        items(storageState.roots.size, key = { storageState.roots[it].id }) { idx ->
          val root = storageState.roots[idx]
          StorageVolumeRootCard(
            root = root,
            onClick = { onOpenRoot(root) },
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }

        item {
          GlassSurface(
            cornerRadius = PrismDimens.ToastCornerRadius,
            elevation = GlassElevation.SUBTLE,
            onClick = onAddSafFolder,
            clickRole = Role.Button,
            modifier =
              Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
                .fillMaxWidth()
                .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                .testTag("storage_add_another_saf_folder"),
          ) {
            Row(
              modifier = Modifier.padding(PrismDimens.SpaceSection),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
            ) {
              Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                tint = colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.storage_link_another_saf),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
    return
  }

  val hasNoFiles =
    if (isSafOpen) storageState.liveSafFiles.isEmpty()
    else pagedLocalFiles.itemCount == 0 && pagedLocalFiles.loadState.refresh is LoadState.NotLoading

  if (isGridMode) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 156.dp),
      contentPadding =
        PaddingValues(
          top = topContentPadding,
          bottom = navBarBottom + 144.dp,
          start = 16.dp,
          end = 16.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      modifier =
        modifier
          .fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("storage_directory_grid"),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        BreadcrumbAndSortHeader(
          breadcrumbs = storageState.breadcrumbs,
          isWatchingLive = storageState.isWatchingLiveDirectory,
          sortField = sortField,
          sortDirection = sortDirection,
          onNavigateUp = { onNavigateUp() },
          onNavigateToBreadcrumb = onNavigateToBreadcrumb,
          onCreateFolderClick = onCreateFolderClick,
          onSortFieldSelected = onSortFieldSelected,
          onToggleSortDirection = onToggleSortDirection,
        )
      }

      items(storageState.subfolders.size, key = { "dir_${storageState.subfolders[it].uriString}" }) {
        idx ->
        FolderGridTile(
          folder = storageState.subfolders[idx],
          onClick = { onOpenSubfolder(storageState.subfolders[idx]) },
        )
      }

      if (isSafOpen) {
        items(
          storageState.liveSafFiles.size,
          key = { "saf_${storageState.liveSafFiles[it].meta.uriString}" },
        ) { idx ->
          val item = storageState.liveSafFiles[idx]
          val entity = item.indexedEntity
          if (entity != null) {
            DocumentGridCard(
              document = entity,
              thumbnailPipeline = thumbnailPipeline,
              isSelected = entity.id in selectedIds,
              isSelectionMode = isSelectionMode,
              onClick = {
                if (isSelectionMode) onToggleSelection(entity.id)
                else onOpenIndexedDocument(entity)
              },
              onLongClick = { onToggleSelection(entity.id) },
              onSelectItem = { onToggleSelection(entity.id) },
              onToggleFavorite = { onToggleFavorite(entity.id, entity.isFavorite) },
              onRename = { onRenameDocument(entity) },
              onCopy = { onCopyDocument(entity) },
              onMove = { onMoveDocument(entity) },
              onShare = { onShareDocument(entity) },
              onTrash = { onTrashDocument(entity) },
            )
          } else {
            LiveSafGridCard(
              meta = item.meta,
              thumbnailPipeline = thumbnailPipeline,
              onClick = {
                onOpenRawDocument(
                  IncomingDocumentRequest(
                    uriString = item.meta.uriString,
                    displayName = item.meta.displayName,
                    mimeType = item.meta.mimeType,
                    sizeBytes = item.meta.sizeBytes,
                    sourceAction = "SAF_LIVE",
                  )
                )
              },
            )
          }
        }
      } else {
        items(
          count = pagedLocalFiles.itemCount,
          key = { idx -> pagedLocalFiles.peek(idx)?.id ?: "local_grid_$idx" },
        ) { idx ->
          val doc = pagedLocalFiles[idx] ?: return@items
          DocumentGridCard(
            document = doc,
            thumbnailPipeline = thumbnailPipeline,
            isSelected = doc.id in selectedIds,
            isSelectionMode = isSelectionMode,
            onClick = {
              if (isSelectionMode) onToggleSelection(doc.id) else onOpenIndexedDocument(doc)
            },
            onLongClick = { onToggleSelection(doc.id) },
            onSelectItem = { onToggleSelection(doc.id) },
            onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
            onRename = { onRenameDocument(doc) },
            onCopy = { onCopyDocument(doc) },
            onMove = { onMoveDocument(doc) },
            onShare = { onShareDocument(doc) },
            onTrash = { onTrashDocument(doc) },
          )
        }
      }

      if (storageState.subfolders.isEmpty() && hasNoFiles && !storageState.isLoadingDirectory) {
        item(span = { GridItemSpan(maxLineSpan) }) { EmptyFolderCard(isSaf = isSafOpen) }
      }
    }
  } else {
    LazyColumn(
      state = directoryListState,
      contentPadding =
        PaddingValues(
          top = topContentPadding,
          bottom = navBarBottom + 144.dp,
          start = 16.dp,
          end = 16.dp,
        ),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        modifier
          .fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("storage_directory_list"),
    ) {
      item {
        BreadcrumbAndSortHeader(
          breadcrumbs = storageState.breadcrumbs,
          isWatchingLive = storageState.isWatchingLiveDirectory,
          sortField = sortField,
          sortDirection = sortDirection,
          onNavigateUp = { onNavigateUp() },
          onNavigateToBreadcrumb = onNavigateToBreadcrumb,
          onCreateFolderClick = onCreateFolderClick,
          onSortFieldSelected = onSortFieldSelected,
          onToggleSortDirection = onToggleSortDirection,
          modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
        )
      }

      items(storageState.subfolders.size, key = { "dir_${storageState.subfolders[it].uriString}" }) {
        idx ->
        FolderListRow(
          folder = storageState.subfolders[idx],
          onClick = { onOpenSubfolder(storageState.subfolders[idx]) },
          modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
        )
      }

      if (isSafOpen) {
        items(
          storageState.liveSafFiles.size,
          key = { "saf_${storageState.liveSafFiles[it].meta.uriString}" },
        ) { idx ->
          val item = storageState.liveSafFiles[idx]
          val entity = item.indexedEntity
          if (entity != null) {
            DocumentListRow(
              document = entity,
              thumbnailPipeline = thumbnailPipeline,
              isSelected = entity.id in selectedIds,
              isSelectionMode = isSelectionMode,
              onClick = {
                if (isSelectionMode) onToggleSelection(entity.id)
                else onOpenIndexedDocument(entity)
              },
              onLongClick = { onToggleSelection(entity.id) },
              onSelectItem = { onToggleSelection(entity.id) },
              onToggleFavorite = { onToggleFavorite(entity.id, entity.isFavorite) },
              onRename = { onRenameDocument(entity) },
              onCopy = { onCopyDocument(entity) },
              onMove = { onMoveDocument(entity) },
              onShare = { onShareDocument(entity) },
              onTrash = { onTrashDocument(entity) },
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          } else {
            LiveSafListRow(
              meta = item.meta,
              thumbnailPipeline = thumbnailPipeline,
              onClick = {
                onOpenRawDocument(
                  IncomingDocumentRequest(
                    uriString = item.meta.uriString,
                    displayName = item.meta.displayName,
                    mimeType = item.meta.mimeType,
                    sizeBytes = item.meta.sizeBytes,
                    sourceAction = "SAF_LIVE",
                  )
                )
              },
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }
        }
      } else {
        items(
          count = pagedLocalFiles.itemCount,
          key = { idx -> pagedLocalFiles.peek(idx)?.id ?: "local_list_$idx" },
        ) { idx ->
          val doc = pagedLocalFiles[idx] ?: return@items
          DocumentListRow(
            document = doc,
            thumbnailPipeline = thumbnailPipeline,
            isSelected = doc.id in selectedIds,
            isSelectionMode = isSelectionMode,
            onClick = {
              if (isSelectionMode) onToggleSelection(doc.id) else onOpenIndexedDocument(doc)
            },
            onLongClick = { onToggleSelection(doc.id) },
            onSelectItem = { onToggleSelection(doc.id) },
            onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
            onRename = { onRenameDocument(doc) },
            onCopy = { onCopyDocument(doc) },
            onMove = { onMoveDocument(doc) },
            onShare = { onShareDocument(doc) },
            onTrash = { onTrashDocument(doc) },
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }
      }

      if (storageState.subfolders.isEmpty() && hasNoFiles && !storageState.isLoadingDirectory) {
        item {
          EmptyFolderCard(
            isSaf = isSafOpen,
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun StorageVolumeRootCard(
  root: StorageVolumeRoot,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val icon =
    when {
      root.sourceType == StorageSourceType.SAF -> Icons.Rounded.FolderOpen
      root.isRemovable -> Icons.Rounded.SdStorage
      else -> Icons.Rounded.Smartphone
    }
  val hasSpaceInfo = root.totalBytes > 0L && root.availableBytes >= 0L
  val usedBytes = if (hasSpaceInfo) (root.totalBytes - root.availableBytes).coerceAtLeast(0L) else 0L
  val usedFraction =
    if (hasSpaceInfo) (usedBytes.toFloat() / root.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

  val subtitle =
    when {
      root.sourceType == StorageSourceType.SAF -> stringResource(R.string.storage_saf_live_subtitle)
      hasSpaceInfo ->
        stringResource(
          R.string.storage_free_space,
          rememberFormattedByteSize(root.availableBytes),
          rememberFormattedByteSize(root.totalBytes),
        )
      else -> root.pathOrUri
    }

  GlassSurface(
    cornerRadius = 22.dp,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = modifier.testTag("storage_root_${root.id}"),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceSection),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      ) {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(colorScheme.primary.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(imageVector = icon, contentDescription = null, tint = colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = root.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          imageVector = Icons.Rounded.ChevronRight,
          contentDescription = stringResource(R.string.storage_open_root_cd, root.displayName),
          tint = colorScheme.onSurfaceVariant,
        )
      }
      if (hasSpaceInfo) {
        LinearProgressIndicator(
          progress = { usedFraction },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
        )
      }
    }
  }
}

@Composable
private fun BreadcrumbAndSortHeader(
  breadcrumbs: List<BreadcrumbSegment>,
  isWatchingLive: Boolean,
  sortField: FileSortField,
  sortDirection: SortDirection,
  onNavigateUp: () -> Unit,
  onNavigateToBreadcrumb: (Int) -> Unit,
  onCreateFolderClick: () -> Unit,
  onSortFieldSelected: (FileSortField) -> Unit,
  onToggleSortDirection: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium)) {
    GlassSurface(cornerRadius = PrismDimens.FilterBarCornerRadius, elevation = GlassElevation.SUBTLE) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onNavigateUp,
          modifier =
            Modifier.defaultMinSize(
                minWidth = PrismDimens.TouchTargetMin,
                minHeight = PrismDimens.TouchTargetMin,
              )
              .testTag("breadcrumb_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.storage_parent_folder_cd),
            tint = colorScheme.onSurface,
          )
        }
        LazyRow(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
        ) {
          item {
            Box(
              modifier =
                Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                  .clip(RoundedCornerShape(8.dp))
                  .glassClickable(role = Role.Button) { onNavigateToBreadcrumb(-1) }
                  .padding(horizontal = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(R.string.storage_volumes_breadcrumb),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
              )
            }
          }
          itemsIndexed(breadcrumbs) { idx, seg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              val isCurrent = idx == breadcrumbs.lastIndex
              Box(
                modifier =
                  Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                    .clip(RoundedCornerShape(8.dp))
                    .glassClickable(enabled = !isCurrent, role = Role.Button) {
                      onNavigateToBreadcrumb(idx)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = seg.label,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (isCurrent) colorScheme.onSurface else colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
        if (isWatchingLive) {
          val liveCd = stringResource(R.string.storage_live_watching_cd)
          val tokens = LocalPrismVisualTokens.current
          Box(
            modifier =
              Modifier.padding(end = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(tokens.successAccent)
                .semantics { contentDescription = liveCd }
          )
        }
        IconButton(
          onClick = onCreateFolderClick,
          modifier =
            Modifier.defaultMinSize(
                minWidth = PrismDimens.TouchTargetMin,
                minHeight = PrismDimens.TouchTargetMin,
              )
              .testTag("create_folder_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.CreateNewFolder,
            contentDescription = stringResource(R.string.storage_create_folder_cd),
            tint = colorScheme.primary,
          )
        }
      }
    }

    SortControlRow(
      sortField = sortField,
      sortDirection = sortDirection,
      onSortFieldSelected = onSortFieldSelected,
      onToggleSortDirection = onToggleSortDirection,
    )
  }
}

@Composable
private fun FolderListRow(
  folder: ScannedDocumentMetadata,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val modifiedLabel = rememberFormattedModifiedTimestamp(folder.lastModifiedEpochMs)
  GlassSurface(
    cornerRadius = PrismDimens.FilterBarCornerRadius,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = modifier,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
          .padding(horizontal = PrismDimens.SpaceRow, vertical = PrismDimens.SpaceStandard),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    ) {
      Box(
        modifier =
          Modifier.size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.warningAccent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = tokens.warningAccent)
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = folder.displayName,
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = modifiedLabel,
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = stringResource(R.string.storage_open_folder_cd, folder.displayName),
        tint = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun FolderGridTile(folder: ScannedDocumentMetadata, onClick: () -> Unit) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  GlassSurface(
    cornerRadius = PrismDimens.ToastCornerRadius,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceStandard),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.warningAccent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Folder,
          contentDescription = null,
          tint = tokens.warningAccent,
          modifier = Modifier.size(40.dp),
        )
      }
      Text(
        text = folder.displayName,
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = stringResource(R.string.storage_folder_label),
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyFolderCard(isSaf: Boolean, modifier: Modifier = Modifier) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 22.dp,
    elevation = GlassElevation.SUBTLE,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceXLarge).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmallMedium),
    ) {
      Icon(
        imageVector = Icons.Rounded.FolderOpen,
        contentDescription = null,
        tint = colorScheme.onSurfaceVariant,
        modifier = Modifier.size(32.dp),
      )
      Text(
        text = stringResource(R.string.storage_empty_folder_title),
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
      )
      Text(
        text =
          stringResource(
            if (isSaf) R.string.storage_empty_folder_saf else R.string.storage_empty_folder_local
          ),
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LiveSafListRow(
  meta: ScannedDocumentMetadata,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val request =
    ThumbnailRequest(
      uriString = meta.uriString,
      displayName = meta.displayName,
      mimeType = meta.mimeType,
      category = meta.category,
      lastModifiedEpochMs = meta.lastModifiedEpochMs,
      sizeBytes = meta.sizeBytes,
      targetSizePx = 192,
    )
  val subtitle =
    stringResource(
      R.string.storage_saf_item_subtitle,
      rememberFormattedByteSize(meta.sizeBytes),
      rememberFormattedModifiedTimestamp(meta.lastModifiedEpochMs),
    )
  GlassSurface(
    cornerRadius = PrismDimens.FilterBarCornerRadius,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = modifier,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
          .padding(horizontal = PrismDimens.SpaceStandard, vertical = PrismDimens.SpaceMediumLarge),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    ) {
      DocumentThumbnailView(
        request = request,
        pipeline = thumbnailPipeline,
        cornerRadius = 12.dp,
        compact = true,
        modifier = Modifier.size(PrismDimens.TouchTargetMin),
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = meta.displayName,
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun LiveSafGridCard(
  meta: ScannedDocumentMetadata,
  thumbnailPipeline: DocumentThumbnailPipeline,
  onClick: () -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val request =
    ThumbnailRequest(
      uriString = meta.uriString,
      displayName = meta.displayName,
      mimeType = meta.mimeType,
      category = meta.category,
      lastModifiedEpochMs = meta.lastModifiedEpochMs,
      sizeBytes = meta.sizeBytes,
      targetSizePx = 320,
    )
  val subtitle =
    stringResource(
      R.string.storage_saf_item_subtitle,
      rememberFormattedByteSize(meta.sizeBytes),
      rememberFormattedModifiedTimestamp(meta.lastModifiedEpochMs),
    )
  GlassSurface(
    cornerRadius = PrismDimens.ToastCornerRadius,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    clickRole = Role.Button,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceMediumLarge),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
    ) {
      DocumentThumbnailView(
        request = request,
        pipeline = thumbnailPipeline,
        cornerRadius = 14.dp,
        compact = false,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.18f),
      )
      Text(
        text = meta.displayName,
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListRow(
  document: IndexedDocumentEntity,
  thumbnailPipeline: DocumentThumbnailPipeline,
  isSelected: Boolean,
  isSelectionMode: Boolean = false,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onSelectItem: (() -> Unit)? = onLongClick,
  onToggleFavorite: () -> Unit,
  onRename: () -> Unit,
  onCopy: (() -> Unit)?,
  onMove: (() -> Unit)?,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
  onManageTagsCollections: (() -> Unit)? = null,
  extraActionLabel: String? = null,
  onExtraAction: (() -> Unit)? = null,
  sectionKey: String = "",
  itemIndex: Int = -1,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val coordinator = LocalListRowSwipeCoordinator.current
  val request = document.toThumbnailRequest(targetSizePx = 224)
  val (badgeLabel, badgeColor) =
    remember(document.displayName, document.category) {
      resolveFileTypeBadge(document.displayName, document.resolvedCategory())
    }
  var menuExpanded by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }

  val neighborDelta by
    remember(coordinator, sectionKey, itemIndex) {
      derivedStateOf {
        if (coordinator == null || sectionKey.isEmpty() || itemIndex < 0) 0
        else if (coordinator.activeSectionKey == sectionKey) itemIndex - coordinator.activeIndex
        else 0
      }
    }
  val neighborProgress by
    remember(coordinator, sectionKey, itemIndex) {
      derivedStateOf {
        if (
          coordinator != null &&
            sectionKey.isNotEmpty() &&
            itemIndex >= 0 &&
            coordinator.activeSectionKey == sectionKey &&
            coordinator.activeIndex != itemIndex
        ) {
          coordinator.swipeProgress
        } else {
          0f
        }
      }
    }

  val favTint by
    animateColorAsState(
      targetValue =
        if (document.isFavorite) tokens.favoriteAccent else colorScheme.onSurfaceVariant,
      label = "list_row_fav_tint_${document.id}",
    )
  val favScale =
    animateFloatAsState(
      targetValue = if (document.isFavorite) 1.14f else 1f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.45f, stiffness = 500f) else snap(),
      label = "list_row_fav_scale_${document.id}",
    )

  val sizeLabel = rememberFormattedByteSize(document.sizeBytes)
  val modifiedLabel = rememberFormattedModifiedTimestamp(document.lastModifiedEpochMs)
  val subtitle = stringResource(R.string.storage_saf_item_subtitle, sizeLabel, modifiedLabel)
  val rowCd = stringResource(R.string.doc_row_a11y_cd, document.displayName, subtitle)
  val selectedStateDesc =
    stringResource(if (isSelected) R.string.state_selected else R.string.state_not_selected)
  val favActionLabel =
    stringResource(
      if (document.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite
    )
  val shareActionLabel = stringResource(R.string.action_share)
  val deleteActionLabel = stringResource(R.string.action_delete)

  SwipeableDocumentRowContainer(
    documentId = document.id,
    documentName = document.displayName,
    isFavorite = document.isFavorite,
    isSelectionMode = isSelectionMode,
    restingTopRadius = PrismDimens.BannerCornerRadius,
    restingBottomRadius = PrismDimens.BannerCornerRadius,
    sectionKey = sectionKey,
    itemIndex = itemIndex,
    onToggleFavorite = onToggleFavorite,
    onShare = onShare,
    onTrash = onTrash,
    modifier =
      modifier.graphicsLayer {
        if (motionEnabled && neighborProgress > 0.004f) {
          when (neighborDelta) {
            -1 -> {
              translationY = -3.5.dp.toPx() * neighborProgress
              val s = 1f - 0.012f * neighborProgress
              scaleX = s
              scaleY = s
            }
            1 -> {
              translationY = 3.5.dp.toPx() * neighborProgress
              val s = 1f - 0.012f * neighborProgress
              scaleX = s
              scaleY = s
            }
            -2 -> translationY = -1.2.dp.toPx() * neighborProgress
            2 -> translationY = 1.2.dp.toPx() * neighborProgress
          }
        }
      },
  ) { swipeController ->
    GlassSurface(
      cornerRadius = PrismDimens.BannerCornerRadius,
      elevation = GlassElevation.SUBTLE,
      accentTint = if (isSelected) colorScheme.primary else null,
      modifier =
        Modifier.fillMaxWidth()
          .semantics(mergeDescendants = true) {
            role = if (isSelectionMode) Role.Checkbox else Role.Button
            selected = isSelected
            stateDescription = selectedStateDesc
            contentDescription = rowCd
            customActions =
              listOf(
                CustomAccessibilityAction(favActionLabel) {
                  onToggleFavorite()
                  true
                },
                CustomAccessibilityAction(shareActionLabel) {
                  onShare()
                  true
                },
                CustomAccessibilityAction(deleteActionLabel) {
                  onTrash()
                  true
                },
              )
          }
          .glassPress(interactionSource)
          .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
              if (swipeController.isOpen) {
                haptics.tap()
                swipeController.close()
              } else {
                if (!isSelectionMode) haptics.tap()
                onClick()
              }
            },
            onLongClick = {
              if (swipeController.isOpen) {
                swipeController.close()
              } else if (isSelectionMode) {
                onLongClick()
              } else {
                haptics.longPress()
                menuExpanded = true
              }
            },
          )
          .testTag("doc_row_${document.id}"),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              start = PrismDimens.SpaceRow,
              top = PrismDimens.SpaceRow,
              bottom = PrismDimens.SpaceRow,
              end = PrismDimens.SpaceSmallMedium,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      ) {
        Box {
          DocumentThumbnailView(
            request = request,
            pipeline = thumbnailPipeline,
            cornerRadius = PrismDimens.SpaceSection,
            compact = true,
            modifier = Modifier.size(PrismDimens.HeaderBarHeight),
          )
          androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            enter =
              scaleIn(
                initialScale = 0.45f,
                animationSpec = spring(dampingRatio = 0.56f, stiffness = 520f),
              ) + fadeIn(tween(120)),
            exit = scaleOut(targetScale = 0.45f, animationSpec = tween(110)) + fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.TopEnd),
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
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Text(
            text = document.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          ) {
            FileTypeBadgePill(label = badgeLabel, accent = badgeColor)
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        IconButton(
          onClick = {
            haptics.confirm()
            onToggleFavorite()
          },
          modifier =
            Modifier.defaultMinSize(
                minWidth = PrismDimens.TouchTargetMin,
                minHeight = PrismDimens.TouchTargetMin,
              )
              .testTag("doc_fav_${document.id}"),
        ) {
          Icon(
            imageVector = if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = favActionLabel,
            tint = favTint,
            modifier =
              Modifier.graphicsLayer {
                val s = favScale.value
                scaleX = s
                scaleY = s
              },
          )
        }
        Box {
          IconButton(
            onClick = {
              haptics.tap()
              menuExpanded = true
            },
            modifier =
              Modifier.defaultMinSize(
                  minWidth = PrismDimens.TouchTargetMin,
                  minHeight = PrismDimens.TouchTargetMin,
                )
                .testTag("doc_more_${document.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.MoreVert,
              contentDescription = stringResource(R.string.doc_actions_cd, document.displayName),
              tint = colorScheme.onSurfaceVariant,
            )
          }
          DocumentItemContextMenu(
            expanded = menuExpanded,
            isFavorite = document.isFavorite,
            showSelectOption = !isSelectionMode && onSelectItem != null,
            onSelectItem = onSelectItem,
            onToggleFavorite = onToggleFavorite,
            onDismiss = { menuExpanded = false },
            onRename = onRename,
            onCopy = onCopy,
            onMove = onMove,
            onShare = onShare,
            onTrash = onTrash,
            onManageTagsCollections = onManageTagsCollections,
            extraActionLabel = extraActionLabel,
            onExtraAction = onExtraAction,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridCard(
  document: IndexedDocumentEntity,
  thumbnailPipeline: DocumentThumbnailPipeline,
  isSelected: Boolean,
  isSelectionMode: Boolean = false,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onSelectItem: (() -> Unit)? = onLongClick,
  onToggleFavorite: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
  onManageTagsCollections: (() -> Unit)? = null,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val request = document.toThumbnailRequest(targetSizePx = 360)
  var menuExpanded by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }

  val favTint by
    animateColorAsState(
      targetValue = if (document.isFavorite) tokens.favoriteAccent else colorScheme.onSurface,
      label = "grid_fav_tint_${document.id}",
    )
  val favScale =
    animateFloatAsState(
      targetValue = if (document.isFavorite) 1.14f else 1f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.45f, stiffness = 500f) else snap(),
      label = "grid_fav_scale_${document.id}",
    )

  val sizeLabel = rememberFormattedByteSize(document.sizeBytes)
  val modifiedLabel = rememberFormattedModifiedTimestamp(document.lastModifiedEpochMs)
  val subtitle = stringResource(R.string.storage_saf_item_subtitle, sizeLabel, modifiedLabel)
  val cardCd = stringResource(R.string.doc_row_a11y_cd, document.displayName, subtitle)
  val selectedStateDesc =
    stringResource(if (isSelected) R.string.state_selected else R.string.state_not_selected)
  val favActionLabel =
    stringResource(
      if (document.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite
    )

  GlassSurface(
    cornerRadius = PrismDimens.RecentCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    accentTint = if (isSelected) colorScheme.primary else null,
    modifier =
      modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {
          role = if (isSelectionMode) Role.Checkbox else Role.Button
          selected = isSelected
          stateDescription = selectedStateDesc
          contentDescription = cardCd
        }
        .glassPress(interactionSource)
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = {
            if (!isSelectionMode) haptics.tap()
            onClick()
          },
          onLongClick = {
            if (isSelectionMode) {
              onLongClick()
            } else {
              haptics.longPress()
              menuExpanded = true
            }
          },
        )
        .testTag("doc_grid_${document.id}"),
  ) {
    Column(
      modifier = Modifier.padding(PrismDimens.SpaceStandard),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    ) {
      val (badgeLabel, badgeColor) =
        remember(document.displayName, document.category) {
          resolveFileTypeBadge(document.displayName, document.resolvedCategory())
        }
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.08f)) {
        DocumentThumbnailView(
          request = request,
          pipeline = thumbnailPipeline,
          cornerRadius = PrismDimens.RecentThumbnailCornerRadius,
          compact = false,
          showCornerBadge = false,
          modifier = Modifier.fillMaxSize(),
        )
        FileTypeBadgePill(
          label = badgeLabel,
          accent = badgeColor,
          modifier = Modifier.align(Alignment.BottomStart).padding(PrismDimens.BadgeCornerInset),
        )
        androidx.compose.animation.AnimatedVisibility(
          visible = isSelected,
          enter =
            scaleIn(
              initialScale = 0.45f,
              animationSpec = spring(dampingRatio = 0.56f, stiffness = 520f),
            ) + fadeIn(tween(120)),
          exit = scaleOut(targetScale = 0.45f, animationSpec = tween(110)) + fadeOut(tween(100)),
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
        Row(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(6.dp)
              .clip(CircleShape)
              .background(tokens.gridActionScrim),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = {
              haptics.confirm()
              onToggleFavorite()
            },
            modifier =
              Modifier.size(PrismDimens.TouchTargetMin)
                .testTag("doc_grid_fav_${document.id}"),
          ) {
            Icon(
              imageVector = if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
              contentDescription = favActionLabel,
              tint = favTint,
              modifier =
                Modifier.size(19.dp).graphicsLayer {
                  val s = favScale.value
                  scaleX = s
                  scaleY = s
                },
            )
          }
          Box {
            IconButton(
              onClick = {
                haptics.tap()
                menuExpanded = true
              },
              modifier =
                Modifier.size(PrismDimens.TouchTargetMin)
                  .testTag("doc_grid_more_${document.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.doc_actions_cd, document.displayName),
                tint = colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
              )
            }
            DocumentItemContextMenu(
              expanded = menuExpanded,
              isFavorite = document.isFavorite,
              showSelectOption = !isSelectionMode && onSelectItem != null,
              onSelectItem = onSelectItem,
              onToggleFavorite = onToggleFavorite,
              onDismiss = { menuExpanded = false },
              onRename = onRename,
              onCopy = onCopy,
              onMove = onMove,
              onShare = onShare,
              onTrash = onTrash,
              onManageTagsCollections = onManageTagsCollections,
            )
          }
        }
      }
      Text(
        text = document.displayName,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(0.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Connected Library document row with:
 * - Non-translated GPU-accelerated swipe gestures (right = Favorite toggle, left = Share / Delete tray, deep left = Move to Bin)
 * - Long-press Liquid Glass context menu (with Select, Favorite, Rename, Copy, Move, Share, Delete)
 * - Full selection & TalkBack accessibility semantics (Role, selected, stateDescription, customActions)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCardDocumentRow(
  document: IndexedDocumentEntity,
  isFirst: Boolean,
  isLast: Boolean,
  isSelected: Boolean,
  isSelectionMode: Boolean = false,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onSelectItem: (() -> Unit)? = onLongClick,
  onToggleFavorite: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
  entranceIndex: Int? = null,
  onManageTagsCollections: (() -> Unit)? = null,
  sectionKey: String = "",
  itemIndex: Int = -1,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val coordinator = LocalListRowSwipeCoordinator.current

  val (badgeLabel, badgeColor) =
    remember(document.displayName, document.category) {
      resolveFileTypeBadge(document.displayName, document.resolvedCategory())
    }
  var menuExpanded by remember { mutableStateOf(false) }

  val sizeText = rememberFormattedByteSize(document.sizeBytes)
  val dayText = rememberFormattedRelativeDay(document.lastModifiedEpochMs)
  val caption =
    if (document.isFavorite) {
      stringResource(R.string.doc_row_caption_favorite_format, sizeText, dayText)
    } else {
      stringResource(R.string.doc_row_caption_format, sizeText, dayText)
    }

  val interactionSource = remember { MutableInteractionSource() }

  // Neighbor reaction when a nearby filename bar in the same section is being swiped
  val neighborDelta by
    remember(coordinator, sectionKey, itemIndex) {
      derivedStateOf {
        if (coordinator == null || sectionKey.isEmpty() || itemIndex < 0) 0
        else if (coordinator.activeSectionKey == sectionKey) itemIndex - coordinator.activeIndex
        else 0
      }
    }
  val neighborSwipeProgress by
    remember(coordinator, sectionKey, itemIndex) {
      derivedStateOf {
        if (
          coordinator != null &&
            sectionKey.isNotEmpty() &&
            itemIndex >= 0 &&
            coordinator.activeSectionKey == sectionKey &&
            coordinator.activeIndex != itemIndex
        ) {
          coordinator.swipeProgress
        } else {
          0f
        }
      }
    }

  val baseTopRadius = if (isFirst) PrismDimens.ListCardCornerRadius else 0.dp
  val baseBottomRadius = if (isLast) PrismDimens.ListCardCornerRadius else 0.dp
  val neighborTopProgress = if (neighborDelta == 1) neighborSwipeProgress else 0f
  val neighborBottomProgress = if (neighborDelta == -1) neighborSwipeProgress else 0f

  val restingTopRadius =
    if (neighborTopProgress > 0.004f) lerp(baseTopRadius, 18.dp, neighborTopProgress)
    else baseTopRadius
  val restingBottomRadius =
    if (neighborBottomProgress > 0.004f) lerp(baseBottomRadius, 18.dp, neighborBottomProgress)
    else baseBottomRadius

  val selectedStateText =
    stringResource(if (isSelected) R.string.state_selected else R.string.state_not_selected)
  val favoriteActionLabel =
    stringResource(
      if (document.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite
    )
  val shareActionLabel = stringResource(R.string.action_share)
  val deleteActionLabel = stringResource(R.string.action_delete)
  val selectActionLabel = stringResource(R.string.action_select_item)
  val a11yDescription = stringResource(R.string.doc_row_a11y_cd, document.displayName, caption)

  SwipeableDocumentRowContainer(
    documentId = document.id,
    documentName = document.displayName,
    isFavorite = document.isFavorite,
    isSelectionMode = isSelectionMode,
    restingTopRadius = restingTopRadius,
    restingBottomRadius = restingBottomRadius,
    sectionKey = sectionKey,
    itemIndex = itemIndex,
    onToggleFavorite = onToggleFavorite,
    onShare = onShare,
    onTrash = onTrash,
    modifier =
      modifier
        .then(if (entranceIndex != null) Modifier.glassEntrance(index = entranceIndex) else Modifier)
        .graphicsLayer {
          if (motionEnabled && neighborSwipeProgress > 0.004f) {
            when (neighborDelta) {
              -1 -> {
                translationY = -4.dp.toPx() * neighborSwipeProgress
                val s = 1f - 0.012f * neighborSwipeProgress
                scaleX = s
                scaleY = s
              }
              1 -> {
                translationY = 4.dp.toPx() * neighborSwipeProgress
                val s = 1f - 0.012f * neighborSwipeProgress
                scaleX = s
                scaleY = s
              }
              -2 -> translationY = -1.4.dp.toPx() * neighborSwipeProgress
              2 -> translationY = 1.4.dp.toPx() * neighborSwipeProgress
            }
          }
        },
  ) { swipeController ->
    val selfSwipeProgress = swipeController.swipeProgress
    val morphedTopRadius =
      if (selfSwipeProgress > 0.004f) lerp(restingTopRadius, 22.dp, selfSwipeProgress)
      else restingTopRadius
    val morphedBottomRadius =
      if (selfSwipeProgress > 0.004f) lerp(restingBottomRadius, 22.dp, selfSwipeProgress)
      else restingBottomRadius
    val dividerAlpha =
      (1f - maxOf(selfSwipeProgress, neighborBottomProgress)).coerceIn(0f, 1f)

    ConnectedGlassCardSlice(
      isFirst = isFirst,
      isLast = isLast,
      cornerRadius = PrismDimens.ListCardCornerRadius,
      topCornerRadius = morphedTopRadius,
      bottomCornerRadius = morphedBottomRadius,
      fullBorderProgress = selfSwipeProgress,
      accentTint = if (isSelected) colorScheme.primary else null,
      modifier =
        Modifier.fillMaxWidth()
          .semantics(mergeDescendants = true) {
            role = if (isSelectionMode) Role.Checkbox else Role.Button
            selected = isSelected
            stateDescription = selectedStateText
            contentDescription = a11yDescription
            customActions =
              buildList {
                add(
                  CustomAccessibilityAction(favoriteActionLabel) {
                    onToggleFavorite()
                    true
                  }
                )
                if (!isSelectionMode && onSelectItem != null) {
                  add(
                    CustomAccessibilityAction(selectActionLabel) {
                      onSelectItem()
                      true
                    }
                  )
                }
                add(
                  CustomAccessibilityAction(shareActionLabel) {
                    onShare()
                    true
                  }
                )
                add(
                  CustomAccessibilityAction(deleteActionLabel) {
                    onTrash()
                    true
                  }
                )
              }
          }
          .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
              if (swipeController.isOpen) {
                haptics.tap()
                swipeController.close()
              } else {
                if (!isSelectionMode) haptics.tap()
                onClick()
              }
            },
            onLongClick = {
              if (swipeController.isOpen) {
                swipeController.close()
              } else if (isSelectionMode) {
                onLongClick()
              } else {
                haptics.longPress()
                menuExpanded = true
              }
            },
          )
          .testTag("doc_row_${document.id}"),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .glassPress(interactionSource, pressedScale = 0.985f)
              .padding(
                start = PrismDimens.SpaceSection,
                top = PrismDimens.SpaceRow,
                bottom = PrismDimens.SpaceRow,
                end = PrismDimens.SpaceSmallMedium,
              ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
        ) {
          FileTypeSquareBadge(
            label = badgeLabel,
            accent = badgeColor,
            isSelected = isSelected,
          )

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              text = document.displayName,
              style = MaterialTheme.typography.titleMedium,
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

          Box {
            IconButton(
              onClick = {
                haptics.tap()
                menuExpanded = true
              },
              modifier =
                Modifier.defaultMinSize(
                    minWidth = PrismDimens.TouchTargetMin,
                    minHeight = PrismDimens.TouchTargetMin,
                  )
                  .testTag("doc_more_${document.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.doc_actions_cd, document.displayName),
                tint = colorScheme.onSurfaceVariant,
              )
            }
            DocumentItemContextMenu(
              expanded = menuExpanded,
              isFavorite = document.isFavorite,
              showSelectOption = !isSelectionMode && onSelectItem != null,
              onSelectItem = onSelectItem,
              onDismiss = { menuExpanded = false },
              onToggleFavorite = onToggleFavorite,
              onRename = onRename,
              onCopy = onCopy,
              onMove = onMove,
              onShare = onShare,
              onTrash = onTrash,
              onManageTagsCollections = onManageTagsCollections,
            )
          }
        }

        if (!isLast) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = PrismDimens.SpaceSection)
                .height(PrismDimens.HairlineWidth)
                .graphicsLayer { alpha = dividerAlpha }
                .background(tokens.rowDivider)
          )
        }
      }
    }
  }
}

@Composable
internal fun DocumentItemContextMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onRename: () -> Unit,
  onCopy: (() -> Unit)?,
  onMove: (() -> Unit)?,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  isFavorite: Boolean? = null,
  onToggleFavorite: (() -> Unit)? = null,
  showSelectOption: Boolean = false,
  onSelectItem: (() -> Unit)? = null,
  onManageTagsCollections: (() -> Unit)? = null,
  extraActionLabel: String? = null,
  onExtraAction: (() -> Unit)? = null,
) {
  val selectLabel = stringResource(R.string.action_select_item)
  val favoriteLabel =
    if (isFavorite == true) stringResource(R.string.action_remove_favorite)
    else stringResource(R.string.action_add_favorite)
  val tagsCollectionsLabel = stringResource(R.string.action_tags_collections)
  val renameLabel = stringResource(R.string.action_rename)
  val copyLabel = stringResource(R.string.action_copy_to)
  val moveLabel = stringResource(R.string.action_move_to)
  val shareLabel = stringResource(R.string.action_share)
  val trashLabel = stringResource(R.string.action_move_to_recycle_bin)

  val items = buildList {
    if (showSelectOption && onSelectItem != null) {
      add(
        GlassContextMenuItem(
          id = "select",
          label = selectLabel,
          icon = Icons.Rounded.CheckCircleOutline,
          onClick = onSelectItem,
        )
      )
    }
    if (isFavorite != null && onToggleFavorite != null) {
      add(
        GlassContextMenuItem(
          id = "favorite",
          label = favoriteLabel,
          icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
          onClick = onToggleFavorite,
        )
      )
    }
    if (onManageTagsCollections != null) {
      add(
        GlassContextMenuItem(
          id = "tags_collections",
          label = tagsCollectionsLabel,
          icon = Icons.AutoMirrored.Rounded.Label,
          onClick = onManageTagsCollections,
        )
      )
    }
    if (extraActionLabel != null && onExtraAction != null) {
      add(
        GlassContextMenuItem(
          id = "extra_action",
          label = extraActionLabel,
          icon = Icons.Rounded.BookmarkRemove,
          onClick = onExtraAction,
        )
      )
    }
    add(GlassContextMenuItem("rename", renameLabel, Icons.Rounded.DriveFileRenameOutline, onClick = onRename))
    if (onCopy != null) {
      add(GlassContextMenuItem("copy", copyLabel, Icons.Rounded.ContentCopy, onClick = onCopy))
    }
    if (onMove != null) {
      add(GlassContextMenuItem("move", moveLabel, Icons.AutoMirrored.Rounded.DriveFileMove, onClick = onMove))
    }
    add(GlassContextMenuItem("share", shareLabel, Icons.Rounded.Share, onClick = onShare))
    add(
      GlassContextMenuItem(
        id = "trash",
        label = trashLabel,
        icon = Icons.Rounded.DeleteOutline,
        isDestructive = true,
        onClick = onTrash,
      )
    )
  }
  GlassContextMenu(
    expanded = expanded,
    onDismissRequest = onDismiss,
    items = items,
  )
}
