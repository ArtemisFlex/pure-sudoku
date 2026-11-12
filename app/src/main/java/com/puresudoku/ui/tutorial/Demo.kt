package com.puresudoku.ui.tutorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.puresudoku.ui.theme.LocalBoardColors
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Named on-screen spots inside a tutorial demo. Real game components tag their
 * cells/buttons with [demoTarget] so a script can "tap" them with the finger.
 */
class DemoTargets {
    internal val map = HashMap<String, LayoutCoordinates>()
}

/** The demo's targets, for components deep inside a demo (buttons tag themselves by their label). */
val LocalDemoTargets = androidx.compose.runtime.staticCompositionLocalOf<DemoTargets?> { null }

fun Modifier.demoTarget(targets: DemoTargets?, key: String): Modifier =
    if (targets == null) this else this.onGloballyPositioned { targets.map[key] = it }

/** Commands available to a tutorial script. */
class DemoScope internal constructor(
    private val targets: DemoTargets,
    private val stage: () -> LayoutCoordinates?,
    private val finger: Finger,
    private val setHighlights: (List<Rect>) -> Unit,
) {
    fun rectOf(key: String): Rect? {
        val s = stage() ?: return null
        val t = targets.map[key] ?: return null
        if (!s.isAttached || !t.isAttached) return null
        return s.localBoundingBoxOf(t, clipBounds = false)
    }

    suspend fun wait(ms: Long) = delay(ms)

    /** Moves the finger to [key] and taps; [action] runs at the moment of the tap. */
    suspend fun tap(key: String, action: () -> Unit = {}) {
        val r = rectOf(key) ?: run { action(); return }
        finger.moveTo(r.center)
        finger.press()
        action()
        finger.release()
        delay(220)
    }

    /** Drags the finger from one target to another; [onMove] gets progress 0..1. */
    suspend fun drag(fromKey: String, toKey: String, durationMs: Int = 650, onMove: (Float) -> Unit = {}, action: () -> Unit = {}) {
        val a = rectOf(fromKey)?.center ?: return
        val b = rectOf(toKey)?.center ?: return
        dragPoints(a, b, durationMs, onMove, action)
    }

    suspend fun dragPoints(a: Offset, b: Offset, durationMs: Int = 650, onMove: (Float) -> Unit = {}, action: () -> Unit = {}) {
        finger.moveTo(a)
        finger.press()
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val f = ((now - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
            val e = FastOutSlowInEasing.transform(f)
            finger.jump(a + (b - a) * e)
            onMove(e)
            if (f >= 1f) break
        }
        action()
        finger.release()
        delay(220)
    }

    /** Outlines the given targets (their combined bounds) for [ms]. */
    suspend fun highlight(keys: List<String>, ms: Long) {
        val rects = keys.mapNotNull { rectOf(it) }
        if (rects.isEmpty()) { delay(ms); return }
        val union = rects.reduce { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
        setHighlights(listOf(union))
        delay(ms)
        setHighlights(emptyList())
    }

    suspend fun hideFinger() = finger.hide()
}

internal class Finger {
    lateinit var scope: CoroutineScope
    val pos = Animatable(Offset.Zero, Offset.VectorConverter)
    val alpha = Animatable(0f)
    val scale = Animatable(1f)
    val ripple = Animatable(0f)
    private var shown = false

    suspend fun moveTo(p: Offset) = coroutineScope {
        if (!shown) {
            pos.snapTo(p + Offset(60f, 90f))
            shown = true
        }
        launch { alpha.animateTo(1f, tween(200)) }
        pos.animateTo(p, tween(420, easing = FastOutSlowInEasing))
    }

    suspend fun jump(p: Offset) = pos.snapTo(p)

    suspend fun press() {
        scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(450, easing = LinearEasing)) }
        scale.animateTo(0.78f, tween(110))
    }

    suspend fun release() { scale.animateTo(1f, tween(140)) }

    suspend fun hide() {
        alpha.animateTo(0f, tween(250))
        shown = false
    }
}

/**
 * Plays [script] in a loop while [active], over [content] — the real game UI.
 * The script should reset its game state at the start of each loop.
 */
@Composable
fun DemoStage(
    active: Boolean,
    modifier: Modifier = Modifier,
    script: suspend DemoScope.() -> Unit,
    content: @Composable (DemoTargets) -> Unit,
) {
    val targets = remember { DemoTargets() }
    val finger = remember { Finger() }
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var highlights by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(active) {
        finger.scope = this
        if (!active) { finger.hide(); highlights = emptyList(); return@LaunchedEffect }
        val scope = DemoScope(targets, { stageCoords }, finger) { highlights = it }
        while (true) {
            delay(450)
            scope.script()
            finger.hide()
            delay(900)
        }
    }
    LaunchedEffect(highlights) {
        if (highlights.isNotEmpty()) {
            pulse.snapTo(0f)
            while (true) {
                pulse.animateTo(1f, tween(500)); pulse.animateTo(0f, tween(500))
            }
        }
    }

    val accent = LocalBoardColors.current.entered
    Box(modifier.onGloballyPositioned { stageCoords = it }) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDemoTargets provides targets) { content(targets) }
        // a demo is watched, not played: this layer takes every touch so none reach the game below it,
        // while swipes still reach the card deck around it
        Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        Canvas(Modifier.fillMaxSize()) {
            for (r in highlights) {
                val pad = 3.dp.toPx()
                drawRoundRect(
                    accent.copy(alpha = 0.55f + 0.45f * pulse.value),
                    topLeft = Offset(r.left - pad, r.top - pad),
                    size = Size(r.width + pad * 2, r.height + pad * 2),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(3.dp.toPx()),
                )
            }
            val a = finger.alpha.value
            if (a > 0f) {
                val c = finger.pos.value
                val rad = 17.dp.toPx() * finger.scale.value
                val rp = finger.ripple.value
                if (rp in 0.01f..0.99f) {
                    drawCircle(Color.White.copy(alpha = a * (1f - rp) * 0.8f), radius = 17.dp.toPx() + rp * 22.dp.toPx(), center = c, style = Stroke(2.5.dp.toPx()))
                }
                drawCircle(Color.Black.copy(alpha = 0.18f * a), radius = rad + 2.dp.toPx(), center = c + Offset(0f, 3.dp.toPx()))
                drawCircle(Color(0xFF8C96A6).copy(alpha = 0.55f * a), radius = rad, center = c)
                drawCircle(Color.White.copy(alpha = 0.95f * a), radius = rad, center = c, style = Stroke(2.5.dp.toPx()))
            }
        }
    }
}
