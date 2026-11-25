package com.prism.docs.core.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.prism.docs.core.ui.R
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class GlassTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val selectedIcon: ImageVector = icon,
  val testTag: String = "tab_$id",
)

/**
 * Floating Liquid Glass bottom capsule navigation bar paired with a detached circular Search orb.
 *
 * Supports both tap and 120fps horizontal finger-scrubbing across tabs with live haptic ticks,
 * GPU icon bounce, and constant layout bounds (68.dp) so scrolling never triggers re-measurement
 * or Haze blur buffer reallocations.
 */
@Composable
fun GlassFloatingTabBar(
  tabs: List<GlassTabItem>,
  selectedId: String,
  onSelectTab: (String) -> Unit,
  onSearchClick: () -> Unit,
  isCollapsed: Boolean,
  modifier: Modifier = Modifier,
) {
  if (tabs.isEmpty()) return
  val env = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()
  val colorScheme = MaterialTheme.colorScheme

  val compactProgress =
    animateFloatAsState(
      targetValue = if (isCollapsed) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.82f, stiffness = 420f) else snap(),
      label = "tab_bar_scroll_tuck",
    )

  val selectedContentColor = tokens.tabActiveContent
  val selectedPillBg = tokens.segmentThumb
  val selectedPillBorder = if (env.isDark) tokens.segmentThumbBorder else Color.Transparent
  val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
  val pillBrush = remember(selectedPillBg) { SolidColor(selectedPillBg) }
  val selectedStateLabel = stringResource(R.string.glass_state_selected)
  val notSelectedStateLabel = stringResource(R.string.glass_state_not_selected)
  val openSearchCd = stringResource(R.string.glass_open_search_cd)

  val latestTabs by rememberUpdatedState(tabs)
  val latestSelectedIndex by rememberUpdatedState(selectedIndex)
  val latestOnSelectTab by rememberUpdatedState(onSelectTab)

  val pillAnim = remember(tabs.size) { Animatable(selectedIndex.toFloat()) }
  var isDragging by remember(tabs.size) { mutableStateOf(false) }
  var liveDragSlot by remember(tabs.size) { mutableFloatStateOf(selectedIndex.toFloat()) }
  var hoveredSlot by remember(tabs.size) { mutableStateOf<Int?>(null) }

  LaunchedEffect(selectedIndex) {
    if (!isDragging) {
      pillAnim.animateTo(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (motion) spring(dampingRatio = 0.78f, stiffness = 420f) else snap(),
      )
    }
  }

  val pillPositionState =
    remember(tabs.size) {
      derivedStateOf { if (isDragging) liveDragSlot else pillAnim.value }
    }
  val activeVisualIndex = hoveredSlot ?: selectedIndex

  Row(
    modifier =
      modifier
        .widthIn(max = 560.dp)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 12.dp)
        .graphicsLayer {
          val p = compactProgress.value
          val s = 1f - 0.045f * p
          scaleX = s
          scaleY = s
          translationY = 4.dp.toPx() * p
        },
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlassSurface(
      cornerRadius = 34.dp,
      elevation = GlassElevation.FLOATING,
      useBackdropBlur = true,
      enableAnimatedSheen = true,
      modifier =
        Modifier.weight(1f)
          .height(68.dp)
          .testTag("glass_floating_tab_bar"),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .fillMaxHeight()
            .pointerInput(tabs.size, motion) {
              val insetPx = 5.dp.toPx()
              val maxIdx = (tabs.size - 1).coerceAtLeast(0).toFloat()
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
                val slop =
                  awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                  }
                if (slop != null) {
                  haptics.dragStart()
                  val startRaw = xToSlot(slop.position.x)
                  var currentSnapped = startRaw.roundToInt().coerceIn(0, latestTabs.lastIndex)
                  liveDragSlot = startRaw
                  hoveredSlot = currentSnapped
                  isDragging = true
                  horizontalDrag(slop.id) { change ->
                    change.consume()
                    val raw = xToSlot(change.position.x)
                    liveDragSlot = raw
                    val snapped = raw.roundToInt().coerceIn(0, latestTabs.lastIndex)
                    if (snapped != currentSnapped) {
                      currentSnapped = snapped
                      hoveredSlot = snapped
                      haptics.segmentTick()
                    }
                  }
                  val releasePos = liveDragSlot
                  val finalSlot = currentSnapped
                  hoveredSlot = null
                  if (finalSlot != latestSelectedIndex) {
                    latestTabs.getOrNull(finalSlot)?.let { latestOnSelectTab(it.id) }
                  }
                  scope.launch {
                    pillAnim.snapTo(releasePos)
                    isDragging = false
                    pillAnim.animateTo(
                      targetValue = finalSlot.toFloat(),
                      animationSpec =
                        if (motion) spring(dampingRatio = 0.74f, stiffness = 460f) else snap(),
                    )
                  }
                }
              }
            }
            .glassSlidingPill(
              selectedIndex = selectedIndex,
              count = tabs.size,
              inset = 5.dp,
              cornerRadius = 999.dp,
              brush = pillBrush,
              shadowColor = tokens.pillShadow,
              borderColor = selectedPillBorder,
              positionOverride = pillPositionState,
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
              targetValue = if (isHighlighted) 1.09f else 1f,
              animationSpec =
                if (motion) spring(dampingRatio = 0.56f, stiffness = 460f) else snap(),
              label = "tab_icon_scale_${tab.id}",
            )

          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(29.dp))
                .glassClickable(role = Role.Tab, hapticOnClick = false) {
                  if (!isSelected) {
                    haptics.segmentTick()
                  } else {
                    haptics.tap()
                  }
                  onSelectTab(tab.id)
                }
                .semantics {
                  selected = isSelected
                  stateDescription = if (isSelected) selectedStateLabel else notSelectedStateLabel
                  contentDescription = tab.label
                }
                .testTag(tab.testTag)
                .padding(horizontal = 8.dp, vertical = 4.dp),
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

    // Detached circular Liquid Glass Search orb (opens Search screen)
    GlassSurface(
      cornerRadius = 34.dp,
      shape = CircleShape,
      elevation = GlassElevation.FLOATING,
      useBackdropBlur = true,
      onClick = {
        haptics.tap()
        onSearchClick()
      },
      hapticOnClick = false,
      clickRole = Role.Button,
      modifier =
        Modifier.size(68.dp)
          .defaultMinSize(minWidth = PrismDimens.TouchTargetMin, minHeight = PrismDimens.TouchTargetMin)
          .semantics { contentDescription = openSearchCd }
          .testTag("glass_search_orb_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = colorScheme.onSurface,
        modifier = Modifier.align(Alignment.Center).size(26.dp),
      )
    }
  }
}

