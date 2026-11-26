package com.prism.docs.core.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.prism.docs.core.ui.R
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
import com.prism.docs.core.ui.theme.PrismMotion
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Slim Liquid Glass segmented control with a clear raised sliding pill thumb, 120fps horizontal
 * finger-scrubbing across segments with live haptic ticks, and spring slot settlement.
 */
@Composable
fun GlassSegmentedControl(
  options: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  animateFromIndex: Int? = null,
) {
  if (options.isEmpty()) return
  val env = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val motion = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()
  val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
  val initialAnimatedIndex = (animateFromIndex ?: clampedIndex).coerceIn(0, options.lastIndex)

  val latestSelectedIndex by rememberUpdatedState(clampedIndex)
  val latestOnSelect by rememberUpdatedState(onSelect)

  val pillAnim = remember(options.size) { Animatable(initialAnimatedIndex.toFloat()) }
  var isDragging by remember(options.size) { mutableStateOf(false) }
  var liveDragSlot by remember(options.size) { mutableFloatStateOf(initialAnimatedIndex.toFloat()) }
  var hoveredSlot by remember(options.size) { mutableStateOf<Int?>(null) }

  LaunchedEffect(clampedIndex) {
    if (!isDragging) {
      pillAnim.animateTo(
        targetValue = clampedIndex.toFloat(),
        animationSpec = if (motion) spring(dampingRatio = 0.78f, stiffness = 420f) else snap(),
      )
    }
  }

  val pillPositionState =
    remember(options.size) {
      derivedStateOf { if (isDragging) liveDragSlot else pillAnim.value }
    }

  val trackRecessColor = tokens.segmentRecess
  val thumbBgColor = tokens.segmentThumb
  val thumbBorderColor = if (env.isDark) tokens.segmentThumbBorder else Color.Transparent
  val thumbBrush = remember(thumbBgColor) { SolidColor(thumbBgColor) }
  val activeTextColor = tokens.segmentActiveText
  val inactiveTextColor =
    if (env.isDark) PrismColors.DarkTabInactive else PrismColors.LightSegmentInactive
  val selectedStateLabel = stringResource(R.string.glass_state_selected)
  val notSelectedStateLabel = stringResource(R.string.glass_state_not_selected)

  val activeVisualIndex = hoveredSlot ?: clampedIndex

  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    enableRefraction = false,
    enableTopSheen = false,
    modifier =
      modifier
        .fillMaxWidth()
        .height(PrismDimens.TouchTargetMin)
        .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
        .testTag("glass_segmented_control"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxSize()
          .background(trackRecessColor)
          .pointerInput(options.size, motion) {
            val insetPx = 4.dp.toPx()
            val maxIdx = options.lastIndex.toFloat()
            fun xToSlot(x: Float): Float {
              val segW = ((size.width - 2f * insetPx) / options.size).coerceAtLeast(1f)
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
                var currentSnapped = startRaw.roundToInt().coerceIn(0, options.lastIndex)
                liveDragSlot = startRaw
                hoveredSlot = currentSnapped
                isDragging = true
                var settledNormally = false
                try {
                  horizontalDrag(slop.id) { change ->
                    change.consume()
                    val raw = xToSlot(change.position.x)
                    liveDragSlot = raw
                    val snapped = raw.roundToInt().coerceIn(0, options.lastIndex)
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
                    latestOnSelect(finalSlot)
                  }
                  settledNormally = true
                  scope.launch {
                    pillAnim.snapTo(releasePos)
                    isDragging = false
                    pillAnim.animateTo(
                      targetValue = finalSlot.toFloat(),
                      animationSpec =
                        if (motion) spring(dampingRatio = 0.74f, stiffness = 460f) else snap(),
                    )
                  }
                } finally {
                  if (!settledNormally) {
                    hoveredSlot = null
                    isDragging = false
                    val resetTarget = latestSelectedIndex.toFloat()
                    scope.launch { pillAnim.snapTo(resetTarget) }
                  }
                }
              }
            }
          }
          .glassSlidingPill(
            selectedIndex = clampedIndex,
            count = options.size,
            inset = 4.dp,
            cornerRadius = 999.dp,
            brush = thumbBrush,
            shadowColor = tokens.pillShadow,
            borderColor = thumbBorderColor,
            positionOverride = pillPositionState,
          )
          .padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      options.forEachIndexed { index, label ->
        val isSelected = index == clampedIndex
        val isHighlighted = index == activeVisualIndex
        val textColor by
          animateColorAsState(
            targetValue = if (isHighlighted) activeTextColor else inactiveTextColor,
            label = "segment_text_$index",
          )
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(20.dp))
              .glassClickable(role = Role.Tab, hapticOnClick = false) {
                if (!isSelected) {
                  haptics.segmentTick()
                  onSelect(index)
                }
              }
              .semantics {
                selected = isSelected
                stateDescription = if (isSelected) selectedStateLabel else notSelectedStateLabel
                contentDescription = label
              }
              .padding(horizontal = 6.dp)
              .testTag("glass_segment_$index"),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = label,
            style =
              MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
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
 * Liquid Glass capsule toggle switch with tap + horizontal drag support, elastic thumb stretch,
 * API-aware TOGGLE_ON / TOGGLE_OFF haptics, and 48dp accessible touch area.
 */
@Composable
fun GlassToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  contentDescriptionText: String,
  modifier: Modifier = Modifier,
) {
  val env = LocalGlassEnvironment.current
  val motion = LocalGlassMotion.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()

  val latestChecked by rememberUpdatedState(checked)
  val latestOnCheckedChange by rememberUpdatedState(onCheckedChange)

  val thumbAnim = remember { Animatable(if (checked) 1f else 0f) }
  var isDragging by remember { mutableStateOf(false) }
  var liveProgress by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
  var previewChecked by remember { mutableStateOf<Boolean?>(null) }

  LaunchedEffect(checked) {
    if (!isDragging) {
      thumbAnim.animateTo(
        targetValue = if (checked) 1f else 0f,
        animationSpec = PrismMotion.tactileSpring(env.reduceMotion),
      )
    }
  }

  val visualChecked = previewChecked ?: checked
  val trackColor by
    animateColorAsState(
      targetValue =
        if (visualChecked) {
          colorScheme.primary.copy(alpha = 0.88f)
        } else {
          colorScheme.onSurface.copy(alpha = if (env.isDark) 0.18f else 0.14f)
        },
      label = "glass_toggle_track",
    )
  val dragStretch =
    animateFloatAsState(
      targetValue = if (isDragging) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.65f, stiffness = 500f) else snap(),
      label = "glass_toggle_stretch",
    )

  Box(
    modifier =
      modifier
        .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
        .pointerInput(motion) {
          val travelPx = 20.dp.toPx()
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop =
              awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
              }
            if (slop != null) {
              var current = if (latestChecked) 1f else 0f
              var crossed = latestChecked
              isDragging = true
              liveProgress = current
              previewChecked = crossed
              var settledNormally = false
              try {
                horizontalDrag(slop.id) { change ->
                  change.consume()
                  current = (current + change.positionChange().x / travelPx).coerceIn(0f, 1f)
                  liveProgress = current
                  val nowChecked = current >= 0.5f
                  if (nowChecked != crossed) {
                    crossed = nowChecked
                    previewChecked = crossed
                    haptics.toggle(crossed)
                  }
                }
                val releaseProgress = liveProgress
                val finalChecked = crossed
                previewChecked = null
                if (finalChecked != latestChecked) {
                  latestOnCheckedChange(finalChecked)
                }
                settledNormally = true
                scope.launch {
                  thumbAnim.snapTo(releaseProgress)
                  isDragging = false
                  thumbAnim.animateTo(
                    targetValue = if (finalChecked) 1f else 0f,
                    animationSpec = PrismMotion.tactileSpring(env.reduceMotion),
                  )
                }
              } finally {
                if (!settledNormally) {
                  previewChecked = null
                  isDragging = false
                  val resetTarget = if (latestChecked) 1f else 0f
                  scope.launch { thumbAnim.snapTo(resetTarget) }
                }
              }
            }
          }
        }
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          role = Role.Switch,
          onClick = {
            val next = !checked
            haptics.toggle(next)
            onCheckedChange(next)
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
          Modifier.graphicsLayer {
              val p = if (isDragging) liveProgress else thumbAnim.value
              val stretch = dragStretch.value
              translationX = (2.dp.toPx() + 20.dp.toPx() * p)
              scaleX = 1f + 0.12f * stretch
              scaleY = 1f - 0.05f * stretch
            }
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
 * Liquid Glass tactile slider with discrete step haptic ticks.
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
  val haptics = rememberPrismHaptics()
  val tickBuckets = if (steps > 0) steps + 1 else 20
  val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
  var lastBucket by remember {
    mutableIntStateOf((((value - valueRange.start) / span) * tickBuckets).roundToInt())
  }

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
      onValueChange = { next ->
        val bucket = (((next - valueRange.start) / span) * tickBuckets).roundToInt()
        if (bucket != lastBucket) {
          lastBucket = bucket
          haptics.segmentTick()
        }
        onValueChange(next)
      },
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

/**
 * Liquid Glass clarity slider (0% Frosted -> 100% Clear):
 * - Translucent glass track with active Prism optical fill and tick marks at 0 / 25 / 50 / 75 / 100%.
 * - Swelling Liquid Glass capsule thumb (~1.20x while dragged) that captures and refracts/magnifies
 *   the track underneath it in the draw phase (AGSL lens shader on API 33+, optical highlight fallback
 *   on API 26–32).
 * - Haptic detent tick at 0%, 25%, 50%, 75%, and 100%.
 */
@Composable
fun LiquidGlassClaritySlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onValueChangeFinished: () -> Unit = {},
) {
  val env = LocalGlassEnvironment.current
  val motion = LocalGlassMotion.current
  val debugConfig = LocalGlassDebugConfig.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val clamped = value.coerceIn(0f, 1f)

  val latestValue by rememberUpdatedState(clamped)
  val latestOnChange by rememberUpdatedState(onValueChange)
  val latestOnFinished by rememberUpdatedState(onValueChangeFinished)

  var isDragging by remember { mutableStateOf(false) }
  var lastNotch by remember { mutableIntStateOf((clamped * 4f).roundToInt().coerceIn(0, 4)) }

  val swellState =
    animateFloatAsState(
      targetValue = if (isDragging) 1f else 0f,
      animationSpec = if (motion) spring(dampingRatio = 0.54f, stiffness = 460f) else snap(),
      label = "clarity_thumb_swell",
    )

  val trackLayer = rememberGraphicsLayer()
  val thumbPath = remember { Path() }
  val lensEffect =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      remember { CachedLensMagnifyEffect() }
    } else {
      null
    }

  val percent = (clamped * 100f).roundToInt()

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(52.dp)
        .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
        .graphicsLayer { alpha = if (enabled) 1f else 0.48f }
        .pointerInput(motion, enabled) {
          if (!enabled) return@pointerInput
          val thumbHalfPx = 22.dp.toPx()

          fun xToFraction(x: Float): Float {
            val usable = (size.width - thumbHalfPx * 2f).coerceAtLeast(1f)
            return ((x - thumbHalfPx) / usable).coerceIn(0f, 1f)
          }

          fun emitValue(next: Float) {
            val notches = floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f)
            val nearestIdx =
              notches.indices.minByOrNull { kotlin.math.abs(next - notches[it]) } ?: 0
            val nearNotch = kotlin.math.abs(next - notches[nearestIdx]) <= 0.032f
            if (nearNotch && nearestIdx != lastNotch) {
              lastNotch = nearestIdx
              haptics.segmentTick()
            } else if (!nearNotch) {
              val crossedNotch =
                when {
                  next > latestValue -> notches.indexOfFirst { it > latestValue && it <= next }
                  next < latestValue -> notches.indexOfLast { it < latestValue && it >= next }
                  else -> -1
                }
              if (crossedNotch != -1 && crossedNotch != lastNotch) {
                lastNotch = crossedNotch
                haptics.segmentTick()
              }
            }
            latestOnChange(next)
          }

          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            isDragging = true
            var finishedNormally = false
            try {
              haptics.dragStart()
              emitValue(xToFraction(down.position.x))
              val pointerId = down.id
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                  isDragging = false
                  finishedNormally = true
                  latestOnFinished()
                  break
                }
                if (change.positionChange().x != 0f) {
                  change.consume()
                  emitValue(xToFraction(change.position.x))
                }
              }
            } finally {
              if (!finishedNormally) {
                isDragging = false
                latestOnFinished()
              }
            }
          }
        }
        .semantics {
          contentDescription = "Glass clarity slider"
          stateDescription = "$percent%"
        }
        .testTag("liquid_glass_clarity_slider"),
    contentAlignment = Alignment.Center,
  ) {
    // 1. Track layer recorded into GraphicsLayer so the glass thumb can genuinely refract it
    Box(
      modifier =
        Modifier.fillMaxSize()
          .drawWithContent {
            if (
              size.width < 2f ||
                size.height < 2f ||
                GlassBackdropRecordingGuard.isRecording() ||
                trackLayer.isReleased
            ) {
              drawContent()
              return@drawWithContent
            }
            val trackH = 12.dp.toPx()
            val trackTop = (size.height - trackH) * 0.5f
            val padX = 22.dp.toPx()
            val trackW = (size.width - padX * 2f).coerceAtLeast(1f)
            val corner = CornerRadius(trackH * 0.5f, trackH * 0.5f)
            val fillW = (trackW * clamped).coerceAtLeast(trackH * 0.5f)

            trackLayer.record {
              // Frosted recess track
              drawRoundRect(
                color =
                  if (env.isDark) Color.White.copy(alpha = 0.14f)
                  else Color(0xFF1A2C56).copy(alpha = 0.12f),
                topLeft = Offset(padX, trackTop),
                size = Size(trackW, trackH),
                cornerRadius = corner,
              )
              // Prism optical active fill
              drawRoundRect(
                brush =
                  Brush.horizontalGradient(
                    colors = listOf(PrismColors.IndigoBlue, colorScheme.primary, PrismColors.IceCyan),
                    startX = padX,
                    endX = padX + trackW,
                  ),
                topLeft = Offset(padX, trackTop),
                size = Size(fillW.coerceAtMost(trackW), trackH),
                cornerRadius = corner,
              )
              // 0 / 25 / 50 / 75 / 100% tick notches along track
              for (i in 0..4) {
                val frac = i / 4f
                val tx = padX + trackW * frac
                val isFilled = clamped >= frac - 0.01f
                drawCircle(
                  color =
                    if (isFilled) Color.White.copy(alpha = 0.85f)
                    else colorScheme.onSurface.copy(alpha = if (env.isDark) 0.34f else 0.28f),
                  radius = 2.dp.toPx(),
                  center = Offset(tx, size.height * 0.5f),
                )
              }
              // Hairline glass border around track
              drawRoundRect(
                color = Color.White.copy(alpha = if (env.isDark) 0.24f else 0.72f),
                topLeft = Offset(padX, trackTop),
                size = Size(trackW, trackH),
                cornerRadius = corner,
                style = Stroke(width = 0.85.dp.toPx()),
              )
            }
            drawLayer(trackLayer)
            drawContent()
          }
    )

    // 2. Swelling, track-refracting Liquid Glass capsule thumb
    Box(
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer {
            val swell = swellState.value
            val padX = 22.dp.toPx()
            val trackW = (size.width - padX * 2f).coerceAtLeast(1f)
            val thumbW = (38.dp.toPx() * (1f + 0.22f * swell))
            val thumbH = (26.dp.toPx() * (1f + 0.20f * swell))
            val cx = padX + trackW * clamped
            val cy = size.height * 0.5f

            if (
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                lensEffect != null &&
                debugConfig.enableEdgeRefraction &&
                !env.reduceTransparency
            ) {
              renderEffect =
                lensEffect.obtain(
                  widthPx = size.width,
                  heightPx = size.height,
                  centerX = cx,
                  centerY = cy,
                  halfW = thumbW * 0.5f,
                  halfH = thumbH * 0.5f,
                  radius = thumbH * 0.5f,
                  magnifyStrength = 0.75f + swell * 0.65f,
                  dispersionPx = 1.0.dp.toPx() + swell * 1.8.dp.toPx(),
                )
            } else {
              renderEffect = null
            }
          }
          .drawBehind {
            if (size.width < 2f || size.height < 2f) return@drawBehind
            val swell = swellState.value
            val padX = 22.dp.toPx()
            val trackW = (size.width - padX * 2f).coerceAtLeast(1f)
            val thumbW = 38.dp.toPx() * (1f + 0.22f * swell)
            val thumbH = 26.dp.toPx() * (1f + 0.20f * swell)
            val cx = padX + trackW * clamped
            val cy = size.height * 0.5f
            val left = cx - thumbW * 0.5f
            val top = cy - thumbH * 0.5f
            val corner = CornerRadius(thumbH * 0.5f, thumbH * 0.5f)

            // Draw magnified underlying track through the lens capsule
            if (!env.reduceTransparency) {
              thumbPath.reset()
              thumbPath.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                  left = left,
                  top = top,
                  right = left + thumbW,
                  bottom = top + thumbH,
                  cornerRadius = corner,
                )
              )
              clipPath(thumbPath) {
                if (!GlassBackdropRecordingGuard.isRecording() && !trackLayer.isReleased) {
                  drawLayer(trackLayer)
                }
                // Translucent crystalline body over the refracted track
                drawRoundRect(
                  brush =
                    Brush.verticalGradient(
                      colors =
                        if (env.isDark) {
                          listOf(
                            Color.White.copy(alpha = 0.24f + swell * 0.08f),
                            Color.White.copy(alpha = 0.06f),
                            colorScheme.primary.copy(alpha = 0.14f + swell * 0.08f),
                          )
                        } else {
                          listOf(
                            Color.White.copy(alpha = 0.64f - swell * 0.22f),
                            Color.White.copy(alpha = 0.22f - swell * 0.08f),
                            Color.White.copy(alpha = 0.42f - swell * 0.12f),
                          )
                        },
                      startY = top,
                      endY = top + thumbH,
                    ),
                  topLeft = Offset(left, top),
                  size = Size(thumbW, thumbH),
                  cornerRadius = corner,
                )
              }
            } else {
              drawRoundRect(
                color = colorScheme.surface,
                topLeft = Offset(left, top),
                size = Size(thumbW, thumbH),
                cornerRadius = corner,
              )
            }

            // Upper curved specular lens highlight inside thumb
            val sheenInset = 5.dp.toPx()
            val sheenW = (thumbW - sheenInset * 2f).coerceAtLeast(2f)
            val sheenH = thumbH * 0.34f
            drawRoundRect(
              brush =
                Brush.verticalGradient(
                  colors = listOf(Color.White.copy(alpha = 0.78f), Color.Transparent),
                  startY = top + 2.dp.toPx(),
                  endY = top + 2.dp.toPx() + sheenH,
                ),
              topLeft = Offset(left + sheenInset, top + 2.dp.toPx()),
              size = Size(sheenW, sheenH),
              cornerRadius = CornerRadius(sheenH * 0.5f, sheenH * 0.5f),
            )

            // Specular bezel stroke around thumb
            val strokePx = (1.25.dp.toPx() + swell * 0.4.dp.toPx())
            drawRoundRect(
              brush =
                Brush.verticalGradient(
                  colors =
                    listOf(
                      Color.White.copy(alpha = 0.96f),
                      colorScheme.primary.copy(alpha = if (env.isDark) 0.48f else 0.34f),
                    ),
                  startY = top,
                  endY = top + thumbH,
                ),
              topLeft = Offset(left + strokePx * 0.5f, top + strokePx * 0.5f),
              size = Size(thumbW - strokePx, thumbH - strokePx),
              cornerRadius = corner,
              style = Stroke(width = strokePx),
            )
          }
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
 * Floating Liquid Glass context popover menu with GPU spring scale/alpha reveal and semantic haptics.
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
  val motion = LocalGlassMotion.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val revealProgress = remember { Animatable(if (motion) 0f else 1f) }

  LaunchedEffect(Unit) {
    if (motion) {
      revealProgress.animateTo(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
      )
    } else {
      revealProgress.snapTo(1f)
    }
  }

  Popup(
    alignment = Alignment.TopEnd,
    offset = offset,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = true),
  ) {
    GlassSurface(
      cornerRadius = 22.dp,
      elevation = GlassElevation.OVERLAY,
      useBackdropBlur = false,
      modifier =
        modifier
          .graphicsLayer {
            val p = revealProgress.value
            val s = 0.86f + 0.14f * p
            scaleX = s
            scaleY = s
            alpha = p.coerceIn(0f, 1f)
            transformOrigin = TransformOrigin(0.92f, 0.05f)
          }
          .widthIn(min = 200.dp, max = 264.dp)
          .testTag("glass_context_menu"),
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
                  indication = ripple(color = tint),
                  role = Role.Button,
                  onClick = {
                    if (item.isDestructive) haptics.reject() else haptics.tap()
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
  PEEK(0.42f),
  HALF(0.64f),
  FULL(0.90f),
}

/**
 * Liquid Glass bottom sheet overlay supporting PEEK / HALF / FULL detents with non-translated
 * gesture tracking (zero coordinate-feedback jitter), nested-scroll handoff, velocity-aware fling
 * snapping, rubber-band overdrag, and live threshold haptics.
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
  val motion = LocalGlassMotion.current
  val dynamics = LocalGlassDynamics.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current

  val latestDetent by rememberUpdatedState(detent)
  val latestOnDetentChange by rememberUpdatedState(onDetentChange)
  val latestOnDismiss by rememberUpdatedState(onDismiss)

  AnimatedVisibility(
    visible = visible,
    enter = EnterTransition.None,
    exit = ExitTransition.None,
    modifier = modifier.fillMaxSize(),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val containerHeight = maxHeight
      val snapThresholdPx = with(density) { 64.dp.toPx() }
      val flingVelocityThresholdPx = with(density) { 420.dp.toPx() }
      val maxUpPullPx = with(density) { 180.dp.toPx() }
      val rubberBandPx = with(density) { 44.dp.toPx() }

      val dragOffsetAnim = remember { Animatable(0f) }
      var isDragging by remember { mutableStateOf(false) }
      var liveDragPx by remember { mutableFloatStateOf(0f) }
      var armedDirection by remember { mutableIntStateOf(0) }

      fun applyResistance(rawPx: Float, currentDetent: GlassSheetDetent): Float {
        return when {
          rawPx < 0f && currentDetent == GlassSheetDetent.FULL ->
            -rubberBandPx * (1f - exp(rawPx / (rubberBandPx * 2.5f)))
          rawPx < -maxUpPullPx ->
            -maxUpPullPx - rubberBandPx * (1f - exp((rawPx + maxUpPullPx) / (rubberBandPx * 2f)))
          else -> rawPx
        }
      }

      fun updateThresholdHaptics(offsetPx: Float, currentDetent: GlassSheetDetent) {
        val nextArmed =
          when {
            offsetPx > snapThresholdPx -> 1
            offsetPx < -snapThresholdPx && currentDetent != GlassSheetDetent.FULL -> -1
            armedDirection == 1 && offsetPx < snapThresholdPx * 0.72f -> 0
            armedDirection == -1 && offsetPx > -snapThresholdPx * 0.72f -> 0
            else -> armedDirection
          }
        if (nextArmed != armedDirection) {
          if (nextArmed != 0) haptics.gestureThresholdActivate()
          else haptics.gestureThresholdDeactivate()
          armedDirection = nextArmed
        }
      }

      fun settleSheet(offsetPx: Float, velocityY: Float) {
        val currentDetent = latestDetent
        val fastDismiss =
          velocityY > flingVelocityThresholdPx * 2.2f && offsetPx > snapThresholdPx * 0.5f
        val stepDown =
          offsetPx > snapThresholdPx ||
            (velocityY > flingVelocityThresholdPx && offsetPx > 12f)
        val stepUp =
          (offsetPx < -snapThresholdPx ||
            (velocityY < -flingVelocityThresholdPx && offsetPx < -12f)) &&
            currentDetent != GlassSheetDetent.FULL

        armedDirection = 0
        var dismissed = false
        when {
          fastDismiss -> {
            haptics.segmentTick()
            dismissed = true
            latestOnDismiss()
          }
          stepDown -> {
            haptics.segmentTick()
            when (currentDetent) {
              GlassSheetDetent.FULL -> latestOnDetentChange(GlassSheetDetent.HALF)
              GlassSheetDetent.HALF -> latestOnDetentChange(GlassSheetDetent.PEEK)
              GlassSheetDetent.PEEK -> {
                dismissed = true
                latestOnDismiss()
              }
            }
          }
          stepUp -> {
            haptics.segmentTick()
            when (currentDetent) {
              GlassSheetDetent.PEEK -> latestOnDetentChange(GlassSheetDetent.HALF)
              GlassSheetDetent.HALF -> latestOnDetentChange(GlassSheetDetent.FULL)
              GlassSheetDetent.FULL -> Unit
            }
          }
        }
        scope.launch {
          dragOffsetAnim.snapTo(offsetPx)
          isDragging = false
          dynamics.sheetDragEnergy = 0f
          if (!dismissed) {
            dragOffsetAnim.animateTo(
              targetValue = 0f,
              initialVelocity = velocityY * 0.25f,
              animationSpec =
                if (motion) spring(dampingRatio = 0.80f, stiffness = 420f) else snap(),
            )
          }
        }
      }

      LaunchedEffect(detent) {
        if (!isDragging && dragOffsetAnim.value != 0f) {
          dragOffsetAnim.animateTo(
            targetValue = 0f,
            animationSpec =
              if (motion) spring(dampingRatio = 0.82f, stiffness = 420f) else snap(),
          )
        }
      }

      val targetFraction by
        animateFloatAsState(
          targetValue = detent.heightFraction,
          animationSpec = PrismMotion.fluidSpring(env.reduceMotion),
          label = "sheet_detent_fraction",
        )

      val sheetNestedScroll =
        remember(snapThresholdPx, flingVelocityThresholdPx, motion) {
          object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
              if (source != NestedScrollSource.UserInput) return Offset.Zero
              val currentOffset = if (isDragging) liveDragPx else dragOffsetAnim.value
              if (currentOffset > 0f && available.y < 0f) {
                val next = (currentOffset + available.y).coerceAtLeast(0f)
                val consumedY = next - currentOffset
                isDragging = true
                liveDragPx = next
                updateThresholdHaptics(next, latestDetent)
                return Offset(0f, consumedY)
              }
              return Offset.Zero
            }

            override fun onPostScroll(
              consumed: Offset,
              available: Offset,
              source: NestedScrollSource,
            ): Offset {
              if (source != NestedScrollSource.UserInput) return Offset.Zero
              if (available.y > 0f) {
                val currentOffset = if (isDragging) liveDragPx else dragOffsetAnim.value
                val next = currentOffset + available.y
                isDragging = true
                liveDragPx = next
                updateThresholdHaptics(next, latestDetent)
                return Offset(0f, available.y)
              }
              return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
              if (isDragging && liveDragPx > 0f) {
                settleSheet(liveDragPx, available.y)
                return available
              }
              return Velocity.Zero
            }
          }
        }

      // Scrim fades smoothly in lockstep with sheet slide
      Box(
        modifier =
          Modifier.fillMaxSize()
            .animateEnterExit(
              enter = fadeIn(animationSpec = tween(durationMillis = if (motion) 200 else 0)),
              exit = fadeOut(animationSpec = tween(durationMillis = if (motion) 180 else 0)),
            )
            .background(Color.Black.copy(alpha = if (env.isDark) 0.56f else 0.34f))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = {
                haptics.tap()
                onDismiss()
              },
            )
      )

      // Outer NON-TRANSLATED gesture host guarantees pointer coordinates never shift mid-drag
      Box(
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .animateEnterExit(
              enter =
                slideInVertically(
                  animationSpec =
                    if (motion) spring(dampingRatio = 0.84f, stiffness = 380f) else snap(),
                  initialOffsetY = { it },
                ),
              exit =
                slideOutVertically(
                  animationSpec = tween(durationMillis = if (motion) 200 else 0),
                  targetOffsetY = { it },
                ),
            )
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .height(containerHeight * targetFraction)
            .nestedScroll(sheetNestedScroll)
            .pointerInput(snapThresholdPx, flingVelocityThresholdPx, motion) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop =
                  awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                  }
                if (slop != null) {
                  val velocityTracker = VelocityTracker()
                  velocityTracker.addPosition(down.uptimeMillis, down.position)
                  velocityTracker.addPosition(slop.uptimeMillis, slop.position)
                  var rawAccumulated =
                    (if (isDragging) liveDragPx else dragOffsetAnim.value) +
                      (slop.position.y - down.position.y)
                  isDragging = true
                  val initialVisual = applyResistance(rawAccumulated, latestDetent)
                  liveDragPx = initialVisual
                  updateThresholdHaptics(initialVisual, latestDetent)
                  var settledNormally = false
                  try {
                    val completed =
                      verticalDrag(slop.id) { change ->
                        val deltaY = change.positionChange().y
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        rawAccumulated += deltaY
                        val visual = applyResistance(rawAccumulated, latestDetent)
                        liveDragPx = visual
                        if (motion) {
                          dynamics.sheetDragEnergy =
                            (kotlin.math.abs(visual) / (snapThresholdPx * 2f)).coerceIn(0f, 1f)
                        }
                        updateThresholdHaptics(visual, latestDetent)
                      }

                    val velocityY = if (completed) velocityTracker.calculateVelocity().y else 0f
                    settledNormally = true
                    settleSheet(liveDragPx, velocityY)
                  } finally {
                    if (!settledNormally) {
                      armedDirection = 0
                      isDragging = false
                      liveDragPx = 0f
                      dynamics.sheetDragEnergy = 0f
                      scope.launch { dragOffsetAnim.snapTo(0f) }
                    }
                  }
                }
              }
            }
      ) {
        GlassSurface(
          cornerRadius = 30.dp,
          shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
          elevation = GlassElevation.OVERLAY,
          useBackdropBlur = true,
          enableRefraction = true,
          modifier =
            Modifier.fillMaxSize()
              .graphicsLayer {
                translationY = if (isDragging) liveDragPx else dragOffsetAnim.value
              }
              .testTag("glass_bottom_sheet"),
        ) {
          Column(
            modifier =
              Modifier.fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
          ) {
            // Generous touch-friendly drag handle zone
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(34.dp)
                  .testTag("glass_bottom_sheet_handle"),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier =
                  Modifier.width(44.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onSurface.copy(alpha = 0.30f))
              )
            }
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
  val motion = LocalGlassMotion.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()
  val pages = remember(pageCount) { (1..pageCount).toList() }
  val selectedStateLabel = stringResource(R.string.glass_state_selected)
  val notSelectedStateLabel = stringResource(R.string.glass_state_not_selected)

  GlassSurface(
    cornerRadius = 24.dp,
    elevation = GlassElevation.FLOATING,
    useBackdropBlur = true,
    modifier = modifier.fillMaxWidth().testTag("glass_page_thumbnail_strip"),
  ) {
    LazyRow(
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      itemsIndexed(pages) { index, pageNumber ->
        val isActive = index == currentPage
        val borderColor by
          animateColorAsState(
            targetValue =
              if (isActive) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.16f),
            label = "thumb_border_$pageNumber",
          )
        val bgColor by
          animateColorAsState(
            targetValue =
              if (isActive) {
                colorScheme.primary.copy(alpha = 0.16f)
              } else {
                colorScheme.surface.copy(alpha = 0.78f)
              },
            label = "thumb_bg_$pageNumber",
          )
        val thumbScale =
          animateFloatAsState(
            targetValue = if (isActive) 1.05f else 0.98f,
            animationSpec =
              if (motion) spring(dampingRatio = 0.68f, stiffness = 460f) else snap(),
            label = "thumb_scale_$pageNumber",
          )
        val pageCd = stringResource(R.string.glass_page_number_cd, pageNumber, pageCount)
        Box(
          modifier =
            Modifier.size(width = thumbnailWidth, height = thumbnailHeight)
              .defaultMinSize(
                minWidth = PrismDimens.TouchTargetMin,
                minHeight = PrismDimens.TouchTargetMin,
              )
              .graphicsLayer {
                val s = thumbScale.value
                scaleX = s
                scaleY = s
              }
              .clip(RoundedCornerShape(10.dp))
              .background(bgColor)
              .border(
                width = if (isActive) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
              )
              .glassClickable(role = Role.Button, hapticOnClick = false) {
                haptics.segmentTick()
                onSelectPage(index)
              }
              .semantics {
                selected = isActive
                stateDescription = if (isActive) selectedStateLabel else notSelectedStateLabel
                contentDescription = pageCd
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
