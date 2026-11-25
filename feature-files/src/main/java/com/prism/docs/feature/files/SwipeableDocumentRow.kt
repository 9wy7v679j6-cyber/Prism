package com.prism.docs.feature.files

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismDimens
import com.prism.docs.core.ui.theme.PrismMotion
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch

/**
 * Controller passed to swipeable document row content so tap/long-press handlers can check
 * whether an action tray is currently revealed and close it smoothly.
 */
@Stable
internal class RowSwipeController(
  private val isOpenProvider: () -> Boolean,
  private val onCloseRequest: () -> Unit,
) {
  val isOpen: Boolean
    get() = isOpenProvider()

  fun close() = onCloseRequest()
}

/**
 * 120fps GPU-translated swipe container for document rows:
 * - Gesture detection lives strictly on the outer NON-TRANSLATED Box (eliminating the Compose
 *   local-coordinate oscillation bug when translating a node before its gesture detector).
 * - Zero coroutine races: tracks live drag offset synchronously in a float state during drag and
 *   launches a single spring settle coroutine with VelocityTracker fling momentum on release.
 * - Right swipe = Favorite toggle (with armed icon scale pop + threshold/confirm haptics).
 * - Left swipe = Share & Delete action tray; deep left swipe = direct Move to Bin (with reject haptic).
 */
