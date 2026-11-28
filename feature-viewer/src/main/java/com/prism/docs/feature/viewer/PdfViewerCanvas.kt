package com.prism.docs.feature.viewer

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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

private class CoordsRef(var coords: LayoutCoordinates? = null)

private data class RenderedPdfTile(
  val spec: TileRenderSpec,
  val bitmap: Bitmap,
)

private fun computeVisibleTileSpec(
  viewportCoords: LayoutCoordinates?,
  pageCoords: LayoutCoordinates?,
  settledZoom: Float,
  pageRenderWidthPx: Int,
  aspectRatio: Float,
): TileRenderSpec? {
  if (settledZoom <= 1.25f) return null
  val vp = viewportCoords?.takeIf { it.isAttached } ?: return null
  val pg = pageCoords?.takeIf { it.isAttached } ?: return null
  val vpSize = vp.size
  val pgSize = pg.size
  if (vpSize.width <= 0 || vpSize.height <= 0 || pgSize.width <= 0 || pgSize.height <= 0) return null

  val vpTopLeft = vp.localToWindow(Offset.Zero)
  val vpBottomRight = vp.localToWindow(Offset(vpSize.width.toFloat(), vpSize.height.toFloat()))
  val pgTopLeft = pg.localToWindow(Offset.Zero)
  val pgBottomRight = pg.localToWindow(Offset(pgSize.width.toFloat(), pgSize.height.toFloat()))

  val pw = (pgBottomRight.x - pgTopLeft.x).coerceAtLeast(1f)
  val ph = (pgBottomRight.y - pgTopLeft.y).coerceAtLeast(1f)

  val rawLeft = ((vpTopLeft.x - pgTopLeft.x) / pw).coerceIn(0f, 1f)
  val rawRight = ((vpBottomRight.x - pgTopLeft.x) / pw).coerceIn(0f, 1f)
  val rawTop = ((vpTopLeft.y - pgTopLeft.y) / ph).coerceIn(0f, 1f)
  val rawBottom = ((vpBottomRight.y - pgTopLeft.y) / ph).coerceIn(0f, 1f)

  if (rawRight - rawLeft < 0.01f || rawBottom - rawTop < 0.01f) return null

  val left = (floor((rawLeft - 0.08f).coerceAtLeast(0f) * 50f) / 50f).coerceIn(0f, 0.95f)
  val right = (ceil((rawRight + 0.08f).coerceAtMost(1f) * 50f) / 50f).coerceIn(left + 0.05f, 1f)
  val top = (floor((rawTop - 0.08f).coerceAtLeast(0f) * 50f) / 50f).coerceIn(0f, 0.95f)
  val bottom = (ceil((rawBottom + 0.08f).coerceAtMost(1f) * 50f) / 50f).coerceIn(top + 0.05f, 1f)

  val regionW = (right - left).coerceIn(0.05f, 1f)
  val regionH = (bottom - top).coerceIn(0.05f, 1f)
  val idealW = pageRenderWidthPx * settledZoom * regionW
  val idealH = (pageRenderWidthPx / aspectRatio) * settledZoom * regionH
  val downscale = (2048f / maxOf(idealW, idealH, 1f)).coerceAtMost(1f)

  return TileRenderSpec(
    scaleFactor = (settledZoom * 10f).roundToInt() / 10f,
    regionLeftFraction = left,
    regionTopFraction = top,
    regionRightFraction = right,
    regionBottomFraction = bottom,
    targetWidthPx = (idealW * downscale).roundToInt().coerceIn(64, 2048),
    targetHeightPx = (idealH * downscale).roundToInt().coerceIn(64, 2048),
  )
}

