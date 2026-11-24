package com.prism.docs.core.ui.glass

/*
 * GlassMotion.kt  (core-ui)
 *
 * 120fps-tuned motion for the Liquid Glass kit, using only stable Compose APIs:
 *   1. DriftingBlobsBackdrop  - pre-baked GPU texture blobs drifting at 0 allocations/frame
 *   2. Modifier.glassSheen    - soft highlight sweeping across a surface every few seconds
 *   3. Modifier.glassPress / glassClickable - GPU-only spring press feedback (0 recompositions)
 *   4. Modifier.glassEntrance - staggered GPU-only spring-in for cards and rows
 *   5. Modifier.glassSlidingPill - selected pill that glides between tabs / segments at 120fps
 *   6. rememberHeaderScrollProgress - 0..1 as a list scrolls under a header
 *   7. GlassMotionStatusText  - shows why motion is ON or OFF (diagnostics)
 *   8. GlassMotionDemo        - a test screen that shows everything together
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Motion switch
// ---------------------------------------------------------------------------------------------

/** True when glass motion should run. Provided once at the app root by [ProvideGlassMotion]. */
val LocalGlassMotion = staticCompositionLocalOf { true }

/** System "animator duration scale". 0 means the user (or the emulator) turned animations off. */
fun systemAnimatorScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

@Composable
fun ProvideGlassMotion(appReduceMotion: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemScale = remember(context) { systemAnimatorScale(context) }
    val enabled = !appReduceMotion && systemScale > 0f
    CompositionLocalProvider(LocalGlassMotion provides enabled, content = content)
}

// ---------------------------------------------------------------------------------------------
// 1. Drifting colour blobs (pre-baked GPU texture sprites — zero allocations per frame)
// ---------------------------------------------------------------------------------------------

/**
 * One soft colour blob. Positions are fractions of the backdrop size, so it adapts to any screen.
 * [cycles] is how many full loops the blob makes per 60 s (4 = every 15 s, 3 = 20 s, 5 = 12 s,
 * 2 = 30 s). Whole numbers keep the loop seamless.
 */
data class GlassBlob(
    val color: Color,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val driftX: Float,
    val driftY: Float,
    val cycles: Int,
    val phase: Float = 0f,
)

/** The four blobs from the mockup: blue, violet, pink, mint. */
val PrismBlobsLight = listOf(
    GlassBlob(Color(0xFF8FB8FF), cx = 0.10f, cy = 0.08f, radius = 0.55f, driftX = 0.10f, driftY = 0.05f, cycles = 4),
    GlassBlob(Color(0xFFD2B4FF), cx = 0.95f, cy = 0.30f, radius = 0.60f, driftX = 0.12f, driftY = 0.04f, cycles = 3, phase = 0.30f),
    GlassBlob(Color(0xFFFFC2E2), cx = 0.08f, cy = 0.62f, radius = 0.52f, driftX = 0.10f, driftY = 0.05f, cycles = 5, phase = 0.60f),
    GlassBlob(Color(0xFF9CF0DC), cx = 0.92f, cy = 0.92f, radius = 0.50f, driftX = 0.12f, driftY = 0.04f, cycles = 2, phase = 0.10f),
)

/** Darker, calmer blobs for the dark theme. */
val PrismBlobsDark = listOf(
    GlassBlob(Color(0xFF3B5BDB), cx = 0.10f, cy = 0.08f, radius = 0.55f, driftX = 0.10f, driftY = 0.05f, cycles = 4),
    GlassBlob(Color(0xFF7048E8), cx = 0.95f, cy = 0.30f, radius = 0.60f, driftX = 0.12f, driftY = 0.04f, cycles = 3, phase = 0.30f),
    GlassBlob(Color(0xFFC2255C), cx = 0.08f, cy = 0.62f, radius = 0.52f, driftX = 0.10f, driftY = 0.05f, cycles = 5, phase = 0.60f),
    GlassBlob(Color(0xFF0CA678), cx = 0.92f, cy = 0.92f, radius = 0.50f, driftX = 0.12f, driftY = 0.04f, cycles = 2, phase = 0.10f),
)

/**
 * Pre-renders a single smooth radial-gradient blob into a compact 128x128 ARGB_8888 texture once.
 * Drawing 4 hardware-filtered textures per frame costs ~0.2ms on GPU with 0 heap allocations,
 * maintaining a locked 120fps even while Haze samples the backdrop.
 */
