package com.prism.docs.core.ui.glass

import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Draw-phase Liquid Glass lens pill for horizontal floating navigation docks.
 *
 * Renders at locked 120fps with zero recompositions per frame:
 * - Elastic mercury stretch driven by velocity lag (`abs(position - lagPosition)`)
 * - Interactive lens inflation and iridescent caustic rim lift while finger-scrubbing (`dragLift`)
 * - Multi-layer crystalline fill, top specular highlight crescent, and reactive top-rim bloom
 */
internal fun Modifier.liquidGlassDockPill(
  positionState: State<Float>,
  lagPositionState: State<Float>,
  dragLiftState: State<Float>,
  count: Int,
  isDark: Boolean,
  accentColor: Color,
  shadowColor: Color,
  inset: Dp = 5.dp,
): Modifier {
  if (count <= 0) return this
  return this
    .drawBehind {
      val insetPx = inset.toPx()
      if (size.width <= 2f * insetPx + 4f || size.height <= 2f * insetPx + 4f) return@drawBehind
      val innerW = (size.width - 2f * insetPx).coerceAtLeast(1f)
      val segW = innerW / count
      val baseH = (size.height - 2f * insetPx).coerceAtLeast(1f)
      if (segW <= 0f || baseH <= 0f) return@drawBehind

      val pos = positionState.value.coerceIn(-0.16f, (count - 1).toFloat() + 0.16f)
      val lag = lagPositionState.value.coerceIn(-0.16f, (count - 1).toFloat() + 0.16f)
      val lift = dragLiftState.value.coerceIn(0f, 1.25f)

      // Elastic velocity-driven horizontal stretch & vertical volume compression
      val velocityDelta = pos - lag
      val stretch = abs(velocityDelta).coerceIn(0f, 1.35f)
      val extraW = (stretch * segW * 0.28f) + (lift * 5.dp.toPx())
      val heightDelta = (lift * 3.dp.toPx()) - (stretch * 3.2.dp.toPx())

      val pillW = (segW + extraW).coerceAtMost(innerW).coerceAtLeast(1f)
      val minPillH = (baseH * 0.78f).coerceAtLeast(1f)
      val maxPillH = (size.height - 3.dp.toPx()).coerceAtLeast(minPillH)
      val pillH = (baseH + heightDelta).coerceIn(minPillH, maxPillH)

      // Bias center slightly toward the leading edge of motion for organic liquid inertia
      val baseCenterX = insetPx + (pos + 0.5f) * segW
      val motionBiasX = velocityDelta.coerceIn(-0.5f, 0.5f) * segW * 0.08f
      val minLeft = insetPx - 2.dp.toPx()
      val maxLeft = (size.width - insetPx + 2.dp.toPx() - pillW).coerceAtLeast(minLeft)
      val pillLeft = (baseCenterX + motionBiasX - pillW * 0.5f).coerceIn(minLeft, maxLeft)
      val pillTop = (size.height - pillH) * 0.5f
      val cornerR = min(pillW, pillH) * 0.5f

      // 1. Soft diffused ambient shadow underneath the floating lens
      val shadowOffsetY = (2.5.dp.toPx() + lift * 2.dp.toPx())
      drawRoundRect(
        color = shadowColor.copy(alpha = (shadowColor.alpha * (1f + lift * 0.35f)).coerceAtMost(0.65f)),
        topLeft = Offset(pillLeft, pillTop + shadowOffsetY),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(cornerR, cornerR),
      )

      // 2. Crystalline multi-stop liquid body fill
      val fillBrush =
        if (isDark) {
          val topAlpha = (0.27f + lift * 0.10f).coerceAtMost(0.42f)
          val midAlpha = (0.17f + lift * 0.07f).coerceAtMost(0.30f)
          val botAlpha = (0.13f + lift * 0.05f).coerceAtMost(0.24f)
          Brush.verticalGradient(
            colorStops =
              arrayOf(
                0.0f to Color.White.copy(alpha = topAlpha),
                0.48f to Color.White.copy(alpha = midAlpha),
                1.0f to accentColor.copy(alpha = botAlpha),
              ),
            startY = pillTop,
            endY = pillTop + pillH,
          )
        } else {
          val topAlpha = (0.96f + lift * 0.04f).coerceAtMost(1f)
          val midAlpha = (0.86f + lift * 0.08f).coerceAtMost(0.96f)
          val botAlpha = (0.76f + lift * 0.10f).coerceAtMost(0.92f)
          Brush.verticalGradient(
            colorStops =
              arrayOf(
                0.0f to Color.White.copy(alpha = topAlpha),
                0.55f to Color.White.copy(alpha = midAlpha),
                1.0f to Color.White.copy(alpha = botAlpha),
              ),
            startY = pillTop,
            endY = pillTop + pillH,
          )
        }

      drawRoundRect(
        brush = fillBrush,
        topLeft = Offset(pillLeft, pillTop),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(cornerR, cornerR),
      )

      // 3. Subtle prismatic caustic glow along lower lens curvature
      val causticAlpha =
        if (isDark) (0.20f + lift * 0.14f + stretch * 0.06f).coerceAtMost(0.42f)
        else (0.11f + lift * 0.10f + stretch * 0.05f).coerceAtMost(0.26f)
      val causticRadius = pillW * 0.62f
      drawRoundRect(
        brush =
          Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = causticAlpha), Color.Transparent),
            center = Offset(pillLeft + pillW * 0.5f, pillTop + pillH * 0.92f),
            radius = causticRadius.coerceAtLeast(1f),
          ),
        topLeft = Offset(pillLeft, pillTop),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(cornerR, cornerR),
      )

      // 4. Inner upper specular sheen crescent (curved optical lens reflection)
      val sheenInsetX = 6.dp.toPx()
      val sheenW = (pillW - sheenInsetX * 2f).coerceAtLeast(0f)
      val sheenH = (pillH * 0.36f).coerceAtLeast(2f)
      if (sheenW > 4f) {
        drawRoundRect(
          brush =
            Brush.verticalGradient(
              colors =
                listOf(
                  Color.White.copy(
                    alpha =
                      if (isDark) (0.28f + lift * 0.14f).coerceAtMost(0.48f)
                      else (0.64f + lift * 0.18f).coerceAtMost(0.88f)
                  ),
                  Color.Transparent,
                ),
              startY = pillTop + 1.5.dp.toPx(),
              endY = pillTop + 1.5.dp.toPx() + sheenH,
            ),
          topLeft = Offset(pillLeft + sheenInsetX, pillTop + 1.5.dp.toPx()),
          size = Size(sheenW, sheenH),
          cornerRadius = CornerRadius(sheenH * 0.5f, sheenH * 0.5f),
        )
      }

      // 5. Crisp 1dp specular bezel stroke around the floating glass lens
      val strokePx = (1.dp.toPx() + lift * 0.35.dp.toPx())
      val halfStroke = strokePx * 0.5f
      val borderBrush =
        if (isDark) {
          Brush.verticalGradient(
            colorStops =
              arrayOf(
                0.0f to Color.White.copy(alpha = (0.64f + lift * 0.18f).coerceAtMost(0.88f)),
                0.40f to Color.White.copy(alpha = 0.22f),
                0.85f to accentColor.copy(alpha = 0.26f),
                1.0f to Color.White.copy(alpha = 0.30f),
              ),
            startY = pillTop,
            endY = pillTop + pillH,
          )
        } else {
          Brush.verticalGradient(
            colorStops =
              arrayOf(
                0.0f to Color.White.copy(alpha = 0.98f),
                0.45f to Color.White.copy(alpha = 0.55f),
                1.0f to accentColor.copy(alpha = 0.20f + lift * 0.14f),
              ),
            startY = pillTop,
            endY = pillTop + pillH,
          )
        }
      drawRoundRect(
        brush = borderBrush,
        topLeft = Offset(pillLeft + halfStroke, pillTop + halfStroke),
        size = Size((pillW - strokePx).coerceAtLeast(1f), (pillH - strokePx).coerceAtLeast(1f)),
        cornerRadius =
          CornerRadius((cornerR - halfStroke).coerceAtLeast(0f), (cornerR - halfStroke).coerceAtLeast(0f)),
        style = Stroke(width = strokePx),
      )
    }
    .drawWithContent {
      drawContent()
      // 6. Reactive top-edge specular light bloom tracking the active pill across the outer glass rim
      val insetPx = inset.toPx()
      if (size.width <= 2f * insetPx + 4f || size.height < 4f) return@drawWithContent
      val innerW = (size.width - 2f * insetPx).coerceAtLeast(1f)
      val segW = innerW / count
      val pos = positionState.value.coerceIn(-0.12f, (count - 1).toFloat() + 0.12f)
      val lift = dragLiftState.value.coerceIn(0f, 1.25f)
      val centerX = insetPx + (pos + 0.5f) * segW
      val bloomRadius = (segW * 0.72f).coerceAtLeast(1f)
      val peakAlpha =
        if (isDark) (0.34f + lift * 0.20f).coerceAtMost(0.62f)
        else (0.55f + lift * 0.22f).coerceAtMost(0.85f)
      drawRect(
        brush =
          Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = peakAlpha), Color.Transparent),
            center = Offset(centerX, 0f),
            radius = bloomRadius,
          ),
        topLeft = Offset(0f, 0f),
        size = Size(size.width, 3.dp.toPx()),
      )
    }
}

