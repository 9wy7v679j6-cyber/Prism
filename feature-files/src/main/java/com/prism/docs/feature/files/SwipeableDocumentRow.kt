package com.prism.docs.feature.files

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.prism.docs.core.ui.glass.LocalGlassMotion
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismDimens
import com.prism.docs.core.ui.theme.PrismMotion
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch

/**
 * Coordinates active row swipe progress across sibling filename bars in the same grouped card
 * section so adjacent rows smoothly round their facing edges, gently separate, and animate in
 * concert with the swiped card at 120fps without recomposing unrelated rows.
 */
@Stable
class ListRowSwipeCoordinator {
  var activeSectionKey by mutableStateOf<String?>(null)
    private set

  var activeIndex by mutableIntStateOf(-1)
    private set

  var activeDocumentId by mutableLongStateOf(-1L)
    private set

  var swipeProgress by mutableFloatStateOf(0f)
    private set

  var swipeOffsetPx by mutableFloatStateOf(0f)
    private set

  fun update(
    sectionKey: String,
    index: Int,
    documentId: Long,
    progress: Float,
    offsetPx: Float,
  ) {
    if (sectionKey.isEmpty() || index < 0) return
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped > 0.004f) {
      activeSectionKey = sectionKey
      activeIndex = index
      activeDocumentId = documentId
      swipeProgress = clamped
      swipeOffsetPx = offsetPx
    } else if (activeDocumentId == documentId) {
      activeSectionKey = null
      activeIndex = -1
      activeDocumentId = -1L
      swipeProgress = 0f
      swipeOffsetPx = 0f
    }
  }

  fun clearIfOwner(documentId: Long) {
    if (activeDocumentId == documentId) {
      activeSectionKey = null
      activeIndex = -1
      activeDocumentId = -1L
      swipeProgress = 0f
      swipeOffsetPx = 0f
    }
  }
}

val LocalListRowSwipeCoordinator = compositionLocalOf<ListRowSwipeCoordinator?> { null }

/**
 * Controller passed to swipeable document row content so tap/long-press handlers can check
 * whether an action tray is currently revealed, close it smoothly, and read live swipe progress
 * for squircle corner morphing.
 */
@Stable
internal class RowSwipeController(
  private val isOpenProvider: () -> Boolean,
  private val swipeProgressProvider: () -> Float,
  private val onCloseRequest: () -> Unit,
) {
  val isOpen: Boolean
    get() = isOpenProvider()

  val swipeProgress: Float
    get() = swipeProgressProvider()

  fun close() = onCloseRequest()
}

/**
 * 120fps GPU-translated swipe container for document rows:
 * - Intentional horizontal drag lock (raised 22dp / 2.25x slop + 1.75x axis dominance) so vertical
 *   scrolling and casual touches never trigger accidental swipes.
 * - Morphs the swiped filename bar into a smooth rounded squircle with soft floating lift and
 *   coordinates live neighbor separation via [LocalListRowSwipeCoordinator].
 * - Right swipe = Favorite toggle (with armed icon scale pop + threshold/confirm haptics).
 * - Left swipe = Share & Delete action tray; deep left swipe = direct Move to Bin (with reject haptic).
 */
