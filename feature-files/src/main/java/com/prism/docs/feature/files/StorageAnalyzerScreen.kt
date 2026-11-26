package com.prism.docs.feature.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.LocalGlassBackdrop
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.ScrollEdgeFade
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.glassContentCaptureSource
import com.prism.docs.core.ui.glass.rememberHeaderScrollProgress
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.glass.rememberScreenGlassBackdropState
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class ArcSliceGeometry(
  val segment: StorageRingSegment,
  val startAngleDeg: Float,
  val sweepAngleDeg: Float,
)

/**
 * Computes non-overlapping arc sweeps summing strictly to `360f - gapDeg * count` while ensuring
 * every non-empty category stays visually legible (`minSweepDeg`).
 */
internal fun computeNormalizedArcSlices(
  segments: List<StorageRingSegment>,
  gapDeg: Float = 3.5f,
  minSweepDeg: Float = 6f,
): List<ArcSliceGeometry> {
  if (segments.isEmpty()) return emptyList()
  if (segments.size == 1) {
    return listOf(ArcSliceGeometry(segments.first(), startAngleDeg = -90f, sweepAngleDeg = 360f))
  }

  val count = segments.size
  val usableSweep = (360f - gapDeg * count).coerceAtLeast(count * minSweepDeg)
  val rawSweeps = FloatArray(count) { i -> segments[i].fraction.coerceAtLeast(0f) * usableSweep }

  // Iteratively enforce minSweepDeg while reclaiming excess strictly from larger slices so sum == usableSweep
  val locked = BooleanArray(count)
  repeat(count) {
    var deficit = 0f
    var freeTotal = 0f
    for (i in 0 until count) {
      if (!locked[i] && rawSweeps[i] < minSweepDeg) {
        deficit += (minSweepDeg - rawSweeps[i])
        rawSweeps[i] = minSweepDeg
        locked[i] = true
      }
    }
    if (deficit <= 0.001f) return@repeat
    for (i in 0 until count) {
      if (!locked[i]) freeTotal += (rawSweeps[i] - minSweepDeg).coerceAtLeast(0f)
    }
    if (freeTotal > 0.001f) {
      for (i in 0 until count) {
        if (!locked[i]) {
          val reducible = (rawSweeps[i] - minSweepDeg).coerceAtLeast(0f)
          rawSweeps[i] -= deficit * (reducible / freeTotal)
        }
      }
    }
  }

  val sum = rawSweeps.sum().coerceAtLeast(1f)
  val scale = usableSweep / sum
  val result = ArrayList<ArcSliceGeometry>(count)
  var cursor = -90f
  for (i in 0 until count) {
    val sweep = (rawSweeps[i] * scale).coerceAtLeast(1f)
    result += ArcSliceGeometry(segments[i], startAngleDeg = cursor, sweepAngleDeg = sweep)
    cursor += sweep + gapDeg
  }
  return result
}

private fun StorageRingBucket.iconVector(): ImageVector =
  when (this) {
    StorageRingBucket.PDF -> Icons.Rounded.PictureAsPdf
    StorageRingBucket.WORD -> Icons.Rounded.Description
    StorageRingBucket.EXCEL -> Icons.Rounded.TableChart
    StorageRingBucket.POWERPOINT -> Icons.Rounded.Slideshow
    StorageRingBucket.IMAGES -> Icons.Rounded.Image
    StorageRingBucket.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
  }

private fun formatReadableStoragePath(rawPath: String): String {
  val trimmed = rawPath.trimEnd('/')
  if (trimmed.isBlank()) return "Internal Storage"
  val cleaned =
    when {
      trimmed == "/storage/emulated/0" -> "Internal Storage"
      trimmed.startsWith("/storage/emulated/0/") ->
        "Internal / " + trimmed.removePrefix("/storage/emulated/0/")
      trimmed.startsWith("/storage/") -> trimmed.removePrefix("/storage/")
      else -> trimmed
    }
  val parts = cleaned.split('/').filter { it.isNotBlank() }
  return if (parts.size > 3) {
    "${parts.first()} / … / ${parts.takeLast(2).joinToString(" / ")}"
  } else {
    parts.joinToString(" / ")
  }
}