/**
 * Draw/layer-phase AGSL optical magnifying lens for the floating tab bar (API 33+).
 *
 * Magnifies and chromatically refracts the tab icons underneath the sliding selection pill as it
 * glides with a spring or is finger-scrubbed, without causing any recompositions. Automatically
 * renders static magnification when Reduce Motion is enabled, and no-ops cleanly on API 26–32.
 */
internal fun Modifier.liquidGlassTabMagnifyingLens(
  positionState: State<Float>,
  lagPositionState: State<Float>,
  dragLiftState: State<Float>,
  count: Int,
  inset: Dp = 5.dp,
): Modifier = composed {
  if (count <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this
  val debugConfig = LocalGlassDebugConfig.current
  if (!debugConfig.enableEdgeRefraction) return@composed this
  val motion = LocalGlassMotion.current
  val lensEffect = remember { CachedLensMagnifyEffect() }

  this.graphicsLayer {
    val insetPx = inset.toPx()
    if (size.width <= 2f * insetPx + 4f || size.height <= 2f * insetPx + 4f) {
      renderEffect = null
      return@graphicsLayer
    }
    val innerW = (size.width - 2f * insetPx).coerceAtLeast(1f)
    val segW = innerW / count
    val baseH = (size.height - 2f * insetPx).coerceAtLeast(1f)
    if (segW <= 0f || baseH <= 0f) {
      renderEffect = null
      return@graphicsLayer
    }

    val pos = positionState.value.coerceIn(-0.16f, (count - 1).toFloat() + 0.16f)
    val lag = lagPositionState.value.coerceIn(-0.16f, (count - 1).toFloat() + 0.16f)
    val lift = if (motion) dragLiftState.value.coerceIn(0f, 1.25f) else 0f
    val stretch = if (motion) abs(pos - lag).coerceIn(0f, 1.35f) else 0f

    val pillW = (segW + stretch * segW * 0.24f + lift * 5.dp.toPx()).coerceAtMost(innerW).coerceAtLeast(1f)
    val minPillH = (baseH * 0.78f).coerceAtLeast(1f)
    val maxPillH = size.height.coerceAtLeast(minPillH)
    val pillH = (baseH + lift * 3.dp.toPx() - stretch * 2.5.dp.toPx()).coerceIn(minPillH, maxPillH)
    val minCenterX = insetPx + pillW * 0.5f
    val maxCenterX = (size.width - insetPx - pillW * 0.5f).coerceAtLeast(minCenterX)
    val centerX = (insetPx + (pos + 0.5f) * segW).coerceIn(minCenterX, maxCenterX)
    val centerY = size.height * 0.5f

    val magStrength = 0.55f + lift * 0.45f + stretch * 0.35f
    val dispersionPx = (0.75.dp.toPx() + (lift + stretch) * 1.4.dp.toPx())
    renderEffect =
      lensEffect.obtain(
        widthPx = size.width,
        heightPx = size.height,
        centerX = centerX,
        centerY = centerY,
        halfW = pillW * 0.5f,
        halfH = pillH * 0.5f,
        radius = min(pillW, pillH) * 0.5f,
        magnifyStrength = magStrength,
        dispersionPx = dispersionPx,
      )
  }
}

/**
 * Vertical Liquid Glass sliding lens pill for [GlassNavigationRail] on tablets and foldables.
 */
internal fun Modifier.liquidGlassRailPill(
  positionState: State<Float>,
  lagPositionState: State<Float>,
  dragLiftState: State<Float>,
  count: Int,
  isDark: Boolean,
  accentColor: Color,
  verticalInset: Dp = 10.dp,
  horizontalInset: Dp = 8.dp,
  itemSpacing: Dp = 6.dp,
): Modifier {
  if (count <= 0) return this
  return this.drawBehind {
    val vInset = verticalInset.toPx()
    val hInset = horizontalInset.toPx()
    val gap = itemSpacing.toPx()
    if (size.width <= 2f * hInset + 4f || size.height <= 2f * vInset + 4f) return@drawBehind
    val totalH = (size.height - 2f * vInset - gap * (count - 1)).coerceAtLeast(1f)
    val segH = totalH / count
    val pillW = (size.width - 2f * hInset).coerceAtLeast(1f)
    if (segH <= 0f || pillW <= 0f) return@drawBehind

    val pos = positionState.value.coerceIn(-0.14f, (count - 1).toFloat() + 0.14f)
    val lag = lagPositionState.value.coerceIn(-0.14f, (count - 1).toFloat() + 0.14f)
    val lift = dragLiftState.value.coerceIn(0f, 1.2f)
    val stretch = abs(pos - lag).coerceIn(0f, 1.2f)

    val maxTrackH = (size.height - 2f * vInset + 4.dp.toPx()).coerceAtLeast(1f)
    val pillH = (segH + stretch * segH * 0.24f + lift * 3.dp.toPx()).coerceAtMost(maxTrackH).coerceAtLeast(1f)
    val centerY = vInset + pos * (segH + gap) + segH * 0.5f
    val minTop = vInset - 2.dp.toPx()
    val maxTop = (size.height - vInset + 2.dp.toPx() - pillH).coerceAtLeast(minTop)
    val pillTop = (centerY - pillH * 0.5f).coerceIn(minTop, maxTop)
    val cornerR = min(20.dp.toPx(), min(pillW, pillH) * 0.5f)

    val fillBrush =
      if (isDark) {
        Brush.verticalGradient(
          colors =
            listOf(
              accentColor.copy(alpha = (0.30f + lift * 0.10f).coerceAtMost(0.45f)),
              Color.White.copy(alpha = (0.14f + lift * 0.06f).coerceAtMost(0.24f)),
            ),
          startY = pillTop,
          endY = pillTop + pillH,
        )
      } else {
        Brush.verticalGradient(
          colors =
            listOf(
              Color.White.copy(alpha = 0.94f),
              accentColor.copy(alpha = (0.14f + lift * 0.08f).coerceAtMost(0.26f)),
            ),
          startY = pillTop,
          endY = pillTop + pillH,
        )
      }

    drawRoundRect(
      brush = fillBrush,
      topLeft = Offset(hInset, pillTop),
      size = Size(pillW, pillH),
      cornerRadius = CornerRadius(cornerR, cornerR),
    )

    val strokePx = 1.dp.toPx()
    drawRoundRect(
      brush =
        Brush.verticalGradient(
          colors =
            listOf(
              Color.White.copy(alpha = if (isDark) 0.55f else 0.95f),
              accentColor.copy(alpha = if (isDark) 0.28f else 0.20f),
            ),
          startY = pillTop,
          endY = pillTop + pillH,
        ),
      topLeft = Offset(hInset + strokePx * 0.5f, pillTop + strokePx * 0.5f),
      size = Size((pillW - strokePx).coerceAtLeast(1f), (pillH - strokePx).coerceAtLeast(1f)),
      cornerRadius = CornerRadius(cornerR, cornerR),
      style = Stroke(width = strokePx),
    )
  }
}

/**
 * Smooth hyperbolic rubber-band curve so dragging past limits feels soft and elastic rather than
 * hitting a hard wall.
 */
internal fun liquidRubberBand(offset: Float, maxTravel: Float): Float {
  if (maxTravel <= 0f || offset == 0f) return 0f
  val absVal = abs(offset)
  val damped = (absVal * maxTravel) / (absVal + maxTravel * 1.15f)
  return sign(offset) * damped
}