@Composable
internal fun SwipeableDocumentRowContainer(
  documentId: Long,
  documentName: String,
  isFavorite: Boolean,
  isSelectionMode: Boolean,
  restingTopRadius: Dp,
  restingBottomRadius: Dp,
  onToggleFavorite: () -> Unit,
  onShare: () -> Unit,
  onTrash: () -> Unit,
  modifier: Modifier = Modifier,
  sectionKey: String = "",
  itemIndex: Int = -1,
  swipingSquircleRadius: Dp = 22.dp,
  content: @Composable (RowSwipeController) -> Unit,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current
  val motionEnabled = LocalGlassMotion.current
  val haptics = rememberPrismHaptics()
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val coordinator = LocalListRowSwipeCoordinator.current

  val latestOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
  val latestOnShare by rememberUpdatedState(onShare)
  val latestOnTrash by rememberUpdatedState(onTrash)

  // Raised, deliberate thresholds to eliminate accidental triggers
  val rightTriggerPx = with(density) { 96.dp.toPx() }
  val maxRightPx = with(density) { 128.dp.toPx() }
  val leftTrayPx = with(density) { 128.dp.toPx() }
  val leftTrayCommitPx = leftTrayPx * 0.56f
  val maxLeftPx = with(density) { 176.dp.toPx() }
  val deepDeletePx = with(density) { 160.dp.toPx() }
  val flingVelocityPx = with(density) { 640.dp.toPx() }
  val squircleRampPx = with(density) { 26.dp.toPx() }
  val minHorizontalSlopDpPx = with(density) { 22.dp.toPx() }
  val minVerticalCancelDpPx = with(density) { 10.dp.toPx() }
  val springSpec = PrismMotion.tactileSpring<Float>(reduceMotion = !motionEnabled)

  val swipeAnim = remember(documentId) { Animatable(0f) }
  var isDragging by remember(documentId) { mutableStateOf(false) }
  var liveOffsetPx by remember(documentId) { mutableFloatStateOf(0f) }
  // 0 = neutral, 1 = right favorite armed, -1 = left tray armed, -2 = deep left trash armed
  var armedStage by remember(documentId) { mutableIntStateOf(0) }

  fun currentOffset(): Float = if (isDragging) liveOffsetPx else swipeAnim.value
  fun currentProgress(): Float = (abs(currentOffset()) / squircleRampPx).coerceIn(0f, 1f)

  // Broadcast live swipe progress to nearby rows during both drag and spring settle
  LaunchedEffect(documentId, sectionKey, itemIndex, coordinator) {
    if (coordinator != null && sectionKey.isNotEmpty() && itemIndex >= 0) {
      snapshotFlow { currentOffset() }.collect { offset ->
        val progress = (abs(offset) / squircleRampPx).coerceIn(0f, 1f)
        coordinator.update(
          sectionKey = sectionKey,
          index = itemIndex,
          documentId = documentId,
          progress = progress,
          offsetPx = offset,
        )
      }
    }
  }

  DisposableEffect(documentId, coordinator) {
    onDispose { coordinator?.clearIfOwner(documentId) }
  }

  fun applyRubberBand(raw: Float): Float {
    return when {
      raw > rightTriggerPx -> {
        val extra = maxRightPx - rightTriggerPx
        rightTriggerPx + extra * (1f - exp(-(raw - rightTriggerPx) / (extra * 1.6f).coerceAtLeast(1f)))
      }
      raw < -maxLeftPx -> {
        val extra = 26f
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
        offset <= -leftTrayCommitPx -> -1
        armedStage == 1 && offset < rightTriggerPx * 0.80f -> 0
        armedStage == -2 && offset > -deepDeletePx * 0.88f -> -1
        armedStage == -1 && offset > -leftTrayCommitPx * 0.65f -> 0
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
        initialVelocity = velocityX * 0.24f,
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
        swipeProgressProvider = { currentProgress() },
        onCloseRequest = { settleOffset(0f) },
      )
    }

  val showSwipeReveal by remember(documentId) { derivedStateOf { abs(currentOffset()) > 1.5f } }
  val isRightSwipe by remember(documentId) { derivedStateOf { currentOffset() > 0f } }
  val favoriteArmed by remember(documentId) { derivedStateOf { armedStage == 1 } }
  val trashArmed by remember(documentId) { derivedStateOf { armedStage == -2 } }
  val swipeProgress by remember(documentId) { derivedStateOf { currentProgress() } }

  // Smooth squircle corner interpolation: middle rows (0dp) rapidly round to 22dp on swipe
  val activeTopRadius = lerp(restingTopRadius, swipingSquircleRadius, swipeProgress)
  val activeBottomRadius = lerp(restingBottomRadius, swipingSquircleRadius, swipeProgress)
  val liveSquircleShape =
    RoundedCornerShape(
      topStart = activeTopRadius,
      topEnd = activeTopRadius,
      bottomStart = activeBottomRadius,
      bottomEnd = activeBottomRadius,
    )

  val favIconScale =
    animateFloatAsState(
      targetValue = if (favoriteArmed) 1.24f else 0.90f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.50f, stiffness = 520f) else snap(),
      label = "swipe_fav_scale_$documentId",
    )
  val trashIconScale =
    animateFloatAsState(
      targetValue = if (trashArmed) 1.22f else 1f,
      animationSpec =
        if (motionEnabled) spring(dampingRatio = 0.52f, stiffness = 520f) else snap(),
      label = "swipe_trash_scale_$documentId",
    )

  // Outer Box is NEVER translated, guaranteeing rock-solid pointer coordinates during horizontal drag
  Box(
    modifier =
      modifier
        .pointerInput(documentId, isSelectionMode, motionEnabled) {
          if (isSelectionMode) return@pointerInput
          val minHorizontalSlop = maxOf(viewConfiguration.touchSlop * 2.25f, minHorizontalSlopDpPx)
          val minVerticalCancel = maxOf(viewConfiguration.touchSlop * 1.15f, minVerticalCancelDpPx)

          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDx = 0f
            var totalDy = 0f
            var lockedChange: androidx.compose.ui.input.pointer.PointerInputChange? = null

            // Intentional horizontal lock: yield immediately if vertical scroll dominates
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (change.changedToUpIgnoreConsumed() || !change.pressed) break
              val delta = change.positionChange()
              totalDx += delta.x
              totalDy += delta.y

              val absX = abs(totalDx)
              val absY = abs(totalDy)
              if (absY >= minVerticalCancel && absY * 1.12f >= absX) {
                // Vertical scroll intent detected — immediately yield to LazyColumn
                break
              }
              if (absX >= minHorizontalSlop && absX >= absY * 1.75f) {
                change.consume()
                lockedChange = change
                break
              }
            }

            if (lockedChange != null) {
              val velocityTracker = VelocityTracker()
              velocityTracker.addPosition(down.uptimeMillis, down.position)
              velocityTracker.addPosition(lockedChange.uptimeMillis, lockedChange.position)

              val startOffset = currentOffset()
              // Gentle lead-in rather than snapping the full slop jump
              var rawAccumulated = startOffset + totalDx * 0.25f
              var settledNormally = false
              isDragging = true
              try {
                val firstVisual = applyRubberBand(rawAccumulated)
                liveOffsetPx = firstVisual
                updateArmedStage(firstVisual)

                val completed =
                  horizontalDrag(lockedChange.id) { change ->
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
                      (velocityX > flingVelocityPx && finalOffset > rightTriggerPx * 0.62f)) -> {
                    haptics.confirm()
                    latestOnToggleFavorite()
                    settleOffset(0f, velocityX)
                  }
                  completed &&
                    (finalOffset <= -deepDeletePx ||
                      (velocityX < -flingVelocityPx * 1.85f && finalOffset < -leftTrayPx * 0.92f)) -> {
                    haptics.reject()
                    settleOffset(0f, velocityX)
                    latestOnTrash()
                  }
                  completed &&
                    (finalOffset <= -leftTrayCommitPx ||
                      (velocityX < -flingVelocityPx && finalOffset < -leftTrayPx * 0.46f)) -> {
                    if (startOffset > -leftTrayPx * 0.5f) {
                      haptics.segmentTick()
                    }
                    settleOffset(-leftTrayPx, velocityX)
                  }
                  else -> {
                    settleOffset(0f, velocityX)
                  }
                }
                settledNormally = true
              } finally {
                if (!settledNormally) {
                  armedStage = 0
                  isDragging = false
                  liveOffsetPx = 0f
                  scope.launch { swipeAnim.snapTo(0f) }
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
            .padding(vertical = 2.dp * swipeProgress)
            .clip(liveSquircleShape)
            .background(
              when {
                isRightSwipe && favoriteArmed -> tokens.favoriteAccent.copy(alpha = 0.28f)
                isRightSwipe -> tokens.favoriteAccent.copy(alpha = 0.16f)
                trashArmed -> tokens.swipeDeleteAccent.copy(alpha = 0.26f)
                else -> colorScheme.surfaceVariant.copy(alpha = if (tokens.isDark) 0.52f else 0.78f)
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
                  translationX = -(1f - p) * 14.dp.toPx()
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
                  .background(tokens.swipeShareAccent.copy(alpha = 0.18f))
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
                    tokens.swipeDeleteAccent.copy(alpha = if (trashArmed) 0.34f else 0.18f)
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

    // Foreground slice translated & squircle-clipped strictly on GPU AFTER outer Box pointerInput
    Box(
      modifier =
        Modifier.fillMaxWidth().graphicsLayer {
          val p = currentProgress()
          translationX = currentOffset()
          val sX = 1f + 0.008f * p
          val sY = 1f - 0.014f * p
          scaleX = sX
          scaleY = sY
          shadowElevation = if (p > 0.02f) 10.dp.toPx() * p else 0f
          shape = liveSquircleShape
          clip = p > 0.01f
        }
    ) {
      content(controller)
    }
  }
}
