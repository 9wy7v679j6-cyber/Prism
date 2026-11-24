package com.prism.docs.feature.files

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.prism.docs.core.ui.glass.glassPress
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismColors
import dev.chrisbanes.haze.hazeSource

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
  rootsListState: LazyListState = rememberLazyListState(),
  directoryListState: LazyListState = rememberLazyListState(),
) {
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val currentDir = storageState.currentDirectory
  val isSafOpen = currentDir?.isSaf == true

  BackHandler(enabled = storageState.breadcrumbs.isNotEmpty()) { onNavigateUp() }

  if (currentDir == null) {
    LazyColumn(
      state = rootsListState,
      contentPadding = PaddingValues(top = 146.dp, bottom = 128.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        modifier
          .fillMaxSize()
          .statusBarsPadding()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("storage_roots_list"),
    ) {
      item {
        Text(
          text = "Storage Volumes & Linked Folders",
          style = MaterialTheme.typography.titleMedium,
          color = colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        )
      }

      if (storageState.roots.isEmpty()) {
        item {
          GlassSurface(
            cornerRadius = 24.dp,
            elevation = GlassElevation.SUBTLE,
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "No browsable storage volumes yet",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
              )
              Text(
                text =
                  "Grant All Files Access to browse Internal Storage and SD cards directly, or link specific folders through Android's folder picker.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasFullAccess) {
                  Button(
                    onClick = onRequestAllFilesAccess,
                    modifier = Modifier.testTag("storage_grant_all_files_button"),
                  ) {
                    Text("Grant Full Access")
                  }
                }
                OutlinedButton(
                  onClick = onAddSafFolder,
                  modifier = Modifier.testTag("storage_pick_saf_folder_button"),
                ) {
                  Text("Add SAF Folder")
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
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          )
        }

        item {
          GlassSurface(
            cornerRadius = 20.dp,
            elevation = GlassElevation.SUBTLE,
            onClick = onAddSafFolder,
            modifier =
              Modifier.fillMaxWidth()
                .widthIn(max = 760.dp)
                .testTag("storage_add_another_saf_folder"),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                tint = colorScheme.primary,
              )
              Text(
                text = "Link another folder via Storage Access Framework",
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
      contentPadding = PaddingValues(top = 146.dp, bottom = 144.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
        modifier
          .fillMaxSize()
          .statusBarsPadding()
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
              onClick = {
                if (isSelectionMode) onToggleSelection(entity.id)
                else onOpenIndexedDocument(entity)
              },
              onLongClick = { onToggleSelection(entity.id) },
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
            onClick = {
              if (isSelectionMode) onToggleSelection(doc.id) else onOpenIndexedDocument(doc)
            },
            onLongClick = { onToggleSelection(doc.id) },
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
      contentPadding = PaddingValues(top = 146.dp, bottom = 144.dp, start = 16.dp, end = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        modifier
          .fillMaxSize()
          .statusBarsPadding()
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
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
        )
      }

      items(storageState.subfolders.size, key = { "dir_${storageState.subfolders[it].uriString}" }) {
        idx ->
        FolderListRow(
          folder = storageState.subfolders[idx],
          onClick = { onOpenSubfolder(storageState.subfolders[idx]) },
          modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
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
              onClick = {
                if (isSelectionMode) onToggleSelection(entity.id)
                else onOpenIndexedDocument(entity)
              },
              onLongClick = { onToggleSelection(entity.id) },
              onToggleFavorite = { onToggleFavorite(entity.id, entity.isFavorite) },
              onRename = { onRenameDocument(entity) },
              onCopy = { onCopyDocument(entity) },
              onMove = { onMoveDocument(entity) },
              onShare = { onShareDocument(entity) },
              onTrash = { onTrashDocument(entity) },
              modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
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
              modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
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
            onClick = {
              if (isSelectionMode) onToggleSelection(doc.id) else onOpenIndexedDocument(doc)
            },
            onLongClick = { onToggleSelection(doc.id) },
            onToggleFavorite = { onToggleFavorite(doc.id, doc.isFavorite) },
            onRename = { onRenameDocument(doc) },
            onCopy = { onCopyDocument(doc) },
            onMove = { onMoveDocument(doc) },
            onShare = { onShareDocument(doc) },
            onTrash = { onTrashDocument(doc) },
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
          )
        }
      }

      if (storageState.subfolders.isEmpty() && hasNoFiles && !storageState.isLoadingDirectory) {
        item {
          EmptyFolderCard(
            isSaf = isSafOpen,
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
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

  GlassSurface(
    cornerRadius = 22.dp,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    modifier = modifier.testTag("storage_root_${root.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            text =
              if (root.sourceType == StorageSourceType.SAF) "Live SAF folder (DocumentsContract)"
              else if (hasSpaceInfo)
                "${formatByteSize(root.availableBytes)} free of ${formatByteSize(root.totalBytes)}"
              else root.pathOrUri,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          imageVector = Icons.Rounded.ChevronRight,
          contentDescription = "Open ${root.displayName}",
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
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    GlassSurface(cornerRadius = 18.dp, elevation = GlassElevation.SUBTLE) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onNavigateUp, modifier = Modifier.testTag("breadcrumb_back_button")) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Parent folder",
            tint = colorScheme.onSurface,
          )
        }
        LazyRow(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          item {
            Text(
              text = "Volumes",
              style = MaterialTheme.typography.labelLarge,
              color = colorScheme.primary,
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .clickable { onNavigateToBreadcrumb(-1) }
                  .padding(horizontal = 6.dp, vertical = 6.dp),
            )
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
              Text(
                text = seg.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) colorScheme.onSurface else colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isCurrent) { onNavigateToBreadcrumb(idx) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
              )
            }
          }
        }
        if (isWatchingLive) {
          Box(
            modifier =
              Modifier.padding(end = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(PrismColors.EmeraldSheet)
          )
        }
        IconButton(
          onClick = onCreateFolderClick,
          modifier = Modifier.testTag("create_folder_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.CreateNewFolder,
            contentDescription = "Create new folder",
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
  GlassSurface(
    cornerRadius = 18.dp,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PrismColors.AmberSlide.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null, tint = PrismColors.AmberSlide)
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
          text = formatModifiedTimestamp(folder.lastModifiedEpochMs),
          style = MaterialTheme.typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = "Open folder ${folder.displayName}",
        tint = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun FolderGridTile(folder: ScannedDocumentMetadata, onClick: () -> Unit) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = 20.dp,
    elevation = GlassElevation.SUBTLE,
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(14.dp))
            .background(PrismColors.AmberSlide.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Folder,
          contentDescription = null,
          tint = PrismColors.AmberSlide,
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
      Text(text = "Folder", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun EmptyFolderCard(isSaf: Boolean, modifier: Modifier = Modifier) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(cornerRadius = 22.dp, elevation = GlassElevation.SUBTLE, modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(24.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.FolderOpen,
        contentDescription = null,
        tint = colorScheme.onSurfaceVariant,
        modifier = Modifier.size(32.dp),
      )
      Text(text = "This folder is empty", style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface)
      Text(
        text =
          if (isSaf) "Listed live via DocumentsContract."
          else "Changes made here appear live via FileObserver.",
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
  GlassSurface(cornerRadius = 18.dp, elevation = GlassElevation.SUBTLE, onClick = onClick, modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      DocumentThumbnailView(request = request, pipeline = thumbnailPipeline, cornerRadius = 12.dp, compact = true, modifier = Modifier.size(48.dp))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = meta.displayName, style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = "${formatByteSize(meta.sizeBytes)} • ${formatModifiedTimestamp(meta.lastModifiedEpochMs)}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1)
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
  GlassSurface(cornerRadius = 20.dp, elevation = GlassElevation.SUBTLE, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      DocumentThumbnailView(request = request, pipeline = thumbnailPipeline, cornerRadius = 14.dp, compact = false, modifier = Modifier.fillMaxWidth().aspectRatio(1.18f))
      Text(text = meta.displayName, style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(text = "${formatByteSize(meta.sizeBytes)} • ${formatModifiedTimestamp(meta.lastModifiedEpochMs)}", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListRow(
  document: IndexedDocumentEntity,
  thumbnailPipeline: DocumentThumbnailPipeline,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val request = document.toThumbnailRequest(targetSizePx = 224)
  val (badgeLabel, badgeColor) =
    remember(document.displayName, document.category) {
      resolveFileTypeBadge(document.displayName, document.resolvedCategory())
    }
  var menuExpanded by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }

  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.SUBTLE,
    accentTint = if (isSelected) colorScheme.primary else Color.Transparent,
    modifier =
      modifier
        .glassPress(interactionSource)
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .testTag("doc_row_${document.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box {
        DocumentThumbnailView(
          request = request,
          pipeline = thumbnailPipeline,
          cornerRadius = 16.dp,
          compact = true,
          modifier = Modifier.size(56.dp),
        )
        if (isSelected) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Selected",
            tint = colorScheme.primary,
            modifier =
              Modifier.align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(colorScheme.surface),
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
          text = document.displayName,
          style = MaterialTheme.typography.titleMedium,
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FileTypeBadgePill(label = badgeLabel, accent = badgeColor)
          Text(
            text =
              "${formatByteSize(document.sizeBytes)} • ${formatModifiedTimestamp(document.lastModifiedEpochMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("doc_fav_${document.id}")) {
        Icon(
          imageVector = if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
          contentDescription = if (document.isFavorite) "Remove favorite" else "Add favorite",
          tint = if (document.isFavorite) PrismColors.AmberSlide else colorScheme.onSurfaceVariant,
        )
      }
      Box {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("doc_more_${document.id}")) {
          Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "Document actions",
            tint = colorScheme.onSurfaceVariant,
          )
        }
        DocumentItemContextMenu(
          expanded = menuExpanded,
          onDismiss = { menuExpanded = false },
          onRename = onRename,
          onCopy = onCopy,
          onMove = onMove,
          onShare = onShare,
          onTrash = onTrash,
        )
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
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val request = document.toThumbnailRequest(targetSizePx = 360)
  var menuExpanded by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }

  GlassSurface(
    cornerRadius = 26.dp,
    elevation = GlassElevation.SUBTLE,
    accentTint = if (isSelected) colorScheme.primary else Color.Transparent,
    modifier =
      modifier
        .fillMaxWidth()
        .glassPress(interactionSource)
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .testTag("doc_grid_${document.id}"),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.08f)) {
        DocumentThumbnailView(
          request = request,
          pipeline = thumbnailPipeline,
          cornerRadius = 18.dp,
          compact = false,
          modifier = Modifier.fillMaxSize(),
        )
        if (isSelected) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Selected",
            tint = colorScheme.primary,
            modifier =
              Modifier.align(Alignment.TopStart)
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(colorScheme.surface),
          )
        }
        Row(modifier = Modifier.align(Alignment.TopEnd)) {
          IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(34.dp).testTag("doc_grid_fav_${document.id}"),
          ) {
            Icon(
              imageVector = if (document.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
              contentDescription = if (document.isFavorite) "Remove favorite" else "Add favorite",
              tint = if (document.isFavorite) PrismColors.AmberSlide else colorScheme.onSurfaceVariant,
              modifier = Modifier.size(19.dp),
            )
          }
          Box {
            IconButton(
              onClick = { menuExpanded = true },
              modifier = Modifier.size(34.dp).testTag("doc_grid_more_${document.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Document actions",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
              )
            }
            DocumentItemContextMenu(
              expanded = menuExpanded,
              onDismiss = { menuExpanded = false },
              onRename = onRename,
              onCopy = onCopy,
              onMove = onMove,
              onShare = onShare,
              onTrash = onTrash,
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
        text =
          "${formatByteSize(document.sizeBytes)} • ${formatModifiedTimestamp(document.lastModifiedEpochMs)}",
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCardDocumentRow(
  document: IndexedDocumentEntity,
  isFirst: Boolean,
  isLast: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val (badgeLabel, badgeColor) =
    remember(document.displayName, document.category) {
      resolveFileTypeBadge(document.displayName, document.resolvedCategory())
    }
  var menuExpanded by remember { mutableStateOf(false) }

  val caption =
    remember(document.sizeBytes, document.lastModifiedEpochMs, document.isFavorite) {
      val base = "${formatByteSize(document.sizeBytes)}, ${formatRelativeDay(document.lastModifiedEpochMs)}"
      if (document.isFavorite) "$base • ★" else base
    }

  val interactionSource = remember { MutableInteractionSource() }

  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = 28.dp,
    accentTint = if (isSelected) colorScheme.primary else null,
    modifier =
      modifier
        .glassPress(interactionSource, pressedScale = 0.985f)
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .testTag("doc_row_${document.id}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            onClick = { menuExpanded = true },
            modifier = Modifier.testTag("doc_more_${document.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.MoreHoriz,
              contentDescription = "Document actions",
              tint = colorScheme.onSurfaceVariant,
            )
          }
          DocumentItemContextMenu(
            expanded = menuExpanded,
            isFavorite = document.isFavorite,
            onDismiss = { menuExpanded = false },
            onToggleFavorite = onToggleFavorite,
            onRename = onRename,
            onCopy = onCopy,
            onMove = onMove,
            onShare = onShare,
            onTrash = onTrash,
          )
        }
      }

      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .height(0.75.dp)
              .background(colorScheme.onSurface.copy(alpha = 0.08f))
        )
      }
    }
  }
}

@Composable
private fun DocumentItemContextMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onRename: () -> Unit,
  onCopy: () -> Unit,
  onMove: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  isFavorite: Boolean? = null,
  onToggleFavorite: (() -> Unit)? = null,
) {
  val items = buildList {
    if (isFavorite != null && onToggleFavorite != null) {
      add(
        GlassContextMenuItem(
          id = "favorite",
          label = if (isFavorite) "Remove favorite" else "Add to favorites",
          icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
          onClick = onToggleFavorite,
        )
      )
    }
    add(GlassContextMenuItem("rename", "Rename", Icons.Rounded.DriveFileRenameOutline, onClick = onRename))
    add(GlassContextMenuItem("copy", "Copy to…", Icons.Rounded.ContentCopy, onClick = onCopy))
    add(GlassContextMenuItem("move", "Move to…", Icons.AutoMirrored.Rounded.DriveFileMove, onClick = onMove))
    add(GlassContextMenuItem("share", "Share", Icons.Rounded.Share, onClick = onShare))
    add(
      GlassContextMenuItem(
        "trash",
        "Move to Recycle Bin",
        Icons.Rounded.DeleteOutline,
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