@OptIn(FlowPreview::class)
@Composable
internal fun PdfViewerCanvas(
  pdfController: PdfDocumentController,
  pageCount: Int,
  currentPage: Int,
  initialScrollOffsetFraction: Float,
  initialZoom: Float,
  zoomResetNonce: Int,
  isScrubbing: Boolean,
  scrollMode: PdfScrollMode,
  paperMode: ReaderPaperMode,
  bookmarkedPages: Set<Int>,
  searchMatches: List<PdfSearchMatch>,
  currentMatchIndex: Int,
  selection: TextSelectionRange?,
  currentPageHasTextLayer: Boolean?,
  topContentPadding: Dp,
  bottomContentPadding: Dp,
  bottomOverlayOffset: Dp,
  continuousPageState: MutableFloatState,
  liveZoomState: MutableFloatState,
  nestedScrollConnection: NestedScrollConnection,
  jumpEvents: SharedFlow<ViewerJumpEvent>,
  onVisiblePageChanged: (Int, Float) -> Unit,
  onZoomSettled: (Float) -> Unit,
  onActiveScrollChanged: (Boolean) -> Unit,
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
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()

  var appliedSavedZoom by remember(pdfController) { mutableStateOf(false) }
  var zoom by remember(pdfController) { mutableFloatStateOf(initialZoom.coerceIn(1f, 5f)) }
  var panOffset by remember(pdfController) { mutableStateOf(Offset.Zero) }
  var settledZoom by remember(pdfController) { mutableFloatStateOf(initialZoom.coerceIn(1f, 5f)) }
  var settleEpoch by remember(pdfController) { mutableIntStateOf(0) }
  var isPinching by remember(pdfController) { mutableStateOf(false) }
  var isDraggingHandle by remember { mutableStateOf(false) }
  val isZoomed by remember(pdfController) { derivedStateOf { zoom > 1.02f } }

  LaunchedEffect(pdfController) {
    liveZoomState.floatValue = zoom
  }

  LaunchedEffect(pdfController, initialZoom) {
    if (!appliedSavedZoom && initialZoom > 1.01f) {
      appliedSavedZoom = true
      val clamped = initialZoom.coerceIn(1f, 5f)
      zoom = clamped
      liveZoomState.floatValue = clamped
      settledZoom = clamped
      settleEpoch++
    }
  }

  val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, pageCount - 1))
  val pagerState = rememberPagerState(initialPage = currentPage.coerceIn(0, pageCount - 1), pageCount = { pageCount })

  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  val viewportCoordsRef = remember { CoordsRef() }
  var lastDownScreenOffset by remember(pdfController) { mutableStateOf(Offset.Zero) }
  var flingJob by remember(pdfController) { mutableStateOf<Job?>(null) }

  // Smoothly animate zoom back to 1x when user taps the Zoom Reset pill
  LaunchedEffect(zoomResetNonce) {
    if (zoomResetNonce > 0) {
      flingJob?.cancel()
      if (!motion) {
        zoom = 1f
        liveZoomState.floatValue = 1f
        panOffset = Offset.Zero
      } else {
        val startZoom = zoom
        val startPan = panOffset
        val anim = Animatable(0f)
        anim.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f)) {
          val nextZoom = startZoom + (1f - startZoom) * value
          zoom = nextZoom
          liveZoomState.floatValue = nextZoom
          panOffset = Offset(startPan.x * (1f - value), startPan.y * (1f - value))
        }
        zoom = 1f
        liveZoomState.floatValue = 1f
        panOffset = Offset.Zero
      }
    }
  }

  // Pulse animation when navigating to a new search hit (read strictly in draw phase)
  val matchPulseAnim = remember { Animatable(0f) }
  LaunchedEffect(currentMatchIndex) {
    if (currentMatchIndex >= 0 && motion) {
      matchPulseAnim.snapTo(1f)
      matchPulseAnim.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 300f))
    } else {
      matchPulseAnim.snapTo(0f)
    }
  }

  // Smooth double-tap zoom helper
  fun animateZoomTo(targetZoom: Float, targetPan: Offset) {
    flingJob?.cancel()
    if (!motion) {
      zoom = targetZoom
      liveZoomState.floatValue = targetZoom
      panOffset = targetPan
      return
    }
    flingJob = scope.launch {
      val startZoom = zoom
      val startPan = panOffset
      val anim = Animatable(0f)
      anim.animateTo(1f, spring(dampingRatio = 0.80f, stiffness = 440f)) {
        val nextZoom = startZoom + (targetZoom - startZoom) * value
        zoom = nextZoom
        liveZoomState.floatValue = nextZoom
        panOffset = Offset(
          x = startPan.x + (targetPan.x - startPan.x) * value,
          y = startPan.y + (targetPan.y - startPan.y) * value,
        )
      }
      zoom = targetZoom
      liveZoomState.floatValue = targetZoom
      panOffset = targetPan
    }
  }

  // Track active scroll momentum so scrubber & top page pill react synchronously
  LaunchedEffect(listState, pagerState, scrollMode) {
    snapshotFlow {
      if (scrollMode == PdfScrollMode.CONTINUOUS) listState.isScrollInProgress
      else pagerState.isScrollInProgress
    }
      .distinctUntilChanged()
      .collectLatest { scrolling ->
        onActiveScrollChanged(scrolling)
      }
  }

  // Debounce zoom, pan, and scroll settle so sharp tile re-render triggers only once gestures settle
  LaunchedEffect(pdfController, scrollMode) {
    snapshotFlow {
      val scrolling =
        if (scrollMode == PdfScrollMode.CONTINUOUS) listState.isScrollInProgress
        else pagerState.isScrollInProgress
      val firstIdx =
        if (scrollMode == PdfScrollMode.CONTINUOUS) listState.firstVisibleItemIndex
        else pagerState.currentPage
      val firstOff =
        if (scrollMode == PdfScrollMode.CONTINUOUS) listState.firstVisibleItemScrollOffset
        else 0
      listOf(zoom, panOffset.x, panOffset.y, isPinching, scrolling, firstIdx, firstOff)
    }
      .debounce(150L)
      .collectLatest { values ->
        val z = values[0] as Float
        val activePinch = values[3] as Boolean
        val activeScroll = values[4] as Boolean
        if (!activePinch && !activeScroll) {
          settledZoom = z
          settleEpoch++
          onZoomSettled(z)
        }
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
            val distance = abs(target - listState.firstVisibleItemIndex)
            if (motion && !isScrubbing && distance in 1..5 && offsetPx == 0) {
              listState.animateScrollToItem(target, scrollOffset = offsetPx)
            } else {
              listState.scrollToItem(target, scrollOffset = offsetPx)
            }
          } else {
            val distance = abs(target - pagerState.currentPage)
            if (motion && !isScrubbing && distance in 1..3) {
              pagerState.animateScrollToPage(target)
            } else {
              pagerState.scrollToPage(target)
            }
          }
        }
      }
    }
  }

  // Update continuous page float for draw-phase progress bars & scrubber without recomposing shell
  LaunchedEffect(scrollMode, pageCount, listState, pagerState) {
    if (scrollMode == PdfScrollMode.CONTINUOUS) {
      snapshotFlow {
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (visible.isEmpty()) {
          listState.firstVisibleItemIndex.toFloat()
        } else {
          val first = visible.first()
          val frac =
            if (first.size > 0) ((-first.offset).toFloat() / first.size.toFloat()).coerceIn(0f, 0.99f)
            else 0f
          (first.index + frac).coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
        }
      }
        .collectLatest { continuous ->
          continuousPageState.floatValue = continuous
        }
    } else {
      snapshotFlow {
        (pagerState.currentPage + pagerState.currentPageOffsetFraction)
          .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
      }
        .collectLatest { continuous ->
          continuousPageState.floatValue = continuous
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
  var appliedInitialOffset by remember(pdfController) { mutableStateOf(false) }
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

  val toolbarDragAlpha by
    animateFloatAsState(
      targetValue = if (isDraggingHandle) 0.25f else 1f,
      animationSpec = if (motion) tween(130) else snap(),
      label = "selection_toolbar_drag_alpha",
    )

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .viewerFocusBackdrop(paperMode)
        .onGloballyPositioned { coords ->
          viewportSize = coords.size
          viewportCoordsRef.coords = coords
        }
        // Pass 1 (Initial): intercepts 2-finger pinch before LazyColumn / HorizontalPager starts scrolling
        .pointerInput(pdfController) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            lastDownScreenOffset = down.position
            flingJob?.cancel()
            var prevPointerCount = 1
            do {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val pointerCount = event.changes.count { it.pressed }
              val pinchingNow = pointerCount >= 2
              if (isPinching != pinchingNow) {
                isPinching = pinchingNow
              }
              if (pointerCount != prevPointerCount) {
                prevPointerCount = pointerCount
                if (pinchingNow) {
                  event.changes.forEach { if (it.pressed) it.consume() }
                }
                continue
              }
              if (pinchingNow) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)
                val oldZoom = zoom
                val newZoom = (oldZoom * zoomChange).coerceIn(1f, 5f)
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                if (
                  zoomChange.isFinite() &&
                  zoomChange in 0.25f..4f &&
                  centroid.isSpecified &&
                  panChange.x.isFinite() &&
                  panChange.y.isFinite() &&
                  oldZoom > 0f
                ) {
                  val focusX = centroid.x.coerceIn(0f, w)
                  val focusY = centroid.y.coerceIn(0f, h)
                  val factor = newZoom / oldZoom
                  val maxPanX = (w * (newZoom - 1f)).coerceAtLeast(0f)
                  val maxPanY = (h * (newZoom - 1f)).coerceAtLeast(0f)
                  val adjustedPanX = (panOffset.x - focusX) * factor + focusX + panChange.x
                  val adjustedPanY = (panOffset.y - focusY) * factor + focusY + panChange.y
                  zoom = newZoom
                  liveZoomState.floatValue = newZoom
                  panOffset =
                    if (newZoom <= 1.01f) Offset.Zero
                    else Offset(
                      x = adjustedPanX.coerceIn(-maxPanX, 0f),
                      y = adjustedPanY.coerceIn(-maxPanY, 0f),
                    )
                }
                event.changes.forEach { if (it.positionChanged() || it.pressed) it.consume() }
              }
            } while (event.changes.any { it.pressed })
            if (isPinching) isPinching = false
          }
        }
        // Pass 2 (Main): handles smooth 1-finger pan + inertial fling + continuous vertical page overflow while zoomed
        .pointerInput(pdfController, scrollMode) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            flingJob?.cancel()
            val velocityTracker = VelocityTracker()
            var totalDrag = Offset.Zero
            var pastTouchSlop = false
            val touchSlop = viewConfiguration.touchSlop
            do {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val pressedChanges = event.changes.filter { it.pressed }
              if (pressedChanges.size != 1 || isPinching || zoom <= 1.02f || event.changes.any { it.isConsumed }) {
                if (pressedChanges.size >= 2 || isPinching) {
                  pastTouchSlop = false
                  velocityTracker.resetTracking()
                }
                continue
              }
              val change = pressedChanges.first()
              val delta = change.positionChange()
              if (!pastTouchSlop) {
                totalDrag += delta
                if (totalDrag.getDistance() >= touchSlop) {
                  pastTouchSlop = true
                  velocityTracker.resetTracking()
                }
              }
              if (pastTouchSlop && (abs(delta.x) > 0.01f || abs(delta.y) > 0.01f)) {
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                val maxPanX = (w * (zoom - 1f)).coerceAtLeast(0f)
                val maxPanY = (h * (zoom - 1f)).coerceAtLeast(0f)
                val candX = panOffset.x + delta.x
                val candY = panOffset.y + delta.y
                val clampedX = candX.coerceIn(-maxPanX, 0f)
                val clampedY = candY.coerceIn(-maxPanY, 0f)
                val overflowY = candY - clampedY
                panOffset = Offset(clampedX, clampedY)
                if (scrollMode == PdfScrollMode.CONTINUOUS && abs(overflowY) > 0.05f) {
                  listState.dispatchRawDelta(-overflowY / zoom)
                }
                change.consume()
              }
            } while (event.changes.any { it.pressed })

            if (pastTouchSlop && !isPinching && zoom > 1.02f) {
              val vel = velocityTracker.calculateVelocity()
              if (abs(vel.x) > 80f || abs(vel.y) > 80f) {
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                flingJob = scope.launch {
                  val decay = exponentialDecay<Float>(frictionMultiplier = 1.5f)
                  val animX = Animatable(panOffset.x).apply {
                    val maxX = (w * (zoom - 1f)).coerceAtLeast(0f)
                    updateBounds(lowerBound = -maxX, upperBound = 0f)
                  }
                  val animY = Animatable(panOffset.y).apply {
                    if (scrollMode == PdfScrollMode.PAGED) {
                      val maxY = (h * (zoom - 1f)).coerceAtLeast(0f)
                      updateBounds(lowerBound = -maxY, upperBound = 0f)
                    }
                  }
                  launch {
                    animX.animateDecay(vel.x, decay) {
                      val maxX = (w * (zoom - 1f)).coerceAtLeast(0f)
                      panOffset = panOffset.copy(x = value.coerceIn(-maxX, 0f))
                    }
                  }
                  var prevY = panOffset.y
                  animY.animateDecay(vel.y, decay) {
                    val stepDy = value - prevY
                    prevY = value
                    val maxY = (h * (zoom - 1f)).coerceAtLeast(0f)
                    val candY = panOffset.y + stepDy
                    val clampedY = candY.coerceIn(-maxY, 0f)
                    val overflowY = candY - clampedY
                    panOffset = panOffset.copy(y = clampedY)
                    if (scrollMode == PdfScrollMode.CONTINUOUS && abs(overflowY) > 0.05f) {
                      listState.dispatchRawDelta(-overflowY / zoom)
                    }
                  }
                }
              }
            }
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
        translationY = panOffset.y
      }

    if (scrollMode == PdfScrollMode.CONTINUOUS) {
      LazyColumn(
        state = listState,
        userScrollEnabled = !isZoomed && !isPinching,
        contentPadding =
          PaddingValues(
            top = topContentPadding + 8.dp,
            bottom = bottomContentPadding + 16.dp,
            start = horizontalPad,
            end = horizontalPad,
          ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = zoomedLayerModifier.nestedScroll(nestedScrollConnection),
      ) {
        items(
          count = pageCount,
          key = { pageIdx -> "pdf_page_$pageIdx" },
          contentType = { "pdf_page" },
        ) { pageIdx ->
          val pageModifier =
            Modifier.graphicsLayer {
              if (motion && zoom <= 1.03f) {
                val layout = listState.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == pageIdx }
                if (item != null && layout.viewportSize.height > 0) {
                  val vpCenter = (layout.viewportStartOffset + layout.viewportEndOffset) * 0.5f
                  val itemCenter = item.offset + item.size * 0.5f
                  val normDist = (abs(itemCenter - vpCenter) / (layout.viewportSize.height * 0.72f)).coerceIn(0f, 1f)
                  val s = 1f - normDist * 0.032f
                  scaleX = s
                  scaleY = s
                  alpha = 1f - normDist * 0.12f
                }
              }
            }
          PdfPageSurfaceItem(
            pdfController = pdfController,
            pageIndex = pageIdx,
            pageRenderWidthPx = pageTargetRenderPx,
            viewportCoordsRef = viewportCoordsRef,
            settledZoom = settledZoom,
            settleEpoch = settleEpoch,
            paperMode = paperMode,
            isBookmarked = pageIdx in bookmarkedPages,
            matches = matchesByPage[pageIdx].orEmpty(),
            currentGlobalMatchIndex = currentMatchIndex,
            matchPulseProgress = { matchPulseAnim.value },
            selection = selection?.takeIf { it.pageIndex == pageIdx },
            modifier = pageModifier,
            onTapPage = {
              if (selection != null) onClearSelection() else onToggleChrome()
            },
            onDoubleTapPage = {
              if (zoom > 1.15f) {
                animateZoomTo(1f, Offset.Zero)
              } else {
                val targetZoom = 2.4f
                val w = viewportSize.width.toFloat().coerceAtLeast(1f)
                val h = viewportSize.height.toFloat().coerceAtLeast(1f)
                val fx = lastDownScreenOffset.x.coerceIn(0f, w)
                val fy = lastDownScreenOffset.y.coerceIn(0f, h)
                animateZoomTo(
                  targetZoom,
                  Offset(
                    x = (fx - fx * targetZoom).coerceIn(-(w * (targetZoom - 1f)), 0f),
                    y = (fy - fy * targetZoom).coerceIn(-(h * (targetZoom - 1f)), 0f),
                  ),
                )
              }
            },
            onLongPressAt = { xNorm, yNorm ->
              haptics.longPress()
              onLongPressSelectAt(pageIdx, xNorm, yNorm)
            },
            onDragSelectionHandle = { isStart, xNorm, yNorm ->
              onDragSelectionHandle(isStart, pageIdx, xNorm, yNorm)
            },
            onHandleDragStateChanged = { dragging -> isDraggingHandle = dragging },
          )
        }
      }
    } else {
      HorizontalPager(
        state = pagerState,
        userScrollEnabled = !isZoomed && !isPinching,
        contentPadding = PaddingValues(horizontal = horizontalPad),
        pageSpacing = 12.dp,
        modifier = zoomedLayerModifier,
      ) { pageIdx ->
        Box(
          modifier =
            Modifier.fillMaxSize()
              .graphicsLayer {
                if (motion && zoom <= 1.03f) {
                  val offsetFraction =
                    ((pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction)
                      .coerceIn(-1f, 1f)
                  val absOffset = abs(offsetFraction)
                  val s = 1f - absOffset * 0.055f
                  scaleX = s
                  scaleY = s
                  alpha = 1f - absOffset * 0.18f
                  rotationY = -offsetFraction * 3.5f
                  cameraDistance = 16f * density.density
                }
              }
              .padding(top = topContentPadding + 8.dp, bottom = bottomContentPadding + 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          PdfPageSurfaceItem(
            pdfController = pdfController,
            pageIndex = pageIdx,
            pageRenderWidthPx = pageTargetRenderPx,
            viewportCoordsRef = viewportCoordsRef,
            settledZoom = settledZoom,
            settleEpoch = settleEpoch,
            paperMode = paperMode,
            isBookmarked = pageIdx in bookmarkedPages,
            matches = matchesByPage[pageIdx].orEmpty(),
            currentGlobalMatchIndex = currentMatchIndex,
            matchPulseProgress = { matchPulseAnim.value },
            selection = selection?.takeIf { it.pageIndex == pageIdx },
            onTapPage = {
              if (selection != null) onClearSelection() else onToggleChrome()
            },
            onDoubleTapPage = {
              if (zoom > 1.15f) {
                animateZoomTo(1f, Offset.Zero)
              } else {
                val targetZoom = 2.4f
                val w = viewportSize.width.toFloat().coerceAtLeast(1f)
                val h = viewportSize.height.toFloat().coerceAtLeast(1f)
                val fx = lastDownScreenOffset.x.coerceIn(0f, w)
                val fy = lastDownScreenOffset.y.coerceIn(0f, h)
                animateZoomTo(
                  targetZoom,
                  Offset(
                    x = (fx - fx * targetZoom).coerceIn(-(w * (targetZoom - 1f)), 0f),
                    y = (fy - fy * targetZoom).coerceIn(-(h * (targetZoom - 1f)), 0f),
                  ),
                )
              }
            },
            onLongPressAt = { xNorm, yNorm ->
              haptics.longPress()
              onLongPressSelectAt(pageIdx, xNorm, yNorm)
            },
            onDragSelectionHandle = { isStart, xNorm, yNorm ->
              onDragSelectionHandle(isStart, pageIdx, xNorm, yNorm)
            },
            onHandleDragStateChanged = { dragging -> isDraggingHandle = dragging },
          )
        }
      }
    }

    // Solid Scanned page stub chip (appears softly when current page lacks a text layer)
    AnimatedVisibility(
      visible = currentPageHasTextLayer == false && selection == null,
      enter =
        if (motion) slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 2 } + fadeIn(tween(150))
        else EnterTransition.None,
      exit =
        if (motion) slideOutVertically(tween(120)) { it / 2 } + fadeOut(tween(110))
        else ExitTransition.None,
      modifier =
        Modifier.align(Alignment.BottomStart)
          .padding(start = 16.dp, bottom = bottomOverlayOffset + 12.dp),
    ) {
      ViewerSolidSurface(
        cornerRadius = 18.dp,
        shape = CircleShape,
        shadowElevation = 8.dp,
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

    // Solid Floating selection toolbar (Copy / Share / Search)
    AnimatedVisibility(
      visible = selection != null,
      enter =
        if (motion) scaleIn(initialScale = 0.88f) + fadeIn(tween(140))
        else EnterTransition.None,
      exit =
        if (motion) scaleOut(targetScale = 0.88f) + fadeOut(tween(110))
        else ExitTransition.None,
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .padding(horizontal = 16.dp, vertical = bottomOverlayOffset + 12.dp)
          .graphicsLayer { alpha = toolbarDragAlpha },
    ) {
      ViewerSolidSurface(
        cornerRadius = 24.dp,
        shape = CircleShape,
        shadowElevation = 10.dp,
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
  val haptics = rememberPrismHaptics()
  val colorScheme = MaterialTheme.colorScheme
  Row(
    modifier =
      Modifier.defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
        .clip(CircleShape)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = ripple(color = colorScheme.primary),
          role = Role.Button,
          onClick = {
            haptics.tap()
            onClick()
          },
        )
        .testTag(testTag)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = colorScheme.onSurface,
    )
  }
}

@Composable
private fun PdfPageSurfaceItem(
  pdfController: PdfDocumentController,
  pageIndex: Int,
  pageRenderWidthPx: Int,
  viewportCoordsRef: CoordsRef,
  settledZoom: Float,
  settleEpoch: Int,
  paperMode: ReaderPaperMode,
  isBookmarked: Boolean,
  matches: List<Pair<Int, PdfSearchMatch>>,
  currentGlobalMatchIndex: Int,
  matchPulseProgress: () -> Float,
  selection: TextSelectionRange?,
  onTapPage: () -> Unit,
  onDoubleTapPage: () -> Unit,
  onLongPressAt: (Float, Float) -> Unit,
  onDragSelectionHandle: (Boolean, Float, Float) -> Unit,
  onHandleDragStateChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val motion = LocalGlassMotion.current
  val colorScheme = MaterialTheme.colorScheme
  val paperBg by
    animateColorAsState(
      targetValue = paperSurfaceColor(paperMode),
      animationSpec = if (motion) tween(220) else snap(),
      label = "pdf_page_paper_bg",
    )
  val colorFilter = remember(paperMode) { paperModeColorFilter(paperMode) }

  var aspectRatio by remember(pageIndex) {
    mutableFloatStateOf(pdfController.aspectRatioForPage(pageIndex))
  }

  val initialCached = remember(pageIndex, pageRenderWidthPx, pdfController) {
    pdfController.peekCachedPage(pageIndex, pageRenderWidthPx)
  }

  // Base display-scale bitmap — automatically cancelled when page scrolls out of LazyColumn
  val baseBitmap by
    produceState<Bitmap?>(
      initialValue = initialCached,
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

  val pageFadeAlpha by
    animateFloatAsState(
      targetValue = if (baseBitmap != null) 1f else 0f,
      animationSpec = if (motion && initialCached == null) tween(140) else snap(),
      label = "pdf_page_bitmap_fade",
    )

  // Non-state coordinates ref so measuring never causes recomposition during scroll or zoom
  val pageCoordsRef = remember { CoordsRef() }
  var renderedTile by remember(pageIndex, pdfController) { mutableStateOf<RenderedPdfTile?>(null) }

  LaunchedEffect(pageIndex, settledZoom, settleEpoch, aspectRatio, pageRenderWidthPx, pdfController) {
    val spec =
      computeVisibleTileSpec(
        viewportCoords = viewportCoordsRef.coords,
        pageCoords = pageCoordsRef.coords,
        settledZoom = settledZoom,
        pageRenderWidthPx = pageRenderWidthPx,
        aspectRatio = aspectRatio,
      )
    if (spec == null) {
      renderedTile = null
    } else if (renderedTile?.spec != spec) {
      val bmp = pdfController.renderPage(pageIndex, pageRenderWidthPx, spec)
      if (bmp != null) {
        renderedTile = RenderedPdfTile(spec, bmp)
      }
    }
  }

  val latestOnTap by rememberUpdatedState(onTapPage)
  val latestOnDoubleTap by rememberUpdatedState(onDoubleTapPage)
  val latestOnLongPress by rememberUpdatedState(onLongPressAt)
  val latestOnDragHandle by rememberUpdatedState(onDragSelectionHandle)
  val latestOnHandleDragState by rememberUpdatedState(onHandleDragStateChanged)

  val pageBorderColor =
    when (paperMode) {
      ReaderPaperMode.LIGHT -> colorScheme.outline.copy(alpha = 0.35f)
      ReaderPaperMode.SEPIA -> colorScheme.outline.copy(alpha = 0.42f)
      ReaderPaperMode.DARK -> colorScheme.primary.copy(alpha = 0.24f)
    }

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxWidth()
        .shadow(
          elevation = if (paperMode == ReaderPaperMode.DARK) 6.dp else 12.dp,
          shape = RoundedCornerShape(8.dp),
          clip = false,
        )
        .clip(RoundedCornerShape(8.dp))
        .background(paperBg)
        .border(BorderStroke(1.dp, pageBorderColor), RoundedCornerShape(8.dp))
        .onGloballyPositioned { pageCoordsRef.coords = it }
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
              onDoubleTap = { latestOnDoubleTap() },
              onLongPress = { offset ->
                val xNorm = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                val yNorm = (offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                latestOnLongPress(xNorm, yNorm)
              },
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      val base = baseBitmap
      val tile = renderedTile
      if (base != null || tile != null) {
        if (base != null) {
          Image(
            bitmap = base.asImageBitmap(),
            contentDescription = "PDF page ${pageIndex + 1}",
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pageFadeAlpha },
          )
        }
        if (tile != null) {
          val spec = tile.spec
          val tileLeftPx = spec.regionLeftFraction * pageWidthPx
          val tileTopPx = spec.regionTopFraction * pageHeightPx
          val tileDrawWidthDp =
            with(density) { ((spec.regionRightFraction - spec.regionLeftFraction) * pageWidthPx).toDp() }
          val tileDrawHeightDp =
            with(density) { ((spec.regionBottomFraction - spec.regionTopFraction) * pageHeightPx).toDp() }
          Image(
            bitmap = tile.bitmap.asImageBitmap(),
            contentDescription = "PDF page ${pageIndex + 1} high resolution",
            contentScale = ContentScale.FillBounds,
            colorFilter = colorFilter,
            modifier =
              Modifier.align(Alignment.TopStart)
                .graphicsLayer {
                  translationX = tileLeftPx
                  translationY = tileTopPx
                }
                .width(tileDrawWidthDp)
                .height(tileDrawHeightDp),
          )
        }
      } else {
        CircularProgressIndicator(
          modifier = Modifier.size(28.dp),
          strokeWidth = 2.5.dp,
        )
      }

      // Bookmark corner badge on bookmarked pages
      if (isBookmarked) {
        Box(
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(top = 8.dp, end = 8.dp)
              .clip(CircleShape)
              .background(colorScheme.primary)
              .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Bookmark,
            contentDescription = stringResource(R.string.viewer_bookmarked_badge),
            tint = colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
          )
        }
      }

      // Vector overlay: search highlights & text selection rects (draw-phase only)
      if (matches.isNotEmpty() || selection != null) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height
          val pulse = matchPulseProgress().coerceIn(0f, 1f)
          val matchRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
          val allMatchFill = Color(0x66FACC15)
          val activeMatchFill = Color(0x99F97316)
          val activeMatchOutline = Color(0xFFEA580C)
          val activeStroke = Stroke(width = (2f + pulse * 2f).dp.toPx())
          val selectionFill = Color(0x553B82F6)

          matches.forEach { (globalIdx, match) ->
            val isActive = globalIdx == currentGlobalMatchIndex
            val fill = if (isActive) activeMatchFill else allMatchFill
            val padPx = if (isActive) pulse * 2.5.dp.toPx() else 0f
            match.rects.forEach { r ->
              val left = r.left * w - padPx
              val top = r.top * h - padPx
              val rw = ((r.right - r.left) * w).coerceAtLeast(4f) + padPx * 2f
              val rh = ((r.bottom - r.top) * h).coerceAtLeast(6f) + padPx * 2f
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
            onDragStateChanged = latestOnHandleDragState,
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
            onDragStateChanged = latestOnHandleDragState,
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
  onDragStateChanged: (Boolean) -> Unit,
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
              onDragStateChanged(true)
            },
            onDragEnd = { onDragStateChanged(false) },
            onDragCancel = { onDragStateChanged(false) },
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
