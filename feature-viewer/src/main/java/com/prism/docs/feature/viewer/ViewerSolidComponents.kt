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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
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
 * Material You themed viewport backdrop that recesses behind document pages with subtle tonal
 * vignette depth so pages (light, sepia, or dark paper) stay distinctly framed and in focus.
 */
@Composable
internal fun Modifier.viewerFocusBackdrop(paperMode: ReaderPaperMode): Modifier {
  val colorScheme = MaterialTheme.colorScheme
  val isDarkApp = LocalGlassEnvironment.current.isDark
  val motion = LocalGlassMotion.current

  val materialYouTone = lerp(colorScheme.surfaceVariant, colorScheme.primaryContainer, 0.48f)
  val materialYouAccent = lerp(colorScheme.primaryContainer, colorScheme.secondaryContainer, 0.5f)

  val targetBase =
    when {
      paperMode == ReaderPaperMode.DARK ->
        lerp(Color(0xFF0A0C14), colorScheme.primaryContainer, 0.24f)
      paperMode == ReaderPaperMode.SEPIA ->
        if (isDarkApp) {
          lerp(Color(0xFF1A1611), colorScheme.primaryContainer, 0.22f)
        } else {
          lerp(Color(0xFFDCD2BC), materialYouTone, 0.46f)
        }
      isDarkApp ->
        lerp(colorScheme.background, colorScheme.primaryContainer, 0.20f)
      else ->
        lerp(materialYouTone, colorScheme.onBackground, 0.09f)
    }

  val targetHalo =
    when {
      paperMode == ReaderPaperMode.DARK || isDarkApp ->
        lerp(targetBase, materialYouAccent, 0.22f)
      paperMode == ReaderPaperMode.SEPIA ->
        lerp(targetBase, colorScheme.secondaryContainer, 0.24f)
      else ->
        lerp(targetBase, colorScheme.primaryContainer, 0.34f)
    }

  val baseColor by
    animateColorAsState(
      targetValue = targetBase,
      animationSpec = if (motion) tween(220) else snap(),
      label = "viewer_backdrop_base",
    )
  val haloColor by
    animateColorAsState(
      targetValue = targetHalo,
      animationSpec = if (motion) tween(220) else snap(),
      label = "viewer_backdrop_halo",
    )
  val perimeterTint = colorScheme.onBackground.copy(alpha = if (isDarkApp || paperMode == ReaderPaperMode.DARK) 0.22f else 0.08f)

  return this.drawBehind {
    drawRect(color = baseColor)
    val maxDim = maxOf(size.width, size.height, 1f)
    drawRect(
      brush =
        Brush.radialGradient(
          colorStops =
            arrayOf(
              0.0f to haloColor.copy(alpha = 0.72f),
              0.55f to baseColor.copy(alpha = 0.25f),
              1.0f to perimeterTint,
            ),
          center = Offset(size.width * 0.5f, size.height * 0.45f),
          radius = maxDim * 0.78f,
        )
    )
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
