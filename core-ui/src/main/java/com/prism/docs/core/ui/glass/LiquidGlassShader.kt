package com.prism.docs.core.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AGSL shader implementing subtle convex edge lens refraction, saturation boost, and luminous
 * Fresnel rim caustics around rounded-rectangle Liquid Glass capsules (Android 13 / API 33+).
 *
 * Properly preserves premultiplied alpha so rounded corners never produce white fringe halos.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader composable;
uniform float2 resolution;
uniform float cornerRadius;
uniform float bezelWidth;
uniform float refractionStrength;
uniform float saturationBoost;
uniform float isDark;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 fragCoord) {
    float2 halfRes = max(resolution * 0.5, float2(1.0));
    float2 p = fragCoord - halfRes;
    float r = min(cornerRadius, min(halfRes.x, halfRes.y));

    float d = sdRoundedBox(p, halfRes, r);
    // Distance inward from the capsule edge (0 at edge, positive inside)
    float edgeDist = clamp(-d, 0.0, bezelWidth);
    float rimFactor = 1.0 - smoothstep(0.0, max(bezelWidth, 1.0), edgeDist);

    // Approximate inward normal for convex bezel refraction
    float2 eps = float2(1.5, 0.0);
    float nx = sdRoundedBox(p + eps.xy, halfRes, r) - sdRoundedBox(p - eps.xy, halfRes, r);
    float ny = sdRoundedBox(p + eps.yx, halfRes, r) - sdRoundedBox(p - eps.yx, halfRes, r);
    float2 normal = normalize(float2(nx, ny) + float2(0.0001));

    // Refract inward along the edge bevel with subtle chromatic dispersion
    float2 refractOffset = normal * rimFactor * rimFactor * refractionStrength;
    float2 uvBase = clamp(fragCoord - refractOffset, float2(0.5), resolution - float2(0.5));
    float2 uvR = clamp(fragCoord - refractOffset * 1.10, float2(0.5), resolution - float2(0.5));
    float2 uvB = clamp(fragCoord - refractOffset * 0.90, float2(0.5), resolution - float2(0.5));

    half4 baseSample = composable.eval(uvBase);
    half alpha = baseSample.a;
    if (alpha <= 0.001) {
        return half4(0.0);
    }

    half rChan = composable.eval(uvR).r;
    half bChan = composable.eval(uvB).b;
    // Un-premultiply before saturation & specular sheen, then re-premultiply cleanly
    half3 unpremul = clamp(half3(rChan, baseSample.g, bChan) / alpha, 0.0, 1.0);

    half luma = dot(unpremul, half3(0.299, 0.587, 0.114));
    half3 saturated = mix(half3(luma), unpremul, half(saturationBoost));

    float lightDir = clamp(-normal.y * 0.7 - normal.x * 0.35, 0.0, 1.0);
    float specular = rimFactor * rimFactor * (0.05 + 0.12 * lightDir);
    half3 sheenColor = isDark > 0.5 ? half3(0.82, 0.91, 1.0) : half3(1.0, 1.0, 1.0);

    half3 finalRgb = clamp(saturated + sheenColor * half(specular), 0.0, 1.0) * alpha;
    return half4(finalRgb, alpha);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class CachedLiquidGlassEffect {
  private val shader = RuntimeShader(LIQUID_GLASS_AGSL)
  private var lastW = -1f
  private var lastH = -1f
  private var lastCorner = -1f
  private var lastBezel = -1f
  private var lastRefraction = -1f
  private var lastSat = -1f
  private var lastDark = false
  private var cached: androidx.compose.ui.graphics.RenderEffect? = null

  fun obtain(
    widthPx: Float,
    heightPx: Float,
    cornerRadiusPx: Float,
    bezelWidthPx: Float,
    refractionStrengthPx: Float,
    saturationBoost: Float,
    isDark: Boolean,
  ): androidx.compose.ui.graphics.RenderEffect? {
    if (widthPx < 4f || heightPx < 4f) return null
    val existing = cached
    if (
      existing != null &&
        lastW == widthPx &&
        lastH == heightPx &&
        lastCorner == cornerRadiusPx &&
        lastBezel == bezelWidthPx &&
        lastRefraction == refractionStrengthPx &&
        lastSat == saturationBoost &&
        lastDark == isDark
    ) {
      return existing
    }
    lastW = widthPx
    lastH = heightPx
    lastCorner = cornerRadiusPx
    lastBezel = bezelWidthPx
    lastRefraction = refractionStrengthPx
    lastSat = saturationBoost
    lastDark = isDark

    shader.setFloatUniform("resolution", widthPx, heightPx)
    shader.setFloatUniform("cornerRadius", cornerRadiusPx)
    shader.setFloatUniform("bezelWidth", bezelWidthPx)
    shader.setFloatUniform("refractionStrength", refractionStrengthPx)
    shader.setFloatUniform("saturationBoost", saturationBoost)
    shader.setFloatUniform("isDark", if (isDark) 1f else 0f)
    return RenderEffect.createRuntimeShaderEffect(shader, "composable")
      .asComposeRenderEffect()
      .also { cached = it }
  }
}

/**
 * Applies cached AGSL rounded-box lens refraction on Android 13+ when explicitly enabled.
 * Zero per-frame RenderEffect allocations once sized; automatically a no-op on API 26-32.
 */
fun Modifier.liquidGlassEdgeRefraction(
  enabled: Boolean,
  cornerRadius: Dp,
  isDark: Boolean,
  bezelWidth: Dp = 7.dp,
  refractionStrength: Dp = 1.4.dp,
  saturationBoost: Float = 1.06f,
): Modifier = composed {
  if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    return@composed this
  }
  val cache = remember { CachedLiquidGlassEffect() }
  this.graphicsLayer {
    renderEffect =
      cache.obtain(
        widthPx = size.width,
        heightPx = size.height,
        cornerRadiusPx = cornerRadius.toPx(),
        bezelWidthPx = bezelWidth.toPx(),
        refractionStrengthPx = refractionStrength.toPx(),
        saturationBoost = saturationBoost,
        isDark = isDark,
      )
    clip = true
  }
}
