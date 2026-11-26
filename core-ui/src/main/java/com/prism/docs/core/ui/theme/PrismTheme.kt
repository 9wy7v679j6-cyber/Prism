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
import androidx.compose.runtime.remember
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

  val OnAccent = Color.White
  val BrandGradientColors = listOf(IndigoBlue, VioletAccent)

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

  // Deep navy-violet dark palette paired with white 8–12% glass surfaces
  val DarkCanvas = Color(0xFF0A0B1A)
  val DarkSurface = Color(0xFF12142B)
  val DarkSurfaceVariant = Color(0xFF1B1E3D)
  val DarkOnSurface = Color(0xFFF3F5FF)
  val DarkOnSurfaceMuted = Color(0xFFA0AAC9)

  // Thumbnail sheet & miniature preview tokens
  val ThumbnailSheetLight = Color.White.copy(alpha = 0.96f)
  val ThumbnailSheetDark = Color(0xFF161936)
  val ThumbnailBorderLight = Color(0xFFD7E2F0).copy(alpha = 0.85f)
  val ThumbnailBorderDark = Color.White.copy(alpha = 0.14f)
  val FallbackLineLight = Color(0xFFD0DAE8)
  val FallbackLineDark = Color.White.copy(alpha = 0.16f)
  val FallbackHeaderLight = Color(0xFF1D2940)
  val FallbackHeaderDark = Color.White.copy(alpha = 0.72f)
  val SheetGridLight = Color(0xFFD6E0EC)
  val SheetGridDark = Color.White.copy(alpha = 0.14f)
  val SheetHeaderBgLight = Color(0xFFC2F0D3)
  val SlideBgLight = Color(0xFFFFDFC4)
  val SlideBgDark = Color(0xFF2D1E26)
  val SlideInnerLight = Color.White.copy(alpha = 0.88f)
  val SlideInnerDark = Color.White.copy(alpha = 0.10f)

  // Glass & scrim tokens
  val LightShadowAmbient = Color(0xFF334778).copy(alpha = 0.14f)
  val DarkShadowAmbient = Color.Black.copy(alpha = 0.56f)
  val DarkPillShadow = Color.Black.copy(alpha = 0.42f)
  val LightPillShadow = Color(0x1E344E9E)
  val LightGlassBase = Color.White
  val DarkGlassTint = Color.White
  val DarkOverlayGlassFallback = Color(0xFF141732)
  val DarkSegmentRecess = Color(0xFF060816).copy(alpha = 0.54f)
  val LightSegmentRecess = Color(0xFF1A2C56).copy(alpha = 0.11f)
  val DarkSegmentThumb = Color.White.copy(alpha = 0.21f)
  val LightSegmentThumb = Color.White
  val DarkSegmentThumbBorder = Color.White.copy(alpha = 0.34f)
  val LightSegmentThumbBorder = Color.White.copy(alpha = 0.92f)
  val DarkTabInactive = Color(0xFFB5BFDF)
  val LightTabInactive = Color(0xFF172236)
  val LightSegmentInactive = Color(0xFF3E4C66)
  val DarkOutline = Color(0xFF39426E)
  val DarkOutlineVariant = Color(0xFF23284A)

  // Semantic accents tuned for dark glass legibility
  val DarkSuccessAccent = Color(0xFF4ADE80)
  val DarkWarningAccent = Color(0xFFFFB74D)
  val DarkDestructiveAccent = Color(0xFFFF7B7F)

  // Shimmer sweep tokens
  val ShimmerBaseLight = Color(0xFFDDE5F2).copy(alpha = 0.75f)
  val ShimmerHighlightLight = Color.White.copy(alpha = 0.88f)
  val ShimmerBaseDark = Color.White.copy(alpha = 0.08f)
  val ShimmerHighlightDark = Color.White.copy(alpha = 0.22f)
}

object PrismDimens {
  val HairlineWidth = 0.75.dp
  val GlassBorderWidth = 1.dp
  val GlassTopEdgeWidth = 1.dp

