package com.prism.docs.feature.viewer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun paperModeColorFilter(mode: ReaderPaperMode): ColorFilter? =
  when (mode) {
    ReaderPaperMode.LIGHT -> null
    ReaderPaperMode.SEPIA ->
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            0.96f, 0f, 0f, 0f, 0f,
            0f, 0.90f, 0f, 0f, 0f,
            0f, 0f, 0.78f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      )
    ReaderPaperMode.DARK ->
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            -0.88f, 0f, 0f, 0f, 232f,
            0f, -0.88f, 0f, 0f, 234f,
            0f, 0f, -0.85f, 0f, 240f,
            0f, 0f, 0f, 1f, 0f,
          )
        )
      )
  }

internal fun paperSurfaceColor(mode: ReaderPaperMode): Color =
  when (mode) {
    ReaderPaperMode.LIGHT -> Color.White
    ReaderPaperMode.SEPIA -> Color(0xFFF4ECD8)
    ReaderPaperMode.DARK -> Color(0xFF171A21)
  }

internal fun paperViewportGutterColor(mode: ReaderPaperMode): Color =
  when (mode) {
    ReaderPaperMode.LIGHT -> Color(0xFFECEFF4)
    ReaderPaperMode.SEPIA -> Color(0xFFE5DCC6)
    ReaderPaperMode.DARK -> Color(0xFF0C0E13)
  }

