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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import com.prism.docs.core.ui.theme.PrismDimens
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
    blurRadius = 20.dp,
    shadowElevation = 8.dp,
    lightTintAlpha = 0.56f,
    darkTintAlpha = 0.08f,
  ),

  /** Floating navigation capsules, search bar, and round glass buttons. */
  FLOATING(
    blurRadius = 24.dp,
    shadowElevation = 14.dp,
    lightTintAlpha = 0.62f,
    darkTintAlpha = 0.10f,
  ),

  /** Modal bottom sheets, context menus, and popovers. */
  OVERLAY(
    blurRadius = 32.dp,
    shadowElevation = 20.dp,
    lightTintAlpha = 0.82f,
    darkTintAlpha = 0.12f,
  ),
}

/**
 * Core Liquid Glass surface container engineered for locked 120fps rendering:
 * - True floating overlays ([useBackdropBlur] = true, OUTSIDE any `hazeSource` scroll container)
 *   run hardware-accelerated Haze real-time backdrop blur over both [DriftingBlobsBackdrop] and
 *   scrolling content.
 * - Inline cards inside scrolling `hazeSource` lists ([useBackdropBlur] = false, default for
 *   [GlassElevation.SUBTLE]) render luminous frosted translucent glass over the canvas/blobs
 *   with zero cyclic Haze bugs and zero per-card blur passes.
 * - Dark theme pairs deep navy-violet canvas depth with white 8–12% glass tint and a crisp
 *   1dp brighter top specular edge.
 */
