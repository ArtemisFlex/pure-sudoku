package com.puresudoku.ui.games.realm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.puresudoku.core.board.Realm

// =====================================================================  parchment theme

internal val PARCH_TOP = Color(0xFFF8EED6)
internal val PARCH_BOT = Color(0xFFE8D3A6)
internal val INK = Color(0xFF3A2A18)
internal val INK_SOFT = Color(0xFF6E5636)
internal val GOLD = Color(0xFFB8872E)
internal val WOOD = Color(0xFF4E321C)
internal val WOOD_LIGHT = Color(0xFF7A5230)
internal val SERIF = FontFamily.Serif

internal fun Modifier.parchment(shape: Shape, border: Color = GOLD, width: Dp = 1.5.dp) =
    this.background(Brush.verticalGradient(listOf(PARCH_TOP, PARCH_BOT)), shape).border(width, border, shape)

internal fun Modifier.wood(shape: Shape) =
    this.background(Brush.verticalGradient(listOf(WOOD_LIGHT, WOOD)), shape).border(1.5.dp, GOLD, shape)

/** A small canvas icon; [draw] gets the centre and the box size in px. */
@Composable
internal fun RIcon(size: Dp, modifier: Modifier = Modifier, draw: DrawScope.(Offset, Float) -> Unit) {
    Canvas(modifier.size(size)) { draw(center, this.size.minDimension) }
}

