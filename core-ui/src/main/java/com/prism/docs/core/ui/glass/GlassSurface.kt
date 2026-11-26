package com.prism.docs.core.ui.glass

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
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
    blurRadius = 18.dp,
    shadowElevation = 0.dp,
    lightTintAlpha = 0.60f,
    darkTintAlpha = 0.09f,
  ),

  /** Floating navigation capsules, search bar, top bar, and round glass buttons. */
  FLOATING(
    blurRadius = 24.dp,
    shadowElevation = 12.dp,
    lightTintAlpha = 0.64f,
    darkTintAlpha = 0.11f,
  ),

  /** Modal bottom sheets, context menus, and popovers. */
  OVERLAY(
    blurRadius = 28.dp,
    shadowElevation = 18.dp,
    lightTintAlpha = 0.78f,
    darkTintAlpha = 0.13f,
  ),
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
  start + (stop - start) * fraction.coerceIn(0f, 1f)

/**
 * Draws a soft exterior-only drop shadow strictly OUTSIDE [shape] using `ClipOp.Difference`.
 * Because interior pixels are excluded mathematically before drawing, hardware shadow umbra
 * polygons can NEVER bleed through translucent or crystal-clear Liquid Glass.
 */
private fun Modifier.exteriorGlassShadow(
  enabled: Boolean,
  elevation: Dp,
  shape: Shape,
  shadowColor: Color,
): Modifier {
  if (!enabled || elevation <= 0.dp) return this
  return this.drawWithCache {
    if (size.width < 2f || size.height < 2f) {
      return@drawWithCache onDrawBehind {}
    }
    val outline = shape.createOutline(size, layoutDirection, this)
    val clipOutPath = Path().apply { addOutline(outline) }
    val baseAlpha = shadowColor.alpha
    val dy1 = (elevation.toPx() * 0.22f).coerceAtMost(6.dp.toPx())
    val dy2 = (elevation.toPx() * 0.48f).coerceAtMost(12.dp.toPx())
    val farColor = shadowColor.copy(alpha = (baseAlpha * 0.22f).coerceIn(0f, 0.16f))
    val nearColor = shadowColor.copy(alpha = (baseAlpha * 0.36f).coerceIn(0f, 0.24f))
    onDrawBehind {
      clipPath(path = clipOutPath, clipOp = ClipOp.Difference) {
        translate(top = dy2) {
          drawOutline(outline = outline, color = farColor)
        }
        translate(top = dy1) {
          drawOutline(outline = outline, color = nearColor)
        }
      }
    }
  }
}

