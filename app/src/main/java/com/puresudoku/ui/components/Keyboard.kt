package com.puresudoku.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.demoTarget

private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

/** On-screen A–Z keyboard. Each key is a demo target "kb-<letter>". */
@Composable
fun LetterKeyboard(onKey: (Char) -> Unit, onDelete: (() -> Unit)? = null, targets: DemoTargets? = null, keyHeight: Int = 48) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for ((ri, row) in ROWS.withIndex()) {
            Row(Modifier.fillMaxWidth(if (ri == 0) 1f else if (ri == 1) 0.92f else 1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (ch in row) Key(Modifier.weight(1f).demoTarget(targets, "kb-$ch"), keyHeight, { onKey(ch) }) {
                    Text(ch.uppercase(), fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
                if (ri == 2 && onDelete != null) Key(Modifier.weight(1.6f).demoTarget(targets, "kb-del"), keyHeight, onDelete) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Key(modifier: Modifier, height: Int, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.5f, Spring.StiffnessHigh), label = "key")
    Box(
        modifier.height(height.dp).graphicsLayer { scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(src, null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) { content() }
}
