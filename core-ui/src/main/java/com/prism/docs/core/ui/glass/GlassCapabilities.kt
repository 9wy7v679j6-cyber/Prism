package com.prism.docs.core.ui.glass

import android.os.Build

/**
 * Hardware-aware Liquid Glass rendering tiers:
 * - [LIQUID_FULL] (API 33+): Real backdrop blur + saturation boost + AGSL edge refraction shader.
 * - [BLUR_TINT] (API 31-32): Real backdrop blur + adaptive tint + specular edge stroke, no AGSL.
 * - [FROSTED_TRANSLUCENT] (API 26-30): Frosted multi-stop translucent gradient without real blur.
 * - [SOLID_REDUCED_TRANSPARENCY]: Accessibility contrast fallback when Reduce Transparency is on.
 */
enum class GlassCapabilityTier(
  val label: String,
  val supportsBackdropBlur: Boolean,
  val supportsAgslRefraction: Boolean,
) {
  LIQUID_FULL(
    label = "Liquid Glass + AGSL (API 33+)",
    supportsBackdropBlur = true,
    supportsAgslRefraction = true,
  ),
  BLUR_TINT(
    label = "Backdrop Blur + Tint (API 31–32)",
    supportsBackdropBlur = true,
    supportsAgslRefraction = false,
  ),
  FROSTED_TRANSLUCENT(
    label = "Frosted Translucent (API 26–30)",
    supportsBackdropBlur = false,
    supportsAgslRefraction = false,
  ),
  SOLID_REDUCED_TRANSPARENCY(
    label = "High-Contrast Opaque",
    supportsBackdropBlur = false,
    supportsAgslRefraction = false,
  ),
}

fun resolveGlassCapabilityTier(
  reduceTransparency: Boolean,
  sdkInt: Int = Build.VERSION.SDK_INT,
): GlassCapabilityTier {
  if (reduceTransparency) return GlassCapabilityTier.SOLID_REDUCED_TRANSPARENCY
  return when {
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> GlassCapabilityTier.LIQUID_FULL
    sdkInt >= Build.VERSION_CODES.S -> GlassCapabilityTier.BLUR_TINT
    else -> GlassCapabilityTier.FROSTED_TRANSLUCENT
  }
}
