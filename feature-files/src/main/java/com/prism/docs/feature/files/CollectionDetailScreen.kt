package com.prism.docs.feature.files

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismDimens
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

@Composable
fun CollectionDetailScreen(
  onNavigateBack: () -> Unit,
  onOpenDocument: (IncomingDocumentRequest) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val density = LocalDensity.current
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val listState = rememberLazyListState()
  val scrollProgress = rememberHeaderScrollProgress(listState)
  val isScrolledUnder by remember { derivedStateOf { scrollProgress.value > 0.02f } }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var topBarHeightPx by remember { mutableIntStateOf(0) }
  val topBarHeightDp =
    if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() }
    else statusBarTop + PrismDimens.HeaderBarHeight + 16.dp

  var showRenameDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  val shareTitle = stringResource(R.string.action_share_chooser_title)
  val removeActionLabel = stringResource(R.string.collection_remove_doc)

  LaunchedEffect(uiState.statusMessage) {
    if (uiState.statusMessage != null) {
      delay(3_000L)
      viewModel.clearStatusMessage()
    }
  }

  val collection = uiState.collection
  val accentColor =
    remember(collection?.accentHex, colorScheme.primary) {
      parseHexColorOrDefault(collection?.accentHex, colorScheme.primary)
    }
  val haptics = rememberPrismHaptics()
  val swipeCoordinator = remember { ListRowSwipeCoordinator() }

  CompositionLocalProvider(LocalListRowSwipeCoordinator provides swipeCoordinator) {
  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      contentPadding =
        PaddingValues(
          top = topBarHeightDp + 8.dp,
          bottom = navBarBottom + 104.dp,
          start = PrismDimens.ScreenHorizontalPadding,
          end = PrismDimens.ScreenHorizontalPadding,
        ),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("collection_detail_list"),
    ) {
      if (uiState.isLoaded && collection == null) {
        item(key = "collection_missing") {
          GlassSurface(
            cornerRadius = PrismDimens.EmptyCardCornerRadius,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceXLarge),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
            ) {
              Text(
                text = stringResource(R.string.collection_not_found),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
              )
              PrimaryGlassButton(
                label = stringResource(R.string.collection_back_to_library),
                onClick = onNavigateBack,
              )
            }
          }
        }
      } else if (collection != null) {
        item(key = "collection_summary_header") {
          val docCountText =
            pluralStringResource(
              R.plurals.collection_doc_count,
              uiState.documents.size,
              uiState.documents.size,
            )
          val bytesText = rememberFormattedByteSize(uiState.totalBytes)

          GlassSurface(
            cornerRadius = PrismDimens.BannerCornerRadius,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            accentTint = accentColor,
            modifier =
              Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
                .fillMaxWidth()
                .testTag("collection_summary_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceLarge),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
            ) {
              Box(
                modifier =
                  Modifier.size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.CollectionsBookmark,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(24.dp),
                )
              }
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = collection.name,
                  style = MaterialTheme.typography.headlineMedium,
                  color = colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "$docCountText • $bytesText",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        if (uiState.documents.isEmpty()) {
          item(key = "collection_empty") {
            GlassSurface(
              cornerRadius = PrismDimens.EmptyCardCornerRadius,
              elevation = GlassElevation.SUBTLE,
              useBackdropBlur = false,
              modifier =
                Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
                  .fillMaxWidth()
                  .testTag("collection_empty_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceXLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CollectionsBookmark,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(36.dp),
                )
                Text(
                  text = stringResource(R.string.collection_detail_empty_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = colorScheme.onSurface,
                  textAlign = TextAlign.Center,
                )
                Text(
                  text = stringResource(R.string.collection_detail_empty_body, collection.name),
                  style = MaterialTheme.typography.bodyMedium,
                  color = colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        } else {
          itemsIndexed(uiState.documents, key = { _, doc -> doc.id }) { idx, doc ->
            DocumentListRow(
              document = doc,
              thumbnailPipeline = viewModel.thumbnailPipeline,
              isSelected = false,
              isSelectionMode = false,
              sectionKey = "collection_detail",
              itemIndex = idx,
              onClick = {
                viewModel.recordDocumentOpened(doc.id)
                onOpenDocument(
                  IncomingDocumentRequest(
                    uriString = doc.uriString,
                    displayName = doc.displayName,
                    mimeType = doc.mimeType,
                    sizeBytes = doc.sizeBytes,
                    sourceAction = "COLLECTION",
                  )
                )
              },
              onLongClick = {},
              onSelectItem = null,
              onToggleFavorite = { viewModel.toggleFavorite(doc.id, doc.isFavorite) },
              onRename = { viewModel.openRenameDialog(doc) },
              onCopy = null,
              onMove = null,
              onShare = {
                viewModel.buildShareIntent(doc.id) { intent ->
                  if (intent != null) {
                    runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                  }
                }
              },
              onTrash = { viewModel.moveToRecycleBin(doc.id) },
              extraActionLabel = removeActionLabel,
              onExtraAction = { viewModel.removeFromCollection(doc.id) },
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }
        }
      }
    }

    ScrollEdgeFade(
      isTop = true,
      height = topBarHeightDp + 14.dp,
      scrollProgress = scrollProgress,
      modifier = Modifier.align(Alignment.TopCenter),
    )

    ScrollEdgeFade(
      isTop = false,
      height = navBarBottom + 56.dp,
      modifier = Modifier.align(Alignment.BottomCenter),
    )

    // Floating Glass Top Bar (outside Haze source)
    Box(
      modifier =
        Modifier.align(Alignment.TopCenter)
          .fillMaxWidth()
          .onSizeChanged { topBarHeightPx = it.height }
          .statusBarsPadding()
          .padding(
            horizontal = PrismDimens.ScreenHorizontalPadding,
            vertical = PrismDimens.SpaceSmall,
          ),
      contentAlignment = Alignment.Center,
    ) {
      GlassSurface(
        cornerRadius = 28.dp,
        shape = RoundedCornerShape(28.dp),
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = isScrolledUnder,
        enableRefraction = true,
        modifier =
          Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
            .fillMaxWidth()
            .height(PrismDimens.HeaderBarHeight),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          IconButton(
            onClick = {
              haptics.tap()
              onNavigateBack()
            },
            modifier =
              Modifier.size(PrismDimens.TouchTargetMin).testTag("collection_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.dup_back_cd),
              tint = colorScheme.onSurface,
            )
          }
          Text(
            text = collection?.name ?: stringResource(R.string.collections_section_title),
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).semantics { heading() },
          )
          if (collection != null) {
            IconButton(
              onClick = {
                haptics.tap()
                showRenameDialog = true
              },
              modifier =
                Modifier.size(PrismDimens.TouchTargetMin).testTag("collection_rename_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.action_rename_cd, collection.name),
                tint = colorScheme.onSurface,
              )
            }
            IconButton(
              onClick = {
                haptics.tap()
                showDeleteDialog = true
              },
              modifier =
                Modifier.size(PrismDimens.TouchTargetMin).testTag("collection_delete_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.action_delete_cd, collection.name),
                tint = colorScheme.error,
              )
            }
          }
        }
      }
    }

    uiState.statusMessage?.let { msg ->
      GlassSurface(
        cornerRadius = PrismDimens.ToastCornerRadius,
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = true,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
              horizontal = PrismDimens.ScreenHorizontalPadding,
              vertical = PrismDimens.SpaceXLarge,
            )
            .widthIn(max = PrismDimens.OverlayMaxWidth)
            .testTag("collection_status_toast"),
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

  uiState.renameTarget?.let { doc ->
    RenameDocumentDialog(
      document = doc,
      onDismiss = viewModel::dismissRenameDialog,
      onConfirm = viewModel::confirmRename,
    )
  }

  if (showRenameDialog && collection != null) {
    NameAndColorInputDialog(
      title = stringResource(R.string.collection_rename_title),
      fieldLabel = stringResource(R.string.collection_name_label),
      confirmLabel = stringResource(R.string.action_save),
      initialName = collection.name,
      showColorPicker = false,
      onDismiss = { showRenameDialog = false },
      onConfirm = { newName, _ ->
        showRenameDialog = false
        viewModel.renameCollection(newName)
      },
    )
  }

  if (showDeleteDialog && collection != null) {
    ConfirmDeleteEntityDialog(
      title = stringResource(R.string.confirm_delete_collection_title),
      body = stringResource(R.string.confirm_delete_collection_body, collection.name),
      onDismiss = { showDeleteDialog = false },
      onConfirm = {
        showDeleteDialog = false
        viewModel.deleteCollection(onNavigateBack)
      },
    )
  }
}
