package com.prism.docs.core.ui.glass

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tracks scroll direction on a [LazyListState] with density-aware hysteresis so small finger
 * jitters never flicker the floating bottom tab capsule.
 */
@Composable
fun ObserveScrollCollapse(
  listState: LazyListState,
  onCollapsedChanged: (Boolean) -> Unit,
) {
  val density = LocalDensity.current
  val topZonePx = with(density) { 32.dp.toPx() }
  val thresholdPx = with(density) { 48.dp.toPx() }

  LaunchedEffect(listState, topZonePx, thresholdPx) {
    var previousIndex = listState.firstVisibleItemIndex
    var previousOffset = listState.firstVisibleItemScrollOffset
    var accumulatedDelta = 0f
    var collapsed = false

    snapshotFlow {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
      }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        val isAtTop = index == 0 && offset < topZonePx
        if (isAtTop) {
          accumulatedDelta = 0f
          if (collapsed) {
            collapsed = false
            onCollapsedChanged(false)
          }
        } else {
          val delta =
            when {
              index > previousIndex -> thresholdPx
              index < previousIndex -> -thresholdPx
              else -> (offset - previousOffset).toFloat()
            }
          // Reset accumulator on direction reversal so hysteresis feels crisp and immediate
          if ((delta > 0f && accumulatedDelta < 0f) || (delta < 0f && accumulatedDelta > 0f)) {
            accumulatedDelta = 0f
          }
          accumulatedDelta += delta

          if (!collapsed && accumulatedDelta >= thresholdPx) {
            collapsed = true
            accumulatedDelta = 0f
            onCollapsedChanged(true)
          } else if (collapsed && accumulatedDelta <= -thresholdPx) {
            collapsed = false
            accumulatedDelta = 0f
            onCollapsedChanged(false)
          }
        }
        previousIndex = index
        previousOffset = offset
      }
  }
}