@Composable
fun GlassSurface(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 24.dp,
  shape: Shape = RoundedCornerShape(cornerRadius),
  elevation: GlassElevation = GlassElevation.FLOATING,
  hazeState: HazeState? = LocalHazeState.current,
  accentTint: Color? = null,
  useBackdropBlur: Boolean = elevation != GlassElevation.SUBTLE,
  enableRefraction: Boolean = false,
  enableTopSheen: Boolean = true,
  enableAnimatedSheen: Boolean = false,
  onClick: (() -> Unit)? = null,
  clickRole: Role? = null,
  hapticOnClick: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val tier = env.capabilityTier
  val isDark = env.isDark

  val baseSurfaceColor = if (isDark) PrismColors.DarkGlassTint else PrismColors.LightGlassBase

  val adaptiveTintAlpha =
    if (isDark) {
      (elevation.darkTintAlpha * env.tintScale).coerceIn(0.06f, 0.15f)
    } else {
      (elevation.lightTintAlpha * env.tintScale).coerceIn(0.16f, 0.94f)
    }

  val tintColor =
    remember(baseSurfaceColor, accentTint, tier, adaptiveTintAlpha, isDark) {
      if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
        if (isDark) PrismColors.DarkSurface.copy(alpha = 0.98f)
        else PrismColors.LightSurface.copy(alpha = 0.98f)
      } else if (accentTint != null && accentTint.alpha > 0f) {
        Color(
          red = baseSurfaceColor.red * 0.82f + accentTint.red * 0.18f,
          green = baseSurfaceColor.green * 0.82f + accentTint.green * 0.18f,
          blue = baseSurfaceColor.blue * 0.82f + accentTint.blue * 0.18f,
          alpha = adaptiveTintAlpha,
        )
      } else {
        baseSurfaceColor.copy(alpha = adaptiveTintAlpha)
      }
    }

  val blurDp = (elevation.blurRadius * env.blurScale).coerceIn(14.dp, 36.dp)

  // Specular perimeter border with a noticeably brighter 1dp top edge
  val specularBorderBrush =
    remember(tier, isDark, colorScheme.outline, colorScheme.outlineVariant) {
      if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
        Brush.verticalGradient(
          colors = listOf(colorScheme.outline, colorScheme.outlineVariant)
        )
      } else if (isDark) {
        Brush.verticalGradient(
          colorStops =
            arrayOf(
              0.0f to Color.White.copy(alpha = 0.50f),
              0.20f to Color.White.copy(alpha = 0.20f),
              0.72f to Color.White.copy(alpha = 0.09f),
              1.0f to Color.White.copy(alpha = 0.16f),
            )
        )
      } else {
        Brush.verticalGradient(
          colorStops =
            arrayOf(
              0.0f to Color.White.copy(alpha = 0.98f),
              0.25f to Color.White.copy(alpha = 0.68f),
              0.70f to Color.White.copy(alpha = 0.30f),
              1.0f to Color.White.copy(alpha = 0.72f),
            )
        )
      }
    }

  val frostedFillBrush =
    remember(tintColor, adaptiveTintAlpha, isDark, elevation, accentTint) {
      if (isDark) {
        if (elevation == GlassElevation.OVERLAY) {
          val overlayTop =
            if (accentTint != null && accentTint.alpha > 0f) {
              Color(
                red = PrismColors.DarkOverlayGlassFallback.red * 0.86f + accentTint.red * 0.14f,
                green = PrismColors.DarkOverlayGlassFallback.green * 0.86f + accentTint.green * 0.14f,
                blue = PrismColors.DarkOverlayGlassFallback.blue * 0.86f + accentTint.blue * 0.14f,
                alpha = 0.96f,
              )
            } else {
              PrismColors.DarkOverlayGlassFallback.copy(alpha = 0.96f)
            }
          // Non-blur overlays (e.g., popup context menus in separate windows) pair deep navy-violet with 12% white tint
          Brush.verticalGradient(
            colors =
              listOf(
                overlayTop,
                PrismColors.DarkCanvas.copy(alpha = 0.94f),
              )
          )
        } else {
          Brush.verticalGradient(
            colors =
              listOf(
                tintColor.copy(alpha = (adaptiveTintAlpha + 0.03f).coerceAtMost(0.15f)),
                tintColor,
              )
          )
        }
      } else {
        Brush.verticalGradient(
          colors =
            listOf(
              tintColor.copy(alpha = (adaptiveTintAlpha + 0.16f).coerceAtMost(0.84f)),
              tintColor.copy(alpha = (adaptiveTintAlpha + 0.04f).coerceAtMost(0.72f)),
            )
        )
      }
    }

  val shadowColor =
    if (isDark) PrismColors.DarkShadowAmbient else PrismColors.LightShadowAmbient

  val shouldUseHaze = useBackdropBlur && tier.supportsBackdropBlur && hazeState != null
  val useAgsl = shouldUseHaze && tier.supportsAgslRefraction && enableRefraction

  val backgroundModifier =
    if (shouldUseHaze && hazeState != null) {
      val hazeBg =
        if (isDark && elevation == GlassElevation.OVERLAY) {
          PrismColors.DarkOverlayGlassFallback.copy(alpha = 0.74f)
        } else {
          colorScheme.background
        }
      Modifier.hazeEffect(
        state = hazeState,
        style =
          HazeStyle(
            backgroundColor = hazeBg,
            blurRadius = blurDp,
            tint = HazeTint(color = tintColor),
            noiseFactor = 0f,
          ),
      )
    } else if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.background(color = colorScheme.surface)
    } else {
      val overlayTintMod =
        if (isDark && elevation == GlassElevation.OVERLAY) {
          Modifier.background(brush = frostedFillBrush).background(color = tintColor)
        } else {
          Modifier.background(brush = frostedFillBrush)
        }
      overlayTintMod
    }

  val topSheenModifier =
    if (enableTopSheen && tier != GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      val sheenStart = Color.White.copy(alpha = if (isDark) 0.10f else 0.34f)
      Modifier.drawBehind {
        val sheenHeight = size.height * 0.42f
        drawRect(
          brush =
            Brush.verticalGradient(
              colors = listOf(sheenStart, Color.Transparent),
              startY = 0f,
              endY = sheenHeight,
            )
        )
      }
    } else {
      Modifier
    }

  val animatedSheenModifier =
    if (enableAnimatedSheen && tier != GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.glassSheen(cornerRadius = cornerRadius)
    } else {
      Modifier
    }

  val clickableModifier =
    if (onClick != null) {
      Modifier.glassClickable(
        role = clickRole ?: Role.Button,
        hapticOnClick = hapticOnClick,
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
        .then(backgroundModifier)
        .then(topSheenModifier)
        .then(animatedSheenModifier)
        .border(width = PrismDimens.GlassBorderWidth, brush = specularBorderBrush, shape = shape),
  ) {
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
      content()
    }
  }
}

/**
 * Seamless slice of a single large rounded Liquid Glass list card so Paging 3 / LazyColumn
 * stays fully virtualized while rendering visually as ONE continuous rounded glass card
 * with a bright 1dp top specular edge and zero internal seams or cyclic blur passes.
 */