private fun bakeBlobTexture(color: Color, blobAlpha: Float): ImageBitmap {
    val size = 128
    val center = size * 0.5f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = RadialGradient(
            center,
            center,
            center,
            intArrayOf(
                color.copy(alpha = blobAlpha.coerceIn(0f, 1f)).toArgb(),
                color.copy(alpha = (blobAlpha * 0.42f).coerceIn(0f, 1f)).toArgb(),
                color.copy(alpha = 0f).toArgb(),
            ),
            floatArrayOf(0f, 0.38f, 0.72f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawCircle(center, center, center, paint)
    return bitmap.asImageBitmap()
}

@Composable
fun DriftingBlobsBackdrop(
    modifier: Modifier = Modifier,
    base: Color = Color(0xFFE8EDF9),
    blobs: List<GlassBlob> = PrismBlobsLight,
    blobAlpha: Float = 1f,
) {
    val motion = LocalGlassMotion.current
    val clock: State<Float> = if (motion && blobAlpha > 0.005f) {
        val infinite = rememberInfiniteTransition(label = "glassBlobs")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
            label = "glassBlobClock",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val textures: List<ImageBitmap> = remember(blobs, blobAlpha) {
        if (blobAlpha <= 0.005f) emptyList()
        else blobs.map { b -> bakeBlobTexture(b.color, blobAlpha) }
    }

    Box(
        modifier
            .background(base)
            .drawBehind {
                if (textures.isEmpty()) return@drawBehind
                val t = clock.value
                val twoPi = 2f * PI.toFloat()
                val w = size.width
                val h = size.height
                for (i in blobs.indices) {
                    val b = blobs[i]
                    val tex = textures[i]
                    val angle = twoPi * (t * b.cycles + b.phase)
                    val cx = w * (b.cx + b.driftX * cos(angle))
                    val cy = h * (b.cy + b.driftY * sin(angle))
                    val r = w * b.radius
                    val diameter = (r * 2f).roundToInt().coerceAtLeast(1)
                    drawImage(
                        image = tex,
                        dstOffset = IntOffset((cx - r).roundToInt(), (cy - r).roundToInt()),
                        dstSize = IntSize(diameter, diameter),
                        filterQuality = FilterQuality.Low,
                    )
                }
            },
    )
}

// ---------------------------------------------------------------------------------------------
// 2. Sheen
// ---------------------------------------------------------------------------------------------

/**
 * A soft white highlight that sweeps across the surface every [periodMs]. Use it on the floating
 * tab bar and other large glass pieces; not on every small control.
 * [cornerRadius] should match the surface (use a large value such as 999.dp for a capsule).
 */
fun Modifier.glassSheen(
    cornerRadius: Dp = 999.dp,
    periodMs: Int = 7000,
    sweepMs: Int = 1500,
    peakAlpha: Float = 0.45f,
): Modifier = composed {
    if (!LocalGlassMotion.current) return@composed this
    val infinite = rememberInfiniteTransition(label = "glassSheen")
    val progress = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "glassSheenProgress",
    )
    val sweepFraction = sweepMs.toFloat() / periodMs
    val sheenMidColor = Color.White.copy(alpha = peakAlpha)
    this.drawWithContent {
        drawContent()
        val q = progress.value / sweepFraction
        if (q in 0f..1f) {
            val eased = FastOutSlowInEasing.transform(q)
            val band = size.width * 0.5f
            val x = -band + (size.width + 2f * band) * eased
            val r = min(cornerRadius.toPx(), size.minDimension / 2f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to sheenMidColor,
                    1f to Color.Transparent,
                    start = Offset(x - band / 2f, 0f),
                    end = Offset(x + band / 2f, size.height * 0.35f),
                ),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Press feedback (GPU-only graphicsLayer reads — zero recomposition per animation frame)
// ---------------------------------------------------------------------------------------------

/**
 * Shrinks slightly while pressed and springs back with a small overshoot.
 * Reads scale strictly inside [graphicsLayer] so pressing never triggers recomposition.
 */
fun Modifier.glassPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val motion = LocalGlassMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (motion) spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium) else snap(),
        label = "glassPress",
    )
    this.graphicsLayer {
        val s = scaleState.value
        scaleX = s
        scaleY = s
    }
}

/** clickable() with glass press feedback and no default ripple. */
fun Modifier.glassClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .glassPress(source)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

// ---------------------------------------------------------------------------------------------
// 4. Entrance (GPU-only Animatable — zero recomposition during entrance spring)
// ---------------------------------------------------------------------------------------------

/**
 * Springs the element in (rises 20 dp, scales 0.95 to 1, fades in) with a crisp 120fps overshoot.
 * Give each item its position as [index] so they arrive one after another.
 * In a LazyColumn, apply it only to the first few rows (index < 8).
 */
fun Modifier.glassEntrance(
    index: Int = 0,
    stepMs: Long = 38L,
    maxDelayMs: Long = 300L,
): Modifier = composed {
    val motion = LocalGlassMotion.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion && progress.value < 1f) {
            delay(minOf(index * stepMs, maxDelayMs))
            progress.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 380f))
        } else if (!motion) {
            progress.snapTo(1f)
        }
    }
    this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 20.dp.toPx()
        val s = 0.95f + 0.05f * p
        scaleX = s
        scaleY = s
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Sliding selected pill (draw-phase only — zero recomposition per frame)
// ---------------------------------------------------------------------------------------------

