package com.prism.docs.feature.viewer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.PrismDimens

/**
 * Computes an opaque, high-contrast solid surface color from Material 3 semantic tokens so
 * viewer HUD bars remain effortlessly legible over white, sepia, and dark PDF paper.
 */
@Composable
internal fun viewerSolidContainerColor(emphasized: Boolean = false): Color {
  val colorScheme = MaterialTheme.colorScheme
  return if (emphasized) {
    colorScheme.primaryContainer
  } else {
    lerp(colorScheme.surface, colorScheme.surfaceVariant, 0.56f)
  }
}

@Composable
internal fun viewerSolidBorderColor(emphasized: Boolean = false): Color {
  val colorScheme = MaterialTheme.colorScheme
  return if (emphasized) {
    colorScheme.primary.copy(alpha = 0.55f)
  } else {
    colorScheme.outline
  }
}

/**
 * 100% opaque solid Material 3 floating container for all document viewer HUD bars, pills,
 * scrubber cards, and toolbars. Uses crisp elevation shadows, high-contrast solid fills,
 * border strokes, and spring press feedback without any translucent glass bleed.
 */
@Composable
internal fun ViewerSolidSurface(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 24.dp,
  shape: Shape = RoundedCornerShape(cornerRadius),
  emphasized: Boolean = false,
  containerColor: Color = viewerSolidContainerColor(emphasized),
  contentColor: Color =
    if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface,
  borderColor: Color = viewerSolidBorderColor(emphasized),
  shadowElevation: Dp = 8.dp,
  onClick: (() -> Unit)? = null,
  clickRole: Role? = null,
  hapticOnClick: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val motion = LocalGlassMotion.current
  val haptics = if (onClick != null) rememberPrismHaptics() else null
  val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
  val isPressed =
    if (interactionSource != null) interactionSource.collectIsPressedAsState().value else false
  val pressScale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.96f else 1f,
      animationSpec = if (motion) spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium) else snap(),
      label = "viewer_solid_press_scale",
    )

  val animatedBg by
    animateColorAsState(
      targetValue = containerColor,
      animationSpec = if (motion) spring(stiffness = 460f) else snap(),
      label = "viewer_solid_bg",
    )
  val animatedBorder by
    animateColorAsState(
      targetValue = borderColor,
      animationSpec = if (motion) spring(stiffness = 460f) else snap(),
      label = "viewer_solid_border",
    )

  Surface(
    shape = shape,
    color = animatedBg,
    contentColor = contentColor,
    tonalElevation = 0.dp,
    shadowElevation = shadowElevation,
    border = BorderStroke(1.dp, animatedBorder),
    modifier =
      modifier.graphicsLayer {
        if (onClick != null) {
          scaleX = pressScale
          scaleY = pressScale
        }
      },
  ) {
    val clickMod =
      if (onClick != null && interactionSource != null) {
        Modifier.clickable(
          interactionSource = interactionSource,
          indication = ripple(color = contentColor),
          role = clickRole ?: Role.Button,
          onClick = {
            if (hapticOnClick) haptics?.tap()
            onClick()
          },
        )
      } else {
        Modifier
      }
    Box(modifier = clickMod) {
      CompositionLocalProvider(LocalContentColor provides contentColor) {
        content()
      }
    }
  }
}

internal data class ViewerToolbarAction(
  val icon: ImageVector,
  val contentDescription: String,
  val testTag: String,
  val active: Boolean = false,
  val onClick: () -> Unit,
)

/**
 * High-contrast solid grouped action pill for the top viewer bar.
 */
@Composable
internal fun ViewerSolidToolbarGroup(
  actions: List<ViewerToolbarAction>,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()

  ViewerSolidSurface(
    cornerRadius = 24.dp,
    shape = CircleShape,
    shadowElevation = 8.dp,
    modifier = modifier.height(PrismDimens.TouchTargetMin),
  ) {
    Row(
      modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions.forEachIndexed { index, action ->
        val pillBg by
          animateColorAsState(
            targetValue = if (action.active) colorScheme.primaryContainer else Color.Transparent,
            animationSpec = if (motion) spring(stiffness = 460f) else snap(),
            label = "viewer_toolbar_bg_${action.testTag}",
          )
        val iconTint by
          animateColorAsState(
            targetValue = if (action.active) colorScheme.primary else colorScheme.onSurface,
            animationSpec = if (motion) spring(stiffness = 460f) else snap(),
            label = "viewer_toolbar_tint_${action.testTag}",
          )
        val iconScale by
          animateFloatAsState(
            targetValue = if (action.active) 1.08f else 1f,
            animationSpec = if (motion) spring(dampingRatio = 0.65f, stiffness = 460f) else snap(),
            label = "viewer_toolbar_scale_${action.testTag}",
          )
        Box(
          modifier =
            Modifier.padding(vertical = 4.dp)
              .clip(CircleShape)
              .background(pillBg),
          contentAlignment = Alignment.Center,
        ) {
          IconButton(
            onClick = {
              haptics.tap()
              action.onClick()
            },
            modifier =
              Modifier.defaultMinSize(PrismDimens.TouchTargetMin, PrismDimens.TouchTargetMin)
                .size(40.dp)
                .testTag(action.testTag),
          ) {
            Icon(
              imageVector = action.icon,
              contentDescription = action.contentDescription,
              tint = iconTint,
              modifier =
                Modifier.size(21.dp).graphicsLayer {
                  scaleX = iconScale
                  scaleY = iconScale
                },
            )
          }
        }
        if (index < actions.lastIndex) {
          Box(
            modifier =
              Modifier.width(1.dp)
                .height(20.dp)
                .background(colorScheme.outlineVariant)
          )
        }
      }
    }
  }
}