/**
 * Adaptive floating Liquid Glass side rail for Medium/Expanded screens (tablets & foldables).
 */
@Composable
fun GlassNavigationRail(
  tabs: List<GlassTabItem>,
  selectedId: String,
  onSelectTab: (String) -> Unit,
  onSearchClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val env = LocalGlassEnvironment.current
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val colorScheme = MaterialTheme.colorScheme
  val searchDocumentsCd = stringResource(R.string.glass_search_documents_cd)
  val selectedStateLabel = stringResource(R.string.glass_state_selected)
  val notSelectedStateLabel = stringResource(R.string.glass_state_not_selected)

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
      modifier = Modifier.testTag("glass_navigation_rail"),
    ) {
      Column(
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        tabs.forEach { tab ->
          val isSelected = tab.id == selectedId
          val bg by
            animateColorAsState(
              targetValue =
                if (isSelected) {
                  colorScheme.primary.copy(alpha = if (env.isDark) 0.24f else 0.14f)
                } else {
                  Color.Transparent
                },
              label = "rail_bg_${tab.id}",
            )
          val tint by
            animateColorAsState(
              targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
              label = "rail_tint_${tab.id}",
            )
          val iconScale =
            animateFloatAsState(
              targetValue = if (isSelected) 1.08f else 1f,
              animationSpec =
                if (motion) spring(dampingRatio = 0.60f, stiffness = 460f) else snap(),
              label = "rail_icon_scale_${tab.id}",
            )
          Column(
            modifier =
              Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
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
              imageVector = if (isSelected) tab.selectedIcon else tab.icon,
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