/**
 * Core Liquid Glass surface container engineered for zero square-patch artifacts and locked 120fps:
 * - Only FLOATING / OVERLAY glass (top bar, tab bar, search orb, sheets, selection bar, context
 *   menu) samples/blurs the backdrop; inline cards and everything inside sheets use non-blur
 *   translucent glass (never nested haze).
 * - On API 33+, floating surfaces with [enableRefraction] sample the real captured backdrop
 *   [GraphicsLayer] behind the surface (never distorting foreground text/icons) and apply a cached
 *   AGSL lens-refraction + chromatic-dispersion + vibrancy + Fresnel-rim shader.
 * - Maps `GlassEnvironment.clarity` (0% frosted -> 100% crystal-clear glass with 0–4dp blur,
 *   0–4% tint alpha, strong refraction, specular rim, and automatic legibility guard).
 * - Uses exterior-only shadows (never hardware `Modifier.shadow`) so no inset rectangular patch
 *   ever shows through translucent glass.
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
  val debugConfig = LocalGlassDebugConfig.current
  val insideFloatingGlass = LocalInsideFloatingGlass.current
  val backdropState = LocalGlassBackdrop.current
  val dynamics = LocalGlassDynamics.current
  val motion = LocalGlassMotion.current
  val colorScheme = MaterialTheme.colorScheme
  val tier = env.capabilityTier
  val isDark = env.isDark
  val clarity = env.clarity.coerceIn(0f, 1f)

  // Only top-level floating overlays (never SUBTLE cards or children inside floating glass)
  val isFloatingLayer =
    useBackdropBlur && elevation != GlassElevation.SUBTLE && !insideFloatingGlass && !env.reduceTransparency

  // Clarity mapping: 0% = frosted, 100% = nearly clear (blur 0–4dp, tint 0–4%)
  val baseSurfaceColor = if (isDark) PrismColors.DarkGlassTint else PrismColors.LightGlassBase
  val frostedTintAlpha =
    if (isDark) {
      (elevation.darkTintAlpha * env.tintScale).coerceIn(0.08f, 0.16f)
    } else {
      (elevation.lightTintAlpha * env.tintScale).coerceIn(0.46f, 0.86f)
    }
  val clearTintAlpha =
    if (isFloatingLayer) {
      if (isDark) 0.02f else 0.03f
    } else {
      // Inline non-blur cards stay subtly legible even at 100% clarity
      if (isDark) 0.04f else 0.14f
    }
  val adaptiveTintAlpha = lerpFloat(frostedTintAlpha, clearTintAlpha, clarity)

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

  // Never animate blur radius automatically; derive statically from clarity (0% -> 24-28dp, 100% -> 0-2dp)
  val blurDp = lerp(elevation.blurRadius, if (elevation == GlassElevation.OVERLAY) 2.dp else 0.dp, clarity)

  // Rim specular highlight intensifies slightly on clearer glass so optical edges stay luminous
  val rimBoost = lerpFloat(0.92f, 1.24f, clarity)
  val specularBorderBrush =
    remember(tier, isDark, rimBoost, colorScheme.outline, colorScheme.outlineVariant) {
      if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
        Brush.verticalGradient(colors = listOf(colorScheme.outline, colorScheme.outlineVariant))
      } else if (isDark) {
        Brush.verticalGradient(
          colorStops =
            arrayOf(
              0.0f to Color.White.copy(alpha = (0.48f * rimBoost).coerceAtMost(0.76f)),
              0.22f to Color.White.copy(alpha = (0.20f * rimBoost).coerceAtMost(0.36f)),
              0.74f to Color.White.copy(alpha = (0.09f * rimBoost).coerceAtMost(0.18f)),
              1.0f to Color.White.copy(alpha = (0.18f * rimBoost).coerceAtMost(0.32f)),
            )
        )
      } else {
        Brush.verticalGradient(
          colorStops =
            arrayOf(
              0.0f to Color.White.copy(alpha = 0.98f),
              0.25f to Color.White.copy(alpha = (0.66f * rimBoost).coerceAtMost(0.92f)),
              0.72f to Color.White.copy(alpha = (0.28f * rimBoost).coerceAtMost(0.50f)),
              1.0f to Color.White.copy(alpha = (0.72f * rimBoost).coerceAtMost(0.92f)),
            )
        )
      }
    }

  val frostedFillBrush =
    remember(tintColor, adaptiveTintAlpha, isDark, elevation, accentTint, clarity) {
      if (isDark) {
        if (elevation == GlassElevation.OVERLAY && !useBackdropBlur) {
          val overlayAlpha = lerpFloat(0.94f, 0.72f, clarity)
          val overlayTop =
            if (accentTint != null && accentTint.alpha > 0f) {
              Color(
                red = PrismColors.DarkOverlayGlassFallback.red * 0.86f + accentTint.red * 0.14f,
                green = PrismColors.DarkOverlayGlassFallback.green * 0.86f + accentTint.green * 0.14f,
                blue = PrismColors.DarkOverlayGlassFallback.blue * 0.86f + accentTint.blue * 0.14f,
                alpha = overlayAlpha,
              )
            } else {
              PrismColors.DarkOverlayGlassFallback.copy(alpha = overlayAlpha)
            }
          Brush.verticalGradient(
            colors = listOf(overlayTop, PrismColors.DarkCanvas.copy(alpha = (overlayAlpha - 0.04f).coerceAtLeast(0.62f)))
          )
        } else {
          Brush.verticalGradient(
            colors =
              listOf(
                tintColor.copy(alpha = (adaptiveTintAlpha + 0.025f * (1f - clarity * 0.6f)).coerceAtMost(0.16f)),
                tintColor,
              )
          )
        }
      } else {
        Brush.verticalGradient(
          colors =
            listOf(
              tintColor.copy(alpha = (adaptiveTintAlpha + 0.10f * (1f - clarity * 0.7f)).coerceAtMost(0.84f)),
              tintColor,
            )
        )
      }
    }

  // Real backdrop-sampling AGSL refraction on API 33+ floating glass only (tab bar, top bar, sheets)
  val canSampleBackdrop =
    isFloatingLayer &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      tier.supportsAgslRefraction &&
      backdropState != null &&
      backdropState.blobsLayer != null

  val shouldUseHazeFallback =
    isFloatingLayer &&
      !canSampleBackdrop &&
      debugConfig.enableHaze &&
      tier.supportsBackdropBlur &&
      hazeState != null &&
      blurDp >= 1.dp

  // Interactive press tracking: floating glass swells to ~1.04f and strengthens refraction;
  // inline cards depress gently to 0.97f. Read strictly in graphicsLayer / draw phase!
  val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
  val haptics = if (onClick != null) rememberPrismHaptics() else null
  val isPressed =
    if (interactionSource != null) interactionSource.collectIsPressedAsState().value else false
  val pressEnergyState =
    if (interactionSource != null) {
      animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = if (motion) spring(dampingRatio = 0.56f, stiffness = Spring.StiffnessMedium) else snap(),
        label = "glass_press_energy",
      )
    } else {
      null
    }

  val pressScaleModifier =
    if (pressEnergyState != null) {
      val targetScale = if (elevation != GlassElevation.SUBTLE) 1.04f else 0.97f
      Modifier.graphicsLayer {
        val e = pressEnergyState.value
        val s = 1f + (targetScale - 1f) * e
        scaleX = s
        scaleY = s
      }
    } else {
      Modifier
    }

  val clickModifier =
    if (onClick != null && interactionSource != null) {
      Modifier.clickable(
        interactionSource = interactionSource,
        indication = null,
        role = clickRole ?: Role.Button,
        onClick = {
          if (hapticOnClick) haptics?.tap()
          onClick()
        },
      )
    } else {
      Modifier
    }

  val surfaceWindowOffset = remember { mutableStateOf(Offset.Zero) }
  val positionModifier =
    if (canSampleBackdrop) {
      Modifier.onGloballyPositioned { coords ->
        surfaceWindowOffset.value = coords.positionInWindow()
      }
    } else {
      Modifier
    }

  val shadowColor =
    if (isDark) PrismColors.DarkShadowAmbient else PrismColors.LightShadowAmbient
  val exteriorShadowMod =
    Modifier.exteriorGlassShadow(
      enabled =
        debugConfig.enableShadow &&
          elevation != GlassElevation.SUBTLE &&
          !insideFloatingGlass &&
          tier != GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY,
      elevation = elevation.shadowElevation,
      shape = shape,
      shadowColor = shadowColor,
    )

  // Non-AGSL background pass (Haze with TRANSPARENT backgroundColor, or clean translucent fill)
  val baseBackgroundModifier =
    when {
      tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY ->
        Modifier.background(color = colorScheme.surface)
      canSampleBackdrop ->
        Modifier // Backdrop is drawn in dedicated child layer below content() so foreground text stays sharp!
      shouldUseHazeFallback && hazeState != null ->
        Modifier.hazeEffect(
          state = hazeState,
          style =
            HazeStyle(
              backgroundColor = Color.Transparent,
              blurRadius = blurDp,
              tint = HazeTint(color = tintColor),
              noiseFactor = 0f,
            ),
        )
      else ->
        Modifier.background(brush = frostedFillBrush)
    }

  // Adaptive contrast scrim on ultra-clear floating glass (>70% clarity) to guarantee >=4.5:1 legibility
  val ultraClearScrimAlpha =
    if (isFloatingLayer && clarity > 0.68f) {
      val t = ((clarity - 0.68f) / 0.32f).coerceIn(0f, 1f)
      if (isDark) t * 0.16f else t * 0.22f
    } else {
      0f
    }

  val topSheenModifier =
    if (enableTopSheen && debugConfig.enableSheen && tier != GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      val sheenStart =
        Color.White.copy(
          alpha =
            if (isDark) lerpFloat(0.11f, 0.20f, clarity)
            else lerpFloat(0.32f, 0.50f, clarity)
        )
      Modifier.drawWithCache {
        if (size.width < 2f || size.height < 2f) {
          return@drawWithCache onDrawBehind {}
        }
        val sheenHeight = (size.height * 0.42f).coerceAtLeast(1f)
        val sheenBrush =
          Brush.verticalGradient(
            colors = listOf(sheenStart, Color.Transparent),
            startY = 0f,
            endY = sheenHeight,
          )
        onDrawBehind {
          drawRect(brush = sheenBrush)
        }
      }
    } else {
      Modifier
    }

  val animatedSheenModifier =
    if (enableAnimatedSheen && debugConfig.enableSheen && tier != GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      Modifier.glassSheen(cornerRadius = cornerRadius)
    } else {
      Modifier
    }

  // Dynamic rim brightness lift on floating bars when scrolling fast (draw-phase only)
  val dynamicRimModifier =
    if (isFloatingLayer && motion) {
      Modifier.drawWithContent {
        drawContent()
        if (size.width < 2f || size.height < 2f) return@drawWithContent
        val energy =
          (if (elevation == GlassElevation.OVERLAY) dynamics.sheetDragEnergy else dynamics.scrollEnergy) +
            (pressEnergyState?.value ?: 0f) * 0.45f
        if (energy > 0.02f) {
          val rimAlpha = (energy * if (isDark) 0.32f else 0.45f).coerceIn(0f, 0.55f)
          val outline = shape.createOutline(size, layoutDirection, this)
          drawOutline(
            outline = outline,
            brush =
              Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = rimAlpha), Color.Transparent)
              ),
            style = Stroke(width = 1.25.dp.toPx()),
          )
        }
      }
    } else {
      Modifier
    }

  // Legibility guard for high-clarity glass: subtle text shadow keeps contrast >= 4.5:1
  val currentTextStyle = LocalTextStyle.current
  val guardedTextStyle =
    remember(currentTextStyle, clarity, isDark, env.reduceTransparency, isFloatingLayer) {
      if (env.reduceTransparency || clarity <= 0.45f) {
        currentTextStyle
      } else {
        val boost = ((clarity - 0.45f) / 0.55f).coerceIn(0f, 1f) * if (isFloatingLayer) 1f else 0.65f
        val shadow =
          if (isDark) {
            Shadow(
              color = Color.Black.copy(alpha = (0.62f * boost).coerceIn(0f, 0.65f)),
              offset = Offset(0f, 1.2f),
              blurRadius = 3.5f + 4.5f * boost,
            )
          } else {
            Shadow(
              color = Color.White.copy(alpha = (0.88f * boost).coerceIn(0f, 0.90f)),
              offset = Offset(0f, 0.8f),
              blurRadius = 4.0f + 5.0f * boost,
            )
          }
        currentTextStyle.copy(shadow = shadow)
      }
    }

  Box(
    modifier =
      modifier
        .then(positionModifier)
        .then(pressScaleModifier)
        .then(clickModifier)
        .then(exteriorShadowMod)
        // CRITICAL: clip(shape) strictly BEFORE backdrop/haze/tint/sheen/scrim!
        .clip(shape)
        .then(baseBackgroundModifier)
        .then(topSheenModifier)
        .then(animatedSheenModifier)
        .then(dynamicRimModifier)
        .border(width = PrismDimens.GlassBorderWidth, brush = specularBorderBrush, shape = shape),
  ) {
    // Dedicated backdrop-refraction layer drawn strictly BEHIND child content (API 33+ floating glass)
    if (canSampleBackdrop && backdropState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val cachedEffect = remember { CachedBackdropRefractionEffect() }
      val applyRefraction = enableRefraction && debugConfig.enableEdgeRefraction
      val applyBlur = debugConfig.enableHaze

      Box(
        modifier =
          Modifier.matchParentSize()
            .graphicsLayer {
              val scrollE = if (motion) dynamics.scrollEnergy else 0f
              val dragE = if (motion && elevation == GlassElevation.OVERLAY) dynamics.sheetDragEnergy else 0f
              val pressE = if (motion) (pressEnergyState?.value ?: 0f) else 0f
              val motionBoost = (scrollE * 0.55f + dragE * 0.70f + pressE * 0.85f).coerceIn(0f, 1.4f)

              val baseBezelPx = lerp(10.dp, 18.dp, clarity).toPx()
              val baseRefractPx =
                lerp(3.2.dp, 8.5.dp, clarity).toPx() * (1f + motionBoost * 0.42f)
              val dispersionPx =
                lerp(0.75.dp, 2.2.dp, clarity).toPx() * (1f + motionBoost * 0.75f)
              val vibrancy = lerpFloat(1.08f, 1.20f, clarity)
              val rimBright = lerpFloat(0.85f, 1.35f, clarity) * (1f + motionBoost * 0.35f)
              val effectiveBlurPx = if (applyBlur) blurDp.toPx() else 0f

              renderEffect =
                cachedEffect.obtain(
                  widthPx = size.width,
                  heightPx = size.height,
                  cornerRadiusPx = cornerRadius.toPx(),
                  bezelWidthPx = baseBezelPx,
                  refractionStrengthPx = baseRefractPx,
                  dispersionPx = dispersionPx,
                  vibrancy = vibrancy,
                  rimBrightness = rimBright,
                  blurRadiusPx = effectiveBlurPx,
                  isDark = isDark,
                  applyRefractionShader = applyRefraction,
                )
              clip = true
            }
            .drawBehind {
              if (GlassBackdropRecordingGuard.isRecording() || size.width < 2f || size.height < 2f) {
                return@drawBehind
              }
              val myPos = surfaceWindowOffset.value
              if (!myPos.x.isFinite() || !myPos.y.isFinite()) return@drawBehind

              val blobsSource = backdropState.resolvedBlobsSource()
              val blobs = blobsSource?.blobsLayer
              if (blobs != null && !blobs.isReleased) {
                val bPos = blobsSource.blobsWindowOffset
                val dx = bPos.x - myPos.x
                val dy = bPos.y - myPos.y
                if (dx.isFinite() && dy.isFinite()) {
                  runCatching {
                    translate(left = dx, top = dy) { drawLayer(blobs) }
                  }
                }
              }

              val contentSource = backdropState.resolvedContentSource()
              val contentLayer = contentSource?.contentLayer
              if (contentLayer != null && !contentLayer.isReleased) {
                val cPos = contentSource.contentWindowOffset
                val dx = cPos.x - myPos.x
                val dy = cPos.y - myPos.y
                if (dx.isFinite() && dy.isFinite()) {
                  runCatching {
                    translate(left = dx, top = dy) { drawLayer(contentLayer) }
                  }
                }
              }
            }
      )

      // Subtle translucent tint & legibility scrim overlaid over the refracted backdrop
      val ultraClearScrimBrush =
        remember(ultraClearScrimAlpha, isDark) {
          if (ultraClearScrimAlpha > 0.005f) {
            val scrimColor = if (isDark) PrismColors.DarkCanvas else Color.White
            Brush.verticalGradient(
              colors =
                listOf(
                  scrimColor.copy(alpha = ultraClearScrimAlpha * 0.65f),
                  scrimColor.copy(alpha = ultraClearScrimAlpha),
                  scrimColor.copy(alpha = ultraClearScrimAlpha * 0.72f),
                )
            )
          } else {
            null
          }
        }
      Box(
        modifier =
          Modifier.matchParentSize()
            .drawBehind {
              if (tintColor.alpha > 0.002f) {
                drawRect(color = tintColor)
              }
              if (ultraClearScrimBrush != null) {
                drawRect(brush = ultraClearScrimBrush)
              }
            }
      )
    }

    CompositionLocalProvider(
      LocalContentColor provides colorScheme.onSurface,
      LocalTextStyle provides guardedTextStyle,
      LocalInsideFloatingGlass provides (insideFloatingGlass || isFloatingLayer),
    ) {
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
  val clarity = env.clarity.coerceIn(0f, 1f)

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
      lerpFloat((0.10f * env.tintScale).coerceIn(0.08f, 0.14f), 0.04f, clarity)
    } else {
      lerpFloat((0.72f * env.tintScale).coerceIn(0.56f, 0.88f), 0.24f, clarity)
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
        remember(tintColor, isDark, clarity) {
          Brush.verticalGradient(
            colors =
              listOf(
                tintColor.copy(
                  alpha =
                    (tintColor.alpha + if (isDark) 0.025f else 0.10f * (1f - clarity * 0.6f))
                      .coerceAtMost(if (isDark) 0.14f else 0.92f)
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
      Color.White.copy(alpha = lerpFloat(0.42f, 0.62f, clarity))
    } else {
      Color.White.copy(alpha = 0.96f)
    }
  val borderSide =
    if (tier == GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY) {
      colorScheme.outlineVariant
    } else if (isDark) {
      Color.White.copy(alpha = lerpFloat(0.14f, 0.22f, clarity))
    } else {
      Color.White.copy(alpha = 0.68f)
    }

  val perimeterBorderModifier =
    Modifier.drawWithCache {
      if (size.width < 2f || size.height < 2f) {
        return@drawWithCache onDrawWithContent { drawContent() }
      }
      val strokePx = PrismDimens.GlassTopEdgeWidth.toPx()
      val stroke = Stroke(width = strokePx)
      val halfStroke = strokePx / 2f
      val maxR = minOf(size.width, size.height) / 2f
      val rTop = resolvedTopRadius.toPx().coerceAtMost(maxR)
      val rBottom = resolvedBottomRadius.toPx().coerceAtMost(maxR)

      when {
        drawFullOutline -> {
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
          val brush =
            Brush.verticalGradient(
              colorStops =
                arrayOf(
                  0.0f to borderHighlight,
                  0.35f to borderSide,
                  1.0f to borderSide.copy(alpha = borderSide.alpha * 0.90f),
                )
            )
          onDrawWithContent {
            drawContent()
            drawPath(path = path, brush = brush, style = stroke)
          }
        }
        topRounded -> {
          val path =
            Path().apply {
              moveTo(halfStroke, size.height)
              lineTo(halfStroke, rTop)
              quadraticTo(halfStroke, halfStroke, rTop, halfStroke)
              lineTo(size.width - rTop, halfStroke)
              quadraticTo(size.width - halfStroke, halfStroke, size.width - halfStroke, rTop)
              lineTo(size.width - halfStroke, size.height)
            }
          val brush = Brush.verticalGradient(listOf(borderHighlight, borderSide))
          onDrawWithContent {
            drawContent()
            drawPath(path = path, brush = brush, style = stroke)
          }
        }
        bottomRounded -> {
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
          val brush =
            Brush.verticalGradient(
              listOf(borderSide, borderHighlight.copy(alpha = borderHighlight.alpha * 0.65f))
            )
          onDrawWithContent {
            drawContent()
            drawPath(path = path, brush = brush, style = stroke)
          }
        }
        else -> {
          val leftStart = Offset(halfStroke, 0f)
          val leftEnd = Offset(halfStroke, size.height)
          val rightStart = Offset(size.width - halfStroke, 0f)
          val rightEnd = Offset(size.width - halfStroke, size.height)
          onDrawWithContent {
            drawContent()
            drawLine(color = borderSide, start = leftStart, end = leftEnd, strokeWidth = strokePx)
            drawLine(color = borderSide, start = rightStart, end = rightEnd, strokeWidth = strokePx)
          }
        }
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
 * Lightweight right-edge hint scrim for horizontally scrolling rows without allocating
 * an expensive `CompositingStrategy.Offscreen` buffer over large strips.
 */
