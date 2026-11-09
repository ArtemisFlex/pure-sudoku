package com.puresudoku.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.MAX_STRIKES
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.demoTarget
import com.puresudoku.core.formatTime
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins

/** Score, strikes (or a game-specific counter) and the solve timer. */
@Composable
fun StatusBar(score: Int, middle: MiddleStat, elapsedMs: Long, modifier: Modifier = Modifier, targets: DemoTargets? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).demoTarget(targets, "score")) {
            Label("SCORE")
            val shown by animateIntAsState(score, tween(450), label = "score")
            Text(shown.toString(), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        }
        Column(Modifier.weight(1f).demoTarget(targets, "middle"), horizontalAlignment = Alignment.CenterHorizontally) {
            when (middle) {
                is MiddleStat.Strikes -> { Label("STRIKES"); Strikes(middle.used) }
                is MiddleStat.Counter -> {
                    Label(middle.label.uppercase())
                    AnimatedContent(
                        middle.value, label = "counter",
                        transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
                    ) { v -> Text(v, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace) }
                }
            }
        }
        Column(Modifier.weight(1f).demoTarget(targets, "time"), horizontalAlignment = Alignment.End) {
            Label("TIME")
            Text(formatTime(elapsedMs), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Three hearts; a lost heart jolts and empties. */
@Composable
fun Strikes(strikes: Int, size: Int = 24) {
    val error = LocalBoardColors.current.error
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height((size + 6).dp)) {
        for (k in 0 until MAX_STRIKES) {
            val lost = k < strikes
            val jolt = remember { Animatable(0f) }
            val lostAtStart = remember { lost }
            LaunchedEffect(lost) {
                if (lost && !lostAtStart) jolt.animateTo(0f, keyframes {
                    durationMillis = 450
                    1f at 80; -0.8f at 160; 0.6f at 240; -0.3f at 320; 0f at 450
                })
            }
            val scale by animateFloatAsState(if (lost) 0.85f else 1f, spring(0.4f), label = "heart")
            Icon(
                if (lost) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite,
                contentDescription = if (lost) "Strike used" else "Strike remaining",
                tint = if (lost) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else error,
                modifier = Modifier
                    .size(size.dp)
                    .offset { IntOffset((jolt.value * 6).dp.roundToPx(), 0) }
                    .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = jolt.value * 18f },
            )
        }
    }
}

@Composable
fun ActionRow(
    canUndo: Boolean,
    notesMode: Boolean,
    onUndo: () -> Unit,
    onErase: () -> Unit,
    onNotes: () -> Unit,
    targets: DemoTargets? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = canUndo, onClick = onUndo, modifier = Modifier.demoTarget(targets, "undo"))
        ActionButton(Icons.AutoMirrored.Outlined.Backspace, "Erase", onClick = onErase, modifier = Modifier.demoTarget(targets, "erase"))
        Box(Modifier.demoTarget(targets, "notes")) {
            ActionButton(Icons.Outlined.Edit, "Notes", active = notesMode, onClick = onNotes)
            val badgeColor by animateColorAsState(
                if (notesMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                label = "badge",
            )
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(50)).background(badgeColor).padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                AnimatedContent(
                    notesMode, label = "notesBadge",
                    transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
                ) { on ->
                    Text(if (on) "ON" else "OFF", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.5f, Spring.StiffnessHigh), label = "press")
    val tint by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }, label = "tint",
    )
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        label = "bg",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = tint)
    }
}

/** 1..[maxDigit] keys, optionally with how many of each digit are still left to place. */
@Composable
fun NumberPad(
    maxDigit: Int,
    remaining: List<Int>?,
    notesMode: Boolean,
    onDigit: (Int) -> Unit,
    targets: DemoTargets? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in 1..maxDigit) {
            val left = remaining?.getOrNull(d - 1) ?: -1
            val done = left == 0
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(
                when { pressed -> 0.86f; done -> 0.8f; else -> 1f },
                spring(0.45f, Spring.StiffnessMedium), label = "key",
            )
            val alpha by animateFloatAsState(if (done) 0.22f else 1f, tween(350), label = "keyAlpha")
            val digitColor by animateColorAsState(
                if (notesMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                label = "keyColor",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .demoTarget(targets, "key-$d")
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(source, indication = null, enabled = !done) { onDigit(d) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    d.toString(), fontSize = if (notesMode) 22.sp else 27.sp,
                    fontWeight = FontWeight.Medium, color = digitColor, fontFamily = Poppins,
                )
                if (remaining != null) Text(
                    if (done) "✓" else left.coerceAtLeast(0).toString(), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Dot(color: androidx.compose.ui.graphics.Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))
