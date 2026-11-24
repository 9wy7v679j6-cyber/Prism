package com.prism.docs.feature.files

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.docs.core.data.db.FolderStorageStatRow
import com.prism.docs.core.data.db.IndexedDocumentEntity
import com.prism.docs.core.data.model.DocumentCategory
import com.prism.docs.core.data.model.IncomingDocumentRequest
import com.prism.docs.core.data.thumbnail.DocumentThumbnailPipeline
import com.prism.docs.core.ui.glass.ConnectedGlassCardSlice
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import dev.chrisbanes.haze.hazeSource
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class ArcSliceGeometry(
  val segment: StorageRingSegment,
  val startAngleDeg: Float,
  val sweepAngleDeg: Float,
)

@Composable
fun StorageAnalyzerScreen(
  onNavigateBack: () -> Unit,
  onFilterLibraryByCategory: (category: DocumentCategory?, otherOnly: Boolean) -> Unit,
  onOpenDocument: (IncomingDocumentRequest) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: StorageAnalyzerViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val hazeState = LocalHazeState.current
  val colorScheme = MaterialTheme.colorScheme
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

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      contentPadding =
        PaddingValues(
          top = topBarHeightDp + 8.dp,
          bottom = navBarBottom + 96.dp,
          start = PrismDimens.ScreenHorizontalPadding,
          end = PrismDimens.ScreenHorizontalPadding,
        ),
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier)
          .testTag("storage_analyzer_list"),
    ) {
      if (uiState.totalDocumentCount == 0 && uiState.largestFiles.isEmpty()) {
        item(key = "analyzer_empty") {
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
              Icon(
                imageVector = Icons.Rounded.DonutLarge,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(44.dp),
              )
              Text(
                text = stringResource(R.string.analyzer_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
              )
              Text(
                text = stringResource(R.string.analyzer_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      } else {
        item(key = "analyzer_ring_card") {
          StorageRingSummaryCard(
            totalBytes = uiState.totalBytes,
            totalDocuments = uiState.totalDocumentCount,
            segments = uiState.segments,
            onSegmentTap = { segment ->
              onFilterLibraryByCategory(segment.bucket.filterCategory, segment.bucket.isOtherBucket)
            },
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
        }

        if (uiState.biggestFolders.isNotEmpty()) {
          item(key = "analyzer_folders_header") {
            Text(
              text = stringResource(R.string.analyzer_biggest_folders_title),
              style = MaterialTheme.typography.titleLarge,
              color = colorScheme.onSurface,
              modifier =
                Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
                  .fillMaxWidth()
                  .semantics { heading() },
            )
          }

          val maxFolderBytes = uiState.biggestFolders.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
          itemsIndexed(uiState.biggestFolders, key = { idx, f -> "folder_${idx}_${f.parentPath}" }) { idx, folder ->
            FolderStatSliceRow(
              folder = folder,
              maxFolderBytes = maxFolderBytes,
              isFirst = idx == 0,
              isLast = idx == uiState.biggestFolders.lastIndex,
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }
        }

        if (uiState.largestFiles.isNotEmpty()) {
          item(key = "analyzer_largest_header") {
            Text(
              text = stringResource(R.string.analyzer_largest_files_title),
              style = MaterialTheme.typography.titleLarge,
              color = colorScheme.onSurface,
              modifier =
                Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
                  .fillMaxWidth()
                  .padding(top = PrismDimens.SpaceMedium)
                  .semantics { heading() },
            )
          }

          itemsIndexed(uiState.largestFiles, key = { _, doc -> "largest_${doc.id}" }) { idx, doc ->
            LargestFileSliceRow(
              document = doc,
              thumbnailPipeline = viewModel.thumbnailPipeline,
              isFirst = idx == 0,
              isLast = idx == uiState.largestFiles.lastIndex,
              onClick = {
                onOpenDocument(
                  IncomingDocumentRequest(
                    uriString = doc.uriString,
                    displayName = doc.displayName,
                    mimeType = doc.mimeType,
                    sizeBytes = doc.sizeBytes,
                    sourceAction = "ANALYZER",
                  )
                )
              },
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

    // Floating Glass Top Bar
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
        modifier =
          Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
            .fillMaxWidth()
            .height(PrismDimens.HeaderBarHeight),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(PrismDimens.TouchTargetMin).testTag("analyzer_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.dup_back_cd),
              tint = colorScheme.onSurface,
            )
          }
          Text(
            text = stringResource(R.string.analyzer_title),
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageRingSummaryCard(
  totalBytes: Long,
  totalDocuments: Int,
  segments: List<StorageRingSegment>,
  onSegmentTap: (StorageRingSegment) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current

  var revealTarget by remember { mutableStateOf(0f) }
  LaunchedEffect(segments) { revealTarget = 1f }
  val sweepReveal by animateFloatAsState(
    targetValue = revealTarget,
    animationSpec = tween(durationMillis = if (motionEnabled) 520 else 0),
    label = "storage_ring_reveal",
  )

  val arcSlices =
    remember(segments) {
      val list = mutableListOf<ArcSliceGeometry>()
      val count = segments.size
      val gapDeg = if (count > 1) 4f else 0f
      val usableSweep = 360f - gapDeg * count
      var cursor = -90f
      for (seg in segments) {
        val rawSweep = (seg.fraction * usableSweep).coerceAtLeast(if (count == 1) 360f else 8f)
        list += ArcSliceGeometry(seg, cursor, rawSweep)
        cursor += rawSweep + gapDeg
      }
      list
    }

  val totalBytesText = rememberFormattedByteSize(totalBytes)
  val totalCountFormatted = remember(totalDocuments) { formatFileCount(totalDocuments) }
  val trackColor =
    if (tokens.isDark) Color.White.copy(alpha = 0.08f)
    else colorScheme.primary.copy(alpha = 0.10f)

  GlassSurface(
    cornerRadius = PrismDimens.EmptyCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    accentTint = colorScheme.primary,
    modifier = modifier.testTag("analyzer_ring_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSection),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = stringResource(R.string.analyzer_total_used_label),
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        Text(
          text =
            pluralStringResource(
              R.plurals.analyzer_indexed_count,
              totalDocuments,
              totalBytesText,
              totalCountFormatted,
            ),
          style = MaterialTheme.typography.titleLarge,
          color = colorScheme.onSurface,
        )
      }

      Box(
        modifier = Modifier.size(216.dp),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(
          modifier =
            Modifier.fillMaxSize()
              .pointerInput(arcSlices) {
                detectTapGestures { tap ->
                  val cx = size.width / 2f
                  val cy = size.height / 2f
                  val dx = tap.x - cx
                  val dy = tap.y - cy
                  val outerRadius = minOf(size.width, size.height) / 2f
                  val dist = hypot(dx, dy)
                  if (dist in (outerRadius * 0.28f)..outerRadius) {
                    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    // Normalize clockwise from -90 deg
                    val rel = ((angleDeg + 90f) % 360f + 360f) % 360f
                    val hit =
                      arcSlices.firstOrNull { slice ->
                        val startRel = ((slice.startAngleDeg + 90f) % 360f + 360f) % 360f
                        rel >= startRel && rel <= startRel + slice.sweepAngleDeg
                      }
                    if (hit != null) {
                      onSegmentTap(hit.segment)
                    }
                  }
                }
              }
              .testTag("analyzer_ring_canvas")
        ) {
          val strokeWidth = 28.dp.toPx()
          val diameter = minOf(size.width, size.height) - strokeWidth
          val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
          val arcSize = Size(diameter, diameter)

          drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
          )

          for (slice in arcSlices) {
            drawArc(
              color = slice.segment.bucket.colorFor(tokens.isDark),
              startAngle = slice.startAngleDeg,
              sweepAngle = (slice.sweepAngleDeg * sweepReveal).coerceAtLeast(1f),
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
          }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = totalBytesText,
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.analyzer_ring_center_label),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = stringResource(R.string.analyzer_tap_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
        verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
      ) {
        segments.forEach { segment ->
          val label = stringResource(segment.bucket.labelRes)
          val segmentColor = segment.bucket.colorFor(tokens.isDark)
          val sizeText = rememberFormattedByteSize(segment.totalBytes)
          val pct = (segment.fraction * 100f).roundToInt().coerceIn(0, 100)
          val cd = stringResource(R.string.analyzer_segment_cd, label, sizeText)

          GlassSurface(
            cornerRadius = 16.dp,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            accentTint = segmentColor.copy(alpha = if (tokens.isDark) 0.22f else 0.16f),
            modifier =
              Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
                .clip(RoundedCornerShape(16.dp))
                .glassClickable(role = Role.Button, onClick = { onSegmentTap(segment) })
                .semantics { contentDescription = cd }
                .testTag("analyzer_segment_${segment.bucket.name}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(segmentColor)
              )
              Column {
                Text(
                  text = "$label ($pct%)",
                  style = MaterialTheme.typography.labelLarge,
                  color = colorScheme.onSurface,
                )
                Text(
                  text = "$sizeText • ${segment.itemCount}",
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
private fun FolderStatSliceRow(
  folder: FolderStorageStatRow,
  maxFolderBytes: Long,
  isFirst: Boolean,
  isLast: Boolean,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val folderName = remember(folder.parentPath) {
    folder.parentPath.trimEnd('/').substringAfterLast('/').ifBlank { folder.parentPath }
  }
  val sizeText = rememberFormattedByteSize(folder.totalBytes)
  val progress = (folder.totalBytes.toFloat() / maxFolderBytes.toFloat()).coerceIn(0.04f, 1f)

  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = PrismDimens.ListCardCornerRadius,
    modifier = modifier.testTag("analyzer_folder_${folderName}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceSection),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      ) {
        Box(
          modifier =
            Modifier.size(42.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(tokens.warningAccent.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = tokens.warningAccent,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = folderName,
              style = MaterialTheme.typography.titleMedium,
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(
              text = sizeText,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = colorScheme.primary,
            )
          }
          Text(
            text =
              pluralStringResource(
                R.plurals.analyzer_folder_subtitle,
                folder.itemCount,
                folder.itemCount,
                folder.parentPath,
              ),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
          )
        }
      }
      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = PrismDimens.SpaceSection)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider)
        )
      }
    }
  }
}

@Composable
private fun LargestFileSliceRow(
  document: IndexedDocumentEntity,
  thumbnailPipeline: DocumentThumbnailPipeline,
  isFirst: Boolean,
  isLast: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val (badgeLabel, badgeColor) =
    remember(document.displayName, document.category) {
      resolveFileTypeBadge(document.displayName, document.resolvedCategory())
    }
  val sizeText = rememberFormattedByteSize(document.sizeBytes)

  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = PrismDimens.ListCardCornerRadius,
    modifier =
      modifier
        .glassClickable(role = Role.Button, onClick = onClick)
        .testTag("analyzer_largest_file_${document.id}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .padding(horizontal = PrismDimens.SpaceSection, vertical = PrismDimens.SpaceRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceRow),
      ) {
        DocumentThumbnailView(
          request = document.toThumbnailRequest(targetSizePx = 160),
          pipeline = thumbnailPipeline,
          cornerRadius = 12.dp,
          compact = true,
          showCornerBadge = false,
          modifier = Modifier.size(46.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
              text = document.parentPath,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Text(
          text = sizeText,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = colorScheme.primary,
        )
      }
      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = PrismDimens.SpaceSection)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider)
        )
      }
    }
  }
}
