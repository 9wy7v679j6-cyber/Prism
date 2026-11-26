package com.prism.docs.core.ui.glass

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Root-level [NestedScrollConnection] that minimizes the floating navigation bar when scrolling
 * down through content (giving more vertical space for files) and maximizes it when scrolling up.
 *
 * Works universally across every [LazyColumn], [LazyVerticalGrid], and vertical scroll container
 * while ignoring horizontal carousels and tiny finger jitters.
 */
@Composable
fun rememberTabBarNestedScrollConnection(
  isCollapsed: () -> Boolean,
  onCollapsedChanged: (Boolean) -> Unit,
): NestedScrollConnection {
  val density = LocalDensity.current
  val dynamics = LocalGlassDynamics.current
  val motion = LocalGlassMotion.current
  val scope = rememberCoroutineScope()

  val collapseThresholdPx = with(density) { 28.dp.toPx() }
  val expandThresholdPx = with(density) { 20.dp.toPx() }
  val flingCollapseVelocityPx = with(density) { 900.dp.toPx() }
  val flingExpandVelocityPx = with(density) { 650.dp.toPx() }
  val fastFlingPauseVelocityPx = with(density) { 1450.dp.toPx() }
  val scrollEnergyScalePx = with(density) { 46.dp.toPx() }

  val latestIsCollapsed by rememberUpdatedState(isCollapsed)
  val latestOnCollapsedChanged by rememberUpdatedState(onCollapsedChanged)
  val latestMotion by rememberUpdatedState(motion)

  return remember(
    collapseThresholdPx,
    expandThresholdPx,
    flingCollapseVelocityPx,
    flingExpandVelocityPx,
    fastFlingPauseVelocityPx,
    dynamics,
    scope,
  ) {
    object : NestedScrollConnection {
      private var accumulatedDy = 0f
      private var lastConsumedDy = 0f
      private var decayJob: Job? = null

      private fun scheduleDecay() {
        decayJob?.cancel()
        decayJob =
          scope.launch {
            delay(90L)
            while (dynamics.scrollEnergy > 0.01f) {
              dynamics.scrollEnergy = (dynamics.scrollEnergy * 0.78f).let { if (it < 0.015f) 0f else it }
              delay(16L)
            }
            dynamics.scrollEnergy = 0f
            dynamics.isFastFlinging = false
          }
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        val collapsed = latestIsCollapsed()

        // Pulling past the very top of a list immediately restores the full bar
        if (collapsed && available.y > 6f && abs(available.y) > abs(available.x) && consumed.y == 0f) {
          accumulatedDy = 0f
          lastConsumedDy = 0f
          latestOnCollapsedChanged(false)
          return Offset.Zero
        }

        // Ignore horizontal carousels / micro-jitter
        val dy = consumed.y
        if (abs(dy) < 0.5f || abs(dy) < abs(consumed.x)) {
          return Offset.Zero
        }

        // Drive draw-phase scroll dispersion & rim brightness without recomposition
        if (latestMotion) {
          val impulse = (abs(dy) / scrollEnergyScalePx).coerceIn(0f, 1f)
          dynamics.scrollEnergy = maxOf(dynamics.scrollEnergy * 0.85f, impulse).coerceIn(0f, 1f)
          if (source == NestedScrollSource.SideEffect && abs(dy) > scrollEnergyScalePx * 0.9f) {
            dynamics.isFastFlinging = true
          }
          scheduleDecay()
        }

        lastConsumedDy = dy

        // Reset accumulator cleanly on direction reversal for crisp responsiveness
        if ((dy < 0f && accumulatedDy > 0f) || (dy > 0f && accumulatedDy < 0f)) {
          accumulatedDy = 0f
        }
        accumulatedDy += dy

        // Negative consumed.y = scrolling DOWN through content -> minimize nav bar
        if (!collapsed && accumulatedDy <= -collapseThresholdPx) {
          accumulatedDy = 0f
          latestOnCollapsedChanged(true)
        } else if (collapsed && accumulatedDy >= expandThresholdPx) {
          // Positive consumed.y = scrolling UP toward top -> maximize nav bar
          accumulatedDy = 0f
          latestOnCollapsedChanged(false)
        }

        return Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (abs(available.y) >= fastFlingPauseVelocityPx && abs(available.y) > abs(available.x)) {
          dynamics.isFastFlinging = true
          if (latestMotion) {
            dynamics.scrollEnergy = 1f
          }
        }
        return Velocity.Zero
      }

      override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val collapsed = latestIsCollapsed()
        val vy = consumed.y
        if (abs(vy) > abs(consumed.x)) {
          if (!collapsed && vy <= -flingCollapseVelocityPx && lastConsumedDy < 0f) {
            accumulatedDy = 0f
            latestOnCollapsedChanged(true)
          } else if (collapsed && (vy >= flingExpandVelocityPx || available.y >= flingExpandVelocityPx)) {
            accumulatedDy = 0f
            latestOnCollapsedChanged(false)
          }
        }
        lastConsumedDy = 0f
        dynamics.isFastFlinging = false
        scheduleDecay()
        return Velocity.Zero
      }
    }
  }
}

/**
 * Ensures the floating bottom navigation bar automatically restores to maximized when a
 * [LazyListState] returns to the very top of the list (e.g., after programmatic scroll-to-top
 * or gentle deceleration), without fighting manual swipe-to-minimize gestures.
 */
@Composable
fun ObserveScrollCollapse(
  listState: LazyListState,
  onCollapsedChanged: (Boolean) -> Unit,
) {
  val density = LocalDensity.current
  val topZonePx = with(density) { 24.dp.toPx() }
  val latestOnCollapsedChanged by rememberUpdatedState(onCollapsedChanged)

  LaunchedEffect(listState, topZonePx) {
    var wasAtTop =
      listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < topZonePx

    snapshotFlow {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < topZonePx
      }
      .distinctUntilChanged()
      .collect { isAtTop ->
        if (isAtTop && !wasAtTop) {
          latestOnCollapsedChanged(false)
        }
        wasAtTop = isAtTop
      }
  }
}

/**
 * Grid counterpart of [ObserveScrollCollapse] for [LazyGridState].
 */
@Composable
fun ObserveScrollCollapse(
  gridState: LazyGridState,
  onCollapsedChanged: (Boolean) -> Unit,
) {
  val density = LocalDensity.current
  val topZonePx = with(density) { 24.dp.toPx() }
  val latestOnCollapsedChanged by rememberUpdatedState(onCollapsedChanged)

  LaunchedEffect(gridState, topZonePx) {
    var wasAtTop =
      gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < topZonePx

    snapshotFlow {
        gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < topZonePx
      }
      .distinctUntilChanged()
      .collect { isAtTop ->
        if (isAtTop && !wasAtTop) {
          latestOnCollapsedChanged(false)
        }
        wasAtTop = isAtTop
      }
  }
}