private fun poly(a: Offset, b: Offset, c: Offset, d: Offset? = null, e: Offset? = null, f: Offset? = null, g: Offset? = null, h: Offset? = null, i: Offset? = null, j: Offset? = null) =
    Path().apply { listOfNotNull(a, b, c, d, e, f, g, h, i, j).forEachIndexed { k, p -> if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }

// =====================================================================  resource icons

internal fun DrawScope.resIcon(r: Int, c: Offset, s: Float) {
    val u = s / 2
    when (r) {
        Realm.TIMBER -> {
            for ((dy, rot) in listOf(0.22f to -8f, -0.18f to 6f)) rotate(rot, c) {
                val tl = Offset(c.x - u * 0.78f, c.y + u * dy - u * 0.2f)
                drawRoundRect(Color(0xFF8D5A2B), tl, Size(u * 1.4f, u * 0.4f), CornerRadius(u * 0.2f))
                drawLine(Color(0xFF6B3F1A), tl + Offset(u * 0.2f, u * 0.13f), tl + Offset(u * 1.1f, u * 0.13f), u * 0.05f)
                val end = Offset(tl.x + u * 1.4f, tl.y + u * 0.2f)
                drawCircle(Color(0xFFE2B57A), u * 0.21f, end)
                drawCircle(Color(0xFF8D5A2B), u * 0.12f, end, style = Stroke(u * 0.04f))
            }
            drawPath(poly(c + Offset(-u * 0.55f, -u * 0.5f), c + Offset(-u * 0.25f, -u * 0.85f), c + Offset(-u * 0.1f, -u * 0.48f)), Color(0xFF3E8E52))
        }
        Realm.ORE -> {
            val rock = poly(c + Offset(-u * 0.8f, u * 0.5f), c + Offset(-u * 0.65f, -u * 0.25f), c + Offset(-u * 0.15f, -u * 0.7f), c + Offset(u * 0.5f, -u * 0.5f),
                c + Offset(u * 0.82f, u * 0.1f), c + Offset(u * 0.55f, u * 0.6f))
            drawPath(rock, Color(0xFF6F7782))
            drawPath(poly(c + Offset(-u * 0.65f, -u * 0.25f), c + Offset(-u * 0.15f, -u * 0.7f), c + Offset(u * 0.5f, -u * 0.5f), c + Offset(u * 0.05f, -u * 0.05f)), Color(0xFFA7AFB9))
            drawPath(rock, Color(0xFF3F454D), style = Stroke(u * 0.07f))
            for (p in listOf(Offset(0.25f, 0.2f), Offset(-0.3f, 0.25f), Offset(0.5f, -0.05f))) drawCircle(Color(0xFFF2C14E), u * 0.09f, c + p * u)
        }
        Realm.GRAIN -> {
            for (k in -1..1) {
                val base = c + Offset(k * u * 0.08f, u * 0.8f); val top = c + Offset(k * u * 0.45f, -u * 0.55f)
                drawLine(Color(0xFFB68420), base, top, u * 0.07f, StrokeCap.Round)
                for (j in 0..3) {
                    val p = base + (top - base) * (0.55f + j * 0.15f)
                    drawOval(Color(0xFFE6B13A), Offset(p.x - u * 0.14f, p.y - u * 0.08f), Size(u * 0.13f, u * 0.2f))
                    drawOval(Color(0xFFE6B13A), Offset(p.x + u * 0.01f, p.y - u * 0.08f), Size(u * 0.13f, u * 0.2f))
                }
            }
            drawRoundRect(Color(0xFF9C2E2E), c + Offset(-u * 0.25f, u * 0.25f), Size(u * 0.5f, u * 0.14f), CornerRadius(u * 0.05f))
        }
        Realm.BEASTS -> {
            val col = Color(0xFF5E7F2E)
            drawOval(col, Offset(c.x - u * 0.42f, c.y - u * 0.05f), Size(u * 0.84f, u * 0.7f))
            for ((dx, dy) in listOf(-0.62f to -0.32f, -0.24f to -0.72f, 0.24f to -0.72f, 0.62f to -0.32f)) drawOval(col, Offset(c.x + dx * u - u * 0.17f, c.y + dy * u - u * 0.2f), Size(u * 0.34f, u * 0.42f))
            drawOval(Color(0xFFB9D98A), Offset(c.x - u * 0.2f, c.y + u * 0.08f), Size(u * 0.4f, u * 0.22f))
        }
        Realm.MANA -> {
            for ((dx, h, w) in listOf(Triple(-0.42f, 0.7f, 0.28f), Triple(0.0f, 1.0f, 0.36f), Triple(0.42f, 0.65f, 0.26f))) {
                val b = c + Offset(dx * u, u * 0.75f); val t = b - Offset(0f, h * u * 1.45f)
                val crystal = poly(b + Offset(-w * u, -u * 0.2f), t + Offset(-w * u * 0.6f, u * 0.25f), t, t + Offset(w * u * 0.6f, u * 0.25f), b + Offset(w * u, -u * 0.2f), b)
                drawPath(crystal, Color(0xFF8E6BD8))
                drawPath(poly(t, t + Offset(w * u * 0.6f, u * 0.25f), b + Offset(w * u, -u * 0.2f), b), Color(0xFF5E40A6))
                drawPath(crystal, Color(0xFF3B2470), style = Stroke(u * 0.05f))
            }
            drawCircle(Color.White.copy(alpha = 0.8f), u * 0.07f, c + Offset(-u * 0.08f, -u * 0.4f))
        }
    }
}

// =====================================================================  action and spell icons

internal enum class RI { ROAD, VILLAGE, TOWER, SCROLL, TRADE, BOOK, END, DICE, KNIGHT, RELIC, EARTH, MOON, FIRE, CROWN, SHIELD, LEY, DRAGON, SUN }

internal fun DrawScope.actionIcon(i: RI, c: Offset, s: Float, tint: Color = INK) {
    val u = s / 2
    when (i) {
        RI.ROAD -> {
            drawPath(poly(c + Offset(-u * 0.25f, -u * 0.85f), c + Offset(u * 0.25f, -u * 0.85f), c + Offset(u * 0.8f, u * 0.85f), c + Offset(-u * 0.8f, u * 0.85f)), Color(0xFFC9A46A))
            for (k in 0..2) drawLine(Color.White, c + Offset(0f, -u * 0.6f + k * u * 0.55f), c + Offset(0f, -u * 0.35f + k * u * 0.55f), u * 0.1f)
            drawPath(poly(c + Offset(-u * 0.25f, -u * 0.85f), c + Offset(u * 0.25f, -u * 0.85f), c + Offset(u * 0.8f, u * 0.85f), c + Offset(-u * 0.8f, u * 0.85f)), tint, style = Stroke(u * 0.08f))
        }
        RI.VILLAGE -> {
            drawPath(poly(c + Offset(-u * 0.75f, u * 0.75f), c + Offset(-u * 0.75f, -u * 0.05f), c + Offset(0f, -u * 0.8f), c + Offset(u * 0.75f, -u * 0.05f), c + Offset(u * 0.75f, u * 0.75f)), Color(0xFFE9D3A6))
            drawPath(poly(c + Offset(-u * 0.9f, 0f), c + Offset(0f, -u * 0.85f), c + Offset(u * 0.9f, 0f), c + Offset(u * 0.75f, u * 0.1f), c + Offset(0f, -u * 0.6f), c + Offset(-u * 0.75f, u * 0.1f)), Color(0xFFA0442C))
            drawRect(tint, c + Offset(-u * 0.18f, u * 0.25f), Size(u * 0.36f, u * 0.5f))
        }
        RI.TOWER -> {
            drawRect(Color(0xFFB8B2A6), c + Offset(-u * 0.45f, -u * 0.5f), Size(u * 0.9f, u * 1.35f))
            for (k in 0..2) drawRect(Color(0xFFB8B2A6), c + Offset(-u * 0.45f + k * u * 0.36f, -u * 0.8f), Size(u * 0.2f, u * 0.32f))
            drawRect(tint, c + Offset(-u * 0.45f, -u * 0.5f), Size(u * 0.9f, u * 1.35f), style = Stroke(u * 0.07f))
            drawRoundRect(tint, c + Offset(-u * 0.13f, u * 0.25f), Size(u * 0.26f, u * 0.6f), CornerRadius(u * 0.13f))
            drawLine(Color(0xFF9C2E2E), c + Offset(u * 0.3f, -u * 0.8f), c + Offset(u * 0.3f, -u * 1.0f), u * 0.05f)
        }
        RI.SCROLL -> {
            drawRoundRect(Color(0xFFF1DFB4), c + Offset(-u * 0.6f, -u * 0.7f), Size(u * 1.2f, u * 1.4f), CornerRadius(u * 0.1f))
            drawRoundRect(Color(0xFFC8A465), c + Offset(-u * 0.75f, -u * 0.85f), Size(u * 1.5f, u * 0.3f), CornerRadius(u * 0.15f))
            drawRoundRect(Color(0xFFC8A465), c + Offset(-u * 0.75f, u * 0.55f), Size(u * 1.5f, u * 0.3f), CornerRadius(u * 0.15f))
            for (k in 0..2) drawLine(INK_SOFT, c + Offset(-u * 0.35f, -u * 0.3f + k * u * 0.3f), c + Offset(u * 0.35f, -u * 0.3f + k * u * 0.3f), u * 0.06f)
            drawCircle(Color(0xFF8E6BD8), u * 0.13f, c + Offset(u * 0.35f, u * 0.38f))
        }
        RI.TRADE -> {
            drawCircle(Color(0xFFE6B13A), u * 0.42f, c + Offset(-u * 0.3f, u * 0.25f))
            drawCircle(Color(0xFFB68420), u * 0.42f, c + Offset(-u * 0.3f, u * 0.25f), style = Stroke(u * 0.07f))
            drawCircle(Color(0xFFD0D4DA), u * 0.42f, c + Offset(u * 0.3f, -u * 0.25f))
            drawCircle(Color(0xFF6F7782), u * 0.42f, c + Offset(u * 0.3f, -u * 0.25f), style = Stroke(u * 0.07f))
            drawLine(tint, c + Offset(-u * 0.9f, -u * 0.55f), c + Offset(-u * 0.35f, -u * 0.85f), u * 0.08f, StrokeCap.Round)
            drawLine(tint, c + Offset(u * 0.9f, u * 0.55f), c + Offset(u * 0.35f, u * 0.85f), u * 0.08f, StrokeCap.Round)
        }
        RI.BOOK -> {
            drawRoundRect(Color(0xFF5E40A6), c + Offset(-u * 0.7f, -u * 0.8f), Size(u * 1.4f, u * 1.6f), CornerRadius(u * 0.12f))
            drawRect(Color(0xFFF1DFB4), c + Offset(-u * 0.55f, u * 0.55f), Size(u * 1.2f, u * 0.18f))
            drawPath(poly(c + Offset(0f, -u * 0.5f), c + Offset(u * 0.12f, -u * 0.15f), c + Offset(u * 0.45f, -u * 0.1f), c + Offset(u * 0.18f, u * 0.12f),
                c + Offset(u * 0.28f, u * 0.45f), c + Offset(0f, u * 0.25f), c + Offset(-u * 0.28f, u * 0.45f), c + Offset(-u * 0.18f, u * 0.12f), c + Offset(-u * 0.45f, -u * 0.1f), c + Offset(-u * 0.12f, -u * 0.15f)), Color(0xFFF2C14E))
        }
        RI.END -> {
            drawPath(poly(c + Offset(-u * 0.55f, -u * 0.8f), c + Offset(u * 0.55f, -u * 0.8f), c + Offset(0f, 0f)), Color(0xFFE9D3A6))
            drawPath(poly(c + Offset(-u * 0.55f, u * 0.8f), c + Offset(u * 0.55f, u * 0.8f), c + Offset(0f, 0f)), Color(0xFFE6B13A))
            drawLine(tint, c + Offset(-u * 0.7f, -u * 0.85f), c + Offset(u * 0.7f, -u * 0.85f), u * 0.12f, StrokeCap.Round)
            drawLine(tint, c + Offset(-u * 0.7f, u * 0.85f), c + Offset(u * 0.7f, u * 0.85f), u * 0.12f, StrokeCap.Round)
            drawPath(poly(c + Offset(-u * 0.55f, -u * 0.8f), c + Offset(u * 0.55f, -u * 0.8f), c + Offset(0f, 0f), c + Offset(u * 0.55f, u * 0.8f), c + Offset(-u * 0.55f, u * 0.8f), c + Offset(0f, 0f)), tint, style = Stroke(u * 0.07f))
        }
        RI.DICE -> die(c, s * 0.85f, 5, false)
        RI.KNIGHT -> {
            drawPath(Path().apply {
                moveTo(c.x - u * 0.6f, c.y + u * 0.8f); lineTo(c.x - u * 0.6f, c.y - u * 0.1f)
                quadraticTo(c.x - u * 0.6f, c.y - u * 0.85f, c.x, c.y - u * 0.85f); quadraticTo(c.x + u * 0.6f, c.y - u * 0.85f, c.x + u * 0.6f, c.y - u * 0.1f)
                lineTo(c.x + u * 0.6f, c.y + u * 0.8f); close()
            }, Color(0xFFB8BEC6))
            drawRect(tint, c + Offset(-u * 0.45f, -u * 0.15f), Size(u * 0.9f, u * 0.14f))
            drawLine(tint, c + Offset(0f, 0f), c + Offset(0f, u * 0.7f), u * 0.08f)
            drawPath(poly(c + Offset(-u * 0.1f, -u * 0.85f), c + Offset(u * 0.3f, -u * 1.0f), c + Offset(u * 0.1f, -u * 0.8f)), Color(0xFF9C2E2E))
        }
        RI.RELIC -> {
            drawPath(poly(c + Offset(-u * 0.7f, -u * 0.25f), c + Offset(-u * 0.35f, -u * 0.7f), c + Offset(u * 0.35f, -u * 0.7f), c + Offset(u * 0.7f, -u * 0.25f), c + Offset(0f, u * 0.8f)), Color(0xFF2EA37A))
            drawPath(poly(c + Offset(-u * 0.7f, -u * 0.25f), c + Offset(u * 0.7f, -u * 0.25f), c + Offset(0f, u * 0.8f)), Color(0xFF1E7556))
            drawCircle(Color.White.copy(alpha = 0.8f), u * 0.1f, c + Offset(-u * 0.2f, -u * 0.45f))
        }
        RI.EARTH -> {
            drawPath(poly(c + Offset(-u * 0.9f, u * 0.7f), c + Offset(-u * 0.25f, -u * 0.5f), c + Offset(u * 0.15f, u * 0.1f), c + Offset(u * 0.45f, -u * 0.3f), c + Offset(u * 0.9f, u * 0.7f)), Color(0xFF8D6E4A))
            drawLine(Color(0xFFC9A46A), c + Offset(-u * 0.6f, u * 0.75f), c + Offset(u * 0.6f, u * 0.75f), u * 0.14f, StrokeCap.Round)
        }
        RI.MOON -> {
            drawCircle(Color(0xFFF6E7A8), u * 0.75f, c)
            drawCircle(Color(0xFF2C2F63), u * 0.62f, c + Offset(u * 0.38f, -u * 0.25f))
        }
        RI.FIRE -> {
            drawPath(Path().apply {
                moveTo(c.x, c.y - u * 0.95f); quadraticTo(c.x + u * 0.85f, c.y - u * 0.1f, c.x + u * 0.5f, c.y + u * 0.6f)
                quadraticTo(c.x, c.y + u * 0.95f, c.x - u * 0.5f, c.y + u * 0.6f); quadraticTo(c.x - u * 0.85f, c.y - u * 0.1f, c.x, c.y - u * 0.95f); close()
            }, Color(0xFFE5532D))
            drawCircle(Color(0xFFFFC34D), u * 0.33f, c + Offset(0f, u * 0.35f))
        }
        RI.CROWN -> {
            drawPath(poly(c + Offset(-u * 0.8f, u * 0.5f), c + Offset(-u * 0.85f, -u * 0.5f), c + Offset(-u * 0.4f, -u * 0.05f), c + Offset(0f, -u * 0.7f),
                c + Offset(u * 0.4f, -u * 0.05f), c + Offset(u * 0.85f, -u * 0.5f), c + Offset(u * 0.8f, u * 0.5f)), Color(0xFFE6B13A))
            drawRect(Color(0xFFB68420), c + Offset(-u * 0.8f, u * 0.4f), Size(u * 1.6f, u * 0.25f))
            drawCircle(Color(0xFF9C2E2E), u * 0.1f, c + Offset(0f, u * 0.15f))
        }
        RI.SHIELD -> {
            val p = Path().apply {
                moveTo(c.x - u * 0.8f, c.y - u * 0.8f); lineTo(c.x + u * 0.8f, c.y - u * 0.8f); lineTo(c.x + u * 0.8f, c.y)
                quadraticTo(c.x + u * 0.7f, c.y + u * 0.7f, c.x, c.y + u * 0.95f); quadraticTo(c.x - u * 0.7f, c.y + u * 0.7f, c.x - u * 0.8f, c.y); close()
            }
            drawPath(p, tint); drawPath(p, GOLD, style = Stroke(u * 0.1f))
        }
        RI.LEY -> {
            drawCircle(Color(0xFFB388FF).copy(alpha = 0.4f), u * 0.85f, c)
            drawPath(poly(c + Offset(0f, -u * 0.8f), c + Offset(u * 0.5f, 0f), c + Offset(0f, u * 0.8f), c + Offset(-u * 0.5f, 0f)), Color(0xFF7C4DFF))
        }
        RI.DRAGON -> {
            drawPath(poly(c + Offset(-u * 0.9f, u * 0.4f), c + Offset(-u * 0.1f, -u * 0.05f), c + Offset(u * 0.55f, -u * 0.35f), c + Offset(u * 0.95f, -u * 0.6f),
                c + Offset(u * 0.85f, -u * 0.2f), c + Offset(u * 0.2f, u * 0.25f), c + Offset(-u * 0.9f, u * 0.6f)), Color(0xFFB71C1C))
            drawPath(poly(c + Offset(-u * 0.15f, 0f), c + Offset(-u * 0.6f, -u * 0.9f), c + Offset(-u * 0.15f, -u * 0.6f), c + Offset(u * 0.1f, -u * 0.95f), c + Offset(u * 0.3f, -u * 0.25f)), Color(0xFF7F1D1D))
        }
        RI.SUN -> {
            for (k in 0 until 8) rotate(k * 45f, c) { drawLine(Color(0xFFF2A93B), c + Offset(0f, -u * 0.6f), c + Offset(0f, -u * 0.92f), u * 0.12f, StrokeCap.Round) }
            drawCircle(Color(0xFFFFC34D), u * 0.48f, c)
        }
    }
}

internal fun DrawScope.die(c: Offset, s: Float, v: Int, hot: Boolean) {
    drawRoundRect(Color.Black.copy(alpha = 0.3f), c - Offset(s / 2, s / 2) + Offset(s * 0.04f, s * 0.06f), Size(s, s), CornerRadius(s * 0.2f))
    drawRoundRect(if (hot) Color(0xFFFFD5CC) else Color(0xFFFFFBF0), c - Offset(s / 2, s / 2), Size(s, s), CornerRadius(s * 0.2f))
    drawRoundRect(INK, c - Offset(s / 2, s / 2), Size(s, s), CornerRadius(s * 0.2f), style = Stroke(s * 0.05f))
    val q = s * 0.26f
    val pos = when (v) {
        1 -> listOf(0 to 0); 2 -> listOf(-1 to -1, 1 to 1); 3 -> listOf(-1 to -1, 0 to 0, 1 to 1)
        4 -> listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1); 5 -> listOf(-1 to -1, 1 to -1, 0 to 0, -1 to 1, 1 to 1)
        else -> listOf(-1 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 1 to 1)
    }
    for ((a, b) in pos) drawCircle(if (hot) Color(0xFFB71C1C) else INK, s * 0.09f, c + Offset(a * q, b * q))
}

