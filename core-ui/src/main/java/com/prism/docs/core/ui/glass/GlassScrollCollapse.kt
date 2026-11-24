package com.prism.docs.core.ui.glass

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tracks scroll direction on a [LazyListState] to collapse the floating bottom tab capsule
 * smoothly when scrolling downward and expand it when scrolling up or resting near the top.
 */
@Composable
fun ObserveScrollCollapse(
  listState: LazyListState,
  onCollapsedChanged: (Boolean) -> Unit,
) {
  LaunchedEffect(listState) {
    var previousIndex = listState.firstVisibleItemIndex
    var previousOffset = listState.firstVisibleItemScrollOffset
    snapshotFlow {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
      }
      .distinctUntilChanged()
      .collect { (index, offset) ->
        val isAtTop = index == 0 && offset < 24
        if (isAtTop) {
          onCollapsedChanged(false)
        } else {
          val scrollingDown =
            index > previousIndex || (index == previousIndex && offset > previousOffset + 18)
          val scrollingUp =
            index < previousIndex || (index == previousIndex && offset < previousOffset - 18)
          if (scrollingDown) {
            onCollapsedChanged(true)
          } else if (scrollingUp) {
            onCollapsedChanged(false)
          }
        }
        previousIndex = index
        previousOffset = offset
      }
  }
}
