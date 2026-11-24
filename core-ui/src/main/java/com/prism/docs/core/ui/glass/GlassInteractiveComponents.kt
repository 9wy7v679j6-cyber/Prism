package com.prism.docs.core.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.PrismMotion

/**
 * Slim Liquid Glass segmented control with a clear raised white pill thumb, blue text,
 * and soft shadow in light mode (and elevated translucent pill in dark mode).
 */
@Composable
fun GlassSegmentedControl(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (options.isEmpty()) return
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current
  val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)

  val trackRecessColor =
    if (env.isDark) Color.Black.copy(alpha = 0.28f) else Color(0xFF1A2C56).copy(alpha = 0.13f)
  val thumbBgColor = if (env.isDark) Color(0xFF283654) else Color.White
  val activeTextColor =
    if (env.isDark) colorScheme.primary else com.prism.docs.core.ui.theme.PrismColors.IndigoBlue
  val inactiveTextColor =
    if (env.isDark) colorScheme.onSurfaceVariant else Color(0xFF3E4C66)

  GlassSurface(
    cornerRadius = 22.dp,
    elevation = GlassElevation.SUBTLE,
    enableRefraction = false,
    enableTopSheen = false,
    modifier = modifier.fillMaxWidth().height(44.dp).testTag("glass_segmented_control"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxSize()
          .background(trackRecessColor)
          .glassSlidingPill(
            selectedIndex = clampedIndex,
            count = options.size,
            inset = 4.dp,
            cornerRadius = 999.dp,
            brush = SolidColor(thumbBgColor),
          )
          .padding(4.dp),
    ) {
      options.forEachIndexed { index, label ->
        val isSelected = index == clampedIndex
        val textColor by
          animateColorAsState(
            targetValue = if (isSelected) activeTextColor else inactiveTextColor,
            label = "segment_text_$index",
          )
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(18.dp))
              .glassClickable(role = Role.Tab) {
                if (!isSelected) {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelect(index)
                }
              }
              .semantics { selected = isSelected }
              .testTag("glass_segment_$index"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style =
              MaterialTheme.typography.labelLarge.copy(
                fontWeight =
                  if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold
                  else androidx.compose.ui.text.font.FontWeight.SemiBold,
              ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/**
 * Liquid Glass capsule toggle switch with spring-driven crystal thumb and 48dp accessible touch area.
 */
@Composable
fun GlassToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  contentDescriptionText: String,
  modifier: Modifier = Modifier,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current

  val trackColor by
    animateColorAsState(
      targetValue =
        if (checked) {
          colorScheme.primary.copy(alpha = 0.88f)
        } else {
          colorScheme.onSurface.copy(alpha = if (env.isDark) 0.18f else 0.14f)
        },
      label = "glass_toggle_track",
    )
  val thumbOffset by
    animateDpAsState(
      targetValue = if (checked) 22.dp else 2.dp,
      animationSpec = PrismMotion.tactileSpring(env.reduceMotion),
      label = "glass_toggle_thumb",
    )

  Box(
    modifier =
      modifier
        .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          role = Role.Switch,
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(!checked)
          },
        )
        .semantics { contentDescription = contentDescriptionText },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.width(52.dp)
          .height(32.dp)
          .clip(CircleShape)
          .background(trackColor)
          .border(1.dp, Color.White.copy(alpha = if (env.isDark) 0.25f else 0.55f), CircleShape),
      contentAlignment = Alignment.CenterStart,
    ) {
      Box(
        modifier =
          Modifier.offset(x = thumbOffset)
            .size(28.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(
              Brush.verticalGradient(
                listOf(Color.White, Color(0xFFEAF1FA))
              )
            )
      )
    }
  }
}

/**
 * Liquid Glass tactile slider wrapped in a soft frosted capsule rail.
 */
@Composable
fun GlassSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  label: String,
  valueBadge: String,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
      )
      Text(
        text = valueBadge,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.primary,
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      steps = steps,
      colors =
        SliderDefaults.colors(
          thumbColor = Color.White,
          activeTrackColor = colorScheme.primary,
          inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.14f),
        ),
      modifier = Modifier.fillMaxWidth().testTag("glass_slider_$label"),
    )
  }
}

data class GlassContextMenuItem(
  val id: String,
  val label: String,
  val icon: ImageVector,
  val isDestructive: Boolean = false,
  val onClick: () -> Unit,
)

/**
 * Floating Liquid Glass context popover menu with specular outline and spring reveal.
 */
@Composable
fun GlassContextMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  items: List<GlassContextMenuItem>,
  modifier: Modifier = Modifier,
  offset: IntOffset = IntOffset(0, 12),
) {
  if (!expanded) return
  val colorScheme = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current

  Popup(
    alignment = Alignment.TopEnd,
    offset = offset,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true),
  ) {
    GlassSurface(
      cornerRadius = 22.dp,
      elevation = GlassElevation.OVERLAY,
      modifier = modifier.widthIn(min = 200.dp, max = 264.dp).testTag("glass_context_menu"),
    ) {
      Column(modifier = Modifier.padding(vertical = 6.dp)) {
        items.forEachIndexed { index, item ->
          val tint = if (item.isDestructive) colorScheme.error else colorScheme.onSurface
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = ripple(color = colorScheme.primary),
                  role = Role.Button,
                  onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismissRequest()
                    item.onClick()
                  },
                )
                .testTag("glass_menu_item_${item.id}")
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = item.icon,
              contentDescription = null,
              tint = tint,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = item.label,
              style = MaterialTheme.typography.bodyMedium,
              color = tint,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (index < items.lastIndex) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(horizontal = 16.dp)
                  .height(0.75.dp)
                  .background(colorScheme.onSurface.copy(alpha = 0.08f))
            )
          }
        }
      }
    }
  }
}

