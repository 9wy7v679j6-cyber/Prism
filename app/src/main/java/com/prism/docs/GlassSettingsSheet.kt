package com.prism.docs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LensBlur
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.prism.docs.core.data.model.PrismPreferences
import com.prism.docs.core.data.model.ThemeMode
import com.prism.docs.core.ui.glass.ConnectedGlassCardSlice
import com.prism.docs.core.ui.glass.GlassBottomSheet
import com.prism.docs.core.ui.glass.GlassDebugConfig
import com.prism.docs.core.ui.glass.GlassElevation
import com.prism.docs.core.ui.glass.GlassMotionStatusText
import com.prism.docs.core.ui.glass.GlassSheetDetent
import com.prism.docs.core.ui.glass.GlassSurface
import com.prism.docs.core.ui.glass.GlassToggle
import com.prism.docs.core.ui.glass.LiquidGlassClaritySlider
import com.prism.docs.core.ui.glass.glassClickable
import com.prism.docs.core.ui.glass.rememberPrismHaptics
import com.prism.docs.core.ui.theme.LocalGlassEnvironment
import com.prism.docs.core.ui.theme.LocalPrismVisualTokens
import com.prism.docs.core.ui.theme.PrismColors
import com.prism.docs.core.ui.theme.PrismDimens
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
  onGlassClarityPreview: (Float) -> Unit,
  onGlassClarityCommit: (Float) -> Unit,
  onReduceTransparencyChange: (Boolean) -> Unit,
  onReduceMotionChange: (Boolean) -> Unit,
  debugConfig: GlassDebugConfig = GlassDebugConfig(),
  onDebugConfigChange: (GlassDebugConfig) -> Unit = {},
) {
  if (visible) {
    BackHandler(onBack = onDismiss)
  }

  val env = LocalGlassEnvironment.current
  val tokens = LocalPrismVisualTokens.current
  val colorScheme = MaterialTheme.colorScheme
  val haptics = rememberPrismHaptics()

  GlassBottomSheet(
    visible = visible,
    detent = detent,
    onDetentChange = onDetentChange,
    onDismiss = onDismiss,
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Settings",
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
          color = colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (BuildConfig.DEBUG) {
          Box(
            modifier =
              Modifier.clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = if (env.isDark) 0.22f else 0.12f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
          ) {
            Text(
              text = env.capabilityTier.label,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      TextButton(
        onClick = {
          haptics.tap()
          onDismiss()
        },
        modifier =
          Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
            .testTag("close_glass_sheet_button"),
      ) {
        Text(
          text = "Done",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
      }
    }

    Spacer(Modifier.height(14.dp))

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 2.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      // Live Liquid Glass Material Preview
      LiveLiquidGlassPreviewCard(
        preferences = preferences,
        isDark = env.isDark,
      )

      // Section 1: Theme Picker Cards
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionLabel(title = "APPEARANCE")
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ThemeModeCard(
            label = "System",
            icon = Icons.Rounded.BrightnessAuto,
            selected = preferences.themeMode == ThemeMode.SYSTEM,
            onClick = {
              haptics.segmentTick()
              onThemeModeChange(ThemeMode.SYSTEM)
            },
            modifier = Modifier.weight(1f),
          )
          ThemeModeCard(
            label = "Light",
            icon = Icons.Rounded.LightMode,
            selected = preferences.themeMode == ThemeMode.LIGHT,
            onClick = {
              haptics.segmentTick()
              onThemeModeChange(ThemeMode.LIGHT)
            },
            modifier = Modifier.weight(1f),
          )
          ThemeModeCard(
            label = "Dark",
            icon = Icons.Rounded.DarkMode,
            selected = preferences.themeMode == ThemeMode.DARK,
            onClick = {
              haptics.segmentTick()
              onThemeModeChange(ThemeMode.DARK)
            },
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Section 2: Liquid Glass Clarity Slider & Accent
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionLabel(title = "LIQUID GLASS MATERIAL")

        GlassSurface(
          cornerRadius = 22.dp,
          elevation = GlassElevation.SUBTLE,
          useBackdropBlur = false,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val clarityPct = (preferences.glassClarity * 100f).roundToInt().coerceIn(0, 100)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Glass clarity",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
              )
              Text(
                text = if (preferences.reduceTransparency) "Opaque" else "$clarityPct%",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.primary,
                modifier = Modifier.testTag("glass_clarity_value_label"),
              )
            }
            LiquidGlassClaritySlider(
              value = preferences.glassClarity,
              onValueChange = onGlassClarityPreview,
              onValueChangeFinished = { onGlassClarityCommit(preferences.glassClarity) },
              enabled = !preferences.reduceTransparency,
              modifier = Modifier.fillMaxWidth().testTag("glass_clarity_slider"),
            )
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Frosted",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Balanced",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Clear glass",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
          GroupedSettingToggleRow(
            icon = Icons.Rounded.Palette,
            iconTint = colorScheme.primary,
            title = "Material You Accent",
            subtitle = "Tint Liquid Glass highlights from your wallpaper palette.",
            checked = preferences.useDynamicColor,
            onCheckedChange = onDynamicColorChange,
            isFirst = true,
            isLast = false,
            testTag = "toggle_dynamic_color",
          )
          GroupedSettingToggleRow(
            icon = Icons.Rounded.Contrast,
            iconTint = tokens.warningAccent,
            title = "Reduce Transparency",
            subtitle = "Use high-contrast opaque capsules for maximum legibility.",
            checked = preferences.reduceTransparency,
            onCheckedChange = onReduceTransparencyChange,
            isFirst = false,
            isLast = true,
            testTag = "toggle_reduce_transparency",
          )
        }
      }

      // Section 3: Motion & Accessibility
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSectionLabel(title = "MOTION & ACCESSIBILITY")
        GroupedSettingToggleRow(
          icon = Icons.Rounded.Animation,
          iconTint = colorScheme.secondary,
          title = "Reduce Motion",
          subtitle = "Replace bouncy springs and morphs with instant transitions.",
          checked = preferences.reduceMotion,
          onCheckedChange = onReduceMotionChange,
          isFirst = true,
          isLast = true,
          testTag = "toggle_reduce_motion",
        )

        if (BuildConfig.DEBUG) {
          GlassSurface(
            cornerRadius = 18.dp,
            elevation = GlassElevation.SUBTLE,
            useBackdropBlur = false,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
              ProvideTextStyle(
                MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
              ) {
                GlassMotionStatusText(
                  appReduceMotion = preferences.reduceMotion,
                  modifier = Modifier.fillMaxWidth().testTag("glass_motion_status_text"),
                )
              }
            }
          }
        }
      }

      // Section 4: Debug-Only Bisect Toggles (Job 1.4 — strictly behind BuildConfig.DEBUG)
      if (BuildConfig.DEBUG) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          SettingsSectionLabel(title = "DEBUG GLASS BISECT")
          Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            GroupedSettingToggleRow(
              icon = Icons.Rounded.BlurOn,
              iconTint = colorScheme.primary,
              title = "Backdrop Blur / Haze",
              subtitle = "Toggle live backdrop blur pass on floating glass.",
              checked = debugConfig.enableHaze,
              onCheckedChange = { onDebugConfigChange(debugConfig.copy(enableHaze = it)) },
              isFirst = true,
              isLast = false,
              testTag = "debug_toggle_haze",
            )
            GroupedSettingToggleRow(
              icon = Icons.Rounded.Layers,
              iconTint = tokens.warningAccent,
              title = "Exterior Glass Shadow",
              subtitle = "Toggle exterior ring shadow around floating glass.",
              checked = debugConfig.enableShadow,
              onCheckedChange = { onDebugConfigChange(debugConfig.copy(enableShadow = it)) },
              isFirst = false,
              isLast = false,
              testTag = "debug_toggle_shadow",
            )
            GroupedSettingToggleRow(
              icon = Icons.Rounded.LensBlur,
              iconTint = colorScheme.secondary,
              title = "AGSL Edge Refraction",
              subtitle = "Toggle API 33+ backdrop bezel refraction & dispersion.",
              checked = debugConfig.enableEdgeRefraction,
              onCheckedChange = {
                onDebugConfigChange(debugConfig.copy(enableEdgeRefraction = it))
              },
              isFirst = false,
              isLast = false,
              testTag = "debug_toggle_refraction",
            )
            GroupedSettingToggleRow(
              icon = Icons.Rounded.Flare,
              iconTint = tokens.successAccent,
              title = "Specular Sheen",
              subtitle = "Toggle angled shimmer sweep across glass surfaces.",
              checked = debugConfig.enableSheen,
              onCheckedChange = { onDebugConfigChange(debugConfig.copy(enableSheen = it)) },
              isFirst = false,
              isLast = true,
              testTag = "debug_toggle_sheen",
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))
    }
  }
}