@Composable
fun StorageAnalyzerScreen(
  onNavigateBack: () -> Unit,
  onFilterLibraryByCategory: (category: DocumentCategory?, otherOnly: Boolean) -> Unit,
  onOpenDocument: (IncomingDocumentRequest) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: StorageAnalyzerViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val screenBackdropState = rememberScreenGlassBackdropState()
  val colorScheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val haptics = rememberPrismHaptics()
  val listState = rememberLazyListState()
  val scrollProgress = rememberHeaderScrollProgress(listState)
  val isScrolledUnder by remember { derivedStateOf { scrollProgress.value > 0.02f } }

  val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  var topBarHeightPx by remember { mutableIntStateOf(0) }
  val topBarHeightDp =
    if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() }
    else statusBarTop + PrismDimens.HeaderBarHeight + 16.dp

  val totalBytesText = rememberFormattedByteSize(uiState.totalBytes)

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      contentPadding =
        PaddingValues(
          top = topBarHeightDp + 10.dp,
          bottom = navBarBottom + 96.dp,
          start = PrismDimens.ScreenHorizontalPadding,
          end = PrismDimens.ScreenHorizontalPadding,
        ),
      verticalArrangement = Arrangement.spacedBy(0.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
        Modifier.fillMaxSize()
          .glassContentCaptureSource(screenBackdropState)
          .testTag("storage_analyzer_list"),
    ) {
      // Product Priority: Default to Documents only with toggle for All indexed files
      item(key = "analyzer_scope_toggle", contentType = "scope_toggle") {
        GlassSegmentedControl(
          options =
            listOf(
              stringResource(R.string.analyzer_scope_documents),
              stringResource(R.string.analyzer_scope_all_files),
            ),
          selectedIndex = if (uiState.includeAllIndexedFiles) 1 else 0,
          onSelect = { idx -> viewModel.setIncludeAllIndexedFiles(idx == 1) },
          modifier =
            Modifier.widthIn(max = PrismDimens.ContentMaxWidth)
              .fillMaxWidth()
              .testTag("analyzer_scope_segmented"),
        )
        Spacer(Modifier.height(PrismDimens.SpaceSection))
      }

      if (uiState.totalDocumentCount == 0 && uiState.largestFiles.isEmpty()) {
        item(key = "analyzer_empty", contentType = "empty_card") {
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
              Box(
                modifier =
                  Modifier.size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.DonutLarge,
                  contentDescription = null,
                  tint = colorScheme.primary,
                  modifier = Modifier.size(32.dp),
                )
              }
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
        // 1. Hero Ring + Proportional Stacked Bar Card
        item(key = "analyzer_ring_card", contentType = "ring_hero") {
          StorageRingHeroCard(
            includeAllIndexedFiles = uiState.includeAllIndexedFiles,
            totalBytes = uiState.totalBytes,
            totalDocuments = uiState.totalDocumentCount,
            segments = uiState.segments,
            onSegmentTap = { segment ->
              onFilterLibraryByCategory(segment.bucket.filterCategory, segment.bucket.isOtherBucket)
            },
            modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
          )
          Spacer(Modifier.height(PrismDimens.SpaceLarge))
        }

        // 2. "By file type" Category Breakdown Card (Job 5 screenshot fix)
        if (uiState.segments.isNotEmpty()) {
          item(key = "analyzer_categories_header", contentType = "section_header") {
            AnalyzerSectionHeader(
              title = stringResource(R.string.analyzer_by_file_type_title),
              subtitle = stringResource(R.string.analyzer_tap_hint),
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
            Spacer(Modifier.height(PrismDimens.SpaceMedium))
          }

          itemsIndexed(
            items = uiState.segments,
            key = { _, seg -> "seg_${seg.bucket.name}" },
            contentType = { _, _ -> "category_slice_row" },
          ) { idx, seg ->
            CategoryBreakdownSliceRow(
              segment = seg,
              includeAllIndexedFiles = uiState.includeAllIndexedFiles,
              isFirst = idx == 0,
              isLast = idx == uiState.segments.lastIndex,
              onClick = {
                haptics.segmentTick()
                onFilterLibraryByCategory(seg.bucket.filterCategory, seg.bucket.isOtherBucket)
              },
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }

          item(key = "analyzer_categories_spacer", contentType = "spacer") {
            Spacer(Modifier.height(PrismDimens.SpaceLarge))
          }
        }

        // 3. Biggest Folders
        if (uiState.biggestFolders.isNotEmpty()) {
          item(key = "analyzer_folders_header", contentType = "section_header") {
            AnalyzerSectionHeader(
              title = stringResource(R.string.analyzer_biggest_folders_title),
              subtitle = null,
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
            Spacer(Modifier.height(PrismDimens.SpaceMedium))
          }

          val maxFolderBytes =
            uiState.biggestFolders.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L
          itemsIndexed(
            items = uiState.biggestFolders,
            key = { idx, f -> "folder_${idx}_${f.parentPath}" },
            contentType = { _, _ -> "folder_slice_row" },
          ) { idx, folder ->
            FolderStatSliceRow(
              folder = folder,
              maxFolderBytes = maxFolderBytes,
              isFirst = idx == 0,
              isLast = idx == uiState.biggestFolders.lastIndex,
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
          }

          item(key = "analyzer_folders_spacer", contentType = "spacer") {
            Spacer(Modifier.height(PrismDimens.SpaceLarge))
          }
        }

        // 4. Largest Files
        if (uiState.largestFiles.isNotEmpty()) {
          item(key = "analyzer_largest_header", contentType = "section_header") {
            AnalyzerSectionHeader(
              title = stringResource(R.string.analyzer_largest_files_title),
              subtitle = null,
              modifier = Modifier.widthIn(max = PrismDimens.ContentMaxWidth).fillMaxWidth(),
            )
            Spacer(Modifier.height(PrismDimens.SpaceMedium))
          }

          itemsIndexed(
            items = uiState.largestFiles,
            key = { _, doc -> "largest_${doc.id}" },
            contentType = { _, _ -> "largest_file_row" },
          ) { idx, doc ->
            LargestFileSliceRow(
              rank = idx + 1,
              document = doc,
              thumbnailPipeline = viewModel.thumbnailPipeline,
              isFirst = idx == 0,
              isLast = idx == uiState.largestFiles.lastIndex,
              onClick = {
                haptics.tap()
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
      height = navBarBottom + 72.dp,
      modifier = Modifier.align(Alignment.BottomCenter),
    )

    // Floating Liquid Glass Top Bar sampling screen content + blobs via screenBackdropState
    CompositionLocalProvider(LocalGlassBackdrop provides screenBackdropState) {
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            IconButton(
              onClick = {
                haptics.tap()
                onNavigateBack()
              },
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
            AnimatedVisibility(
              visible = isScrolledUnder && uiState.totalDocumentCount > 0,
              enter = fadeIn(tween(180)),
              exit = fadeOut(tween(120)),
            ) {
              Box(
                modifier =
                  Modifier.padding(end = 6.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
              ) {
                Text(
                  text = totalBytesText,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = colorScheme.primary,
                  maxLines = 1,
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
private fun AnalyzerSectionHeader(
  title: String,
  subtitle: String?,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(
    modifier = modifier.padding(horizontal = 4.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = title,
      style =
        MaterialTheme.typography.titleLarge.copy(
          fontSize = PrismDimens.FontSizeSectionTitle,
          fontWeight = FontWeight.ExtraBold,
        ),
      color = colorScheme.onSurface,
      modifier = Modifier.semantics { heading() },
    )
    if (subtitle != null) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StorageRingHeroCard(
  includeAllIndexedFiles: Boolean,
  totalBytes: Long,
  totalDocuments: Int,
  segments: List<StorageRingSegment>,
  onSegmentTap: (StorageRingSegment) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()

  var revealTarget by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
  LaunchedEffect(segments) { revealTarget = 1f }
  val sweepReveal by
    animateFloatAsState(
      targetValue = revealTarget,
      animationSpec = tween(durationMillis = if (motionEnabled) 560 else 0),
      label = "storage_ring_reveal",
    )

  val arcSlices = remember(segments) { computeNormalizedArcSlices(segments) }

  val totalBytesText = rememberFormattedByteSize(totalBytes)
  val totalCountFormatted = remember(totalDocuments) { formatFileCount(totalDocuments) }
  val trackColor =
    if (tokens.isDark) Color.White.copy(alpha = 0.08f)
    else colorScheme.primary.copy(alpha = 0.09f)
  val dividerColor =
    if (tokens.isDark) Color(0xFF0B0E1D).copy(alpha = 0.72f)
    else Color.White.copy(alpha = 0.78f)
  val innerGlowColor =
    if (tokens.isDark) colorScheme.primary.copy(alpha = 0.12f)
    else colorScheme.primary.copy(alpha = 0.06f)

  GlassSurface(
    cornerRadius = PrismDimens.EmptyCardCornerRadius,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    accentTint = colorScheme.primary.copy(alpha = if (tokens.isDark) 0.14f else 0.08f),
    modifier = modifier.testTag("analyzer_ring_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(PrismDimens.SpaceLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(PrismDimens.SpaceLarge),
    ) {
      // Top KPI summary row with comma-formatted count badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text =
              stringResource(
                if (includeAllIndexedFiles) R.string.analyzer_total_used_label
                else R.string.analyzer_total_documents_label
              ),
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurfaceVariant,
          )
          Text(
            text = totalBytesText,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = colorScheme.onSurface,
          )
        }
        Box(
          modifier =
            Modifier.clip(CircleShape)
              .background(colorScheme.primary.copy(alpha = if (tokens.isDark) 0.22f else 0.12f))
              .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Text(
            text =
              pluralStringResource(
                if (includeAllIndexedFiles) R.plurals.analyzer_file_count_formatted
                else R.plurals.analyzer_doc_count_formatted,
                totalDocuments,
                totalCountFormatted,
              ),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.primary,
          )
        }
      }

      // Interactive Luminous Liquid Glass Ring Chart (StrokeCap.Butt prevents overlapping caps)
      Box(
        modifier = Modifier.size(220.dp),
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
                    val rel = ((angleDeg + 90f) % 360f + 360f) % 360f
                    val hit =
                      arcSlices.firstOrNull { slice ->
                        val startRel = ((slice.startAngleDeg + 90f) % 360f + 360f) % 360f
                        rel >= startRel && rel <= startRel + slice.sweepAngleDeg
                      }
                    if (hit != null) {
                      haptics.segmentTick()
                      onSegmentTap(hit.segment)
                    }
                  }
                }
              }
              .testTag("analyzer_ring_canvas"),
        ) {
          if (size.width < 2f || size.height < 2f) return@Canvas
          val strokeWidth = 26.dp.toPx()
          val diameter = (minOf(size.width, size.height) - strokeWidth - 6.dp.toPx()).coerceAtLeast(2f)
          val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
          val arcSize = Size(diameter, diameter)
          val glowRadius = (diameter * 0.44f).coerceAtLeast(1f)

          // Soft inner halo
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(innerGlowColor, Color.Transparent),
                center = center,
                radius = glowRadius,
              ),
            radius = glowRadius,
            center = center,
          )

          // Frosted track ring
          drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
          )

          // Crisp non-overlapping category arcs
          for (slice in arcSlices) {
            drawArc(
              color = slice.segment.bucket.colorFor(tokens.isDark),
              startAngle = slice.startAngleDeg,
              sweepAngle = (slice.sweepAngleDeg * sweepReveal).coerceAtLeast(1f),
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = totalCountFormatted,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.analyzer_ring_center_label),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
          )
        }
      }

      // True proportional stacked horizontal bar (replaces dot-plus-bar legend)
      if (segments.isNotEmpty()) {
        Canvas(
          modifier =
            Modifier.fillMaxWidth()
              .height(10.dp)
              .clip(CircleShape)
              .background(trackColor)
              .testTag("analyzer_stacked_bar"),
        ) {
          val w = size.width
          val h = size.height
          if (w <= 0f || h <= 0f) return@Canvas
          val dividerPx = 1.5.dp.toPx()
          val minSliverPx = 4.dp.toPx().coerceAtMost(w / segments.size.coerceAtLeast(1))
          val totalGaps = dividerPx * (segments.size - 1).coerceAtLeast(0)
          val availW = (w - totalGaps).coerceAtLeast(1f)

          // Compute normalized widths honoring minSliverPx
          val widths = FloatArray(segments.size) { i ->
            (segments[i].fraction.coerceAtLeast(0f) * availW)
          }
          val locked = BooleanArray(segments.size)
          repeat(segments.size) {
            var deficit = 0f
            var free = 0f
            for (i in widths.indices) {
              if (!locked[i] && widths[i] < minSliverPx) {
                deficit += minSliverPx - widths[i]
                widths[i] = minSliverPx
                locked[i] = true
              }
            }
            if (deficit <= 0.01f) return@repeat
            for (i in widths.indices) {
              if (!locked[i]) free += (widths[i] - minSliverPx).coerceAtLeast(0f)
            }
            if (free > 0.01f) {
              for (i in widths.indices) {
                if (!locked[i]) {
                  val reducible = (widths[i] - minSliverPx).coerceAtLeast(0f)
                  widths[i] -= deficit * (reducible / free)
                }
              }
            }
          }

          val scale = (availW / widths.sum().coerceAtLeast(1f)) * sweepReveal
          var x = 0f
          for (i in segments.indices) {
            val segW = (widths[i] * scale).coerceAtLeast(1f)
            drawRect(
              color = segments[i].bucket.colorFor(tokens.isDark),
              topLeft = Offset(x, 0f),
              size = Size(segW, h),
            )
            x += segW
            if (i < segments.lastIndex) {
              drawRect(
                color = dividerColor,
                topLeft = Offset(x, 0f),
                size = Size(dividerPx, h),
              )
              x += dividerPx
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CategoryBreakdownSliceRow(
  segment: StorageRingSegment,
  includeAllIndexedFiles: Boolean,
  isFirst: Boolean,
  isLast: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val label = stringResource(segment.bucket.labelRes)
  val segmentColor = segment.bucket.colorFor(tokens.isDark)
  val sizeText = rememberFormattedByteSize(segment.totalBytes)
  val pctInt = (segment.fraction * 100f).roundToInt().coerceIn(0, 100)
  val pctText =
    if (segment.totalBytes > 0L && pctInt == 0) {
      stringResource(R.string.analyzer_less_than_one_pct)
    } else {
      "$pctInt%"
    }
  val countFormatted = remember(segment.itemCount) { formatFileCount(segment.itemCount) }
  val cd = stringResource(R.string.analyzer_segment_cd, label, sizeText)
  val trackColor =
    if (tokens.isDark) Color.White.copy(alpha = 0.08f)
    else colorScheme.onSurface.copy(alpha = 0.07f)

  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = PrismDimens.ListCardCornerRadius,
    modifier =
      modifier
        .glassClickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = cd }
        .testTag("analyzer_segment_${segment.bucket.name}"),
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
        Box(
          modifier =
            Modifier.size(42.dp)
              .clip(RoundedCornerShape(13.dp))
              .background(segmentColor.copy(alpha = if (tokens.isDark) 0.22f else 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = segment.bucket.iconVector(),
            contentDescription = null,
            tint = segmentColor,
            modifier = Modifier.size(21.dp),
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(
              text = sizeText,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = colorScheme.onSurface,
              maxLines = 1,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.weight(1f)
                  .height(6.dp)
                  .clip(CircleShape)
                  .background(trackColor),
            ) {
              Box(
                modifier =
                  Modifier.fillMaxWidth(segment.fraction.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(segmentColor),
              )
            }
            Text(
              text = pctText,
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = segmentColor,
              maxLines = 1,
            )
          }

          Text(
            text =
              pluralStringResource(
                if (includeAllIndexedFiles) R.plurals.analyzer_file_count_formatted
                else R.plurals.analyzer_doc_count_formatted,
                segment.itemCount,
                countFormatted,
              ),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }

        Icon(
          imageVector = Icons.Rounded.ChevronRight,
          contentDescription = null,
          tint = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
          modifier = Modifier.size(20.dp),
        )
      }

      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = PrismDimens.SpaceSection)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider),
        )
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
  val folderName =
    remember(folder.parentPath) {
      folder.parentPath.trimEnd('/').substringAfterLast('/').ifBlank { "Internal Storage" }
    }
  val readablePath = remember(folder.parentPath) { formatReadableStoragePath(folder.parentPath) }
  val sizeText = rememberFormattedByteSize(folder.totalBytes)
  val progress = (folder.totalBytes.toFloat() / maxFolderBytes.toFloat()).coerceIn(0.05f, 1f)
  val trackColor =
    if (tokens.isDark) Color.White.copy(alpha = 0.08f)
    else colorScheme.onSurface.copy(alpha = 0.07f)

  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = PrismDimens.ListCardCornerRadius,
    modifier = modifier.testTag("analyzer_folder_${folderName}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = PrismDimens.SpaceSection, vertical = PrismDimens.SpaceRow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceStandard),
      ) {
        Box(
          modifier =
            Modifier.size(42.dp)
              .clip(RoundedCornerShape(13.dp))
              .background(tokens.warningAccent.copy(alpha = if (tokens.isDark) 0.22f else 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = tokens.warningAccent,
            modifier = Modifier.size(22.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = folderName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Box(
              modifier =
                Modifier.clip(CircleShape)
                  .background(colorScheme.primary.copy(alpha = if (tokens.isDark) 0.20f else 0.10f))
                  .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
              Text(
                text = sizeText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.primary,
                maxLines = 1,
              )
            }
          }
          Text(
            text =
              pluralStringResource(
                R.plurals.analyzer_folder_subtitle,
                folder.itemCount,
                folder.itemCount,
                readablePath,
              ),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(trackColor),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth(progress)
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(
                    Brush.horizontalGradient(
                      colors = listOf(tokens.warningAccent, colorScheme.primary),
                    )
                  ),
            )
          }
        }
      }
      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = PrismDimens.SpaceSection)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider),
        )
      }
    }
  }
}

@Composable
private fun LargestFileSliceRow(
  rank: Int,
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
  val readableParent =
    remember(document.parentPath) { formatReadableStoragePath(document.parentPath) }

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
        Box {
          DocumentThumbnailView(
            request = document.toThumbnailRequest(targetSizePx = 160),
            pipeline = thumbnailPipeline,
            cornerRadius = 12.dp,
            compact = true,
            showCornerBadge = false,
            modifier = Modifier.size(48.dp),
          )
          if (rank <= 3) {
            Box(
              modifier =
                Modifier.align(Alignment.TopStart)
                  .clip(RoundedCornerShape(bottomEnd = 8.dp, topStart = 10.dp))
                  .background(colorScheme.primary)
                  .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
              Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = colorScheme.onPrimary,
              )
            }
          }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = document.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
              text = readableParent,
              style = MaterialTheme.typography.bodySmall,
              color = colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        Box(
          modifier =
            Modifier.clip(CircleShape)
              .background(colorScheme.primary.copy(alpha = if (tokens.isDark) 0.20f else 0.10f))
              .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
          Text(
            text = sizeText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.primary,
            maxLines = 1,
          )
        }
      }
      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = PrismDimens.SpaceSection)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider),
        )
      }
    }
  }
}
