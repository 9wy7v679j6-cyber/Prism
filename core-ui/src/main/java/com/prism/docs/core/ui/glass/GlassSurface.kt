package com.prism.docs.core.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalHazeState
import com.prism.docs.core.ui.theme.PrismColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class GlassElevation(
  val blurRadius: Dp,
  val shadowElevation: Dp,
  val lightTintAlpha: Float,
  val darkTintAlpha: Float,
) {
  /** Inline content cards, recent cards, category pills, and grouped list cards. */
  SUBTLE(
    blurRadius = 22.dp,
    shadowElevation = 10.dp,
    lightTintAlpha = 0.54f,
    darkTintAlpha = 0.42f,
  ),

  /** Floating navigation capsules, search bar, and round glass buttons. */
  FLOATING(
    blurRadius = 28.dp,
    shadowElevation = 14.dp,
    lightTintAlpha = 0.60f,
    darkTintAlpha = 0.54f,
  ),

  /** Modal bottom sheets, context menus, and popovers. */
  OVERLAY(
    blurRadius = 36.dp,
    shadowElevation = 22.dp,
    lightTintAlpha = 0.80f,
    darkTintAlpha = 0.68f,
  ),
}

/**
 * Core Liquid Glass surface container.
 * Automatically adapts across:
 * - API 33+: Haze real backdrop blur + AGSL lens-edge refraction + specular rim + layered shadow.
 * - API 31-32: Haze real backdrop blur + adaptive tint + specular rim.
 * - API 26-30: Multi-stop frosted translucent gradient + specular rim.
 * - Reduce Transparency: High-contrast solid surface with crisp hairline outline.
 */
@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 24.dp,
  shape: Shape = RoundedCornerShape(cornerRadius),
  elevation: GlassElevation = GlassElevation.FLOATING,
  hazeState: HazeState? = LocalHazeState.current,
  accentTint: Color? = null,
  enableRefraction: Boolean = true,
  enableTopSheen: Boolean = true,
  onClick: (() -> Unit)? = null,
  clickRole: Role? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val tier = env.capabilityTier
  val isDark = env.isDark

  val baseSurfaceColor =
    if (isDark) Color(0xFF101726) else Color(0xFFFFFFFF)

  val adaptiveTintAlpha =
    ((if (isDark) elevation.darkTintAlpha else elevation.lightTintAlpha) * env.tintScale)
      .coerceIn(0.12f, 0.94f)

  val tintColor =
    if (accentTint != null && accentTint.alpha > 0f) {
      Color(
        red = baseSurfaceColor.red * 0.84f + accentTint.red * 0.16f,
        green = baseSurfaceColor.green * 0.84f + accentTint.green * 0.16f,
        blue = baseSurfaceColor.blue * 0.84f + accentTint.blue * 0.16f,
        alpha = if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) 0.98f else adaptiveTintAlpha,
      )
    } else {
      baseSurfaceColor.copy(
        alpha = if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) 0.98f else adaptiveTintAlpha
      )
    }

  val blurDp = (elevation.blurRadius * env.blurScale).coerceIn(16.dp, 40.dp)

  // Bright white specular edge fading smoothly around the shape
  val specularBorderBrush =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Brush.linearGradient(
        colors = listOf(colorScheme.outline, colorScheme.outlineVariant)
      )
    } else if (isDark) {
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.0f to Color.White.copy(alpha = 0.46f),
            0.28f to Color.White.copy(alpha = 0.16f),
            0.65f to Color.White.copy(alpha = 0.05f),
            1.0f to Color.White.copy(alpha = 0.24f),
          )
      )
    } else {
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.0f to Color.White.copy(alpha = 0.96f),
            0.30f to Color.White.copy(alpha = 0.62f),
            0.64f to Color.White.copy(alpha = 0.20f),
            1.0f to Color.White.copy(alpha = 0.72f),
          )
      )
    }

  val fallbackFrostBrush =
    if (isDark) {
      Brush.verticalGradient(
        colors =
          listOf(
            tintColor.copy(alpha = (adaptiveTintAlpha + 0.22f).coerceAtMost(0.92f)),
            tintColor.copy(alpha = (adaptiveTintAlpha + 0.14f).coerceAtMost(0.86f)),
          )
      )
    } else {
      Brush.verticalGradient(
        colors =
          listOf(
            tintColor.copy(alpha = (adaptiveTintAlpha + 0.22f).coerceAtMost(0.94f)),
            tintColor.copy(alpha = (adaptiveTintAlpha + 0.12f).coerceAtMost(0.88f)),
          )
      )
    }

  val shadowColor =
    if (isDark) Color.Black.copy(alpha = 0.56f) else Color(0xFF334778).copy(alpha = 0.15f)

  val useAgsl = tier.supportsAgslRefraction && enableRefraction

  val hazeModifier =
    if (tier.supportsBackdropBlur && hazeState != null) {
      Modifier.hazeEffect(
        state = hazeState,
        style =
          HazeStyle(
            backgroundColor = colorScheme.surface,
            blurRadius = blurDp,
            tint = HazeTint(color = tintColor),
            noiseFactor = if (isDark) 0.025f else 0.01f,
          ),
      )
    } else if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.background(color = colorScheme.surface)
    } else {
      Modifier.background(brush = fallbackFrostBrush)
    }

  val topSheenModifier =
    if (enableTopSheen && tier != GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.drawBehind {
        val sheenHeight = size.height * 0.42f
        drawRect(
          brush =
            Brush.verticalGradient(
              colors =
                listOf(
                  Color.White.copy(alpha = if (isDark) 0.09f else 0.30f),
                  Color.Transparent,
                ),
              startY = 0f,
              endY = sheenHeight,
            )
        )
      }
    } else {
      Modifier
    }

  val clickableModifier =
    if (onClick != null) {
      Modifier.glassClickable(
        role = clickRole ?: Role.Button,
        onClick = onClick,
      )
    } else {
      Modifier
    }

  Box(
    modifier =
      modifier
        .then(clickableModifier)
        .shadow(
          elevation = elevation.shadowElevation,
          shape = shape,
          ambientColor = shadowColor,
          spotColor = shadowColor,
          clip = false,
        )
        .clip(shape)
        .liquidGlassEdgeRefraction(
          enabled = useAgsl,
          cornerRadius = cornerRadius,
          isDark = isDark,
        )
        .then(hazeModifier)
        .then(topSheenModifier)
        .border(width = 1.25.dp, brush = specularBorderBrush, shape = shape),
  ) {
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
      content()
    }
  }
}

