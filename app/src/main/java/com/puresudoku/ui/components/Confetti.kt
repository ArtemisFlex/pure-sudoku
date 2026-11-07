package com.puresudoku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x: Float, val delay: Float, val speed: Float, val drift: Float,
    val spin: Float, val size: Float, val color: Color, val phase: Float,
)

private val ConfettiColors = listOf(
    Color(0xFFF2B84B), Color(0xFF2F6FD6), Color(0xFF3FA66B),
    Color(0xFFD64545), Color(0xFF7A4FD0), Color(0xFF2F8FB5),
)

/** A one-shot confetti fall, about 3.5 seconds. */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 140) {
    val pieces = remember {
        val r = Random(System.nanoTime())
        List(count) {
            Piece(
                x = r.nextFloat(), delay = r.nextFloat() * 0.9f, speed = 0.35f + r.nextFloat() * 0.35f,
                drift = (r.nextFloat() - 0.5f) * 0.12f, spin = 180f + r.nextFloat() * 540f,
                size = 6f + r.nextFloat() * 8f, color = ConfettiColors[r.nextInt(ConfettiColors.size)],
                phase = r.nextFloat() * 6.28f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < 4f) {
            withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
        }
    }
    if (t >= 4f) return
    Canvas(modifier.fillMaxSize()) {
        for (p in pieces) {
            val life = t - p.delay
            if (life <= 0f) continue
            val y = -20f + life * p.speed * size.height
            if (y > size.height + 20f) continue
            val x = (p.x + p.drift * life + 0.02f * sin(life * 4f + p.phase)) * size.width
            val alpha = if (t > 3.2f) ((4f - t) / 0.8f).coerceIn(0f, 1f) else 1f
            val w = p.size * density * 0.6f
            val h = w * 0.45f
            rotate(p.spin * life, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h))
            }
        }
    }
}