enum class GlassSheetDetent(val heightFraction: Float) {
  PEEK(0.36f),
  HALF(0.60f),
  FULL(0.88f),
}

/**
 * Liquid Glass bottom sheet overlay supporting PEEK / HALF / FULL detents with drag-to-snap
 * spring gestures and scrim dismissal.
 */
@Composable
fun GlassBottomSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier.fillMaxSize(),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val containerHeight = maxHeight
      val targetFraction by
        animateFloatAsState(
          targetValue = detent.heightFraction,
          animationSpec = PrismMotion.fluidSpring(env.reduceMotion),
          label = "sheet_detent_fraction",
        )
      var accumulatedDrag by remember(detent) { mutableFloatStateOf(0f) }

      // Scrim
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (env.isDark) 0.56f else 0.34f))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = onDismiss,
            )
      )

      AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Alignment.BottomCenter.let { Modifier.align(it) },
      ) {
        GlassSurface(
          cornerRadius = 30.dp,
          shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
          elevation = GlassElevation.OVERLAY,
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = 640.dp)
              .height(containerHeight * targetFraction)
              .pointerInput(detent) {
                detectVerticalDragGestures(
                  onDragEnd = {
                    when {
                      accumulatedDrag > 120f -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (detent) {
                          GlassSheetDetent.FULL -> onDetentChange(GlassSheetDetent.HALF)
                          GlassSheetDetent.HALF -> onDetentChange(GlassSheetDetent.PEEK)
                          GlassSheetDetent.PEEK -> onDismiss()
                        }
                      }
                      accumulatedDrag < -120f -> {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (detent) {
                          GlassSheetDetent.PEEK -> onDetentChange(GlassSheetDetent.HALF)
                          GlassSheetDetent.HALF -> onDetentChange(GlassSheetDetent.FULL)
                          GlassSheetDetent.FULL -> Unit
                        }
                      }
                    }
                    accumulatedDrag = 0f
                  },
                  onVerticalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },
                )
              }
              .testTag("glass_bottom_sheet"),
        ) {
          Column(
            modifier =
              Modifier.fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
          ) {
            // Drag handle pill
            Box(
              modifier =
                Modifier.align(Alignment.CenterHorizontally)
                  .width(42.dp)
                  .height(5.dp)
                  .clip(CircleShape)
                  .background(colorScheme.onSurface.copy(alpha = 0.28f))
            )
            Spacer(Modifier.height(14.dp))
            content()
          }
        }
      }
    }
  }
}

/**
 * Floating Liquid Glass horizontal page-thumbnail scrubber strip for document viewers.
 */
@Composable
fun GlassPageThumbnailStrip(
  pageCount: Int,
  currentPage: Int,
  onSelectPage: (Int) -> Unit,
  modifier: Modifier = Modifier,
  thumbnailWidth: Dp = 42.dp,
  thumbnailHeight: Dp = 56.dp,
) {
  if (pageCount <= 0) return
  val colorScheme = MaterialTheme.colorScheme
  val haptics = LocalHapticFeedback.current
  val pages = remember(pageCount) { (1..pageCount).toList() }

  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.FLOATING,
    modifier = modifier.fillMaxWidth().testTag("glass_page_thumbnail_strip"),
  ) {
    LazyRow(
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      itemsIndexed(pages) { index, pageNumber ->
        val isActive = index == currentPage
        val borderColor =
          if (isActive) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.16f)
        Box(
          modifier =
            Modifier.size(width = thumbnailWidth, height = thumbnailHeight)
              .clip(RoundedCornerShape(10.dp))
              .background(
                if (isActive) {
                  colorScheme.primary.copy(alpha = 0.16f)
                } else {
                  colorScheme.surface.copy(alpha = 0.78f)
                }
              )
              .border(
                width = if (isActive) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
              )
              .clickable(
                role = Role.Button,
                onClick = {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  onSelectPage(index)
                },
              )
              .semantics {
                selected = isActive
                contentDescription = "Page $pageNumber of $pageCount"
              }
              .testTag("thumbnail_page_$pageNumber"),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(6.dp),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(3.dp)
                  .clip(CircleShape)
                  .background(colorScheme.onSurface.copy(alpha = 0.22f))
            )
            Box(
              modifier =
                Modifier.fillMaxWidth(0.72f)
                  .height(3.dp)
                  .clip(CircleShape)
                  .background(colorScheme.onSurface.copy(alpha = 0.14f))
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = "$pageNumber",
              style = MaterialTheme.typography.labelSmall,
              color = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