  val ScreenHorizontalPadding = 20.dp
  val ScreenTopPadding = 12.dp
  val ContentMaxWidth = 760.dp
  val OverlayMaxWidth = 600.dp

  val TouchTargetMin = 48.dp
  val HeaderBarHeight = 56.dp
  val HeaderIconButtonSize = 48.dp
  val HeaderIconSize = 22.dp
  val HeaderIndexingSpinnerSize = 12.dp
  val HeaderFilterDotSize = 10.dp
  val HeaderFilterDotPadding = 3.dp
  val HeaderFilterDotBorder = 1.5.dp

  val SearchPillHeight = 58.dp
  val SearchPillCornerRadius = 29.dp
  val SearchBoltSize = 42.dp
  val SearchIconSize = 22.dp

  val CategoryChipMinWidth = 64.dp
  val CategoryChipHeight = 48.dp
  val CategoryChipCornerRadius = 24.dp
  val CategoryChipHorizontalPadding = 20.dp
  val ChipFadeWidth = 30.dp

  val RecentCardWidth = 158.dp
  val RecentCardCornerRadius = 26.dp
  val RecentThumbnailHeight = 134.dp
  val RecentThumbnailCornerRadius = 18.dp
  val RecentFadeWidth = 28.dp

  val GridCardMinSize = 164.dp
  val ListCardCornerRadius = 28.dp
  val StickyHeaderCornerRadius = 18.dp
  val EmptyCardCornerRadius = 28.dp
  val BannerCornerRadius = 24.dp
  val FilterBarCornerRadius = 18.dp
  val ToastCornerRadius = 20.dp

  val ListBottomContentPadding = 168.dp
  val OverlayBottomPadding = 124.dp
  val TopScrimCollapsedHeight = 76.dp
  val TopScrimExpandedHeight = 156.dp
  val BottomScrimHeight = 132.dp

  val BadgeSquareSize = 46.dp
  val BadgeSquareCornerRadius = 14.dp
  val SelectedOverlayBadgeSize = 19.dp
  val BadgePillHorizontalPadding = 9.dp
  val BadgePillVerticalPadding = 3.dp
  val BadgeCornerInset = 8.dp
  val ThumbnailSheetHeaderHeight = 26.dp
  val ThumbnailFallbackIconSize = 22.dp
  val ThumbnailCompactIconSize = 24.dp

  val SwipeFavoriteTrigger = 72.dp
  val SwipeMaxRight = 104.dp
  val SwipeLeftTrayWidth = 118.dp
  val SwipeMaxLeft = 148.dp
  val SwipeActionButtonSize = 48.dp

  val ShadowSmall = 6.dp
  val ShadowMedium = 8.dp

  val SpaceXSmall = 2.dp
  val SpaceSmall = 4.dp
  val SpaceSmallMedium = 6.dp
  val SpaceMedium = 8.dp
  val SpaceMediumLarge = 10.dp
  val SpaceStandard = 12.dp
  val SpaceRow = 14.dp
  val SpaceSection = 16.dp
  val SpaceLarge = 20.dp
  val SpaceXLarge = 24.dp

  val FontSizeSectionTitle = 20.sp
  val FontSizeTextLink = 15.sp
  val FontSizeCategoryChip = 14.5.sp
  val FontSizeBadgePill = 10.sp
  val FontSizeBadgeSquareCompact = 11.5.sp
  val FontSizeBadgeSquareRegular = 12.5.sp
  val LetterSpacingTightBadge = (-0.2).sp
  val LetterSpacingWideBadge = 0.2.sp
}

