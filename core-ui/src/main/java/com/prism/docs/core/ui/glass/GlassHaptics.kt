package com.prism.docs.core.ui.glass

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Semantic, API-level-aware Android haptics engine backed by [View.performHapticFeedback].
 *
 * Uses native [HapticFeedbackConstants] (API 30+ CONFIRM / REJECT / GESTURE_START and API 34+
 * SEGMENT_TICK / TOGGLE_ON / TOGGLE_OFF / GESTURE_THRESHOLD_ACTIVATE / DEACTIVATE) with crisp
 * fallbacks on older Android releases. Requires zero permissions and respects system haptic
 * preferences automatically.
 */
@Stable
class PrismHaptics(private val view: View) {

  /** Light tactile tap for standard buttons, pills, and cards. */
  fun tap() {
    perform(HapticFeedbackConstants.CONTEXT_CLICK)
  }

  /** Crisp notch tick when stepping across segments, tabs, filter chips, or slider stops. */
  fun segmentTick() {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.SEGMENT_TICK
      } else {
        HapticFeedbackConstants.CLOCK_TICK
      }
    perform(code)
  }

  /** Distinct tactile snap when toggling a switch or boolean state on/off. */
  fun toggle(checked: Boolean) {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (checked) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
      } else {
        HapticFeedbackConstants.CLOCK_TICK
      }
    perform(code)
  }

  /** Positive confirmation pulse (favorite toggled, restore, save, keep-newest). */
  fun confirm() {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
      } else {
        HapticFeedbackConstants.CONTEXT_CLICK
      }
    perform(code)
  }

  /** Heavy warning / destructive pulse (delete, move to bin, empty bin, deep-swipe trash). */
  fun reject() {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
      } else {
        HapticFeedbackConstants.LONG_PRESS
      }
    perform(code)
  }

  /** Deep press pulse for long-press context menus and entering selection mode. */
  fun longPress() {
    perform(HapticFeedbackConstants.LONG_PRESS)
  }

  /** Subtle onset tick when beginning a drag or scrub gesture. */
  fun dragStart() {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.GESTURE_START
      } else {
        HapticFeedbackConstants.CLOCK_TICK
      }
    perform(code)
  }

  /** Tactile detent click when crossing an active swipe or bottom-sheet threshold. */
  fun gestureThresholdActivate() {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
      } else {
        HapticFeedbackConstants.CLOCK_TICK
      }
    perform(code)
  }

  /** Softer release tick when backing off below an armed swipe or sheet threshold. */
  fun gestureThresholdDeactivate() {
    val code =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
      } else {
        HapticFeedbackConstants.CLOCK_TICK
      }
    perform(code)
  }

  private fun perform(constant: Int) {
    runCatching { view.performHapticFeedback(constant) }
  }
}

@Composable
fun rememberPrismHaptics(): PrismHaptics {
  val view = LocalView.current
  return remember(view) { PrismHaptics(view) }
}