/**
 * Seamless slice of a single large rounded Liquid Glass list card so Paging 3 / LazyColumn
 * stays fully virtualized while rendering visually as ONE continuous rounded glass card
 * with a bright outer white specular border and zero internal seams.
 */
@Composable
fun ConnectedGlassCardSlice(
  isFirst: Boolean,
  isLast: Boolean,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 28.dp,
  hazeState: HazeState? = LocalHazeState.current,
  accentTint: Color? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val tier = env.capabilityTier
  val isDark = env.isDark

  val shape =
    RoundedCornerShape(
      topStart = if (isFirst) cornerRadius else 0.dp,
      topEnd = if (isFirst) cornerRadius else 0.dp,
      bottomStart = if (isLast) cornerRadius else 0.dp,
      bottomEnd = if (isLast) cornerRadius else 0.dp,
    )

  val baseSurfaceColor = if (isDark) Color(0xFF101726) else Color(0xFFFFFFFF)
  val adaptiveTintAlpha =
    ((if (isDark) GlassElevation.SUBTLE.darkTintAlpha else GlassElevation.SUBTLE.lightTintAlpha) *
        env.tintScale)
      .coerceIn(0.12f, 0.94f)

  val tintColor =
    if (accentTint != null && accentTint.alpha > 0f) {
      Color(
        red = baseSurfaceColor.red * 0.84f + accentTint.red * 0.16f,
        green = baseSurfaceColor.green * 0.84f + accentTint.green * 0.16f,
        blue = baseSurfaceColor.blue * 0.84f + accentTint.blue * 0.16f,
        alpha = if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) 0.98f else adaptiveTintAlpha,
      )
    } else {
      baseSurfaceColor.copy(
        alpha = if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) 0.98f else adaptiveTintAlpha
      )
    }

  val blurDp = (GlassElevation.SUBTLE.blurRadius * env.blurScale).coerceIn(16.dp, 40.dp)

  val hazeModifier =
    if (tier.supportsBackdropBlur && hazeState != null) {
      Modifier.hazeEffect(
        state = hazeState,
        style =
          HazeStyle(
            backgroundColor = colorScheme.surface,
            blurRadius = blurDp,
            tint = HazeTint(color = tintColor),
            noiseFactor = if (isDark) 0.02f else 0.008f,
          ),
      )
    } else if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.background(color = colorScheme.surface)
    } else {
      Modifier.background(color = tintColor.copy(alpha = (adaptiveTintAlpha + 0.18f).coerceAtMost(0.92f)))
    }

  val borderHighlight =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      colorScheme.outlineVariant
    } else if (isDark) {
      Color.White.copy(alpha = 0.24f)
    } else {
      Color.White.copy(alpha = 0.86f)
    }
  val borderSide =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      colorScheme.outlineVariant
    } else if (isDark) {
      Color.White.copy(alpha = 0.14f)
    } else {
      Color.White.copy(alpha = 0.62f)
    }

  val perimeterBorderModifier =
    Modifier.drawWithContent {
      drawContent()
      val strokePx = 1.25.dp.toPx()
      val halfStroke = strokePx / 2f
      val r = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)

      if (isFirst && isLast) {
        drawRoundRect(
          brush =
            Brush.linearGradient(
              colorStops =
                arrayOf(
                  0.0f to borderHighlight,
                  0.5f to borderSide,
                  1.0f to borderHighlight.copy(alpha = borderHighlight.alpha * 0.85f),
                )
            ),
          topLeft = Offset(halfStroke, halfStroke),
          size = Size(size.width - strokePx, size.height - strokePx),
          cornerRadius = CornerRadius(r, r),
          style = Stroke(width = strokePx),
        )
      } else if (isFirst) {
        val path =
          Path().apply {
            moveTo(halfStroke, size.height)
            lineTo(halfStroke, r)
            quadraticTo(halfStroke, halfStroke, r, halfStroke)
            lineTo(size.width - r, halfStroke)
            quadraticTo(size.width - halfStroke, halfStroke, size.width - halfStroke, r)
            lineTo(size.width - halfStroke, size.height)
          }
        drawPath(
          path = path,
          brush = Brush.verticalGradient(listOf(borderHighlight, borderSide)),
          style = Stroke(width = strokePx),
        )
      } else if (isLast) {
        val path =
          Path().apply {
            moveTo(halfStroke, 0f)
            lineTo(halfStroke, size.height - r)
            quadraticTo(halfStroke, size.height - halfStroke, r, size.height - halfStroke)
            lineTo(size.width - r, size.height - halfStroke)
            quadraticTo(
              size.width - halfStroke,
              size.height - halfStroke,
              size.width - halfStroke,
              size.height - r,
            )
            lineTo(size.width - halfStroke, 0f)
          }
        drawPath(
          path = path,
          brush = Brush.verticalGradient(listOf(borderSide, borderHighlight.copy(alpha = borderHighlight.alpha * 0.85f))),
          style = Stroke(width = strokePx),
        )
      } else {
        drawLine(
          color = borderSide,
          start = Offset(halfStroke, 0f),
          end = Offset(halfStroke, size.height),
          strokeWidth = strokePx,
        )
        drawLine(
          color = borderSide,
          start = Offset(size.width - halfStroke, 0f),
          end = Offset(size.width - halfStroke, size.height),
          strokeWidth = strokePx,
        )
      }
    }

  val outerShadowColor =
    if (isDark) Color.Black.copy(alpha = 0.36f) else Color(0xFF334778).copy(alpha = 0.11f)

  val outerGlowShadowModifier =
    Modifier.drawBehind {
      val spread = 8.dp.toPx()
      val r = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
      // Left and right soft ambient shadows (drawn outside bounds without horizontal seams)
      drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, outerShadowColor), startX = -spread, endX = 0f),
        topLeft = Offset(-spread, if (isFirst) r else 0f),
        size = Size(spread, size.height - (if (isFirst) r else 0f) - (if (isLast) r else 0f)),
      )
      drawRect(
        brush = Brush.horizontalGradient(listOf(outerShadowColor, Color.Transparent), startX = size.width, endX = size.width + spread),
        topLeft = Offset(size.width, if (isFirst) r else 0f),
        size = Size(spread, size.height - (if (isFirst) r else 0f) - (if (isLast) r else 0f)),
      )
      if (isFirst) {
        drawRect(
          brush = Brush.verticalGradient(listOf(Color.Transparent, outerShadowColor), startY = -spread * 0.7f, endY = 0f),
          topLeft = Offset(r, -spread * 0.7f),
          size = Size((size.width - r * 2f).coerceAtLeast(0f), spread * 0.7f),
        )
      }
      if (isLast) {
        drawRect(
          brush = Brush.verticalGradient(listOf(outerShadowColor, Color.Transparent), startY = size.height, endY = size.height + spread),
          topLeft = Offset(r, size.height),
          size = Size((size.width - r * 2f).coerceAtLeast(0f), spread),
        )
      }
    }

  Box(
    modifier =
      modifier
        .then(outerGlowShadowModifier)
        .clip(shape)
        .then(hazeModifier)
        .then(perimeterBorderModifier),
  ) {
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
      content()
    }
  }
}

