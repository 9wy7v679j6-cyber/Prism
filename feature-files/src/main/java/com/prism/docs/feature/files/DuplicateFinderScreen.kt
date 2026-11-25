package com.prism.docs.feature.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.duplicates.DuplicateFileGroup
import com.prism.docs.core.data.duplicates.DuplicateScanProgress
import com.prism.docs.core.data.duplicates.DuplicateScanStage
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay

@Composable
fun DuplicateFinderScreen(
  onNavigateBack: () -> Unit,
  onOpenDocument: (IncomingDocumentRequest) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DuplicateFinderViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val density = LocalDensity.current
  val listState = rememberLazyListState()
  val scrollProgress = rememberHeaderScrollProgress(listState)
  val isScrolledUnder by remember { derivedStateOf { scrollProgress.value > 0.02f } }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var topBarHeightPx by remember { mutableIntStateOf(0) }
  val topBarHeightDp =
    if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() }
    else statusBarTop + PrismDimens.HeaderBarHeight + 16.dp

  LaunchedEffect(Unit) { viewModel.triggerInitialScanIfNeeded() }

  LaunchedEffect(uiState.statusMessage) {
    if (uiState.statusMessage != null) {
      delay(3_200L)
      viewModel.clearStatusMessage()
    }
  }

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
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("duplicate_finder_list"),
    ) {
      if (uiState.progress.isRunning) {
        item(key = "dup_scan_progress_card") {
          DuplicateScanProgressCard(
            progress = uiState.progress,
            onCancel = viewModel::cancelScan,
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }
      }

      if (uiState.groups.isNotEmpty()) {
        item(key = "dup_reclaimable_summary_card") {
          DuplicateSummaryCard(
            reclaimableBytes = uiState.totalReclaimableBytes,
            groupCount = uiState.groups.size,
            extraCopiesCount = uiState.totalExtraCopies,
            isTrashing = uiState.isTrashing,
            onKeepNewestAll = {
              haptics.confirm()
              viewModel.keepNewestMoveAllToBin()
            },
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }

        items(uiState.groups, key = { it.groupKey }) { group ->
          DuplicateGroupCard(
            group = group,
            thumbnailPipeline = viewModel.thumbnailPipeline,
            isBusy = uiState.isTrashing,
            onKeepNewestInGroup = {
              haptics.confirm()
              viewModel.keepNewestInGroup(group)
            },
            onOpenDocument = { doc ->
              onOpenDocument(
                IncomingDocumentRequest(
                  uriString = doc.uriString,
                  displayName = doc.displayName,
                  mimeType = doc.mimeType,
                  sizeBytes = doc.sizeBytes,
                  sourceAction = "DUPLICATES",
                )
              )
            },
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }
      } else if (!uiState.progress.isRunning) {
        item(key = "dup_empty_card") {
          DuplicateEmptyStateCard(
            wasCancelled = uiState.progress.stage == DuplicateScanStage.CANCELLED,
            onScanNow = viewModel::startRescan,
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
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
        cornerRadius = PrismDimens.HeaderBarHeight / 2,
        shape = CircleShape,
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = isScrolledUnder,
        modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth().height(PrismDimens.HeaderBarHeight),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = {
              haptics.tap()
              onNavigateBack()
            },
            modifier = Modifier.size(PrismDimens.TouchTargetMin).testTag("dup_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.dup_back_cd),
              tint = colorScheme.onSurface,
            )
          }
          Text(
            text = stringResource(R.string.dup_title),
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
          )
          IconButton(
            onClick = {
              haptics.tap()
              viewModel.startRescan()
            },
            enabled = !uiState.progress.isRunning,
            modifier = Modifier.size(PrismDimens.TouchTargetMin).testTag("dup_rescan_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = stringResource(R.string.dup_rescan_cd),
              tint =
                if (!uiState.progress.isRunning) colorScheme.primary
                else colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
          }
        }
      }
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
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(horizontal = PrismDimens.ScreenHorizontalPadding, vertical = PrismDimens.SpaceXLarge)
          .widthIn(max = PrismDimens.OverlayMaxWidth),
    ) {
      uiState.statusMessage?.let { msg ->
        GlassSurface(
          cornerRadius = PrismDimens.ToastCornerRadius,
          elevation = GlassElevation.FLOATING,
          useBackdropBlur = true,
          modifier = Modifier.testTag("dup_status_toast"),
        ) {
          Text(
            text = msg,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = PrismDimens.SpaceSection, vertical = PrismDimens.SpaceMediumLarge),
          )
        }
      }
    }
  }
}

@Composable
private fun DuplicateScanProgressCard(
  progress: DuplicateScanProgress,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val stageText =
    when (progress.stage) {
      DuplicateScanStage.GROUPING_BY_SIZE -> stringResource(R.string.dup_stage_size)
      DuplicateScanStage.PARTIAL_HASHING ->
        stringResource(R.string.dup_stage_partial, progress.processedCount, progress.totalCandidates)
      DuplicateScanStage.FULL_HASHING ->
        stringResource(R.string.dup_stage_full, progress.processedCount, progress.totalCandidates)
      else -> stringResource(R.string.dup_scanning_title)
    }

  GlassSurface(
    cornerRadius = PrismDimens.BannerCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    accentTint = colorScheme.primary,
    modifier = modifier.testTag("dup_progress_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceSection),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.dup_scanning_title),
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
          )
          Text(
            text = stageText,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
          )
        }
        TextButton(
          onClick = onCancel,
          modifier =
            Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
              .testTag("dup_cancel_scan_button"),
        ) {
          Text(text = stringResource(R.string.dup_cancel_scan), color = colorScheme.error)
        }
      }

      if (progress.totalCandidates > 0 && progress.stage != DuplicateScanStage.GROUPING_BY_SIZE) {
        LinearProgressIndicator(
          progress = { progress.fraction },
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
        )
      } else {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
        )
      }
    }
  }
}