@OptIn(FlowPreview::class)
@Composable
internal fun PdfViewerCanvas(
  pdfController: PdfDocumentController,
  pageCount: Int,
  currentPage: Int,
  initialScrollOffsetFraction: Float,
  initialZoom: Float,
  scrollMode: PdfScrollMode,
  paperMode: ReaderPaperMode,
  searchMatches: List<PdfSearchMatch>,
  currentMatchIndex: Int,
  selection: TextSelectionRange?,
  currentPageHasTextLayer: Boolean?,
  topContentPadding: Dp,
  bottomContentPadding: Dp,
  nestedScrollConnection: NestedScrollConnection,
  jumpEvents: SharedFlow<ViewerJumpEvent>,
  onVisiblePageChanged: (Int, Float) -> Unit,
  onZoomSettled: (Float) -> Unit,
  onToggleChrome: () -> Unit,
  onLongPressSelectAt: (Int, Float, Float) -> Unit,
  onDragSelectionHandle: (Boolean, Int, Float, Float) -> Unit,
  onClearSelection: () -> Unit,
  onCopySelection: () -> Unit,
  onShareSelection: () -> Unit,
  onSearchSelection: () -> Unit,
  onScannedOcrChipClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (pageCount <= 0) return

  val density = LocalDensity.current
  val gutterColor = paperViewportGutterColor(paperMode)

  var zoom by remember(initialZoom) { mutableFloatStateOf(initialZoom.coerceIn(1f, 5f)) }
  var panOffset by remember { mutableStateOf(Offset.Zero) }
  var settledZoom by remember(initialZoom) { mutableFloatStateOf(initialZoom.coerceIn(1f, 5f)) }
  var settledPanOffset by remember { mutableStateOf(Offset.Zero) }

  val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, pageCount - 1))
  val pagerState = rememberPagerState(initialPage = currentPage.coerceIn(0, pageCount - 1), pageCount = { pageCount })

  var viewportSize by remember { mutableStateOf(IntSize.Zero) }

  // Debounce zoom & pan settle so sharp tile re-render triggers only after gesture settles
  LaunchedEffect(Unit) {
    snapshotFlow { Triple(zoom, panOffset.x, panOffset.y) }
      .debounce(170L)
      .collectLatest { (z, px, py) ->
        settledZoom = z
        settledPanOffset = Offset(px, py)
        onZoomSettled(z)
      }
  }

  // Group search matches per page only when searchMatches changes (zero per-frame allocation)
  val matchesByPage = remember(searchMatches) {
    searchMatches.withIndex().groupBy(
      keySelector = { it.value.pageIndex },
      valueTransform = { it.index to it.value },
    )
  }

  // Handle jump commands (from search hit, outline, bookmark, scrubber, thumbnail strip, or resume)
  LaunchedEffect(jumpEvents, scrollMode, pageCount) {
    jumpEvents.collectLatest { event ->
      when (event) {
        is ViewerJumpEvent.JumpToPage -> {
          val target = event.pageIndex.coerceIn(0, pageCount - 1)
          if (scrollMode == PdfScrollMode.CONTINUOUS) {
            val yFrac = event.yFraction ?: 0f
            val estPageHeightPx =
              if (viewportSize.width > 0) {
                (viewportSize.width / pdfController.aspectRatioForPage(target)).roundToInt()
              } else 0
            val offsetPx = (estPageHeightPx * yFrac * zoom).roundToInt().coerceAtLeast(0)
            listState.scrollToItem(target, scrollOffset = offsetPx)
          } else {
            pagerState.scrollToPage(target)
          }
        }
      }
    }
  }

  // Track visible page cleanly without causing unnecessary parent recompositions during fling
  LaunchedEffect(scrollMode, pageCount, listState, pagerState) {
    if (scrollMode == PdfScrollMode.CONTINUOUS) {
      snapshotFlow {
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (visible.isEmpty()) {
          listState.firstVisibleItemIndex to 0f
        } else {
          val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
          val best =
            visible.minByOrNull { item ->
              val itemMid = item.offset + item.size / 2
              abs(itemMid - center)
            } ?: visible.first()
          val offsetFrac =
            if (best.size > 0) {
              ((-best.offset).toFloat() / best.size.toFloat()).coerceIn(0f, 1f)
            } else 0f
          best.index to offsetFrac
        }
      }
        .distinctUntilChanged()
        .collectLatest { (idx, frac) ->
          onVisiblePageChanged(idx, frac)
        }
    } else {
      snapshotFlow { pagerState.settledPage }
        .distinctUntilChanged()
        .collectLatest { page ->
          onVisiblePageChanged(page, 0f)
        }
    }
  }

  // Restore exact intra-page scroll fraction on initial open
  var appliedInitialOffset by remember { mutableStateOf(false) }
  LaunchedEffect(viewportSize.width, initialScrollOffsetFraction) {
    if (!appliedInitialOffset && viewportSize.width > 0 && initialScrollOffsetFraction > 0.01f) {
      appliedInitialOffset = true
      if (scrollMode == PdfScrollMode.CONTINUOUS) {
        val estH = (viewportSize.width / pdfController.aspectRatioForPage(currentPage)).roundToInt()
        listState.scrollToItem(
          currentPage.coerceIn(0, pageCount - 1),
          scrollOffset = (estH * initialScrollOffsetFraction).roundToInt(),
        )
      }
    }
  }

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(gutterColor)
        .onGloballyPositioned { viewportSize = it.size }
        .pointerInput(Unit) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
              val event = awaitPointerEvent()
              val pointerCount = event.changes.count { it.pressed }
              if (pointerCount >= 2 || zoom > 1.03f) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)
                val oldZoom = zoom
                val newZoom = (oldZoom * zoomChange).coerceIn(1f, 5f)
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)

                if (pointerCount >= 2 || (abs(zoomChange - 1f) > 0.002f)) {
                  val focusX = if (centroid.x.isFinite()) centroid.x else w * 0.5f
                  val focusY = if (centroid.y.isFinite()) centroid.y else h * 0.5f
                  val adjustedPanX =
                    (panOffset.x - focusX) * (newZoom / oldZoom) + focusX + panChange.x
                  val adjustedPanY =
                    (panOffset.y - focusY) * (newZoom / oldZoom) + focusY + panChange.y
                  val maxPanX = (w * (newZoom - 1f)).coerceAtLeast(0f)
                  val maxPanY = (h * (newZoom - 1f) * 0.65f).coerceAtLeast(0f)
                  zoom = newZoom
                  panOffset =
                    if (newZoom <= 1.01f) {
                      Offset.Zero
                    } else {
                      Offset(
                        x = adjustedPanX.coerceIn(-maxPanX, 0f),
                        y = adjustedPanY.coerceIn(-maxPanY, maxPanY),
                      )
                    }
                  event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (zoom > 1.03f && pointerCount == 1) {
                  // When zoomed, allow horizontal pan and vertical pan cleanly
                  val maxPanX = (w * (zoom - 1f)).coerceAtLeast(0f)
                  val maxPanY = (h * (zoom - 1f) * 0.45f).coerceAtLeast(0f)
                  val candidateX = panOffset.x + panChange.x
                  val candidateY = panOffset.y + panChange.y
                  val clampedX = candidateX.coerceIn(-maxPanX, 0f)
                  val clampedY =
                    if (scrollMode == PdfScrollMode.PAGED) candidateY.coerceIn(-maxPanY, maxPanY)
                    else 0f
                  val consumedX = abs(clampedX - panOffset.x) > 0.5f
                  val consumedY = abs(clampedY - panOffset.y) > 0.5f
                  panOffset = Offset(clampedX, clampedY)
                  if (consumedX || (scrollMode == PdfScrollMode.PAGED && consumedY)) {
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                  }
                }
              }
            } while (event.changes.any { it.pressed })
          }
        }
        .testTag("viewer_document_canvas"),
  ) {
    val containerWidthPx = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(360) }
    val horizontalPad = if (maxWidth > 680.dp) 40.dp else 10.dp
    val pageWidthPx = with(density) { (maxWidth - horizontalPad * 2).toPx().roundToInt().coerceIn(300, 2200) }
    val pageTargetRenderPx = (pageWidthPx * density.density.coerceAtMost(2.5f)).roundToInt().coerceIn(480, 1800)

    val zoomedLayerModifier =
      Modifier.fillMaxSize().graphicsLayer {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = zoom
        scaleY = zoom
        translationX = panOffset.x
        translationY = if (scrollMode == PdfScrollMode.PAGED) panOffset.y else 0f
      }

    if (scrollMode == PdfScrollMode.CONTINUOUS) {
      LazyColumn(
        state = listState,
        contentPadding =
          PaddingValues(
            top = topContentPadding + 8.dp,
            bottom = bottomContentPadding + 16.dp,
            start = horizontalPad,
            end = horizontalPad,
          ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = zoomedLayerModifier.nestedScroll(nestedScrollConnection),
      ) {
        items(
          count = pageCount,
          key = { pageIdx -> "pdf_page_$pageIdx" },
          contentType = { "pdf_page" },
        ) { pageIdx ->
          PdfPageSurfaceItem(
            pdfController = pdfController,
            pageIndex = pageIdx,
            pageRenderWidthPx = pageTargetRenderPx,
            viewportWidthPx = containerWidthPx,
            viewportHeightPx = viewportSize.height,
            settledZoom = settledZoom,
            settledPanOffset = settledPanOffset,
            paperMode = paperMode,
            matches = matchesByPage[pageIdx].orEmpty(),
            currentGlobalMatchIndex = currentMatchIndex,
            selection = selection?.takeIf { it.pageIndex == pageIdx },
            onTapPage = {
              if (selection != null) onClearSelection() else onToggleChrome()
            },
            onDoubleTapPage = { tapOffset ->
              if (zoom > 1.15f) {
                zoom = 1f
                panOffset = Offset.Zero
              } else {
                val targetZoom = 2.4f
                val w = viewportSize.width.toFloat().coerceAtLeast(1f)
                val maxPanX = (w * (targetZoom - 1f)).coerceAtLeast(0f)
                val targetPanX = (-(tapOffset.x * (targetZoom - 1f))).coerceIn(-maxPanX, 0f)
                zoom = targetZoom
                panOffset = Offset(targetPanX, 0f)
              }
            },
            onLongPressAt = { xNorm, yNorm ->
              onLongPressSelectAt(pageIdx, xNorm, yNorm)
            },
            onDragSelectionHandle = { isStart, xNorm, yNorm ->
              onDragSelectionHandle(isStart, pageIdx, xNorm, yNorm)
            },
          )
        }
      }
    } else {
      HorizontalPager(
        state = pagerState,
        userScrollEnabled = zoom <= 1.05f,
        contentPadding = PaddingValues(horizontal = horizontalPad),
        pageSpacing = 12.dp,
        modifier = zoomedLayerModifier,
      ) { pageIdx ->
        Box(
          modifier =
            Modifier.fillMaxSize()
              .padding(top = topContentPadding + 8.dp, bottom = bottomContentPadding + 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          PdfPageSurfaceItem(
            pdfController = pdfController,
            pageIndex = pageIdx,
            pageRenderWidthPx = pageTargetRenderPx,
            viewportWidthPx = containerWidthPx,
            viewportHeightPx = viewportSize.height,
            settledZoom = settledZoom,
            settledPanOffset = settledPanOffset,
            paperMode = paperMode,
            matches = matchesByPage[pageIdx].orEmpty(),
            currentGlobalMatchIndex = currentMatchIndex,
            selection = selection?.takeIf { it.pageIndex == pageIdx },
            onTapPage = {
              if (selection != null) onClearSelection() else onToggleChrome()
            },
            onDoubleTapPage = { tapOffset ->
              if (zoom > 1.15f) {
                zoom = 1f
                panOffset = Offset.Zero
              } else {
                val targetZoom = 2.4f
                val w = viewportSize.width.toFloat().coerceAtLeast(1f)
                val h = viewportSize.height.toFloat().coerceAtLeast(1f)
                val maxPanX = (w * (targetZoom - 1f)).coerceAtLeast(0f)
                val maxPanY = (h * (targetZoom - 1f) * 0.45f).coerceAtLeast(0f)
                zoom = targetZoom
                panOffset =
                  Offset(
                    x = (-(tapOffset.x * (targetZoom - 1f))).coerceIn(-maxPanX, 0f),
                    y = (-(tapOffset.y * 0.35f)).coerceIn(-maxPanY, maxPanY),
                  )
              }
            },
            onLongPressAt = { xNorm, yNorm ->
              onLongPressSelectAt(pageIdx, xNorm, yNorm)
            },
            onDragSelectionHandle = { isStart, xNorm, yNorm ->
              onDragSelectionHandle(isStart, pageIdx, xNorm, yNorm)
            },
          )
        }
      }
    }

    // Scanned page stub chip (appears softly when current page lacks a text layer)
    AnimatedVisibility(
      visible = currentPageHasTextLayer == false && selection == null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier =
        Modifier.align(Alignment.BottomStart)
          .padding(start = 16.dp, bottom = bottomContentPadding + 14.dp),
    ) {
      GlassSurface(
        cornerRadius = 18.dp,
        shape = CircleShape,
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = true,
        enableRefraction = true,
        allowLiquidGlass = true,
        onClick = onScannedOcrChipClick,
        clickRole = Role.Button,
        modifier =
          Modifier.defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .testTag("viewer_scanned_ocr_chip"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.DocumentScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = stringResource(R.string.viewer_scanned_ocr_chip),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    // Floating selection toolbar (Copy / Share / Search)
    AnimatedVisibility(
      visible = selection != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(horizontal = 16.dp, vertical = bottomContentPadding + 16.dp),
    ) {
      GlassSurface(
        cornerRadius = 24.dp,
        shape = CircleShape,
        elevation = GlassElevation.FLOATING,
        useBackdropBlur = true,
        enableRefraction = true,
        allowLiquidGlass = true,
        modifier = Modifier.testTag("viewer_selection_toolbar"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SelectionToolbarActionButton(
            icon = Icons.Rounded.ContentCopy,
            label = stringResource(R.string.viewer_selection_copy),
            testTag = "viewer_selection_copy",
            onClick = onCopySelection,
          )
          SelectionToolbarActionButton(
            icon = Icons.Rounded.Share,
            label = stringResource(R.string.viewer_selection_share),
            testTag = "viewer_selection_share",
            onClick = onShareSelection,
          )
          SelectionToolbarActionButton(
            icon = Icons.Rounded.Search,
            label = stringResource(R.string.viewer_selection_search),
            testTag = "viewer_selection_search",
            onClick = onSearchSelection,
          )
        }
      }
    }
  }
}

@Composable
private fun SelectionToolbarActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
        .clip(CircleShape)
        .glassClickable(role = Role.Button, onClick = onClick)
        .testTag(testTag)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun PdfPageSurfaceItem(
  pdfController: PdfDocumentController,
  pageIndex: Int,
  pageRenderWidthPx: Int,
  viewportWidthPx: Int,
  viewportHeightPx: Int,
  settledZoom: Float,
  settledPanOffset: Offset,
  paperMode: ReaderPaperMode,
  matches: List<Pair<Int, PdfSearchMatch>>,
  currentGlobalMatchIndex: Int,
  selection: TextSelectionRange?,
  onTapPage: () -> Unit,
  onDoubleTapPage: (Offset) -> Unit,
  onLongPressAt: (Float, Float) -> Unit,
  onDragSelectionHandle: (Boolean, Float, Float) -> Unit,
) {
  val density = LocalDensity.current
  val paperBg = paperSurfaceColor(paperMode)
  val colorFilter = remember(paperMode) { paperModeColorFilter(paperMode) }

  var aspectRatio by remember(pageIndex) {
    mutableFloatStateOf(pdfController.aspectRatioForPage(pageIndex))
  }

  // Base display-scale bitmap — automatically cancelled when page scrolls out of LazyColumn
  val baseBitmap by
    produceState<Bitmap?>(
      initialValue = pdfController.peekCachedPage(pageIndex, pageRenderWidthPx),
      key1 = pageIndex,
      key2 = pageRenderWidthPx,
      key3 = pdfController,
    ) {
      val cached = pdfController.peekCachedPage(pageIndex, pageRenderWidthPx)
      if (cached != null) {
        value = cached
        aspectRatio = pdfController.aspectRatioForPage(pageIndex)
      } else {
        val rendered = pdfController.renderPage(pageIndex, pageRenderWidthPx)
        value = rendered
        aspectRatio = pdfController.aspectRatioForPage(pageIndex)
      }
    }

  // Sharper zoomed tile re-render when pinch-zoom settles > 1.25x
  val tileSpec =
    remember(settledZoom, settledPanOffset, viewportWidthPx, viewportHeightPx, pageIndex, aspectRatio) {
      if (settledZoom <= 1.25f || viewportWidthPx <= 0 || viewportHeightPx <= 0) {
        null
      } else {
        val leftFrac = ((-settledPanOffset.x) / (viewportWidthPx * settledZoom)).coerceIn(0f, 0.85f)
        val widthFrac = (1f / settledZoom).coerceIn(0.15f, 1f)
        val rightFrac = (leftFrac + widthFrac).coerceAtMost(1f)
        val tileW = (pageRenderWidthPx * settledZoom * widthFrac).roundToInt().coerceIn(360, 2000)
        val tileH = (tileW / aspectRatio / widthFrac).roundToInt().coerceIn(360, 2000)
        TileRenderSpec(
          scaleFactor = settledZoom,
          regionLeftFraction = leftFrac,
          regionTopFraction = 0f,
          regionRightFraction = rightFrac,
          regionBottomFraction = 1f,
          targetWidthPx = tileW,
          targetHeightPx = tileH,
        )
      }
    }

  val hiResBitmap by
    produceState<Bitmap?>(
      initialValue = null,
      key1 = pageIndex,
      key2 = tileSpec,
      key3 = pdfController,
    ) {
      value =
        if (tileSpec != null) {
          pdfController.renderPage(pageIndex, pageRenderWidthPx, tileSpec)
        } else null
    }

  val latestOnTap by rememberUpdatedState(onTapPage)
  val latestOnDoubleTap by rememberUpdatedState(onDoubleTapPage)
  val latestOnLongPress by rememberUpdatedState(onLongPressAt)
  val latestOnDragHandle by rememberUpdatedState(onDragSelectionHandle)

  BoxWithConstraints(
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = if (paperMode == ReaderPaperMode.DARK) 2.dp else 6.dp,
          shape = RoundedCornerShape(4.dp),
          clip = false,
        )
        .clip(RoundedCornerShape(4.dp))
        .background(paperBg)
        .testTag("pdf_page_${pageIndex + 1}"),
  ) {
    val pageWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
    val pageHeightPx = (pageWidthPx / aspectRatio).coerceAtLeast(120f)
    val pageHeightDp = with(density) { pageHeightPx.toDp() }

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(pageHeightDp)
          .pointerInput(pageIndex, pageWidthPx, pageHeightPx) {
            detectTapGestures(
              onTap = { latestOnTap() },
              onDoubleTap = { offset -> latestOnDoubleTap(offset) },
              onLongPress = { offset ->
                val xNorm = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                val yNorm = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                latestOnLongPress(xNorm, yNorm)
              },
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      val activeBitmap = hiResBitmap ?: baseBitmap
      if (activeBitmap != null) {
        if (hiResBitmap != null && tileSpec != null) {
          // Base layer + crisp zoomed tile overlay positioned on the visible region
          baseBitmap?.let { base ->
            Image(
              bitmap = base.asImageBitmap(),
              contentDescription = "PDF page ${pageIndex + 1}",
              contentScale = ContentScale.FillBounds,
              colorFilter = colorFilter,
              modifier = Modifier.fillMaxSize(),
            )
          }
          val tileLeftPx = tileSpec.regionLeftFraction * pageWidthPx
          val tileWidthDp = with(density) { ((tileSpec.regionRightFraction - tileSpec.regionLeftFraction) * pageWidthPx).toDp() }
          Image(
            bitmap = activeBitmap.asImageBitmap(),
            contentDescription = "PDF page ${pageIndex + 1} high resolution",
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            modifier =
              Modifier.align(Alignment.TopStart)
                .graphicsLayer { translationX = tileLeftPx }
                .width(tileWidthDp)
                .height(pageHeightDp),
          )
        } else {
          Image(
            bitmap = activeBitmap.asImageBitmap(),
            contentDescription = "PDF page ${pageIndex + 1}",
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize(),
          )
        }
      } else {
        CircularProgressIndicator(
          modifier = Modifier.size(28.dp),
          strokeWidth = 2.5.dp,
        )
      }

      // Vector overlay: search highlights & text selection rects (draw-phase only)
      if (matches.isNotEmpty() || selection != null) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height
          val matchRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
          val allMatchFill = Color(0x66FACC15) // Soft yellow
          val activeMatchFill = Color(0x8CF97316) // Warm orange
          val activeMatchOutline = Color(0xFFEA580C)
          val activeStroke = Stroke(width = 2.dp.toPx())
          val selectionFill = Color(0x553B82F6)

          matches.forEach { (globalIdx, match) ->
            val isActive = globalIdx == currentGlobalMatchIndex
            val fill = if (isActive) activeMatchFill else allMatchFill
            match.rects.forEach { r ->
              val left = r.left * w
              val top = r.top * h
              val rw = ((r.right - r.left) * w).coerceAtLeast(4f)
              val rh = ((r.bottom - r.top) * h).coerceAtLeast(6f)
              drawRoundRect(
                color = fill,
                topLeft = Offset(left, top),
                size = Size(rw, rh),
                cornerRadius = matchRadius,
              )
              if (isActive) {
                drawRoundRect(
                  color = activeMatchOutline,
                  topLeft = Offset(left, top),
                  size = Size(rw, rh),
                  cornerRadius = matchRadius,
                  style = activeStroke,
                )
              }
            }
          }

          selection?.rects?.forEach { r ->
            val left = r.left * w
            val top = r.top * h
            val rw = ((r.right - r.left) * w).coerceAtLeast(4f)
            val rh = ((r.bottom - r.top) * h).coerceAtLeast(6f)
            drawRoundRect(
              color = selectionFill,
              topLeft = Offset(left, top),
              size = Size(rw, rh),
              cornerRadius = matchRadius,
            )
          }
        }
      }

      // Draggable text selection handles
      if (selection != null) {
        selection.startAnchor?.let { anchor ->
          SelectionDragHandle(
            xNorm = anchor.left,
            yNorm = anchor.bottom,
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx,
            isStart = true,
            onDragNormalized = { nx, ny -> latestOnDragHandle(true, nx, ny) },
            modifier = Modifier.align(Alignment.TopStart).testTag("viewer_selection_start_handle"),
          )
        }
        selection.endAnchor?.let { anchor ->
          SelectionDragHandle(
            xNorm = anchor.right,
            yNorm = anchor.bottom,
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx,
            isStart = false,
            onDragNormalized = { nx, ny -> latestOnDragHandle(false, nx, ny) },
            modifier = Modifier.align(Alignment.TopStart).testTag("viewer_selection_end_handle"),
          )
        }
      }
    }
  }
}

@Composable
private fun SelectionDragHandle(
  xNorm: Float,
  yNorm: Float,
  pageWidthPx: Float,
  pageHeightPx: Float,
  isStart: Boolean,
  onDragNormalized: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val handleSizePx = with(density) { 48.dp.toPx() }
  val centerX = xNorm * pageWidthPx
  val centerY = yNorm * pageHeightPx
  var dragX by remember(xNorm) { mutableFloatStateOf(centerX) }
  var dragY by remember(yNorm) { mutableFloatStateOf(centerY) }

  Box(
    modifier =
      modifier
        .size(PrismDimens.TouchTargetMin)
        .graphicsLayer {
          translationX = (centerX - handleSizePx * 0.5f).coerceIn(-12f, pageWidthPx - handleSizePx * 0.5f)
          translationY = (centerY - 4f).coerceIn(0f, pageHeightPx - handleSizePx * 0.4f)
        }
        .pointerInput(isStart, pageWidthPx, pageHeightPx) {
          detectDragGestures(
            onDragStart = {
              dragX = centerX
              dragY = centerY
            },
            onDrag = { change, dragAmount ->
              change.consume()
              dragX = (dragX + dragAmount.x).coerceIn(0f, pageWidthPx)
              dragY = (dragY + dragAmount.y).coerceIn(0f, pageHeightPx)
              onDragNormalized(
                (dragX / pageWidthPx).coerceIn(0f, 1f),
                (dragY / pageHeightPx).coerceIn(0f, 1f),
              )
            },
          )
        },
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(
      modifier =
        Modifier.size(18.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary)
          .shadow(2.dp, CircleShape)
    )
  }
}