/**
 * Soft atmospheric Prism backdrop with slowly drifting pastel colour blobs (blue, violet, pink,
 * mint) on a pale blue-grey base so Liquid Glass chrome refracts luminous colour fields.
 * Automatically freezes motion when Reduce Motion is enabled.
 */
@Composable
fun PrismAmbientBackdrop(
  modifier: Modifier = Modifier,
) {
  val env = LocalGlassEnvironment.current
  val bg = MaterialTheme.colorScheme.background

  val driftPhase =
    if (env.reduceMotion) {
      0f
    } else {
      val transition = rememberInfiniteTransition(label = "ambient_blob_drift")
      val phase by
        transition.animateFloat(
          initialValue = 0f,
          targetValue = (2.0 * PI).toFloat(),
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = 22_000, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
            ),
          label = "ambient_phase",
        )
      phase
    }

  Canvas(modifier = modifier.fillMaxSize()) {
    drawRect(color = bg)
    if (!env.reduceTransparency) {
      val alphaScale = if (env.isDark) 0.28f else 0.64f
      val dx1 = sin(driftPhase) * size.width * 0.055f
      val dy1 = cos(driftPhase) * size.height * 0.035f
      val dx2 = cos(driftPhase * 0.85f + 1.1f) * size.width * 0.06f
      val dy2 = sin(driftPhase * 0.85f + 1.1f) * size.height * 0.04f
      val dx3 = sin(driftPhase + 2.3f) * size.width * 0.05f
      val dy3 = cos(driftPhase + 2.3f) * size.height * 0.045f
      val dx4 = cos(driftPhase + 4.1f) * size.width * 0.06f
      val dy4 = sin(driftPhase + 4.1f) * size.height * 0.035f

      // 1. Top-left soft cornflower blue blob (behind Library title & search pill)
      val c1 = Offset(size.width * 0.14f + dx1, size.height * 0.12f + dy1)
      val r1 = size.maxDimension * 0.52f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                PrismColors.AmbientSky.copy(alpha = alphaScale),
                PrismColors.AmbientSky.copy(alpha = alphaScale * 0.38f),
                Color.Transparent,
              ),
            center = c1,
            radius = r1,
          ),
        radius = r1,
        center = c1,
      )

      // 2. Mid-right soft lavender/violet blob (behind Recent cards & chips)
      val c2 = Offset(size.width * 0.86f + dx2, size.height * 0.36f + dy2)
      val r2 = size.maxDimension * 0.48f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                PrismColors.AmbientLavender.copy(alpha = alphaScale * 0.88f),
                PrismColors.AmbientLavender.copy(alpha = alphaScale * 0.30f),
                Color.Transparent,
              ),
            center = c2,
            radius = r2,
          ),
        radius = r2,
        center = c2,
      )

      // 3. Mid-left soft rose/pink blob (blending between search bar, chips, and file list)
      val c3 = Offset(size.width * 0.16f + dx3, size.height * 0.56f + dy3)
      val r3 = size.maxDimension * 0.44f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                PrismColors.AmbientRose.copy(alpha = alphaScale * 0.72f),
                PrismColors.AmbientRose.copy(alpha = alphaScale * 0.24f),
                Color.Transparent,
              ),
            center = c3,
            radius = r3,
          ),
        radius = r3,
        center = c3,
      )

      // 4. Lower-right luminous mint/aqua blob (glowing through bottom of file list card & floating bar)
      val c4 = Offset(size.width * 0.74f + dx4, size.height * 0.86f + dy4)
      val r4 = size.maxDimension * 0.54f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                PrismColors.AmbientMint.copy(alpha = alphaScale * 0.92f),
                PrismColors.AmbientAqua.copy(alpha = alphaScale * 0.36f),
                Color.Transparent,
              ),
            center = c4,
            radius = r4,
          ),
        radius = r4,
        center = c4,
      )
    }
  }
}