@Composable
private fun DuplicateSummaryCard(
  reclaimableBytes: Long,
  groupCount: Int,
  extraCopiesCount: Int,
  isTrashing: Boolean,
  onKeepNewestAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val reclaimableText = rememberFormattedByteSize(reclaimableBytes)

  GlassSurface(
    cornerRadius = PrismDimens.EmptyCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    accentTint = colorScheme.primary,
    modifier = modifier.testTag("dup_summary_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceLarge),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMediumLarge),
      ) {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(colorScheme.primary.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = colorScheme.primary,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.dup_reclaimable_banner, reclaimableText),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.testTag("dup_reclaimable_text"),
          )
          Text(
            text =
              pluralStringResource(
                R.plurals.dup_groups_summary,
                groupCount,
                groupCount,
                extraCopiesCount,
              ),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = stringResource(R.string.dup_bin_notice),
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )

      PrimaryGlassButton(
        label = stringResource(R.string.dup_keep_newest_action),
        onClick = { if (!isTrashing) onKeepNewestAll() },
        modifier =
          Modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (isTrashing) 0.6f else 1f }
            .testTag("dup_keep_newest_all_button"),
      )
    }
  }
}

@Composable
private fun DuplicateGroupCard(
  group: DuplicateFileGroup,
  thumbnailPipeline: DocumentThumbnailPipeline,
  isBusy: Boolean,
  onKeepNewestInGroup: () -> Unit,
  onOpenDocument: (IndexedDocumentEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val sizeLabel = rememberFormattedByteSize(group.sizeBytes)
  val reclaimableLabel = rememberFormattedByteSize(group.reclaimableBytes)

  GlassSurface(
    cornerRadius = PrismDimens.BannerCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    modifier = modifier.testTag("dup_group_${group.groupKey}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = PrismDimens.SpaceStandard)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PrismDimens.SpaceSection),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "$sizeLabel each • Free $reclaimableLabel",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "SHA-256 ${group.contentHash.take(12)}…",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
          )
        }
        TextButton(
          onClick = onKeepNewestInGroup,
          enabled = !isBusy && group.duplicatesToTrash.isNotEmpty(),
          modifier = Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin),
        ) {
          Text(text = stringResource(R.string.dup_group_keep_newest_group))
        }
      }

      Spacer(Modifier.height(PrismDimens.SpaceSmall))

      group.documents.forEachIndexed { idx, doc ->
        val isNewest = idx == 0
        val modified = rememberFormattedModifiedTimestamp(doc.lastModifiedEpochMs)
        if (idx > 0) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = PrismDimens.SpaceSection)
                .height(PrismDimens.HairlineWidth)
                .background(tokens.rowDivider)
          )
        }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
              .clickable(role = Role.Button) { onOpenDocument(doc) }
              .padding(horizontal = PrismDimens.SpaceSection, vertical = PrismDimens.SpaceMediumLarge)
              .testTag("dup_item_${doc.id}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
        ) {
          DocumentThumbnailView(
            request = doc.toThumbnailRequest(targetSizePx = 192),
            pipeline = thumbnailPipeline,
            cornerRadius = 12.dp,
            compact = true,
            showCornerBadge = false,
            modifier = Modifier.size(52.dp),
          )

          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
            ) {
              Text(
                text = doc.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              Box(
                modifier =
                  Modifier.clip(CircleShape)
                    .background(
                      if (isNewest) tokens.successAccent.copy(alpha = 0.16f)
                      else tokens.destructiveAccent.copy(alpha = 0.14f)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
              ) {
                Text(
                  text =
                    stringResource(
                      if (isNewest) R.string.dup_keep_badge else R.string.dup_trash_badge
                    ),
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isNewest) tokens.successAccent else tokens.destructiveAccent,
                )
              }
            }
            Text(
              text = "${rememberFormattedByteSize(doc.sizeBytes)} • $modified",
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
            Text(
              text = doc.parentPath,
              style = MaterialTheme.typography.labelSmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Icon(
            imageVector = if (isNewest) Icons.Rounded.CheckCircle else Icons.Rounded.DeleteOutline,
            contentDescription = null,
            tint = if (isNewest) tokens.successAccent else colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun DuplicateEmptyStateCard(
  wasCancelled: Boolean,
  onScanNow: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  GlassSurface(
    cornerRadius = PrismDimens.EmptyCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    modifier = modifier.testTag("dup_empty_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceXLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
    ) {
      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.FileCopy,
          contentDescription = null,
          tint = colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
      }
      Text(
        text = stringResource(if (wasCancelled) R.string.dup_cancelled_notice else R.string.dup_empty_title),
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(R.string.dup_empty_body),
        style = MaterialTheme.typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      PrimaryGlassButton(
        label = stringResource(R.string.dup_scan_now),
        onClick = onScanNow,
        modifier = Modifier.testTag("dup_scan_now_button"),
      )
    }
  }
}
