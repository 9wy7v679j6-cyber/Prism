package com.prism.docs.core.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.abs

/**
 * Debug-only bisect toggles for isolating Liquid Glass rendering passes (strictly exposed only
 * when `BuildConfig.DEBUG` is true).
 */
@Immutable
data class GlassDebugConfig(
  val enableHaze: Boolean = true,
  val enableShadow: Boolean = true,
  val enableEdgeRefraction: Boolean = true,
  val enableSheen: Boolean = true,
)

val LocalGlassDebugConfig = compositionLocalOf { GlassDebugConfig() }

/**
 * Prevents nested `hazeEffect` or nested backdrop refraction inside floating glass surfaces
 * (sheets, top bars, tab bars, selection bars, context menus).
 */
val LocalInsideFloatingGlass = compositionLocalOf { false }

/**
 * Thread-local depth guard ensuring Compose [GraphicsLayer.record] calls NEVER nest inside each
 * other and [drawLayer] is NEVER invoked while any backdrop layer is actively recording on the
 * UI thread (preventing HWUI RenderNode cycle crashes on Android 13–16).
 */
internal object GlassBackdropRecordingGuard {
  private val depth = ThreadLocal<Int>()

  fun isRecording(): Boolean = (depth.get() ?: 0) > 0

  inline fun <T> withRecording(block: () -> T): T {
    val prev = depth.get() ?: 0
    depth.set(prev + 1)
    return try {
      block()
    } finally {
      if (prev <= 0) depth.remove() else depth.set(prev)
    }
  }
}

/**
 * Mutable draw-phase motion dynamics updated by scroll/sheet gestures and read strictly inside
 * `drawWithContent` / `drawBehind` / `graphicsLayer` lambdas (zero recompositions per frame).
 */
@Stable
class GlassDynamicsState {
  /** Normalized vertical scroll/fling energy in 0f..1f; boosts bar dispersion and rim brightness. */
  var scrollEnergy by mutableFloatStateOf(0f)

  /** True during high-velocity vertical flings; pauses drifting blobs and sheen sweeps. */
  var isFastFlinging by mutableStateOf(false)

  /** Normalized bottom-sheet drag energy in 0f..1f; intensifies live sheet backdrop refraction. */
  var sheetDragEnergy by mutableFloatStateOf(0f)
}

val LocalGlassDynamics = staticCompositionLocalOf { GlassDynamicsState() }

/**
 * Holds captured Compose [GraphicsLayer] backdrops so floating glass surfaces (top bar, tab bar,
 * sheets) can genuinely sample, refract, chromatically disperse, and magnify pixels behind them
 * on API 33+ without nested or cyclic RenderNode recordings.
 *
 * Hierarchy is strictly acyclic and flat (leaf/sibling layers only):
 * - [blobsLayer]: captures [DriftingBlobsBackdrop] alone (leaf node, zero children).
 * - [contentLayer]: captures the active screen's scrolling list subtree (sibling to screen top bars
 *   AND sibling to root tab bar, navigation rail, and bottom sheets).
 *
 * All draw-phase flags are plain `@Volatile` fields (never Compose `mutableStateOf`) so mutating
 * them inside `drawWithContent` never violates snapshot consistency.
 */
