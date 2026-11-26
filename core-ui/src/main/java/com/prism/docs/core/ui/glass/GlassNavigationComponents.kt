package com.prism.docs.core.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.prism.docs.core.ui.R
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.compose.runtime.Immutable
data class GlassTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val selectedIcon: ImageVector = icon,
  val testTag: String = "tab_$id",
)

private val DockExpandedHeight = 68.dp
private val DockMinimizedHeight = 48.dp
private val SearchOrbExpandedSize = 62.dp
private val SearchOrbMinimizedSize = 48.dp
private val SearchExpandedBarHeight = 56.dp
private val SearchCloseOrbSize = 56.dp
private val DockMinimizedWidth = 154.dp
private val DockHorizontalGap = 12.dp

/**
 * Floating Liquid Glass bottom navigation bar with:
 * - Scroll-driven minimize (split corner pill + mini Search orb) and maximize (full 3-tab dock).
 * - Beautiful bottom Search Bar morph: tapping or swiping up on the Search orb expands it into
 *   a full-width Liquid Glass Search Bar right at the bottom (lifting above the keyboard with
 *   `imePadding()`) while the dock smoothly morphs into a circular Close orb on the left.
 * - Deliberate, high-threshold swipe gestures so accidental swipes never trigger during taps or
 *   vertical list scrolling.
 */
