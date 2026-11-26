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
 * Uses strict float/float3 math for compatibility across all API 33–36 SkSL compilers and
 * preserves premultiplied alpha cleanly.
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
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fragCoord) {
    float2 safeRes = max(resolution, float2(2.0));
    float2 halfRes = safeRes * 0.5;
    float2 p = fragCoord - halfRes;
    float r = clamp(cornerRadius, 0.0, min(halfRes.x, halfRes.y));

    float d = sdRoundedBox(p, halfRes, r);
    float safeBezel = max(bezelWidth, 1.0);
    float edgeDist = clamp(-d, 0.0, safeBezel);
    float rimFactor = 1.0 - smoothstep(0.0, safeBezel, edgeDist);

    float2 eps = float2(1.5, 0.0);
    float nx = sdRoundedBox(p + eps.xy, halfRes, r) - sdRoundedBox(p - eps.xy, halfRes, r);
    float ny = sdRoundedBox(p + eps.yx, halfRes, r) - sdRoundedBox(p - eps.yx, halfRes, r);
    float2 grad = float2(nx, ny);
    float gradLen = length(grad);
    float2 normal = gradLen > 0.0001 ? (grad / gradLen) : float2(0.0, -1.0);

    float2 refractOffset = normal * (rimFactor * rimFactor * refractionStrength);
    float2 minUv = float2(0.5);
    float2 maxUv = max(safeRes - float2(0.5), float2(1.0));
    float2 uvBase = clamp(fragCoord - refractOffset, minUv, maxUv);
    float2 uvR = clamp(fragCoord - refractOffset * 1.10, minUv, maxUv);
    float2 uvB = clamp(fragCoord - refractOffset * 0.90, minUv, maxUv);

    float4 baseSample = float4(composable.eval(uvBase));
    float alpha = baseSample.a;
    if (alpha <= 0.001) {
        return half4(0.0);
    }

    float rChan = float(composable.eval(uvR).r);
    float bChan = float(composable.eval(uvB).b);
    float3 unpremul = clamp(float3(rChan, baseSample.g, bChan) / alpha, float3(0.0), float3(1.0));

    float luma = dot(unpremul, float3(0.299, 0.587, 0.114));
    float3 saturated = mix(float3(luma), unpremul, saturationBoost);

    float lightDir = clamp(-normal.y * 0.7 - normal.x * 0.35, 0.0, 1.0);
    float specular = rimFactor * rimFactor * (0.05 + 0.12 * lightDir);
    float3 sheenColor = isDark > 0.5 ? float3(0.82, 0.91, 1.0) : float3(1.0, 1.0, 1.0);

    float3 finalRgb = clamp(saturated + sheenColor * specular, float3(0.0), float3(1.0)) * alpha;
    return half4(finalRgb, alpha);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class CachedLiquidGlassEffect {
  private val shader: RuntimeShader? = runCatching { RuntimeShader(LIQUID_GLASS_AGSL) }.getOrNull()
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
    val activeShader = shader ?: return null
    if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx < 4f || heightPx < 4f) return null
    val safeCorner = if (cornerRadiusPx.isFinite()) cornerRadiusPx.coerceAtLeast(0f) else 0f
    val safeBezel = if (bezelWidthPx.isFinite()) bezelWidthPx.coerceAtLeast(1f) else 6f
    val safeRefract = if (refractionStrengthPx.isFinite()) refractionStrengthPx.coerceIn(0f, 32f) else 0f
    val safeSat = if (saturationBoost.isFinite()) saturationBoost.coerceIn(0.5f, 2f) else 1f

    val existing = cached
    if (
      existing != null &&
        lastW == widthPx &&
        lastH == heightPx &&
        lastCorner == safeCorner &&
        lastBezel == safeBezel &&
        lastRefraction == safeRefract &&
        lastSat == safeSat &&
        lastDark == isDark
    ) {
      return existing
    }
    lastW = widthPx
    lastH = heightPx
    lastCorner = safeCorner
    lastBezel = safeBezel
    lastRefraction = safeRefract
    lastSat = safeSat
    lastDark = isDark

    return runCatching {
      activeShader.setFloatUniform("resolution", widthPx, heightPx)
      activeShader.setFloatUniform("cornerRadius", safeCorner)
      activeShader.setFloatUniform("bezelWidth", safeBezel)
      activeShader.setFloatUniform("refractionStrength", safeRefract)
      activeShader.setFloatUniform("saturationBoost", safeSat)
      activeShader.setFloatUniform("isDark", if (isDark) 1f else 0f)
      RenderEffect.createRuntimeShaderEffect(activeShader, "composable").asComposeRenderEffect()
    }.getOrNull().also { cached = it }
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