@Composable
internal fun SwipeableDocumentRowContainer(
  documentId: Long,
  documentName: String,
  isFavorite: Boolean,
  isSelectionMode: Boolean,
  shape: Shape,
  onToggleFavorite: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable (RowSwipeController) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val latestOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
  val latestOnShare by rememberUpdatedState(onShare)
  val latestOnTrash by rememberUpdatedState(onTrash)

  val rightTriggerPx = with(density) { PrismDimens.SwipeFavoriteTrigger.toPx() }
  val maxRightPx = with(density) { PrismDimens.SwipeMaxRight.toPx() }
  val leftTrayPx = with(density) { PrismDimens.SwipeLeftTrayWidth.toPx() }
  val maxLeftPx = with(density) { PrismDimens.SwipeMaxLeft.toPx() }
  val deepDeletePx = maxLeftPx * 0.85f
  val flingVelocityPx = with(density) { 360.dp.toPx() }
  val springSpec = PrismMotion.tactileSpring<Float>(reduceMotion = !motionEnabled)

  val swipeAnim = remember(documentId) { Animatable(0f) }
  var isDragging by remember(documentId) { mutableStateOf(false) }
  var liveOffsetPx by remember(documentId) { mutableFloatStateOf(0f) }
  // 0 = neutral, 1 = right favorite armed, -1 = left tray armed, -2 = deep left trash armed
  var armedStage by remember(documentId) { mutableIntStateOf(0) }

  fun currentOffset(): Float = if (isDragging) liveOffsetPx else swipeAnim.value

  fun applyRubberBand(raw: Float): Float {
    return when {
      raw > rightTriggerPx -> {
        val extra = maxRightPx - rightTriggerPx
        rightTriggerPx + extra * (1f - exp(-(raw - rightTriggerPx) / (extra * 1.6f).coerceAtLeast(1f)))
      }
      raw < -maxLeftPx -> {
        val extra = 28f
        -maxLeftPx - extra * (1f - exp((raw + maxLeftPx) / 64f))
      }
      else -> raw
    }
  }

  fun updateArmedStage(offset: Float) {
    val next =
      when {
        offset >= rightTriggerPx -> 1
        offset <= -deepDeletePx -> -2
        offset <= -leftTrayPx * 0.45f -> -1
        armedStage == 1 && offset < rightTriggerPx * 0.78f -> 0
        armedStage == -2 && offset > -deepDeletePx * 0.86f -> -1
        armedStage == -1 && offset > -leftTrayPx * 0.30f -> 0
        else -> armedStage
      }
    if (next != armedStage) {
      when {
        next == 1 -> haptics.gestureThresholdActivate()
        next == -2 -> haptics.reject()
        next == -1 && armedStage == 0 -> haptics.segmentTick()
        next == -1 && armedStage == -2 -> haptics.gestureThresholdDeactivate()
        next == 0 && armedStage != 0 -> haptics.gestureThresholdDeactivate()
      }
      armedStage = next
    }
  }

  fun settleOffset(target: Float, velocityX: Float = 0f) {
    val start = if (isDragging) liveOffsetPx else swipeAnim.value
    armedStage = 0
    scope.launch {
      swipeAnim.snapTo(start)
      isDragging = false
      swipeAnim.animateTo(
        targetValue = target,
        initialVelocity = velocityX * 0.28f,
        animationSpec = springSpec,
      )
    }
  }

  LaunchedEffect(isSelectionMode) {
    if (isSelectionMode && abs(currentOffset()) > 0.5f) {
      settleOffset(0f)
    }
  }

  val controller =
    remember(documentId) {
      RowSwipeController(
        isOpenProvider = { abs(currentOffset()) > 4f },
        onCloseRequest = { settleOffset(0f) },
      )
    }

  val showSwipeReveal by remember(documentId) { derivedStateOf { abs(currentOffset()) > 1.5f } }
  val isRightSwipe by remember(documentId) { derivedStateOf { currentOffset() > 0f } }
  val favoriteArmed by remember(documentId) { derivedStateOf { armedStage == 1 } }
  val trashArmed by remember(documentId) { derivedStateOf { armedStage == -2 } }

  val favIconScale =
    animateFloatAsState(
      targetValue = if (favoriteArmed) 1.22f else 0.92f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.52f, stiffness = 500f) else snap(),
      label = "swipe_fav_scale_$documentId",
    )
  val trashIconScale =
    animateFloatAsState(
      targetValue = if (trashArmed) 1.20f else 1f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.54f, stiffness = 500f) else snap(),
      label = "swipe_trash_scale_$documentId",
    )

  // Outer Box is NEVER translated, guaranteeing rock-solid pointer coordinates during horizontal drag
  Box(
    modifier =
      modifier
        .clip(shape)
        .pointerInput(documentId, isSelectionMode, motionEnabled) {
          if (isSelectionMode) return@pointerInput
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop =
              awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
              }
            if (slop != null) {
              val velocityTracker = VelocityTracker()
              velocityTracker.addPosition(down.uptimeMillis, down.position)
              velocityTracker.addPosition(slop.uptimeMillis, slop.position)

              val startOffset = currentOffset()
              var rawAccumulated = startOffset + (slop.position.x - down.position.x)
              isDragging = true
              val firstVisual = applyRubberBand(rawAccumulated)
              liveOffsetPx = firstVisual
              updateArmedStage(firstVisual)

              val completed =
                horizontalDrag(slop.id) { change ->
                  val dx = change.positionChange().x
                  change.consume()
                  velocityTracker.addPosition(change.uptimeMillis, change.position)
                  rawAccumulated += dx
                  val visual = applyRubberBand(rawAccumulated)
                  liveOffsetPx = visual
                  updateArmedStage(visual)
                }

              val finalOffset = liveOffsetPx
              val velocityX = if (completed) velocityTracker.calculateVelocity().x else 0f

              when {
                completed &&
                  (finalOffset >= rightTriggerPx ||
                    (velocityX > flingVelocityPx && finalOffset > rightTriggerPx * 0.45f)) -> {
                  haptics.confirm()
                  latestOnToggleFavorite()
                  settleOffset(0f, velocityX)
                }
                completed &&
                  (finalOffset <= -deepDeletePx ||
                    (velocityX < -flingVelocityPx * 1.9f && finalOffset < -leftTrayPx * 0.8f)) -> {
                  haptics.reject()
                  settleOffset(0f, velocityX)
                  latestOnTrash()
                }
                completed &&
                  (finalOffset <= -leftTrayPx * 0.42f ||
                    (velocityX < -flingVelocityPx && finalOffset < -16f)) -> {
                  if (startOffset > -leftTrayPx * 0.5f) {
                    haptics.segmentTick()
                  }
                  settleOffset(-leftTrayPx, velocityX)
                }
                else -> {
                  settleOffset(0f, velocityX)
                }
              }
            }
          }
        }
  ) {
    if (showSwipeReveal) {
      Box(
        modifier =
          Modifier.matchParentSize()
            .background(
              when {
                isRightSwipe && favoriteArmed -> tokens.favoriteAccent.copy(alpha = 0.30f)
                isRightSwipe -> tokens.favoriteAccent.copy(alpha = 0.18f)
                trashArmed -> tokens.swipeDeleteAccent.copy(alpha = 0.28f)
                else -> colorScheme.surfaceVariant.copy(alpha = 0.82f)
              }
            )
      ) {
        if (isRightSwipe) {
          Row(
            modifier =
              Modifier.align(Alignment.CenterStart)
                .padding(horizontal = PrismDimens.SpaceLarge)
                .graphicsLayer {
                  val p = (currentOffset() / rightTriggerPx).coerceIn(0f, 1f)
                  alpha = (0.25f + 0.75f * p).coerceIn(0f, 1f)
                  translationX = -(1f - p) * 12.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceMedium),
          ) {
            Icon(
              imageVector = if (isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
              contentDescription = null,
              tint = tokens.favoriteAccent,
              modifier =
                Modifier.graphicsLayer {
                  val s = favIconScale.value
                  scaleX = s
                  scaleY = s
                },
            )
            Text(
              text =
                stringResource(
                  if (isFavorite) R.string.action_unfavorite else R.string.action_favorite
                ),
              style = MaterialTheme.typography.labelLarge,
              color = tokens.favoriteAccent,
            )
          }
        } else {
          Row(
            modifier =
              Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = PrismDimens.SpaceMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PrismDimens.SpaceSmall),
          ) {
            IconButton(
              onClick = {
                haptics.tap()
                settleOffset(0f)
                latestOnShare()
              },
              modifier =
                Modifier.size(PrismDimens.SwipeActionButtonSize)
                  .graphicsLayer {
                    val p = (-currentOffset() / leftTrayPx).coerceIn(0f, 1f)
                    val s = 0.65f + 0.35f * p
                    scaleX = s
                    scaleY = s
                    alpha = if (trashArmed) 0.28f else p
                  }
                  .clip(CircleShape)
                  .background(tokens.swipeShareAccent.copy(alpha = 0.16f))
                  .testTag("doc_swipe_share_$documentId"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.swipe_share_cd, documentName),
                tint = tokens.swipeShareAccent,
              )
            }
            IconButton(
              onClick = {
                haptics.reject()
                settleOffset(0f)
                latestOnTrash()
              },
              modifier =
                Modifier.size(PrismDimens.SwipeActionButtonSize)
                  .graphicsLayer {
                    val p = (-currentOffset() / leftTrayPx).coerceIn(0f, 1f)
                    val s = (0.65f + 0.35f * p) * trashIconScale.value
                    scaleX = s
                    scaleY = s
                    alpha = p.coerceAtLeast(0.2f)
                  }
                  .clip(CircleShape)
                  .background(
                    tokens.swipeDeleteAccent.copy(alpha = if (trashArmed) 0.32f else 0.16f)
                  )
                  .testTag("doc_swipe_delete_$documentId"),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.swipe_delete_cd, documentName),
                tint = tokens.swipeDeleteAccent,
              )
            }
          }
        }
      }
    }

    // Foreground slice translated strictly on GPU AFTER the outer Box's pointerInput
    Box(
      modifier =
        Modifier.fillMaxWidth().graphicsLayer {
          translationX = currentOffset()
        }
    ) {
      content(controller)
    }
  }
}