@Composable
fun GlassFloatingTabBar(
  tabs: List<GlassTabItem>,
  selectedId: String,
  onSelectTab: (String) -> Unit,
  onSearchClick: () -> Unit,
  isCollapsed: Boolean,
  modifier: Modifier = Modifier,
  onCollapsedChange: (Boolean) -> Unit = {},
  isSearchExpanded: Boolean = false,
  searchQuery: String = "",
  onSearchQueryChange: (String) -> Unit = {},
  onSearchExpandedChange: ((Boolean) -> Unit)? = null,
  onSearchSubmit: () -> Unit = {},
) {
  if (tabs.isEmpty()) return

  val env = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()
  val colorScheme = MaterialTheme.colorScheme
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val searchFocusRequester = remember { FocusRequester() }

  val closeSearchBar: () -> Unit = {
    haptics.tap()
    focusManager.clearFocus()
    keyboardController?.hide()
    if (searchQuery.isNotEmpty()) {
      onSearchQueryChange("")
    }
    onSearchExpandedChange?.invoke(false)
  }

  LaunchedEffect(isSearchExpanded) {
    if (isSearchExpanded) {
      delay(120L)
      runCatching { searchFocusRequester.requestFocus() }
      keyboardController?.show()
    } else {
      focusManager.clearFocus()
    }
  }

  val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
  val selectedTab = tabs[selectedIndex]
  val selectedContentColor = tokens.tabActiveContent
  val selectedStateLabel = stringResource(R.string.glass_state_selected)
  val notSelectedStateLabel = stringResource(R.string.glass_state_not_selected)
  val openSearchCd = stringResource(R.string.glass_open_search_cd)
  val expandNavCd = stringResource(R.string.glass_expand_nav_cd)
  val minimizedNavState = stringResource(R.string.glass_minimized_nav_state)
  val bottomSearchPlaceholder = stringResource(R.string.glass_bottom_search_placeholder)
  val closeSearchCd = stringResource(R.string.glass_close_search_cd)
  val clearSearchCd = stringResource(R.string.glass_clear_search_cd)

  val latestTabs by rememberUpdatedState(tabs)
  val latestSelectedIndex by rememberUpdatedState(selectedIndex)
  val latestOnSelectTab by rememberUpdatedState(onSelectTab)
  val latestIsCollapsed by rememberUpdatedState(isCollapsed)
  val latestOnCollapsedChange by rememberUpdatedState(onCollapsedChange)
  val latestIsSearchExpanded by rememberUpdatedState(isSearchExpanded)
  val latestTriggerSearch by rememberUpdatedState {
    if (onSearchExpandedChange != null) {
      onSearchExpandedChange(true)
    } else {
      onSearchClick()
    }
  }

  // Master bouncy morph progress: 0f = maximized full dock, 1f = minimized compact corner pill
  val collapseAnim = remember { Animatable(if (isCollapsed) 1f else 0f) }
  val searchCollapseAnim = remember { Animatable(if (isCollapsed) 1f else 0f) }
  // Search bar expansion morph: 0f = normal nav dock + orb, 1f = close orb + expanded bottom search bar
  val searchExpandAnim = remember { Animatable(if (isSearchExpanded) 1f else 0f) }

  var isVerticalDragging by remember { mutableStateOf(false) }
  var liveVerticalProgress by remember { mutableFloatStateOf(if (isCollapsed) 1f else 0f) }

  // Pill position + trailing lag spring for authentic mercury velocity stretch
  val pillAnim = remember(tabs.size) { Animatable(selectedIndex.toFloat()) }
  var isScrubbing by remember(tabs.size) { mutableStateOf(false) }
  var liveScrubSlot by remember(tabs.size) { mutableFloatStateOf(selectedIndex.toFloat()) }
  var hoveredSlot by remember(tabs.size) { mutableStateOf<Int?>(null) }

  // Minimized horizontal swipe elastic offset + transition direction
  val miniSwipeAnim = remember { Animatable(0f) }
  var isMiniSwiping by remember { mutableStateOf(false) }
  var liveMiniSwipePx by remember { mutableFloatStateOf(0f) }
  var lastTabSlideDirection by remember { mutableIntStateOf(1) }

  // Search orb interactive swipe-up lift
  val searchLiftAnim = remember { Animatable(0f) }
  var isSearchSwiping by remember { mutableStateOf(false) }
  var liveSearchLiftPx by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(isCollapsed, isSearchExpanded, motion) {
    if (!isVerticalDragging) {
      val target = if (isCollapsed && !isSearchExpanded) 1f else 0f
      launch {
        collapseAnim.animateTo(
          targetValue = target,
          animationSpec =
            if (motion) spring(dampingRatio = 0.66f, stiffness = 360f) else snap(),
        )
      }
      launch {
        searchCollapseAnim.animateTo(
          targetValue = target,
          animationSpec =
            if (motion) spring(dampingRatio = 0.58f, stiffness = 300f) else snap(),
        )
      }
    }
  }

  LaunchedEffect(isSearchExpanded, motion) {
    searchExpandAnim.animateTo(
      targetValue = if (isSearchExpanded) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.68f, stiffness = 360f) else snap(),
    )
  }

  LaunchedEffect(selectedIndex, motion) {
    if (!isScrubbing) {
      pillAnim.animateTo(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (motion) spring(dampingRatio = 0.60f, stiffness = 420f) else snap(),
      )
    }
  }

  val pillPositionState =
    remember(tabs.size) {
      derivedStateOf { if (isScrubbing) liveScrubSlot else pillAnim.value }
    }

  val pillLagState =
    animateFloatAsState(
      targetValue = pillPositionState.value,
      animationSpec = if (motion) spring(dampingRatio = 0.76f, stiffness = 210f) else snap(),
      label = "dock_pill_mercury_lag",
    )

  val dragLiftState =
    animateFloatAsState(
      targetValue = if (isScrubbing) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.54f, stiffness = 460f) else snap(),
      label = "dock_pill_lens_lift",
    )

  val activeVisualIndex = hoveredSlot ?: selectedIndex

  val rawDockProgress = if (isVerticalDragging) liveVerticalProgress else collapseAnim.value
  val clampedDockProgress = rawDockProgress.coerceIn(0f, 1f)
  val dockOvershoot = (rawDockProgress - clampedDockProgress).coerceIn(-0.16f, 0.16f)

  val rawSearchProgress = if (isVerticalDragging) liveVerticalProgress else searchCollapseAnim.value
  val clampedSearchProgress = rawSearchProgress.coerceIn(0f, 1f)
  val searchOvershoot = (rawSearchProgress - clampedSearchProgress).coerceIn(-0.18f, 0.18f)

  val rawSearchExpand = searchExpandAnim.value
  val searchExpandProgress = rawSearchExpand.coerceIn(0f, 1f)
  val searchExpandOvershoot = (rawSearchExpand - searchExpandProgress).coerceIn(-0.14f, 0.14f)

  val bottomPad = lerp(lerp(12.dp, 6.dp, clampedDockProgress), 10.dp, searchExpandProgress)

  BoxWithConstraints(
    modifier =
      modifier
        .widthIn(max = 540.dp)
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = bottomPad),
  ) {
    val expandedDockWidth =
      (maxWidth - SearchOrbExpandedSize - DockHorizontalGap).coerceAtLeast(184.dp)
    val minimizedDockWidth = DockMinimizedWidth.coerceAtMost(expandedDockWidth)
    val expandedSearchBarWidth =
      (maxWidth - SearchCloseOrbSize - DockHorizontalGap).coerceAtLeast(200.dp)

    // Left slot morphs: Expanded Dock <-> Minimized Pill, and shrinks smoothly into Close Orb when searching
    val navDockWidth = lerp(expandedDockWidth, minimizedDockWidth, clampedDockProgress)
    val navDockHeight = lerp(DockExpandedHeight, DockMinimizedHeight, clampedDockProgress)
    val leftSlotWidth = lerp(navDockWidth, SearchCloseOrbSize, searchExpandProgress)
    val leftSlotHeight = lerp(navDockHeight, SearchCloseOrbSize, searchExpandProgress)

    // Right slot morphs: Search Orb (62dp <-> 48dp), and expands smoothly into Full Bottom Search Bar
    val orbSize = lerp(SearchOrbExpandedSize, SearchOrbMinimizedSize, clampedSearchProgress)
    val rightSlotWidth = lerp(orbSize, expandedSearchBarWidth, searchExpandProgress)
    val rightSlotHeight = lerp(orbSize, SearchExpandedBarHeight, searchExpandProgress)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Bottom,
    ) {
      // LEFT CAPSULE: Navigation Dock (Expanded/Minimized) <-> Circular Search Close Orb
      Box(
        modifier =
          Modifier.width(leftSlotWidth)
            .height(leftSlotHeight)
            .pointerInput(tabs.size, motion) {
              // Deliberately higher thresholds + directional dominance to prevent accidental swipes
              val activationSlop = maxOf(viewConfiguration.touchSlop * 2.1f, 18.dp.toPx())
              val insetPx = 5.dp.toPx()
              val maxIdx = (tabs.size - 1).coerceAtLeast(0).toFloat()
              val verticalDragRangePx = 92.dp.toPx()
              val verticalCommitPx = 40.dp.toPx()
              val miniSwipeCommitPx = 48.dp.toPx()
              val miniMaxTravelPx = 32.dp.toPx()

              fun xToSlot(x: Float): Float {
                val segW = ((size.width - 2f * insetPx) / tabs.size).coerceAtLeast(1f)
                val raw = (x - insetPx) / segW - 0.5f
                return when {
                  raw < 0f -> raw * 0.22f
                  raw > maxIdx -> maxIdx + (raw - maxIdx) * 0.22f
                  else -> raw
                }
              }

              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (latestIsSearchExpanded) return@awaitEachGesture
                val pointerId = down.id
                var totalDx = 0f
                var totalDy = 0f
                var lockedAxis = 0 // 0 = undecided, 1 = horizontal, 2 = vertical
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                val collapsedAtStart = latestIsCollapsed
                val startSelectedIdx = latestSelectedIndex
                var currentSnappedSlot = startSelectedIdx
                var thresholdArmed = false

                var settledNormally = false
                try {
                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                      if (lockedAxis == 1) {
                        val velocityX = velocityTracker.calculateVelocity().x
                        if (!collapsedAtStart) {
                          val releasePos = liveScrubSlot
                          var finalSlot = currentSnappedSlot
                          if (finalSlot == startSelectedIdx) {
                            if (velocityX > 880f && startSelectedIdx < latestTabs.lastIndex) {
                              finalSlot = startSelectedIdx + 1
                              haptics.segmentTick()
                            } else if (velocityX < -880f && startSelectedIdx > 0) {
                              finalSlot = startSelectedIdx - 1
                              haptics.segmentTick()
                            }
                          }
                          hoveredSlot = null
                          if (finalSlot != startSelectedIdx) {
                            lastTabSlideDirection = if (finalSlot > startSelectedIdx) 1 else -1
                            latestTabs.getOrNull(finalSlot)?.let { latestOnSelectTab(it.id) }
                          }
                          settledNormally = true
                          scope.launch {
                            pillAnim.snapTo(releasePos)
                            isScrubbing = false
                            pillAnim.animateTo(
                              targetValue = finalSlot.toFloat(),
                              animationSpec =
                                if (motion) spring(dampingRatio = 0.56f, stiffness = 420f) else snap(),
                            )
                          }
                        } else {
                          val currentIdx = latestSelectedIndex
                          val targetIdx =
                            when {
                              (totalDx >= miniSwipeCommitPx ||
                                (velocityX > 720f && totalDx > miniSwipeCommitPx * 0.55f)) &&
                                currentIdx < latestTabs.lastIndex -> currentIdx + 1
                              (totalDx <= -miniSwipeCommitPx ||
                                (velocityX < -720f && totalDx < -miniSwipeCommitPx * 0.55f)) &&
                                currentIdx > 0 -> currentIdx - 1
                              else -> currentIdx
                            }
                          val releaseOffset = liveMiniSwipePx
                          if (targetIdx != currentIdx) {
                            lastTabSlideDirection = if (targetIdx > currentIdx) 1 else -1
                            haptics.segmentTick()
                            latestTabs.getOrNull(targetIdx)?.let { latestOnSelectTab(it.id) }
                          } else if (abs(totalDx) >= miniSwipeCommitPx * 0.85f) {
                            haptics.tap()
                          }
                          settledNormally = true
                          scope.launch {
                            miniSwipeAnim.snapTo(releaseOffset)
                            isMiniSwiping = false
                            miniSwipeAnim.animateTo(
                              targetValue = 0f,
                              animationSpec =
                                if (motion) spring(dampingRatio = 0.54f, stiffness = 420f) else snap(),
                            )
                          }
                        }
                      } else if (lockedAxis == 2) {
                        val velocityY = velocityTracker.calculateVelocity().y
                        val shouldCollapse =
                          if (!collapsedAtStart) {
                            totalDy >= verticalCommitPx || (velocityY > 680f && totalDy > verticalCommitPx * 0.5f)
                          } else {
                            !(totalDy <= -verticalCommitPx || (velocityY < -680f && totalDy < -verticalCommitPx * 0.5f))
                          }
                        val releaseProgress = liveVerticalProgress
                        val targetProgress = if (shouldCollapse) 1f else 0f
                        if (shouldCollapse != collapsedAtStart) {
                          haptics.confirm()
                          latestOnCollapsedChange(shouldCollapse)
                        }
                        settledNormally = true
                        scope.launch {
                          collapseAnim.snapTo(releaseProgress)
                          searchCollapseAnim.snapTo(releaseProgress)
                          isVerticalDragging = false
                          val initialVel = (velocityY / verticalDragRangePx).coerceIn(-5f, 5f)
                          launch {
                            collapseAnim.animateTo(
                              targetValue = targetProgress,
                              initialVelocity = initialVel,
                              animationSpec =
                                if (motion) spring(dampingRatio = 0.64f, stiffness = 360f) else snap(),
                            )
                          }
                          launch {
                            searchCollapseAnim.animateTo(
                              targetValue = targetProgress,
                              initialVelocity = initialVel * 0.85f,
                              animationSpec =
                                if (motion) spring(dampingRatio = 0.58f, stiffness = 300f) else snap(),
                            )
                          }
                        }
                      } else {
                        settledNormally = true
                      }
                      break
                    }

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                    if (lockedAxis == 0) {
                      if (hypot(totalDx, totalDy) > activationSlop) {
                        val absX = abs(totalDx)
                        val absY = abs(totalDy)
                        // Require clear axis intent before committing to horizontal vs vertical gesture
                        lockedAxis =
                          when {
                            absX > absY * 1.35f -> 1
                            absY > absX * 1.35f -> 2
                            else -> 0
                          }
                        if (lockedAxis != 0) {
                          change.consume()
                          haptics.dragStart()
                          if (lockedAxis == 1) {
                            if (!collapsedAtStart) {
                              val startRaw = xToSlot(change.position.x)
                              currentSnappedSlot = startRaw.roundToInt().coerceIn(0, latestTabs.lastIndex)
                              liveScrubSlot = startRaw
                              hoveredSlot = currentSnappedSlot
                              isScrubbing = true
                            } else {
                              liveMiniSwipePx = liquidRubberBand(totalDx, miniMaxTravelPx)
                              isMiniSwiping = true
                            }
                          } else {
                            val base = if (collapsedAtStart) 1f else 0f
                            val rawP = base + totalDy / verticalDragRangePx
                            liveVerticalProgress =
                              when {
                                rawP < 0f -> rawP * 0.18f
                                rawP > 1f -> 1f + (rawP - 1f) * 0.18f
                                else -> rawP
                              }
                            isVerticalDragging = true
                          }
                        }
                      }
                    } else if (lockedAxis == 1) {
                      change.consume()
                      if (!collapsedAtStart) {
                        val raw = xToSlot(change.position.x)
                        liveScrubSlot = raw
                        val snapped = raw.roundToInt().coerceIn(0, latestTabs.lastIndex)
                        if (snapped != currentSnappedSlot) {
                          currentSnappedSlot = snapped
                          hoveredSlot = snapped
                          haptics.segmentTick()
                        }
                      } else {
                        liveMiniSwipePx = liquidRubberBand(totalDx, miniMaxTravelPx)
                        val canMoveInDir =
                          (totalDx > 0f && latestSelectedIndex < latestTabs.lastIndex) ||
                            (totalDx < 0f && latestSelectedIndex > 0)
                        val armedNow = canMoveInDir && abs(totalDx) >= miniSwipeCommitPx
                        if (armedNow && !thresholdArmed) {
                          thresholdArmed = true
                          haptics.gestureThresholdActivate()
                        } else if (!armedNow && thresholdArmed) {
                          thresholdArmed = false
                          haptics.gestureThresholdDeactivate()
                        }
                      }
                    } else {
                      change.consume()
                      val base = if (collapsedAtStart) 1f else 0f
                      val rawP = base + totalDy / verticalDragRangePx
                      liveVerticalProgress =
                        when {
                          rawP < 0f -> rawP * 0.18f
                          rawP > 1f -> 1f + (rawP - 1f) * 0.18f
                          else -> rawP
                        }
                      val armedNow =
                        if (!collapsedAtStart) totalDy >= verticalCommitPx
                        else totalDy <= -verticalCommitPx
                      if (armedNow && !thresholdArmed) {
                        thresholdArmed = true
                        haptics.gestureThresholdActivate()
                      } else if (!armedNow && thresholdArmed) {
                        thresholdArmed = false
                        haptics.gestureThresholdDeactivate()
                      }
                    }
                  }
                } finally {
                  if (!settledNormally && lockedAxis != 0) {
                    hoveredSlot = null
                    isScrubbing = false
                    isMiniSwiping = false
                    isVerticalDragging = false
                    liveMiniSwipePx = 0f
                    val resetSlot = latestSelectedIndex.toFloat()
                    val resetCollapse = if (latestIsCollapsed && !latestIsSearchExpanded) 1f else 0f
                    scope.launch {
                      pillAnim.snapTo(resetSlot)
                      miniSwipeAnim.snapTo(0f)
                      collapseAnim.snapTo(resetCollapse)
                      searchCollapseAnim.snapTo(resetCollapse)
                    }
                  }
                }
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        val currentMiniSwipePx = if (isMiniSwiping) liveMiniSwipePx else miniSwipeAnim.value
        val dragLift = dragLiftState.value

        GlassSurface(
          cornerRadius = leftSlotHeight / 2f,
          elevation = GlassElevation.FLOATING,
          useBackdropBlur = true,
          enableRefraction = true,
          enableTopSheen = true,
          enableAnimatedSheen = true,
          modifier =
            Modifier.fillMaxSize()
              .graphicsLayer {
                val liftScale = 1f + dragLift * 0.018f
                val bounceX = 1f - dockOvershoot * 0.14f - searchExpandOvershoot * 0.12f
                val bounceY = 1f + dockOvershoot * 0.16f + searchExpandOvershoot * 0.14f
                scaleX = bounceX * liftScale
                scaleY = bounceY * liftScale
                translationY = dockOvershoot * 9.dp.toPx()
                translationX = currentMiniSwipePx
              }
              .testTag("glass_floating_tab_bar"),
        ) {
          val navContentAlpha = (1f - searchExpandProgress * 2.2f).coerceIn(0f, 1f)
          val closeOrbAlpha = ((searchExpandProgress - 0.45f) * 2.2f).coerceIn(0f, 1f)

          val expandedAlpha = ((1f - clampedDockProgress * 2.1f).coerceIn(0f, 1f)) * navContentAlpha
          val minimizedAlpha = (((clampedDockProgress - 0.42f) * 2.1f).coerceIn(0f, 1f)) * navContentAlpha

          // Expanded 3-tab Liquid Glass dock row
          if (clampedDockProgress < 0.78f && searchExpandProgress < 0.62f) {
            Row(
              modifier =
                Modifier.fillMaxSize()
                  .graphicsLayer {
                    alpha = expandedAlpha
                    val s = 0.92f + 0.08f * expandedAlpha
                    scaleX = s
                    scaleY = s
                  }
                  .liquidGlassDockPill(
                    positionState = pillPositionState,
                    lagPositionState = pillLagState,
                    dragLiftState = dragLiftState,
                    count = tabs.size,
                    isDark = env.isDark,
                    accentColor = colorScheme.primary,
                    shadowColor = tokens.pillShadow,
                    inset = 5.dp,
                  )
                  .liquidGlassTabMagnifyingLens(
                    positionState = pillPositionState,
                    lagPositionState = pillLagState,
                    dragLiftState = dragLiftState,
                    count = tabs.size,
                    inset = 5.dp,
                  )
                  .padding(5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
              tabs.forEachIndexed { index, tab ->
                val isSelected = tab.id == selectedId
                val isHighlighted = index == activeVisualIndex
                val contentColor by
                  animateColorAsState(
                    targetValue =
                      if (isHighlighted) selectedContentColor
                      else if (env.isDark) PrismColors.DarkTabInactive
                      else PrismColors.LightTabInactive,
                    label = "tab_content_color_${tab.id}",
                  )
                val iconScale =
                  animateFloatAsState(
                    targetValue = if (isHighlighted) 1.14f else 1f,
                    animationSpec =
                      if (motion) spring(dampingRatio = 0.44f, stiffness = 460f) else snap(),
                    label = "tab_icon_scale_${tab.id}",
                  )
                val iconLiftDp =
                  animateDpAsState(
                    targetValue = if (isHighlighted) (-1.5).dp else 0.dp,
                    animationSpec =
                      if (motion) spring(dampingRatio = 0.48f, stiffness = 440f) else snap(),
                    label = "tab_icon_lift_${tab.id}",
                  )

                Box(
                  modifier =
                    Modifier.weight(1f)
                      .fillMaxHeight()
                      .clip(RoundedCornerShape(29.dp))
                      .glassClickable(
                        enabled = clampedDockProgress < 0.35f && searchExpandProgress < 0.25f,
                        role = Role.Tab,
                        hapticOnClick = false,
                      ) {
                        if (!isSelected) {
                          lastTabSlideDirection = if (index > selectedIndex) 1 else -1
                          haptics.segmentTick()
                        } else {
                          haptics.tap()
                        }
                        onSelectTab(tab.id)
                      }
                      .semantics {
                        selected = isSelected
                        stateDescription =
                          if (isSelected) selectedStateLabel else notSelectedStateLabel
                        contentDescription = tab.label
                      }
                      .testTag(tab.testTag)
                      .padding(horizontal = 6.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                  ) {
                    Icon(
                      imageVector = if (isHighlighted) tab.selectedIcon else tab.icon,
                      contentDescription = null,
                      tint = contentColor,
                      modifier =
                        Modifier.size(22.dp).graphicsLayer {
                          val s = iconScale.value
                          scaleX = s
                          scaleY = s
                          translationY = iconLiftDp.value.toPx()
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                      text = tab.label,
                      style =
                        MaterialTheme.typography.labelMedium.copy(
                          fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                      color = contentColor,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }

          // Minimized sleek Liquid Glass active-tab pill
          if (clampedDockProgress > 0.24f && searchExpandProgress < 0.62f) {
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .graphicsLayer {
                    alpha = minimizedAlpha
                    val s = 0.86f + 0.14f * minimizedAlpha
                    scaleX = s
                    scaleY = s
                  }
                  .clip(CircleShape)
                  .glassClickable(
                    enabled = clampedDockProgress >= 0.45f && searchExpandProgress < 0.25f,
                    role = Role.Button,
                    hapticOnClick = false,
                  ) {
                    haptics.tap()
                    onCollapsedChange(false)
                  }
                  .semantics {
                    contentDescription = "$expandNavCd, ${selectedTab.label}"
                    stateDescription = minimizedNavState
                  }
                  .testTag("glass_minimized_tab_pill")
                  .padding(horizontal = 10.dp),
              contentAlignment = Alignment.CenterStart,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(32.dp)
                      .clip(CircleShape)
                      .background(
                        colorScheme.primary.copy(alpha = if (env.isDark) 0.26f else 0.15f)
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                      if (motion) {
                        val dir = lastTabSlideDirection
                        (slideInHorizontally(
                          animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f),
                          initialOffsetX = { dir * it / 2 },
                        ) + fadeIn(tween(140)) + scaleIn(initialScale = 0.78f))
                          .togetherWith(
                            slideOutHorizontally(
                              animationSpec = tween(110),
                              targetOffsetX = { -dir * it / 2 },
                            ) + fadeOut(tween(100)) + scaleOut(targetScale = 0.78f)
                          )
                      } else {
                        EnterTransition.None togetherWith ExitTransition.None
                      }
                    },
                    label = "minimized_tab_icon",
                  ) { tab ->
                    Icon(
                      imageVector = tab.selectedIcon,
                      contentDescription = null,
                      tint = selectedContentColor,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  AnimatedContent(
                    targetState = selectedTab.label,
                    transitionSpec = {
                      if (motion) {
                        val dir = lastTabSlideDirection
                        (slideInHorizontally(
                          animationSpec = spring(dampingRatio = 0.68f, stiffness = 440f),
                          initialOffsetX = { dir * it / 3 },
                        ) + fadeIn(tween(140)))
                          .togetherWith(
                            slideOutHorizontally(
                              animationSpec = tween(110),
                              targetOffsetX = { -dir * it / 3 },
                            ) + fadeOut(tween(100))
                          )
                      } else {
                        EnterTransition.None togetherWith ExitTransition.None
                      }
                    },
                    label = "minimized_tab_label",
                  ) { label ->
                    Text(
                      text = label,
                      style =
                        MaterialTheme.typography.labelLarge.copy(
                          fontWeight = FontWeight.SemiBold,
                        ),
                      color = colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }

                  Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    tabs.forEachIndexed { idx, _ ->
                      val isCurrent = idx == selectedIndex
                      val dotWidth by
                        animateDpAsState(
                          targetValue = if (isCurrent) 12.dp else 4.dp,
                          animationSpec =
                            if (motion) spring(dampingRatio = 0.58f, stiffness = 460f) else snap(),
                          label = "mini_dot_w_$idx",
                        )
                      val dotColor by
                        animateColorAsState(
                          targetValue =
                            if (isCurrent) colorScheme.primary
                            else colorScheme.onSurface.copy(alpha = if (env.isDark) 0.30f else 0.22f),
                          label = "mini_dot_c_$idx",
                        )
                      Box(
                        modifier =
                          Modifier.width(dotWidth)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                      )
                    }
                  }
                }
              }
            }
          }

          // Search-mode Close orb button (rotates gently into place as dock shrinks into a circle)
          if (searchExpandProgress > 0.35f) {
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .clip(CircleShape)
                  .glassClickable(
                    enabled = searchExpandProgress > 0.65f,
                    role = Role.Button,
                    hapticOnClick = false,
                    onClick = closeSearchBar,
                  )
                  .semantics { contentDescription = closeSearchCd }
                  .testTag("glass_bottom_search_close_button"),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier =
                  Modifier.size(22.dp).graphicsLayer {
                    alpha = closeOrbAlpha
                    val s = 0.65f + 0.35f * closeOrbAlpha
                    scaleX = s
                    scaleY = s
                    rotationZ = (1f - closeOrbAlpha) * -90f
                  },
              )
            }
          }
        }
      }

      // RIGHT CAPSULE: Circular Search Orb <-> Expanded Bottom Liquid Glass Search Bar
      Box(
        modifier =
          Modifier.width(rightSlotWidth)
            .height(rightSlotHeight)
            .defaultMinSize(
              minWidth = PrismDimens.TouchTargetMin,
              minHeight = PrismDimens.TouchTargetMin,
            )
            .pointerInput(motion) {
              val activationSlop = maxOf(viewConfiguration.touchSlop * 2.1f, 18.dp.toPx())
              val commitUpPx = 36.dp.toPx()
              val maxLiftPx = 26.dp.toPx()
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (latestIsSearchExpanded) return@awaitEachGesture
                val pointerId = down.id
                var totalDx = 0f
                var totalDy = 0f
                var swipingUp = false
                var armed = false
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var settledNormally = false
                try {
                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                      if (swipingUp) {
                        val vy = velocityTracker.calculateVelocity().y
                        val releaseLift = liveSearchLiftPx
                        val trigger =
                          totalDy <= -commitUpPx || (vy < -680f && totalDy < -commitUpPx * 0.55f)
                        if (trigger) {
                          haptics.confirm()
                          latestTriggerSearch()
                        }
                        settledNormally = true
                        scope.launch {
                          searchLiftAnim.snapTo(releaseLift)
                          isSearchSwiping = false
                          searchLiftAnim.animateTo(
                            targetValue = 0f,
                            animationSpec =
                              if (motion) spring(dampingRatio = 0.52f, stiffness = 420f) else snap(),
                          )
                        }
                      } else {
                        settledNormally = true
                      }
                      break
                    }
                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (!swipingUp && totalDy < -activationSlop && abs(totalDy) > abs(totalDx) * 1.35f) {
                      swipingUp = true
                      change.consume()
                      haptics.dragStart()
                      liveSearchLiftPx = liquidRubberBand(totalDy, maxLiftPx)
                      isSearchSwiping = true
                    } else if (swipingUp) {
                      change.consume()
                      liveSearchLiftPx = liquidRubberBand(totalDy, maxLiftPx)
                      val nowArmed = totalDy <= -commitUpPx
                      if (nowArmed && !armed) {
                        armed = true
                        haptics.gestureThresholdActivate()
                      } else if (!nowArmed && armed) {
                        armed = false
                        haptics.gestureThresholdDeactivate()
                      }
                    }
                  }
                } finally {
                  if (!settledNormally && swipingUp) {
                    isSearchSwiping = false
                    liveSearchLiftPx = 0f
                    scope.launch { searchLiftAnim.snapTo(0f) }
                  }
                }
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        val currentSearchLiftPx = if (isSearchSwiping) liveSearchLiftPx else searchLiftAnim.value
        val iconSize = lerp(25.dp, 21.dp, clampedSearchProgress)
        val orbAlpha = (1f - searchExpandProgress * 2.4f).coerceIn(0f, 1f)
        val searchInputAlpha = ((searchExpandProgress - 0.35f) * 1.85f).coerceIn(0f, 1f)

        GlassSurface(
          cornerRadius = rightSlotHeight / 2f,
          elevation = GlassElevation.FLOATING,
          useBackdropBlur = true,
          enableRefraction = true,
          enableTopSheen = true,
          enableAnimatedSheen = true,
          accentTint = if (searchExpandProgress > 0.3f) colorScheme.primary.copy(alpha = 0.10f) else null,
          onClick =
            if (!isSearchExpanded) {
              {
                haptics.tap()
                latestTriggerSearch()
              }
            } else {
              null
            },
          hapticOnClick = false,
          clickRole = Role.Button,
          modifier =
            Modifier.fillMaxSize()
              .graphicsLayer {
                val liftProgress = (-currentSearchLiftPx / 26.dp.toPx()).coerceIn(0f, 1f)
                val stretchX =
                  1f - searchOvershoot * 0.16f + searchExpandOvershoot * 0.10f - liftProgress * 0.05f
                val stretchY =
                  1f + searchOvershoot * 0.18f - searchExpandOvershoot * 0.08f + liftProgress * 0.08f
                scaleX = stretchX
                scaleY = stretchY
                translationY = searchOvershoot * 10.dp.toPx() + currentSearchLiftPx
                rotationZ = if (searchExpandProgress < 0.05f) searchOvershoot * -14f else 0f
              }
              .semantics {
                if (!isSearchExpanded) {
                  contentDescription = openSearchCd
                }
              }
              .testTag(if (isSearchExpanded) "glass_bottom_search_bar" else "glass_search_orb_button"),
        ) {
          // Resting Search Orb icon
          if (searchExpandProgress < 0.55f) {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = colorScheme.onSurface,
              modifier =
                Modifier.align(Alignment.Center)
                  .size(iconSize)
                  .graphicsLayer {
                    alpha = orbAlpha
                    val s = 0.80f + 0.20f * orbAlpha
                    scaleX = s
                    scaleY = s
                  },
            )
          }

          // Expanded Bottom Liquid Glass Search Input Row
          if (searchExpandProgress > 0.28f) {
            Row(
              modifier =
                Modifier.fillMaxSize()
                  .graphicsLayer {
                    alpha = searchInputAlpha
                    translationX = (1f - searchInputAlpha) * 14.dp.toPx()
                  }
                  .padding(start = 16.dp, end = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(21.dp),
              )
              Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                  Text(
                    text = bottomSearchPlaceholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                BasicTextField(
                  value = searchQuery,
                  onValueChange = onSearchQueryChange,
                  singleLine = true,
                  textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                  cursorBrush = SolidColor(colorScheme.primary),
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                  keyboardActions =
                    KeyboardActions(
                      onSearch = {
                        haptics.tap()
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSearchSubmit()
                      }
                    ),
                  modifier =
                    Modifier.fillMaxWidth()
                      .focusRequester(searchFocusRequester)
                      .testTag("glass_bottom_search_input"),
                )
              }
              AnimatedVisibility(
                visible = searchQuery.isNotEmpty(),
                enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.72f),
                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.72f),
              ) {
                IconButton(
                  onClick = {
                    haptics.tap()
                    onSearchQueryChange("")
                  },
                  modifier =
                    Modifier.size(PrismDimens.TouchTargetMin)
                      .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                      .testTag("glass_bottom_search_clear_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = clearSearchCd,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Adaptive floating Liquid Glass side rail for Medium/Expanded screens (tablets & foldables)
 * featuring AGSL bezel refraction, vertical sliding liquid lens pill, and vertical drag-scrubbing.
 */
@Composable
fun GlassNavigationRail(
  tabs: List<GlassTabItem>,
  selectedId: String,
  onSelectTab: (String) -> Unit,
  onSearchClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (tabs.isEmpty()) return
  val env = LocalGlassEnvironment.current
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()
  val colorScheme = MaterialTheme.colorScheme
  val searchDocumentsCd = stringResource(R.string.glass_search_documents_cd)
  val selectedStateLabel = stringResource(R.string.glass_state_selected)
  val notSelectedStateLabel = stringResource(R.string.glass_state_not_selected)

  val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
  val latestTabs by rememberUpdatedState(tabs)
  val latestSelectedIndex by rememberUpdatedState(selectedIndex)
  val latestOnSelectTab by rememberUpdatedState(onSelectTab)

  val pillAnim = remember(tabs.size) { Animatable(selectedIndex.toFloat()) }
  var isDragging by remember(tabs.size) { mutableStateOf(false) }
  var liveDragSlot by remember(tabs.size) { mutableFloatStateOf(selectedIndex.toFloat()) }
  var hoveredSlot by remember(tabs.size) { mutableStateOf<Int?>(null) }

  LaunchedEffect(selectedIndex, motion) {
    if (!isDragging) {
      pillAnim.animateTo(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (motion) spring(dampingRatio = 0.62f, stiffness = 420f) else snap(),
      )
    }
  }

  val pillPosState =
    remember(tabs.size) { derivedStateOf { if (isDragging) liveDragSlot else pillAnim.value } }
  val pillLagState =
    animateFloatAsState(
      targetValue = pillPosState.value,
      animationSpec = if (motion) spring(dampingRatio = 0.76f, stiffness = 210f) else snap(),
      label = "rail_pill_lag",
    )
  val dragLiftState =
    animateFloatAsState(
      targetValue = if (isDragging) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.56f, stiffness = 460f) else snap(),
      label = "rail_pill_lift",
    )
  val activeVisualIndex = hoveredSlot ?: selectedIndex

  Column(
    modifier =
      modifier
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 12.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    GlassSurface(
      cornerRadius = 28.dp,
      shape = CircleShape,
      elevation = GlassElevation.FLOATING,
      useBackdropBlur = true,
      enableRefraction = true,
      enableAnimatedSheen = true,
      onClick = {
        haptics.tap()
        onSearchClick()
      },
      hapticOnClick = false,
      clickRole = Role.Button,
      modifier = Modifier.size(56.dp).testTag("glass_rail_search_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = searchDocumentsCd,
        tint = colorScheme.primary,
        modifier = Modifier.align(Alignment.Center).size(24.dp),
      )
    }

    GlassSurface(
      cornerRadius = 30.dp,
      elevation = GlassElevation.FLOATING,
      useBackdropBlur = true,
      enableRefraction = true,
      enableAnimatedSheen = true,
      modifier = Modifier.testTag("glass_navigation_rail"),
    ) {
      Column(
        modifier =
          Modifier.pointerInput(tabs.size, motion) {
              val activationSlop = maxOf(viewConfiguration.touchSlop * 2.0f, 16.dp.toPx())
              val vInsetPx = 10.dp.toPx()
              val maxIdx = (tabs.size - 1).coerceAtLeast(0).toFloat()

              fun yToSlot(y: Float): Float {
                val segH = ((size.height - 2f * vInsetPx) / tabs.size).coerceAtLeast(1f)
                val raw = (y - vInsetPx) / segH - 0.5f
                return when {
                  raw < 0f -> raw * 0.22f
                  raw > maxIdx -> maxIdx + (raw - maxIdx) * 0.22f
                  else -> raw
                }
              }

              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                var totalDy = 0f
                var scrubbing = false
                var currentSnapped = latestSelectedIndex

                var settledNormally = false
                try {
                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                      if (scrubbing) {
                        val releasePos = liveDragSlot
                        val finalSlot = currentSnapped
                        hoveredSlot = null
                        if (finalSlot != latestSelectedIndex) {
                          latestTabs.getOrNull(finalSlot)?.let { latestOnSelectTab(it.id) }
                        }
                        settledNormally = true
                        scope.launch {
                          pillAnim.snapTo(releasePos)
                          isDragging = false
                          pillAnim.animateTo(
                            targetValue = finalSlot.toFloat(),
                            animationSpec =
                              if (motion) spring(dampingRatio = 0.58f, stiffness = 420f) else snap(),
                          )
                        }
                      } else {
                        settledNormally = true
                      }
                      break
                    }
                    val delta = change.positionChange()
                    totalDy += delta.y
                    if (!scrubbing && abs(totalDy) > activationSlop) {
                      scrubbing = true
                      change.consume()
                      haptics.dragStart()
                      val raw = yToSlot(change.position.y)
                      currentSnapped = raw.roundToInt().coerceIn(0, latestTabs.lastIndex)
                      liveDragSlot = raw
                      hoveredSlot = currentSnapped
                      isDragging = true
                    } else if (scrubbing) {
                      change.consume()
                      val raw = yToSlot(change.position.y)
                      liveDragSlot = raw
                      val snapped = raw.roundToInt().coerceIn(0, latestTabs.lastIndex)
                      if (snapped != currentSnapped) {
                        currentSnapped = snapped
                        hoveredSlot = snapped
                        haptics.segmentTick()
                      }
                    }
                  }
                } finally {
                  if (!settledNormally && scrubbing) {
                    hoveredSlot = null
                    isDragging = false
                    val resetSlot = latestSelectedIndex.toFloat()
                    scope.launch { pillAnim.snapTo(resetSlot) }
                  }
                }
              }
            }
            .liquidGlassRailPill(
              positionState = pillPosState,
              lagPositionState = pillLagState,
              dragLiftState = dragLiftState,
              count = tabs.size,
              isDark = env.isDark,
              accentColor = colorScheme.primary,
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = tab.id == selectedId
          val isHighlighted = index == activeVisualIndex
          val tint by
            animateColorAsState(
              targetValue = if (isHighlighted) colorScheme.primary else colorScheme.onSurfaceVariant,
              label = "rail_tint_${tab.id}",
            )
          val iconScale =
            animateFloatAsState(
              targetValue = if (isHighlighted) 1.13f else 1f,
              animationSpec =
                if (motion) spring(dampingRatio = 0.46f, stiffness = 460f) else snap(),
              label = "rail_icon_scale_${tab.id}",
            )
          Column(
            modifier =
              Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                .clip(RoundedCornerShape(20.dp))
                .glassClickable(role = Role.Tab, hapticOnClick = false) {
                  if (!isSelected) haptics.segmentTick() else haptics.tap()
                  onSelectTab(tab.id)
                }
                .semantics {
                  selected = isSelected
                  stateDescription = if (isSelected) selectedStateLabel else notSelectedStateLabel
                  contentDescription = tab.label
                }
                .testTag("rail_${tab.testTag}")
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = if (isHighlighted) tab.selectedIcon else tab.icon,
              contentDescription = null,
              tint = tint,
              modifier =
                Modifier.size(22.dp).graphicsLayer {
                  val s = iconScale.value
                  scaleX = s
                  scaleY = s
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
              text = tab.label,
              style = MaterialTheme.typography.labelMedium,
              color = tint,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

data class GlassToolbarAction(
  val icon: ImageVector,
  val contentDescription: String,
  val testTag: String,
  val active: Boolean = false,
  val onClick: () -> Unit,
)

/**
 * Grouped floating Liquid Glass toolbar pill for top chrome actions (sort, layout toggle,
 * settings, viewer tools).
 */
@Composable
fun GlassToolbarGroup(
  actions: List<GlassToolbarAction>,
  modifier: Modifier = Modifier,
) {
  val motion = LocalGlassMotion.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()

  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.FLOATING,
    useBackdropBlur = true,
    enableRefraction = true,
    modifier = modifier.height(48.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions.forEachIndexed { index, action ->
        val tint by
          animateColorAsState(
            targetValue = if (action.active) colorScheme.primary else colorScheme.onSurface,
            label = "toolbar_tint_${action.testTag}",
          )
        val iconScale =
          animateFloatAsState(
            targetValue = if (action.active) 1.08f else 1f,
            animationSpec =
              if (motion) spring(dampingRatio = 0.62f, stiffness = 460f) else snap(),
            label = "toolbar_scale_${action.testTag}",
          )
        IconButton(
          onClick = {
            haptics.tap()
            action.onClick()
          },
          modifier =
            Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .testTag(action.testTag),
        ) {
          Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
            tint = tint,
            modifier =
              Modifier.size(21.dp).graphicsLayer {
                val s = iconScale.value
                scaleX = s
                scaleY = s
              },
          )
        }
        if (index < actions.lastIndex) {
          Box(
            modifier =
              Modifier.width(1.dp)
                .height(20.dp)
                .background(colorScheme.onSurface.copy(alpha = 0.12f))
          )
        }
      }
    }
  }
}

/**
 * Liquid Glass search capsule used on the Search screen and scoped folder search overlays.
 */
@Composable
fun GlassSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  onSearchSubmit: () -> Unit = {},
  onFilterClick: (() -> Unit)? = null,
) {
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()

  val clearSearchCd = stringResource(R.string.glass_clear_search_cd)
  val searchFiltersCd = stringResource(R.string.glass_search_filters_cd)

  GlassSurface(
    cornerRadius = 28.dp,
    elevation = GlassElevation.FLOATING,
    useBackdropBlur = true,
    enableRefraction = true,
    modifier = modifier.fillMaxWidth().height(54.dp).testTag("glass_search_field"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(start = 16.dp, end = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = colorScheme.onSurfaceVariant,
        modifier = Modifier.size(21.dp),
      )
      Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
          Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
          cursorBrush = SolidColor(colorScheme.primary),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
          modifier = Modifier.fillMaxWidth().testTag("glass_search_input"),
        )
      }
      AnimatedVisibility(
        visible = query.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
      ) {
        IconButton(
          onClick = {
            haptics.tap()
            onQueryChange("")
          },
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .testTag("glass_search_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = clearSearchCd,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
          )
        }
      }
      if (onFilterClick != null) {
        IconButton(
          onClick = {
            haptics.tap()
            onFilterClick()
          },
          modifier =
            Modifier.size(PrismDimens.TouchTargetMin)
              .defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
              .testTag("glass_search_filter_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = searchFiltersCd,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
