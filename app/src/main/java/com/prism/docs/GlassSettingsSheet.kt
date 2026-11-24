package com.prism.docs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ProvideTextStyle
import com.prism.docs.core.data.model.GlassIntensity
import com.prism.docs.core.data.model.PrismPreferences
import com.prism.docs.core.data.model.ThemeMode
import com.prism.docs.core.ui.glass.GlassBottomSheet
import com.prism.docs.core.ui.glass.GlassMotionStatusText
import com.prism.docs.core.ui.glass.GlassSegmentedControl
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSlider
import com.prism.docs.core.ui.glass.GlassToggle
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import kotlin.math.roundToInt

@Composable
fun GlassSettingsSheet(
  visible: Boolean,
  detent: GlassSheetDetent,
  onDetentChange: (GlassSheetDetent) -> Unit,
  onDismiss: () -> Unit,
  preferences: PrismPreferences,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onGlassIntensityChange: (GlassIntensity) -> Unit,
  onReduceTransparencyChange: (Boolean) -> Unit,
  onReduceMotionChange: (Boolean) -> Unit,
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }

  val env = LocalGlassEnvironment.current
  val colorScheme = MaterialTheme.colorScheme

  GlassBottomSheet(
    visible = visible,
    detent = detent,
    onDetentChange = onDetentChange,
    onDismiss = onDismiss,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "Glass & Accessibility",
          style = MaterialTheme.typography.titleLarge,
          color = colorScheme.onSurface,
        )
        Text(
          text = env.capabilityTier.label,
          style = MaterialTheme.typography.labelSmall,
          color = colorScheme.primary,
        )
      }
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("close_glass_sheet_button"),
      ) {
        Text("Done")
      }
    }

    Spacer(Modifier.height(12.dp))

    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Appearance",
          style = MaterialTheme.typography.titleSmall,
          color = colorScheme.onSurfaceVariant,
        )
        GlassSegmentedControl(
          options = listOf("System", "Light", "Dark"),
          selectedIndex = preferences.themeMode.ordinal,
          onSelect = { idx -> onThemeModeChange(ThemeMode.entries[idx]) },
        )
      }

      GlassSlider(
        value = preferences.glassIntensity.ordinal.toFloat(),
        onValueChange = { raw ->
          val snapped = raw.roundToInt().coerceIn(0, GlassIntensity.entries.lastIndex)
          onGlassIntensityChange(GlassIntensity.entries[snapped])
        },
        valueRange = 0f..2f,
        steps = 1,
        label = "Liquid Glass Intensity",
        valueBadge = preferences.glassIntensity.name,
      )

      SettingToggleRow(
        title = "Material You Accent",
        subtitle = "Tint Liquid Glass highlights with your wallpaper palette (Android 12+).",
        checked = preferences.useDynamicColor,
        onCheckedChange = onDynamicColorChange,
        testTag = "toggle_dynamic_color",
      )

      SettingToggleRow(
        title = "Reduce Transparency",
        subtitle = "Switch glass capsules to high-contrast opaque surfaces for maximum legibility.",
        checked = preferences.reduceTransparency,
        onCheckedChange = onReduceTransparencyChange,
        testTag = "toggle_reduce_transparency",
      )

      SettingToggleRow(
        title = "Reduce Motion",
        subtitle = "Disable spring animations and morph transitions (also respects system setting).",
        checked = preferences.reduceMotion,
        onCheckedChange = onReduceMotionChange,
        testTag = "toggle_reduce_motion",
      )

      ProvideTextStyle(
        MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
      ) {
        GlassMotionStatusText(
          appReduceMotion = preferences.reduceMotion,
          modifier = Modifier.fillMaxWidth().testTag("glass_motion_status_text"),
        )
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )
    }
    GlassToggle(
      checked = checked,
      onCheckedChange = onCheckedChange,
      contentDescriptionText = title,
      modifier = Modifier.testTag(testTag),
    )
  }
}