internal fun spellIcon(k: Int) = when (k) { Realm.KNIGHT -> RI.KNIGHT; Realm.RELIC -> RI.RELIC; Realm.EARTH -> RI.EARTH; Realm.HARVEST -> RI.MOON; else -> RI.FIRE }

// =====================================================================  painted icons

private val RES_ICON_IDS = intArrayOf(com.puresudoku.R.drawable.rt_ic_timber, com.puresudoku.R.drawable.rt_ic_ore, com.puresudoku.R.drawable.rt_ic_grain, com.puresudoku.R.drawable.rt_ic_beasts, com.puresudoku.R.drawable.rt_ic_mana)
private val RES_SHIELD_IDS = intArrayOf(com.puresudoku.R.drawable.rt_sh_timber, com.puresudoku.R.drawable.rt_sh_ore, com.puresudoku.R.drawable.rt_sh_grain, com.puresudoku.R.drawable.rt_sh_beasts, com.puresudoku.R.drawable.rt_sh_mana)

@Composable
internal fun ArtImg(id: Int, size: Dp, modifier: Modifier = Modifier) =
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(id), null, modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit)

/** A resource's painted icon. */
@Composable internal fun ResImg(r: Int, size: Dp, modifier: Modifier = Modifier) = ArtImg(RES_ICON_IDS[r], size, modifier)

/** A resource's painted shield, for bigger pickers. */
@Composable internal fun ShieldImg(r: Int, size: Dp, modifier: Modifier = Modifier) = ArtImg(RES_SHIELD_IDS[r], size, modifier)

/** An action icon: painted where the art has one (knight, dragon), drawn otherwise. */
@Composable
internal fun RIconOf(icon: RI, size: Dp, tint: Color = INK, modifier: Modifier = Modifier) = when (icon) {
    RI.KNIGHT -> ArtImg(com.puresudoku.R.drawable.rt_knight, size, modifier)
    RI.DRAGON -> ArtImg(com.puresudoku.R.drawable.rt_dragon, size * 1.25f, modifier)
    else -> RIcon(size, modifier) { c, sz -> actionIcon(icon, c, sz, tint) }
}