@Immutable
data class PrismVisualTokens(
  val isDark: Boolean,
  val linkAccent: Color,
  val brandGradient: List<Color>,
  val thumbnailSheetBg: Color,
  val thumbnailSheetBorder: Color,
  val fallbackLine: Color,
  val fallbackHeader: Color,
  val sheetGridLine: Color,
  val sheetHeaderBg: Color,
  val slideBg: Color,
  val slideInnerCard: Color,
  val shimmerBase: Color,
  val shimmerHighlight: Color,
  val favoriteAccent: Color,
  val swipeShareAccent: Color,
  val swipeDeleteAccent: Color,
  val stickyHeaderTint: Color,
  val rowDivider: Color,
  val successAccent: Color,
  val warningAccent: Color,
  val destructiveAccent: Color,
  val gridActionScrim: Color,
  val segmentRecess: Color,
  val segmentThumb: Color,
  val segmentThumbBorder: Color,
  val segmentActiveText: Color,
  val pillShadow: Color,
  val tabActiveContent: Color,
)

private fun lightPrismVisualTokens(): PrismVisualTokens =
  PrismVisualTokens(
    isDark = false,
    linkAccent = PrismColors.IndigoBlue,
    brandGradient = PrismColors.BrandGradientColors,
    thumbnailSheetBg = PrismColors.ThumbnailSheetLight,
    thumbnailSheetBorder = PrismColors.ThumbnailBorderLight,
    fallbackLine = PrismColors.FallbackLineLight,
    fallbackHeader = PrismColors.FallbackHeaderLight,
    sheetGridLine = PrismColors.SheetGridLight,
    sheetHeaderBg = PrismColors.SheetHeaderBgLight,
    slideBg = PrismColors.SlideBgLight,
    slideInnerCard = PrismColors.SlideInnerLight,
    shimmerBase = PrismColors.ShimmerBaseLight,
    shimmerHighlight = PrismColors.ShimmerHighlightLight,
    favoriteAccent = PrismColors.AmberSlide,
    swipeShareAccent = PrismColors.IndigoBlue,
    swipeDeleteAccent = PrismColors.CoralPdf,
    stickyHeaderTint = PrismColors.LightSurface,
    rowDivider = PrismColors.LightOnSurface.copy(alpha = 0.08f),
    successAccent = PrismColors.EmeraldSheet,
    warningAccent = PrismColors.AmberSlide,
    destructiveAccent = PrismColors.CoralPdf,
    gridActionScrim = PrismColors.LightSurface,
    segmentRecess = PrismColors.LightSegmentRecess,
    segmentThumb = PrismColors.LightSegmentThumb,
    segmentThumbBorder = PrismColors.LightSegmentThumbBorder,
    segmentActiveText = PrismColors.IndigoBlue,
    pillShadow = PrismColors.LightPillShadow,
    tabActiveContent = PrismColors.IndigoBlue,
  )