@Composable
fun ConnectedGlassCardSlice(
  isFirst: Boolean,
  isLast: Boolean,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 28.dp,
  topCornerRadius: Dp? = null,
  bottomCornerRadius: Dp? = null,
  fullBorderProgress: Float = 0f,
  hazeState: HazeState? = LocalHazeState.current,
  accentTint: Color? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme
  val tier = env.capabilityTier
  val isDark = env.isDark

  val resolvedTopRadius = topCornerRadius ?: if (isFirst) cornerRadius else 0.dp
  val resolvedBottomRadius = bottomCornerRadius ?: if (isLast) cornerRadius else 0.dp
  val topRounded = resolvedTopRadius > 0.5.dp
  val bottomRounded = resolvedBottomRadius > 0.5.dp
  val drawFullOutline = (isFirst && isLast) || fullBorderProgress > 0.08f || (topRounded && bottomRounded)

  val shape =
    remember(resolvedTopRadius, resolvedBottomRadius) {
      RoundedCornerShape(
        topStart = resolvedTopRadius,
        topEnd = resolvedTopRadius,
        bottomStart = resolvedBottomRadius,
        bottomEnd = resolvedBottomRadius,
      )
    }

  val baseSurfaceColor = if (isDark) PrismColors.DarkGlassTint else PrismColors.LightGlassBase
  val adaptiveTintAlpha =
    if (isDark) {
      (0.09f * env.tintScale).coerceIn(0.07f, 0.13f)
    } else {
      (0.68f * env.tintScale).coerceIn(0.24f, 0.94f)
    }

  val tintColor =
    remember(baseSurfaceColor, accentTint, tier, adaptiveTintAlpha, isDark) {
      if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
        if (isDark) PrismColors.DarkSurface.copy(alpha = 0.98f)
        else PrismColors.LightSurface.copy(alpha = 0.98f)
      } else if (accentTint != null && accentTint.alpha > 0f) {
        Color(
          red = baseSurfaceColor.red * 0.82f + accentTint.red * 0.18f,
          green = baseSurfaceColor.green * 0.82f + accentTint.green * 0.18f,
          blue = baseSurfaceColor.blue * 0.82f + accentTint.blue * 0.18f,
          alpha = adaptiveTintAlpha,
        )
      } else {
        baseSurfaceColor.copy(alpha = adaptiveTintAlpha)
      }
    }

  val fillModifier =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.background(color = colorScheme.surface)
    } else if (isFirst || fullBorderProgress > 0.08f) {
      val topHighlight =
        remember(tintColor, isDark) {
          Brush.verticalGradient(
            colors =
              listOf(
                tintColor.copy(
                  alpha = (tintColor.alpha + if (isDark) 0.03f else 0.14f).coerceAtMost(if (isDark) 0.14f else 0.92f)
                ),
                tintColor,
              )
          )
        }
      Modifier.background(brush = topHighlight)
    } else {
      Modifier.background(color = tintColor)
    }

  val borderHighlight =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      colorScheme.outlineVariant
    } else if (isDark) {
      Color.White.copy(alpha = 0.46f)
    } else {
      Color.White.copy(alpha = 0.96f)
    }
  val borderSide =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      colorScheme.outlineVariant
    } else if (isDark) {
      Color.White.copy(alpha = 0.14f)
    } else {
      Color.White.copy(alpha = 0.68f)
    }

  val perimeterBorderModifier =
    Modifier.drawWithContent {
      drawContent()
      val strokePx = PrismDimens.GlassTopEdgeWidth.toPx()
      val halfStroke = strokePx / 2f
      val maxR = minOf(size.width, size.height) / 2f
      val rTop = resolvedTopRadius.toPx().coerceAtMost(maxR)
      val rBottom = resolvedBottomRadius.toPx().coerceAtMost(maxR)

      if (drawFullOutline) {
        val path =
          Path().apply {
            moveTo(halfStroke + rTop, halfStroke)
            lineTo(size.width - halfStroke - rTop, halfStroke)
            if (rTop > 0.5f) {
              quadraticTo(size.width - halfStroke, halfStroke, size.width - halfStroke, halfStroke + rTop)
            }
            lineTo(size.width - halfStroke, size.height - halfStroke - rBottom)
            if (rBottom > 0.5f) {
              quadraticTo(
                size.width - halfStroke,
                size.height - halfStroke,
                size.width - halfStroke - rBottom,
                size.height - halfStroke,
              )
            }
            lineTo(halfStroke + rBottom, size.height - halfStroke)
            if (rBottom > 0.5f) {
              quadraticTo(halfStroke, size.height - halfStroke, halfStroke, size.height - halfStroke - rBottom)
            }
            lineTo(halfStroke, halfStroke + rTop)
            if (rTop > 0.5f) {
              quadraticTo(halfStroke, halfStroke, halfStroke + rTop, halfStroke)
            }
            close()
          }
        drawPath(
          path = path,
          brush =
            Brush.verticalGradient(
              colorStops =
                arrayOf(
                  0.0f to borderHighlight,
                  0.35f to borderSide,
                  1.0f to borderSide.copy(alpha = borderSide.alpha * 0.90f),
                )
            ),
          style = Stroke(width = strokePx),
        )
      } else if (topRounded) {
        val path =
          Path().apply {
            moveTo(halfStroke, size.height)
            lineTo(halfStroke, rTop)
            quadraticTo(halfStroke, halfStroke, rTop, halfStroke)
            lineTo(size.width - rTop, halfStroke)
            quadraticTo(size.width - halfStroke, halfStroke, size.width - halfStroke, rTop)
            lineTo(size.width - halfStroke, size.height)
          }
        drawPath(
          path = path,
          brush = Brush.verticalGradient(listOf(borderHighlight, borderSide)),
          style = Stroke(width = strokePx),
        )
      } else if (bottomRounded) {
        val path =
          Path().apply {
            moveTo(halfStroke, 0f)
            lineTo(halfStroke, size.height - rBottom)
            quadraticTo(halfStroke, size.height - halfStroke, rBottom, size.height - halfStroke)
            lineTo(size.width - rBottom, size.height - halfStroke)
            quadraticTo(
              size.width - halfStroke,
              size.height - halfStroke,
              size.width - halfStroke,
              size.height - rBottom,
            )
            lineTo(size.width - halfStroke, 0f)
          }
        drawPath(
          path = path,
          brush = Brush.verticalGradient(listOf(borderSide, borderHighlight.copy(alpha = borderHighlight.alpha * 0.65f))),
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

  Box(
    modifier =
      modifier
        .clip(shape)
        .then(fillModifier)
        .then(perimeterBorderModifier),
  ) {
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
      content()
    }
  }
}

/**
 * Legacy atmospheric Prism backdrop retained for backwards compatibility; prefer
 * [DriftingBlobsBackdrop] from GlassMotion.kt for zero-allocation 120fps rendering.
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

      val c1 = Offset(size.width * 0.14f + dx1, size.height * 0.12f + dy1)
      val r1 = size.maxDimension * 0.52f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(PrismColors.AmbientSky.copy(alpha = alphaScale), Color.Transparent),
            center = c1,
            radius = r1,
          ),
        radius = r1,
        center = c1,
      )

      val c2 = Offset(size.width * 0.86f + dx2, size.height * 0.36f + dy2)
      val r2 = size.maxDimension * 0.48f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(PrismColors.AmbientLavender.copy(alpha = alphaScale * 0.88f), Color.Transparent),
            center = c2,
            radius = r2,
          ),
        radius = r2,
        center = c2,
      )

      val c3 = Offset(size.width * 0.16f + dx3, size.height * 0.56f + dy3)
      val r3 = size.maxDimension * 0.44f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(PrismColors.AmbientRose.copy(alpha = alphaScale * 0.72f), Color.Transparent),
            center = c3,
            radius = r3,
          ),
        radius = r3,
        center = c3,
      )

      val c4 = Offset(size.width * 0.74f + dx4, size.height * 0.86f + dy4)
      val r4 = size.maxDimension * 0.54f
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(PrismColors.AmbientMint.copy(alpha = alphaScale * 0.92f), Color.Transparent),
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
 * using an offscreen alpha mask. Pair with generous vertical contentPadding so drop shadows
 * are never clipped by the offscreen layer bounds.
 */
fun Modifier.horizontalRightEdgeFade(
  fadeFraction: Float = 1f,
  fadeWidth: Dp = 32.dp,
): Modifier {
  val clamped = fadeFraction.coerceIn(0f, 1f)
  if (clamped <= 0.01f) return this
  return this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
      drawContent()
      val fadePx = fadeWidth.toPx().coerceAtMost(size.width * 0.40f)
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
 * fade gently behind floating Liquid Glass toolbars and tab capsules without recomposing.
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
            blurRadius = 22.dp,
            tint = HazeTint(color = bg.copy(alpha = if (scrollProgress != null) 0.24f else 0.60f)),
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
            val topAlpha = (0.64f + 0.34f * p).coerceIn(0f, 1f)
            val midAlpha = (0.50f + 0.46f * p).coerceIn(0f, 1f)
            val lowerAlpha = (0.20f + 0.56f * p).coerceIn(0f, 1f)
            Brush.verticalGradient(
              colorStops =
                arrayOf(
                  0.0f to bg.copy(alpha = topAlpha),
                  0.70f to bg.copy(alpha = midAlpha),
                  0.88f to bg.copy(alpha = lowerAlpha),
                  1.0f to Color.Transparent,
                )
            )
          } else {
            val topAlpha = (0.28f + 0.60f * p).coerceIn(0f, 1f)
            val midAlpha = (0.10f + 0.36f * p).coerceIn(0f, 1f)
            Brush.verticalGradient(
              colors = listOf(bg.copy(alpha = topAlpha), bg.copy(alpha = midAlpha), Color.Transparent)
            )
          }
        } else {
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, bg.copy(alpha = 0.40f), bg.copy(alpha = 0.84f))
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
