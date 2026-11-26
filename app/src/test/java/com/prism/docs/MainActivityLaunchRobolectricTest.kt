package com.prism.docs

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchRobolectricTest {

  private val disableAnimationsRule = TestRule { base, _ ->
    object : Statement() {
      override fun evaluate() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prevScale =
          runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
          }.getOrDefault(1f)
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
          base.evaluate()
        } finally {
          Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, prevScale)
        }
      }
    }
  }

  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(disableAnimationsRule).around(composeTestRule)

  @Test
  fun coldBootOnboardingLibrarySettingsAndAllTopLevelScreensSucceed() {
    val app = ApplicationProvider.getApplicationContext<PrismApplication>()
    assertNotNull(app)

    composeTestRule.waitForIdle()
    ShadowLooper.idleMainLooper()

    // Wait for DataStore to emit preferences so Onboarding or Library is visible
    composeTestRule.waitUntil(timeoutMillis = 10_000L) {
      composeTestRule.onAllNodesWithTag("onboarding_skip_limited_button").fetchSemanticsNodes().isNotEmpty() ||
        composeTestRule.onAllNodesWithTag("onboarding_enter_app_button").fetchSemanticsNodes().isNotEmpty() ||
        composeTestRule.onAllNodesWithTag("glass_floating_tab_bar").fetchSemanticsNodes().isNotEmpty()
    }

    if (composeTestRule.onAllNodesWithTag("onboarding_skip_limited_button").fetchSemanticsNodes().isNotEmpty()) {
      composeTestRule.onNodeWithTag("onboarding_skip_limited_button").performClick()
    } else if (composeTestRule.onAllNodesWithTag("onboarding_enter_app_button").fetchSemanticsNodes().isNotEmpty()) {
      composeTestRule.onNodeWithTag("onboarding_enter_app_button").performClick()
    }

    composeTestRule.waitUntil(timeoutMillis = 10_000L) {
      composeTestRule.onAllNodesWithTag("glass_floating_tab_bar").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitForIdle()

    // Verify Library renders cleanly
    composeTestRule.waitUntil(timeoutMillis = 10_000L) {
      composeTestRule.onAllNodesWithTag("library_list").fetchSemanticsNodes().isNotEmpty() ||
        composeTestRule.onAllNodesWithTag("library_grid").fetchSemanticsNodes().isNotEmpty()
    }

    // Open Options sheet and switch to Grid view
    composeTestRule.onNodeWithTag("library_sliders_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("top_glass_settings_button").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule
      .onNode(hasTestTag("glass_segment_1") and hasAnyAncestor(hasTestTag("toggle_layout_segmented")))
      .performClick()
    composeTestRule.waitForIdle()

    // Open Glass Settings sheet and exercise LiquidGlassClaritySlider
    composeTestRule.onNodeWithTag("top_glass_settings_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("close_glass_sheet_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("glass_clarity_slider").performScrollTo().performTouchInput { swipeRight() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("close_glass_sheet_button").performClick()
    composeTestRule.waitForIdle()

    // Switch top segmented control to Storage -> Recycle Bin -> All Docs
    composeTestRule.onAllNodesWithTag("glass_segment_1")[0].performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("storage_roots_list").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onAllNodesWithTag("glass_segment_2")[0].performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("recycle_bin_list").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onAllNodesWithTag("glass_segment_0")[0].performClick()
    composeTestRule.waitForIdle()

    // Exercise top-level tab switching: Scan -> Tools -> Duplicate Finder -> Storage Analyzer -> Back
    composeTestRule.onNodeWithTag("tab_scan").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("tab_pdftools").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("tools_duplicate_finder_card").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("tools_duplicate_finder_card").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dup_back_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dup_back_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("tools_storage_analyzer_card").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("tools_storage_analyzer_card").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("storage_analyzer_list").fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag("analyzer_scope_segmented").fetchSemanticsNodes().isNotEmpty()
    }
  }
}