/**
 * Fades the right edge of horizontally scrolling rows (e.g., chip strips) to true transparency
 * using an offscreen alpha mask so colourful ambient blobs show cleanly underneath instead of
 * hard clipping or flat color bars.
 */
fun Modifier.horizontalRightEdgeFade(
  fadeFraction: Float = 1f,
  fadeWidth: Dp = 36.dp,
): Modifier {
  val clamped = fadeFraction.coerceIn(0f, 1f)
  if (clamped <= 0.01f) return this
  return this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
      drawContent()
      val fadePx = fadeWidth.toPx().coerceAtMost(size.width * 0.45f)
      if (fadePx > 0f) {
        val edgeAlpha = (1f - clamped).coerceIn(0f, 1f)
        drawRect(
          brush =
            Brush.horizontalGradient(
              colors = listOf(Color.Black, Color.Black.copy(alpha = edgeAlpha)),
              startX = size.width - fadePx,
              endX = size.width,
            ),
          blendMode = BlendMode.DstIn,
        )
      }
    }
}

/**
 * Soft scroll-edge scrim placed at the top or bottom viewport edge so scrolling document cards
 * fade gently behind floating Liquid Glass toolbars and tab capsules.
 */
@Composable
fun ScrollEdgeFade(
  isTop: Boolean,
  modifier: Modifier = Modifier,
  height: Dp = 64.dp,
  strongTint: Boolean = false,
  scrollProgress: State<Float>? = null,
) {
  val env = LocalGlassEnvironment.current
  val bg = MaterialTheme.colorScheme.background
  val hazeState = LocalHazeState.current

  val blurModifier =
    if (strongTint && isTop && env.capabilityTier.supportsBackdropBlur && hazeState != null) {
      Modifier.hazeEffect(
        state = hazeState,
        style =
          HazeStyle(
            backgroundColor = bg,
            blurRadius = 24.dp,
            tint = HazeTint(color = bg.copy(alpha = if (scrollProgress != null) 0.24f else 0.62f)),
            noiseFactor = 0f,
          ),
      )
    } else {
      Modifier
    }

  val scrimModifier =
    Modifier.drawBehind {
      val p = scrollProgress?.value ?: 1f
      val brush =
        if (isTop) {
          if (strongTint) {
            val topAlpha = (0.68f + 0.31f * p).coerceIn(0f, 1f)
            val midAlpha = (0.54f + 0.44f * p).coerceIn(0f, 1f)
            val lowerAlpha = (0.24f + 0.56f * p).coerceIn(0f, 1f)
            Brush.verticalGradient(
              colorStops =
                arrayOf(
                  0.0f to bg.copy(alpha = topAlpha),
                  0.72f to bg.copy(alpha = midAlpha),
                  0.88f to bg.copy(alpha = lowerAlpha),
                  1.0f to Color.Transparent,
                )
            )
          } else {
            val topAlpha = (0.36f + 0.54f * p).coerceIn(0f, 1f)
            val midAlpha = (0.14f + 0.36f * p).coerceIn(0f, 1f)
            Brush.verticalGradient(
              colors = listOf(bg.copy(alpha = topAlpha), bg.copy(alpha = midAlpha), Color.Transparent)
            )
          }
        } else {
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, bg.copy(alpha = 0.44f), bg.copy(alpha = 0.88f))
          )
        }
      drawRect(brush = brush)
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(height)
        .then(blurModifier)
        .then(scrimModifier)
  )
}