@Composable
private fun LiveLiquidGlassPreviewCard(
  preferences: PrismPreferences,
  isDark: Boolean,
) {
  val colorScheme = MaterialTheme.colorScheme
  val clarityPct = (preferences.glassClarity * 100f).roundToInt().coerceIn(0, 100)
  val bgColors =
    if (isDark) {
      listOf(Color(0xFF1A2250), Color(0xFF3A2464), Color(0xFF15385C))
    } else {
      listOf(Color(0xFFC2D7FF), Color(0xFFE0CCFF), Color(0xFFBCEBFF))
    }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(colors = bgColors))
        .padding(14.dp),
  ) {
    // Non-blur translucent preview inside sheet (no nested hazeEffect)
    GlassSurface(
      cornerRadius = 20.dp,
      elevation = GlassElevation.SUBTLE,
      useBackdropBlur = false,
      enableRefraction = false,
      accentTint = colorScheme.primary.copy(alpha = if (isDark) 0.14f else 0.08f),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(42.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(
                Brush.linearGradient(
                  colors = listOf(PrismColors.IndigoBlue, PrismColors.VioletAccent),
                )
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "Liquid Glass Preview",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.onSurface,
            maxLines = 1,
          )
          val clarityTier =
            when {
              clarityPct >= 80 -> "Clear"
              clarityPct >= 35 -> "Balanced"
              else -> "Frosted"
            }
          val modeLabel =
            if (preferences.reduceTransparency) "Opaque contrast mode"
            else "$clarityTier ($clarityPct%) • ${if (isDark) "Dark" else "Light"}"
          Text(
            text = modeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionLabel(title: String) {
  Text(
    text = title,
    style =
      MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = TextUnit(0.8f, TextUnitType.Sp),
      ),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 4.dp),
  )
}

@Composable
private fun ThemeModeCard(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colorScheme = MaterialTheme.colorScheme
  val borderColor by
    animateColorAsState(
      targetValue = if (selected) colorScheme.primary else Color.Transparent,
      animationSpec = tween(180),
      label = "theme_card_border_$label",
    )
  val contentColor by
    animateColorAsState(
      targetValue = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
      animationSpec = tween(180),
      label = "theme_card_tint_$label",
    )
  val cardShape = RoundedCornerShape(20.dp)

  GlassSurface(
    cornerRadius = 20.dp,
    elevation = GlassElevation.SUBTLE,
    useBackdropBlur = false,
    accentTint = if (selected) colorScheme.primary.copy(alpha = 0.16f) else null,
    modifier =
      modifier
        .defaultMinSize(minHeight = 76.dp)
        .glassClickable(role = Role.RadioButton, hapticOnClick = false, onClick = onClick)
        .semantics { this.selected = selected },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(cardShape)
          .border(width = 1.6.dp, color = borderColor, shape = cardShape)
          .padding(vertical = 12.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier =
          Modifier.size(34.dp)
            .clip(CircleShape)
            .background(
              if (selected) colorScheme.primary.copy(alpha = 0.18f)
              else colorScheme.onSurface.copy(alpha = 0.06f)
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(19.dp),
        )
      }
      Text(
        text = label,
        style =
          MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          ),
        color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun GroupedSettingToggleRow(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  isFirst: Boolean,
  isLast: Boolean,
  testTag: String,
) {
  val colorScheme = MaterialTheme.colorScheme
  val tokens = LocalPrismVisualTokens.current

  ConnectedGlassCardSlice(
    isFirst = isFirst,
    isLast = isLast,
    cornerRadius = 22.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = PrismDimens.TouchTargetMin)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(38.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(iconTint.copy(alpha = if (tokens.isDark) 0.22f else 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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
      if (!isLast) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 14.dp)
              .height(PrismDimens.HairlineWidth)
              .background(tokens.rowDivider),
        )
      }
    }
  }
}