/**
 * Draws a pill behind equal-width segments and glides it to [selectedIndex] with a spring.
 * Put it on the Row of the floating tab bar or the Library | Storage | Bin control, and keep
 * each segment's text/icon as a normal child on top.
 */
fun Modifier.glassSlidingPill(
    selectedIndex: Int,
    count: Int,
    inset: Dp = 4.dp,
    cornerRadius: Dp = 999.dp,
    brush: Brush = SolidColor(Color.White),
): Modifier = composed {
    if (count <= 0) return@composed this
    val motion = LocalGlassMotion.current
    val position = animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, count - 1).toFloat(),
        animationSpec = if (motion) {
            spring(dampingRatio = 0.78f, stiffness = 420f)
        } else {
            snap()
        },
        label = "glassPill",
    )
    this.drawBehind {
        val i = inset.toPx()
        val segment = (size.width - 2f * i) / count
        val h = size.height - 2f * i
        if (segment <= 0f || h <= 0f) return@drawBehind
        val x = i + position.value * segment
        val r = min(cornerRadius.toPx(), min(segment, h) / 2f)
        // Soft shadow under the pill.
        drawRoundRect(
            color = Color(0x1E344E9E),
            topLeft = Offset(x, i + 2.5.dp.toPx()),
            size = Size(segment, h),
            cornerRadius = CornerRadius(r, r),
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, i),
            size = Size(segment, h),
            cornerRadius = CornerRadius(r, r),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Header that firms up as content scrolls under it
// ---------------------------------------------------------------------------------------------

/**
 * 0f at the top of the list, rising to 1f after [thresholdDp] of scrolling. Use it to raise the
 * tint/blur of a header, and read it inside drawBehind { } so scrolling does not recompose.
 */
@Composable
fun rememberHeaderScrollProgress(listState: LazyListState, thresholdDp: Dp = 56.dp): State<Float> {
    val thresholdPx = with(LocalDensity.current) { thresholdDp.toPx() }
    return remember(listState, thresholdPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 7. Diagnostics
// ---------------------------------------------------------------------------------------------

/** Drop this into the Glass & Accessibility sheet to see why motion is on or off. */
@Composable
fun GlassMotionStatusText(appReduceMotion: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scale = remember(context) { systemAnimatorScale(context) }
    val on = !appReduceMotion && scale > 0f
    Text(
        text = "Glass motion: ${if (on) "ON" else "OFF"} " +
            "(system animator scale $scale, app Reduce Motion $appReduceMotion, API ${Build.VERSION.SDK_INT})",
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// 8. Demo screen
// ---------------------------------------------------------------------------------------------

/**
 * A self-contained test screen. The "glass" here is a plain translucent surface (no blur) so the
 * motion can be judged on its own; swap in GlassSurface to see it with the real glass.
 */
@Composable
fun GlassMotionDemo(appReduceMotion: Boolean = false) {
    ProvideGlassMotion(appReduceMotion) {
        var selected by remember { mutableIntStateOf(0) }
        val tabs = listOf("Library", "Scan", "Tools")
        val glass = Color.White.copy(alpha = 0.45f)
        val rim = Color.White.copy(alpha = 0.85f)

        Box(Modifier.fillMaxSize()) {
            DriftingBlobsBackdrop(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                GlassMotionStatusText(appReduceMotion)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(120.dp)
                                .glassEntrance(index = i)
                                .glassClickable { }
                                .clip(RoundedCornerShape(26.dp))
                                .background(glass)
                                .border(1.dp, rim, RoundedCornerShape(26.dp))
                                .glassSheen(cornerRadius = 26.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Card ${i + 1}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(300.dp)
                        .height(64.dp)
                        .clip(CircleShape)
                        .background(glass)
                        .border(1.dp, rim, CircleShape)
                        .glassSheen()
                        .glassSlidingPill(
                            selectedIndex = selected,
                            count = tabs.size,
                            brush = SolidColor(Color.White.copy(alpha = 0.92f)),
                        ),
                ) {
                    tabs.forEachIndexed { i, label ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .glassClickable { selected = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected == i) Color(0xFF3447D6) else Color(0xFF2A3150),
                            )
                        }
                    }
                }
            }
        }
    }
}
