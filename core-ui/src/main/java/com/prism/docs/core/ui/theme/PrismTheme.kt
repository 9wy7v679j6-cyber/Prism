package com.prism.docs.core.ui.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prism.docs.core.ui.R
import com.prism.docs.core.ui.glass.GlassCapabilityTier
import com.prism.docs.core.ui.glass.resolveGlassCapabilityTier
import dev.chrisbanes.haze.HazeState

// Brand & Prism optical spectrum palette
object PrismColors {
  val Sapphire = Color(0xFF236CE0)
  val IndigoBlue = Color(0xFF3B5EF6)
  val VioletAccent = Color(0xFF6C4AF6)
  val GlacierBlue = Color(0xFF54B4FF)
  val IceCyan = Color(0xFF38D9F5)
  val Amethyst = Color(0xFF7B61FF)
  val ScanPurple = Color(0xFF7B3CE9)
  val CoralPdf = Color(0xFFD9383A)
  val EmeraldSheet = Color(0xFF157A42)
  val OrangeSlide = Color(0xFFD9631B)
  val AmberSlide = Color(0xFFE59500)
  val OrchidImage = Color(0xFFB44CE0)
  val IndigoArchive = Color(0xFF535CE6)
  val TealCode = Color(0xFF0E9AA7)

  // Soft drifting ambient backdrop blob colors (blue, violet, pink, mint)
  val AmbientSky = Color(0xFF6B9EFF)
  val AmbientLavender = Color(0xFFB68EFF)
  val AmbientRose = Color(0xFFF4A4D4)
  val AmbientAqua = Color(0xFF5CDFF2)
  val AmbientMint = Color(0xFF66ECCE)

  val LightCanvas = Color(0xFFEEF3FA)
  val LightSurface = Color(0xFFFAFBFD)
  val LightSurfaceVariant = Color(0xFFE5ECF6)
  val LightOnSurface = Color(0xFF0C1526)
  val LightOnSurfaceMuted = Color(0xFF4F5D78)

  val DarkCanvas = Color(0xFF070B14)
  val DarkSurface = Color(0xFF101726)
  val DarkSurfaceVariant = Color(0xFF1A2438)
  val DarkOnSurface = Color(0xFFF1F5FC)
  val DarkOnSurfaceMuted = Color(0xFF98A7C2)
}

private val PrismLightColorScheme =
  lightColorScheme(
    primary = PrismColors.IndigoBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0D1E52),
    secondary = PrismColors.VioletAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E4FF),
    onSecondaryContainer = Color(0xFF1F1054),
    tertiary = PrismColors.TealCode,
    onTertiary = Color.White,
    background = PrismColors.LightCanvas,
    onBackground = PrismColors.LightOnSurface,
    surface = PrismColors.LightSurface,
    onSurface = PrismColors.LightOnSurface,
    surfaceVariant = PrismColors.LightSurfaceVariant,
    onSurfaceVariant = PrismColors.LightOnSurfaceMuted,
    outline = Color(0xFFC4D0E2),
    outlineVariant = Color(0xFFDDE5F0),
    error = PrismColors.CoralPdf,
    onError = Color.White,
  )

private val PrismDarkColorScheme =
  darkColorScheme(
    primary = PrismColors.GlacierBlue,
    onPrimary = Color(0xFF00264F),
    primaryContainer = Color(0xFF0A3970),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFFB4A5FF),
    onSecondary = Color(0xFF271568),
    secondaryContainer = Color(0xFF392782),
    onSecondaryContainer = Color(0xFFE9E4FF),
    tertiary = PrismColors.IceCyan,
    onTertiary = Color(0xFF00323B),
    background = PrismColors.DarkCanvas,
    onBackground = PrismColors.DarkOnSurface,
    surface = PrismColors.DarkSurface,
    onSurface = PrismColors.DarkOnSurface,
    surfaceVariant = PrismColors.DarkSurfaceVariant,
    onSurfaceVariant = PrismColors.DarkOnSurfaceMuted,
    outline = Color(0xFF32415E),
    outlineVariant = Color(0xFF1F2B42),
    error = Color(0xFFFF7B7F),
    onError = Color(0xFF4E0A0E),
  )

val PlusJakartaSansFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val InterFontFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

val PrismTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.2).sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.25).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
      ),
  )

val PrismShapes =
  Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

object PrismMotion {
  fun <T> fluidSpring(reduceMotion: Boolean): AnimationSpec<T> =
    if (reduceMotion) {
      snap()
    } else {
      spring(dampingRatio = 0.80f, stiffness = 360f)
    }

  fun <T> tactileSpring(reduceMotion: Boolean): AnimationSpec<T> =
    if (reduceMotion) {
      snap()
    } else {
      spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
    }

  fun concentricInnerRadius(outer: Dp, padding: Dp): Dp = (outer - padding).coerceAtLeast(8.dp)
}

@Immutable
data class GlassEnvironment(
  val isDark: Boolean = false,
  val capabilityTier: GlassCapabilityTier = GlassCapabilityTier.FROSTED_TRANSLUCENT,
  val blurScale: Float = 1.0f,
  val tintScale: Float = 1.0f,
  val reduceTransparency: Boolean = false,
  val reduceMotion: Boolean = false,
)

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment() }
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

fun isSystemReduceMotionEnabled(context: Context): Boolean {
  return runCatching {
      val scale =
        Settings.Global.getFloat(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
          1f,
        )
      scale == 0f
    }
    .getOrDefault(false)
}

@Composable
fun PrismTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  blurScale: Float = 1.0f,
  tintScale: Float = 1.0f,
  reduceTransparency: Boolean = false,
  reduceMotion: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val effectiveReduceMotion = reduceMotion || isSystemReduceMotionEnabled(context)

  val colorScheme: ColorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val dynamic =
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Preserve Prism's crisp canvas depth and legible typography while adopting Material You accent
        dynamic.copy(
          background = if (darkTheme) PrismColors.DarkCanvas else PrismColors.LightCanvas,
          onBackground = if (darkTheme) PrismColors.DarkOnSurface else PrismColors.LightOnSurface,
          surface = if (darkTheme) PrismColors.DarkSurface else PrismColors.LightSurface,
          onSurface = if (darkTheme) PrismColors.DarkOnSurface else PrismColors.LightOnSurface,
          onSurfaceVariant =
            if (darkTheme) PrismColors.DarkOnSurfaceMuted else PrismColors.LightOnSurfaceMuted,
        )
      }
      darkTheme -> PrismDarkColorScheme
      else -> PrismLightColorScheme
    }

  val tier = resolveGlassCapabilityTier(reduceTransparency = reduceTransparency)
  val glassEnv =
    GlassEnvironment(
      isDark = darkTheme,
      capabilityTier = tier,
      blurScale = blurScale,
      tintScale = tintScale,
      reduceTransparency = reduceTransparency,
      reduceMotion = effectiveReduceMotion,
    )

  CompositionLocalProvider(LocalGlassEnvironment provides glassEnv) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = PrismTypography,
      shapes = PrismShapes,
      content = content,
    )
  }
}
