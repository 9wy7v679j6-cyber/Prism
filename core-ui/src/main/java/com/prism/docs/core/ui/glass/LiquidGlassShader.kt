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
    float2 uvR = clamp(fragCoord - refractOffset * 1.12, float2(0.5), resolution - float2(0.5));
    float2 uvB = clamp(fragCoord - refractOffset * 0.88, float2(0.5), resolution - float2(0.5));

    half4 baseSample = composable.eval(uvBase);
    half rChan = composable.eval(uvR).r;
    half bChan = composable.eval(uvB).b;
    half3 refracted = half3(rChan, baseSample.g, bChan);

    // Vibrancy / optical saturation lift typical of Liquid Glass
    half luma = dot(refracted, half3(0.299, 0.587, 0.114));
    half3 saturated = mix(half3(luma), refracted, half(saturationBoost));

    // Subtle directional top-left specular sheen along the refracting edge
    float lightDir = clamp(-normal.y * 0.7 - normal.x * 0.35, 0.0, 1.0);
    float specular = rimFactor * rimFactor * (0.06 + 0.14 * lightDir);
    half3 sheenColor = isDark > 0.5 ? half3(0.82, 0.91, 1.0) : half3(1.0, 1.0, 1.0);

    half3 finalRgb = clamp(saturated + sheenColor * half(specular), 0.0, 1.0);
    return half4(finalRgb, baseSample.a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun buildLiquidGlassRenderEffect(
  shader: RuntimeShader,
  widthPx: Float,
  heightPx: Float,
  cornerRadiusPx: Float,
  bezelWidthPx: Float,
  refractionStrengthPx: Float,
  saturationBoost: Float,
  isDark: Boolean,
): androidx.compose.ui.graphics.RenderEffect? {
  if (widthPx < 4f || heightPx < 4f) return null
  shader.setFloatUniform("resolution", widthPx, heightPx)
  shader.setFloatUniform("cornerRadius", cornerRadiusPx)
  shader.setFloatUniform("bezelWidth", bezelWidthPx)
  shader.setFloatUniform("refractionStrength", refractionStrengthPx)
  shader.setFloatUniform("saturationBoost", saturationBoost)
  shader.setFloatUniform("isDark", if (isDark) 1f else 0f)
  return RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
}

/**
 * Applies AGSL rounded-box lens refraction on Android 13+ when Liquid Glass full tier is active.
 * Automatically becomes a no-op on API 26-32 or when Reduce Transparency is enabled.
 */
fun Modifier.liquidGlassEdgeRefraction(
  enabled: Boolean,
  cornerRadius: Dp,
  isDark: Boolean,
  bezelWidth: Dp = 7.dp,
  refractionStrength: Dp = 1.4.dp,
  saturationBoost: Float = 1.08f,
): Modifier = composed {
  if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
    return@composed this
  }
  val shader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }
  this.graphicsLayer {
    renderEffect =
      buildLiquidGlassRenderEffect(
        shader = shader,
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