private fun darkPrismVisualTokens(colorScheme: ColorScheme): PrismVisualTokens =
  PrismVisualTokens(
    isDark = true,
    linkAccent = colorScheme.primary,
    brandGradient = PrismColors.BrandGradientColors,
    thumbnailSheetBg = PrismColors.ThumbnailSheetDark,
    thumbnailSheetBorder = PrismColors.ThumbnailBorderDark,
    fallbackLine = PrismColors.FallbackLineDark,
    fallbackHeader = PrismColors.FallbackHeaderDark,
    sheetGridLine = PrismColors.SheetGridDark,
    sheetHeaderBg = PrismColors.EmeraldSheet.copy(alpha = 0.34f),
    slideBg = PrismColors.SlideBgDark,
    slideInnerCard = PrismColors.SlideInnerDark,
    shimmerBase = PrismColors.ShimmerBaseDark,
    shimmerHighlight = PrismColors.ShimmerHighlightDark,
    favoriteAccent = PrismColors.DarkWarningAccent,
    swipeShareAccent = colorScheme.primary,
    swipeDeleteAccent = colorScheme.error,
    stickyHeaderTint = PrismColors.DarkSurface,
    rowDivider = Color.White.copy(alpha = 0.10f),
    successAccent = PrismColors.DarkSuccessAccent,
    warningAccent = PrismColors.DarkWarningAccent,
    destructiveAccent = PrismColors.DarkDestructiveAccent,
    gridActionScrim = PrismColors.DarkSurface,
    segmentRecess = PrismColors.DarkSegmentRecess,
    segmentThumb = PrismColors.DarkSegmentThumb,
    segmentThumbBorder = PrismColors.DarkSegmentThumbBorder,
    segmentActiveText = Color.White,
    pillShadow = PrismColors.DarkPillShadow,
    tabActiveContent = Color.White,
  )

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
    primaryContainer = Color(0xFF1D2858),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFB4A5FF),
    onSecondary = Color(0xFF271568),
    secondaryContainer = Color(0xFF332674),
    onSecondaryContainer = Color(0xFFE9E4FF),
    tertiary = PrismColors.IceCyan,
    onTertiary = Color(0xFF00323B),
    background = PrismColors.DarkCanvas,
    onBackground = PrismColors.DarkOnSurface,
    surface = PrismColors.DarkSurface,
    onSurface = PrismColors.DarkOnSurface,
    surfaceVariant = PrismColors.DarkSurfaceVariant,
    onSurfaceVariant = PrismColors.DarkOnSurfaceMuted,
    outline = PrismColors.DarkOutline,
    outlineVariant = PrismColors.DarkOutlineVariant,
    error = PrismColors.DarkDestructiveAccent,
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
  val clarity: Float = 0.35f,
  val blurScale: Float = 1.0f,
  val tintScale: Float = 1.0f,
  val reduceTransparency: Boolean = false,
  val reduceMotion: Boolean = false,
)

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment() }
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
val LocalPrismVisualTokens = staticCompositionLocalOf { lightPrismVisualTokens() }

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
  glassClarity: Float = 0.35f,
  blurScale: Float = 1.0f,
  tintScale: Float = 1.0f,
  reduceTransparency: Boolean = false,
  reduceMotion: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val systemReduceMotion = remember(context) { isSystemReduceMotionEnabled(context) }
  val effectiveReduceMotion = reduceMotion || systemReduceMotion

  val colorScheme: ColorScheme =
    remember(darkTheme, dynamicColor, context) {
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
            surfaceVariant =
              if (darkTheme) PrismColors.DarkSurfaceVariant else PrismColors.LightSurfaceVariant,
            onSurfaceVariant =
              if (darkTheme) PrismColors.DarkOnSurfaceMuted else PrismColors.LightOnSurfaceMuted,
            outline = if (darkTheme) PrismColors.DarkOutline else PrismLightColorScheme.outline,
            outlineVariant =
              if (darkTheme) PrismColors.DarkOutlineVariant else PrismLightColorScheme.outlineVariant,
            error = if (darkTheme) PrismColors.DarkDestructiveAccent else PrismColors.CoralPdf,
          )
        }
        darkTheme -> PrismDarkColorScheme
        else -> PrismLightColorScheme
      }
    }

  val clampedClarity = if (glassClarity.isFinite()) glassClarity.coerceIn(0f, 1f) else 0.35f
  val tier = remember(reduceTransparency) { resolveGlassCapabilityTier(reduceTransparency = reduceTransparency) }
  val glassEnv =
    remember(darkTheme, tier, clampedClarity, blurScale, tintScale, reduceTransparency, effectiveReduceMotion) {
      GlassEnvironment(
        isDark = darkTheme,
        capabilityTier = tier,
        clarity = clampedClarity,
        blurScale = blurScale,
        tintScale = tintScale,
        reduceTransparency = reduceTransparency,
        reduceMotion = effectiveReduceMotion,
      )
    }
  val visualTokens =
    remember(darkTheme, colorScheme) {
      if (darkTheme) darkPrismVisualTokens(colorScheme) else lightPrismVisualTokens()
    }

  CompositionLocalProvider(
    LocalGlassEnvironment provides glassEnv,
    LocalPrismVisualTokens provides visualTokens,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = PrismTypography,
      shapes = PrismShapes,
      content = content,
    )
  }
}
