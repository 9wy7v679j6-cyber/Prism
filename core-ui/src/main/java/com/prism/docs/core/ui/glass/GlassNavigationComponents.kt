package com.prism.docs.core.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.PrismMotion

data class GlassTabItem(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val selectedIcon: ImageVector = icon,
  val testTag: String = "tab_$id",
)

/**
 * Floating Liquid Glass bottom capsule navigation bar inspired by iOS Preview.
 * Collapses into a compact active-destination pill when scrolling downward, paired with a
 * detached circular Search glass orb that morphs into the search experience.
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
  val env = LocalGlassEnvironment.current
  val haptics = LocalHapticFeedback.current
  val colorScheme = MaterialTheme.colorScheme
  val springSpec = PrismMotion.fluidSpring<androidx.compose.ui.unit.Dp>(env.reduceMotion)

  val capsuleHeight by
    animateDpAsState(
      targetValue = if (isCollapsed) 56.dp else 68.dp,
      animationSpec = springSpec,
      label = "tab_capsule_height",
    )

  val selectedBlue =
    if (env.isDark) colorScheme.primary else com.prism.docs.core.ui.theme.PrismColors.IndigoBlue
  val selectedPillBg = if (env.isDark) Color(0xFF253452) else Color.White
  val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlassSurface(
      cornerRadius = 34.dp,
      elevation = GlassElevation.FLOATING,
      modifier =
        Modifier.height(capsuleHeight)
          .then(if (isCollapsed) Modifier else Modifier.weight(1f))
          .glassSheen(cornerRadius = 999.dp)
          .testTag("glass_floating_tab_bar"),
    ) {
      AnimatedContent(
        targetState = isCollapsed,
        transitionSpec = { fadeIn() + scaleIn(initialScale = 0.94f) togetherWith fadeOut() + scaleOut(targetScale = 0.94f) },
        label = "tab_bar_collapse_content",
      ) { collapsed ->
        if (collapsed) {
          val activeTab = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
          Row(
            modifier =
              Modifier.fillMaxHeight()
                .glassClickable(role = Role.Button) {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelectTab(activeTab.id)
                }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = activeTab.selectedIcon,
              contentDescription = activeTab.label,
              tint = selectedBlue,
              modifier = Modifier.size(22.dp),
            )
            Text(
              text = activeTab.label,
              style = MaterialTheme.typography.labelLarge,
              color = selectedBlue,
              maxLines = 1,
            )
          }
        } else {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .fillMaxHeight()
                .glassSlidingPill(
                  selectedIndex = selectedIndex,
                  count = tabs.size,
                  inset = 5.dp,
                  cornerRadius = 999.dp,
                  brush = SolidColor(selectedPillBg),
                )
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
          ) {
            tabs.forEach { tab ->
              val isSelected = tab.id == selectedId
              val contentColor by
                animateColorAsState(
                  targetValue =
                    if (isSelected) selectedBlue
                    else if (env.isDark) colorScheme.onSurfaceVariant
                    else Color(0xFF172236),
                  label = "tab_content_color_${tab.id}",
                )

              Box(
                modifier =
                  Modifier.weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(29.dp))
                    .glassClickable(role = Role.Tab) {
                      if (!isSelected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      }
                      onSelectTab(tab.id)
                    }
                    .semantics {
                      selected = isSelected
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
                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                  )
                  Spacer(Modifier.height(2.dp))
                  Text(
                    text = tab.label,
                    style =
                      MaterialTheme.typography.labelMedium.copy(
                        fontWeight =
                          if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold
                          else androidx.compose.ui.text.font.FontWeight.Medium
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
      }
    }

    // Detached circular Liquid Glass Search orb (opens Search screen)
    GlassSurface(
      cornerRadius = 34.dp,
      shape = CircleShape,
      elevation = GlassElevation.FLOATING,
      onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSearchClick()
      },
      clickRole = Role.Button,
      modifier =
        Modifier.size(capsuleHeight)
          .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
          .semantics { contentDescription = "Open Search" }
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
  val haptics = LocalHapticFeedback.current
  val colorScheme = MaterialTheme.colorScheme

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
      onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSearchClick()
      },
      clickRole = Role.Button,
      modifier = Modifier.size(56.dp).testTag("glass_rail_search_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = "Search documents",
        tint = colorScheme.primary,
        modifier = Modifier.align(Alignment.Center).size(24.dp),
      )
    }

    GlassSurface(
      cornerRadius = 30.dp,
      elevation = GlassElevation.FLOATING,
      modifier = Modifier.testTag("glass_navigation_rail"),
    ) {
      Column(
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        tabs.forEach { tab ->
          val isSelected = tab.id == selectedId
          val bg =
            if (isSelected) {
              colorScheme.primary.copy(alpha = if (env.isDark) 0.24f else 0.14f)
            } else {
              Color.Transparent
            }
          val tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
          Column(
            modifier =
              Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
                .clickable(
                  role = Role.Tab,
                  onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(tab.id)
                  },
                )
                .semantics { selected = isSelected }
                .testTag("rail_${tab.testTag}")
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = if (isSelected) tab.selectedIcon else tab.icon,
              contentDescription = tab.label,
              tint = tint,
              modifier = Modifier.size(22.dp),
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
  val colorScheme = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current

  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.FLOATING,
    modifier = modifier.height(48.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions.forEachIndexed { index, action ->
        IconButton(
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            action.onClick()
          },
          modifier = Modifier.size(44.dp).defaultMinSize(48.dp, 48.dp).testTag(action.testTag),
        ) {
          Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
            tint = if (action.active) colorScheme.primary else colorScheme.onSurface,
            modifier = Modifier.size(21.dp),
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

  GlassSurface(
    cornerRadius = 28.dp,
    elevation = GlassElevation.FLOATING,
    modifier = modifier.fillMaxWidth().height(54.dp).testTag("glass_search_field"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
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
      AnimatedVisibility(visible = query.isNotEmpty()) {
        IconButton(
          onClick = { onQueryChange("") },
          modifier = Modifier.size(40.dp).testTag("glass_search_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Clear search query",
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
          )
        }
      }
      if (onFilterClick != null) {
        IconButton(
          onClick = onFilterClick,
          modifier = Modifier.size(40.dp).testTag("glass_search_filter_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "Search filters",
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}