@Stable
class GlassBackdropState(
  val blobsLayer: GraphicsLayer? = null,
  val contentLayer: GraphicsLayer? = null,
  internal val parent: GlassBackdropState? = null,
) {
  @Volatile var blobsWindowOffset: Offset = Offset.Zero
  @Volatile var contentWindowOffset: Offset = Offset.Zero
  @Volatile var hasRecordedBlobs: Boolean = false
  @Volatile var hasRecordedContent: Boolean = false
  @Volatile internal var activeContentState: GlassBackdropState? = null

  fun resolvedBlobsSource(): GlassBackdropState? =
    when {
      blobsLayer != null && hasRecordedBlobs && !blobsLayer.isReleased -> this
      parent != null -> parent.resolvedBlobsSource()
      else -> null
    }

  fun resolvedContentSource(): GlassBackdropState? =
    when {
      contentLayer != null && hasRecordedContent && !contentLayer.isReleased -> this
      activeContentState != null -> activeContentState?.takeIf {
        it.contentLayer != null && it.hasRecordedContent && !it.contentLayer.isReleased
      }
      parent != null -> parent.resolvedContentSource()
      else -> null
    }
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropState?> { null }

@Composable
fun rememberRootGlassBackdropState(): GlassBackdropState {
  val blobsLayer = rememberGraphicsLayer()
  return remember(blobsLayer) {
    GlassBackdropState(blobsLayer = blobsLayer)
  }
}

@Composable
fun rememberScreenGlassBackdropState(): GlassBackdropState {
  val parent = LocalGlassBackdrop.current
  val contentLayer = rememberGraphicsLayer()
  val state =
    remember(parent, contentLayer) {
      GlassBackdropState(
        blobsLayer = parent?.blobsLayer,
        contentLayer = contentLayer,
        parent = parent,
      )
    }
  DisposableEffect(parent, state) {
    parent?.activeContentState = state
    onDispose {
      state.hasRecordedContent = false
      if (parent?.activeContentState === state) {
        parent.activeContentState = null
      }
    }
  }
  return state
}

/** Captures the ambient drifting-blobs leaf layer for all floating glass surfaces on API 33+. */
fun Modifier.glassBlobsCaptureSource(state: GlassBackdropState?): Modifier {
  val layer = state?.blobsLayer ?: return this
  return this
    .onGloballyPositioned { coords ->
      val pos = coords.positionInWindow()
      if (pos.x.isFinite() && pos.y.isFinite()) {
        state.blobsWindowOffset = pos
      }
    }
    .drawWithContent {
      if (
        GlassBackdropRecordingGuard.isRecording() ||
          layer.isReleased ||
          size.width < 2f ||
          size.height < 2f ||
          !size.width.isFinite() ||
          !size.height.isFinite()
      ) {
        drawContent()
        return@drawWithContent
      }
      val recorded =
        runCatching {
          GlassBackdropRecordingGuard.withRecording {
            layer.record { this@drawWithContent.drawContent() }
          }
          true
        }.getOrDefault(false)
      if (recorded && !layer.isReleased) {
        state.hasRecordedBlobs = true
        runCatching { drawLayer(layer) }.onFailure { drawContent() }
      } else {
        drawContent()
      }
    }
}

/**
 * Retained as a safe no-op for source compatibility; never wrap NavHost in a root scene layer
 * because nested/self-recursive GraphicsLayer.record crashes HWUI on Android 13–16.
 */
fun Modifier.glassSceneCaptureSource(state: GlassBackdropState?): Modifier = this

/** Captures a screen's scrolling content subtree (sibling to screen top bars and root overlays). */
fun Modifier.glassContentCaptureSource(state: GlassBackdropState?): Modifier {
  val layer = state?.contentLayer ?: return this
  return this
    .onGloballyPositioned { coords ->
      val pos = coords.positionInWindow()
      if (pos.x.isFinite() && pos.y.isFinite()) {
        state.contentWindowOffset = pos
      }
    }
    .drawWithContent {
      if (
        GlassBackdropRecordingGuard.isRecording() ||
          layer.isReleased ||
          size.width < 2f ||
          size.height < 2f ||
          !size.width.isFinite() ||
          !size.height.isFinite()
      ) {
        drawContent()
        return@drawWithContent
      }
      val recorded =
        runCatching {
          GlassBackdropRecordingGuard.withRecording {
            layer.record { this@drawWithContent.drawContent() }
          }
          true
        }.getOrDefault(false)
      if (recorded && !layer.isReleased) {
        state.hasRecordedContent = true
        runCatching { drawLayer(layer) }.onFailure { drawContent() }
      } else {
        drawContent()
      }
    }
}

/**
 * Real AGSL backdrop-sampling Liquid Glass shader (Android 13+ / API 33+).
 *
 * Uses strict float3/float math (no half/float mixed clamp overloads, no pow(0.0, ...) NaN traps)
 * for 100% compatibility across SkSL compilers on API 33–36 (Adreno, Mali, PowerVR, SwiftShader).
 */
private const val LIQUID_BACKDROP_REFRACTION_AGSL = """
uniform shader backdrop;
uniform float2 resolution;
uniform float cornerRadius;
uniform float bezelWidth;
uniform float refractionStrength;
uniform float dispersionStrength;
uniform float vibrancy;
uniform float rimBrightness;
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
    float t = 1.0 - smoothstep(0.0, safeBezel, edgeDist);
    float t2 = t * t;
    float t3 = t2 * t;
    float lensCurve = t2 * (3.0 - 2.0 * t);

    float2 eps = float2(1.5, 0.0);
    float nx = sdRoundedBox(p + eps.xy, halfRes, r) - sdRoundedBox(p - eps.xy, halfRes, r);
    float ny = sdRoundedBox(p + eps.yx, halfRes, r) - sdRoundedBox(p - eps.yx, halfRes, r);
    float2 grad = float2(nx, ny);
    float gradLen = length(grad);
    float2 normal = gradLen > 0.0001 ? (grad / gradLen) : float2(0.0, -1.0);

    float2 centerShift = p * (-0.018 * clamp(refractionStrength / 10.0, 0.0, 1.0));
    float2 bezelShift = normal * (lensCurve * refractionStrength);
    float2 totalOffset = bezelShift + centerShift;

    float2 chromaDir = normal * (lensCurve * dispersionStrength);
    float2 minUv = float2(0.5);
    float2 maxUv = max(safeRes - float2(0.5), float2(1.0));

    float2 uvG = clamp(fragCoord - totalOffset, minUv, maxUv);
    float2 uvR = clamp(fragCoord - totalOffset - chromaDir, minUv, maxUv);
    float2 uvB = clamp(fragCoord - totalOffset + chromaDir, minUv, maxUv);

    float4 gSample = float4(backdrop.eval(uvG));
    float rChan = float(backdrop.eval(uvR).r);
    float bChan = float(backdrop.eval(uvB).b);
    float alpha = max(gSample.a, 0.001);

    float3 rgb = clamp(float3(rChan, gSample.g, bChan) / alpha, float3(0.0), float3(1.0));

    float luma = dot(rgb, float3(0.299, 0.587, 0.114));
    rgb = clamp(mix(float3(luma), rgb, vibrancy), float3(0.0), float3(1.0));

    float topLight = clamp(-normal.y * 0.78 - normal.x * 0.28, 0.0, 1.0);
    float bottomBounce = clamp(normal.y * 0.45, 0.0, 1.0);
    float rim = t2 * (0.10 + 0.34 * topLight + 0.10 * bottomBounce) * rimBrightness;
    float3 rimColor = isDark > 0.5 ? float3(0.84, 0.92, 1.0) : float3(1.0, 1.0, 1.0);

    float3 caustic = float3(
        t3 * dispersionStrength * 0.035,
        t3 * dispersionStrength * 0.015,
        t3 * dispersionStrength * 0.045
    );

    float3 finalRgb = clamp(rgb + rimColor * rim + caustic, float3(0.0), float3(1.0));
    return half4(finalRgb, 1.0);
}
"""

/**
 * Reusable cached AGSL backdrop refraction + optional blur [RenderEffect] builder.
 * Allocates ONE [RuntimeShader] instance lazily and guards all GPU calls so malformed sizes
 * or driver shader quirks gracefully degrade instead of crashing.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class CachedBackdropRefractionEffect {
  private val shader: RuntimeShader? = runCatching { RuntimeShader(LIQUID_BACKDROP_REFRACTION_AGSL) }.getOrNull()
  private var lastW = -1f
  private var lastH = -1f
  private var lastCorner = -1f
  private var lastBezel = -1f
  private var lastRefraction = -1f
  private var lastDispersion = -1f
  private var lastVibrancy = -1f
  private var lastRim = -1f
  private var lastBlurPx = -1f
  private var lastDark = false
  private var lastApplyShader = false
  private var cached: androidx.compose.ui.graphics.RenderEffect? = null

  fun obtain(
    widthPx: Float,
    heightPx: Float,
    cornerRadiusPx: Float,
    bezelWidthPx: Float,
    refractionStrengthPx: Float,
    dispersionPx: Float,
    vibrancy: Float,
    rimBrightness: Float,
    blurRadiusPx: Float,
    isDark: Boolean,
    applyRefractionShader: Boolean,
  ): androidx.compose.ui.graphics.RenderEffect? {
    if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx < 4f || heightPx < 4f) return null
    val safeBlur = if (blurRadiusPx.isFinite()) blurRadiusPx.coerceIn(0f, 150f) else 0f
    val qBlur = if (safeBlur < 0.75f) 0f else (safeBlur * 2f).toInt() / 2f
    val qRefract = if (refractionStrengthPx.isFinite()) (refractionStrengthPx.coerceIn(0f, 64f) * 4f).toInt() / 4f else 0f
    val qDisp = if (dispersionPx.isFinite()) (dispersionPx.coerceIn(0f, 24f) * 4f).toInt() / 4f else 0f
    val qRim = if (rimBrightness.isFinite()) (rimBrightness.coerceIn(0f, 4f) * 20f).toInt() / 20f else 1f
    val safeCorner = if (cornerRadiusPx.isFinite()) cornerRadiusPx.coerceAtLeast(0f) else 0f
    val safeBezel = if (bezelWidthPx.isFinite()) bezelWidthPx.coerceAtLeast(1f) else 8f
    val safeVib = if (vibrancy.isFinite()) vibrancy.coerceIn(0.5f, 2.0f) else 1f

    val existing = cached
    if (
      existing != null &&
        abs(lastW - widthPx) < 0.5f &&
        abs(lastH - heightPx) < 0.5f &&
        abs(lastCorner - safeCorner) < 0.5f &&
        abs(lastBezel - safeBezel) < 0.5f &&
        lastRefraction == qRefract &&
        lastDispersion == qDisp &&
        lastVibrancy == safeVib &&
        lastRim == qRim &&
        lastBlurPx == qBlur &&
        lastDark == isDark &&
        lastApplyShader == applyRefractionShader
    ) {
      return existing
    }

    lastW = widthPx
    lastH = heightPx
    lastCorner = safeCorner
    lastBezel = safeBezel
    lastRefraction = qRefract
    lastDispersion = qDisp
    lastVibrancy = safeVib
    lastRim = qRim
    lastBlurPx = qBlur
    lastDark = isDark
    lastApplyShader = applyRefractionShader

    return runCatching {
      val blurEffect =
        if (qBlur > 0.5f) {
          RenderEffect.createBlurEffect(qBlur, qBlur, Shader.TileMode.CLAMP)
        } else {
          null
        }

      val activeShader = if (applyRefractionShader) shader else null
      if (activeShader == null) {
        return@runCatching blurEffect?.asComposeRenderEffect()
      }

      activeShader.setFloatUniform("resolution", widthPx, heightPx)
      activeShader.setFloatUniform("cornerRadius", safeCorner)
      activeShader.setFloatUniform("bezelWidth", safeBezel)
      activeShader.setFloatUniform("refractionStrength", qRefract)
      activeShader.setFloatUniform("dispersionStrength", qDisp)
      activeShader.setFloatUniform("vibrancy", safeVib)
      activeShader.setFloatUniform("rimBrightness", qRim)
      activeShader.setFloatUniform("isDark", if (isDark) 1f else 0f)

      val shaderEffect = RenderEffect.createRuntimeShaderEffect(activeShader, "backdrop")
      val combined =
        if (blurEffect != null) {
          RenderEffect.createChainEffect(shaderEffect, blurEffect)
        } else {
          shaderEffect
        }
      combined.asComposeRenderEffect()
    }.getOrNull().also { cached = it }
  }
}

/**
 * AGSL optical magnifying capsule lens shader (API 33+) used by the sliding tab bar lens pill
 * and the Liquid Glass clarity slider thumb to genuinely magnify & refract underlying pixels.
 */
private const val LIQUID_LENS_MAGNIFY_AGSL = """
uniform shader content;
uniform float2 resolution;
uniform float2 lensCenter;
uniform float2 lensHalfSize;
uniform float lensRadius;
uniform float magnifyStrength;
uniform float dispersion;

float sdRoundedBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

half4 main(float2 fragCoord) {
    float2 safeRes = max(resolution, float2(2.0));
    float2 safeHalf = max(lensHalfSize, float2(1.0));
    float2 p = fragCoord - lensCenter;
    float r = clamp(lensRadius, 0.0, min(safeHalf.x, safeHalf.y));
    float d = sdRoundedBox(p, safeHalf, r);
    if (d > 0.0) {
        return content.eval(fragCoord);
    }

    float rimWidth = max(r * 0.75, 1.0);
    float edgeDist = clamp(-d, 0.0, rimWidth);
    float rimT = 1.0 - smoothstep(0.0, rimWidth, edgeDist);
    float rimT2 = rimT * rimT;
    float2 normP = p / safeHalf;

    float2 warp = p * (-magnifyStrength * (0.14 + 0.16 * rimT2));
    float2 chroma = normP * (rimT2 * dispersion);

    float2 minUv = float2(0.5);
    float2 maxUv = max(safeRes - float2(0.5), float2(1.0));
    float2 uvG = clamp(fragCoord + warp, minUv, maxUv);
    float2 uvR = clamp(fragCoord + warp - chroma, minUv, maxUv);
    float2 uvB = clamp(fragCoord + warp + chroma, minUv, maxUv);

    float4 g = float4(content.eval(uvG));
    float rC = float(content.eval(uvR).r);
    float bC = float(content.eval(uvB).b);
    return half4(rC, g.g, bC, g.a);
}
"""

/**
 * Reusable cached AGSL magnifying lens [RenderEffect] for sliding glass pills & slider thumbs.
 * Single [RuntimeShader] instance per widget; zero per-frame shader allocations.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class CachedLensMagnifyEffect {
  private val shader: RuntimeShader? = runCatching { RuntimeShader(LIQUID_LENS_MAGNIFY_AGSL) }.getOrNull()
  private var lastW = -1f
  private var lastH = -1f
  private var lastCx = -1f
  private var lastCy = -1f
  private var lastHx = -1f
  private var lastHy = -1f
  private var lastRadius = -1f
  private var lastMag = -1f
  private var lastDispersion = -1f
  private var cached: androidx.compose.ui.graphics.RenderEffect? = null

  fun obtain(
    widthPx: Float,
    heightPx: Float,
    centerX: Float,
    centerY: Float,
    halfW: Float,
    halfH: Float,
    radius: Float,
    magnifyStrength: Float,
    dispersionPx: Float,
  ): androidx.compose.ui.graphics.RenderEffect? {
    val activeShader = shader ?: return null
    if (
      !widthPx.isFinite() ||
        !heightPx.isFinite() ||
        !centerX.isFinite() ||
        !centerY.isFinite() ||
        !halfW.isFinite() ||
        !halfH.isFinite() ||
        widthPx < 4f ||
        heightPx < 4f ||
        halfW < 2f ||
        halfH < 2f
    ) {
      return null
    }
    val safeRadius = if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f
    val safeMag = if (magnifyStrength.isFinite()) magnifyStrength.coerceIn(0f, 3f) else 0.5f
    val safeDisp = if (dispersionPx.isFinite()) dispersionPx.coerceIn(0f, 24f) else 0f

    val existing = cached
    if (
      existing != null &&
        abs(lastW - widthPx) < 0.5f &&
        abs(lastH - heightPx) < 0.5f &&
        abs(lastCx - centerX) < 0.4f &&
        abs(lastCy - centerY) < 0.4f &&
        abs(lastHx - halfW) < 0.4f &&
        abs(lastHy - halfH) < 0.4f &&
        abs(lastRadius - safeRadius) < 0.4f &&
        abs(lastMag - safeMag) < 0.02f &&
        abs(lastDispersion - safeDisp) < 0.08f
    ) {
      return existing
    }
    lastW = widthPx
    lastH = heightPx
    lastCx = centerX
    lastCy = centerY
    lastHx = halfW
    lastHy = halfH
    lastRadius = safeRadius
    lastMag = safeMag
    lastDispersion = safeDisp

    return runCatching {
      activeShader.setFloatUniform("resolution", widthPx, heightPx)
      activeShader.setFloatUniform("lensCenter", centerX, centerY)
      activeShader.setFloatUniform("lensHalfSize", halfW, halfH)
      activeShader.setFloatUniform("lensRadius", safeRadius)
      activeShader.setFloatUniform("magnifyStrength", safeMag)
      activeShader.setFloatUniform("dispersion", safeDisp)
      RenderEffect.createRuntimeShaderEffect(activeShader, "content").asComposeRenderEffect()
    }.getOrNull().also { cached = it }
  }
}