fun Modifier.horizontalRightEdgeFade(
  fadeFraction: Float = 1f,
  fadeWidth: Dp = 28.dp,
  fadeColor: Color = Color.Transparent,
): Modifier {
  val clamped = fadeFraction.coerceIn(0f, 1f)
  if (clamped <= 0.01f || fadeColor.alpha <= 0.001f) return this
  val endColor = fadeColor.copy(alpha = (0.72f * clamped).coerceIn(0f, 0.85f))
  return this.drawWithCache {
    val fadePx = fadeWidth.toPx().coerceAtMost(size.width * 0.30f)
    if (fadePx <= 0f) {
      onDrawWithContent { drawContent() }
    } else {
      val fadeBrush =
        Brush.horizontalGradient(
          colors = listOf(Color.Transparent, endColor),
          startX = size.width - fadePx,
          endX = size.width,
        )
      onDrawWithContent {
        drawContent()
        drawRect(brush = fadeBrush)
      }
    }
  }
}

/**
 * Soft scroll-edge scrim placed at the top or bottom viewport edge so scrolling document cards
 * fade gently behind floating Liquid Glass toolbars and tab capsules without consuming extra
 * Haze blur layers or triggering recompositions.
 */
@Composable
fun ScrollEdgeFade(
  isTop: Boolean,
  modifier: Modifier = Modifier,
  height: Dp = 64.dp,
  strongTint: Boolean = false,
  scrollProgress: State<Float>? = null,
) {
  val bg = MaterialTheme.colorScheme.background

  val staticBottomBrush =
    remember(bg, isTop) {
      if (!isTop) {
        Brush.verticalGradient(
          colorStops =
            arrayOf(
              0.0f to Color.Transparent,
              0.38f to bg.copy(alpha = 0.36f),
              0.72f to bg.copy(alpha = 0.78f),
              1.0f to bg.copy(alpha = 0.96f),
            )
        )
      } else {
        null
      }
    }

  val staticTopBrush =
    remember(bg, isTop, strongTint, scrollProgress == null) {
      if (isTop && scrollProgress == null) {
        if (strongTint) {
          Brush.verticalGradient(
            colorStops =
              arrayOf(
                0.0f to bg.copy(alpha = 0.92f),
                0.66f to bg.copy(alpha = 0.80f),
                0.86f to bg.copy(alpha = 0.50f),
                1.0f to Color.Transparent,
              )
          )
        } else {
          Brush.verticalGradient(
            colors = listOf(bg.copy(alpha = 0.82f), bg.copy(alpha = 0.42f), Color.Transparent)
          )
        }
      } else {
        null
      }
    }

  val scrimModifier =
    when {
      staticBottomBrush != null -> Modifier.background(brush = staticBottomBrush)
      staticTopBrush != null -> Modifier.background(brush = staticTopBrush)
      else ->
        Modifier.drawBehind {
          val p = scrollProgress?.value ?: 1f
          val brush =
            if (strongTint) {
              val topAlpha = (0.58f + 0.34f * p).coerceIn(0f, 0.94f)
              val midAlpha = (0.38f + 0.42f * p).coerceIn(0f, 0.82f)
              val lowerAlpha = (0.12f + 0.38f * p).coerceIn(0f, 0.56f)
              Brush.verticalGradient(
                colorStops =
                  arrayOf(
                    0.0f to bg.copy(alpha = topAlpha),
                    0.66f to bg.copy(alpha = midAlpha),
                    0.86f to bg.copy(alpha = lowerAlpha),
                    1.0f to Color.Transparent,
                  )
              )
            } else {
              val topAlpha = (0.24f + 0.58f * p).coerceIn(0f, 0.86f)
              val midAlpha = (0.08f + 0.34f * p).coerceIn(0f, 0.48f)
              Brush.verticalGradient(
                colors = listOf(bg.copy(alpha = topAlpha), bg.copy(alpha = midAlpha), Color.Transparent)
              )
            }
          drawRect(brush = brush)
        }
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(height)
        .then(scrimModifier)
  )
}
