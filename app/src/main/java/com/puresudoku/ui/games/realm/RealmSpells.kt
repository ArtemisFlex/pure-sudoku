package com.puresudoku.ui.games.realm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A spellbook shared by Kreashun and Veilguard: every spell you hold, with a Cast button.
 * [passive] spells (Relics) are never cast; [harvest] is the spell that asks for two resources first.
 */
@Composable
internal fun SpellbookDialog(
    names: List<String>, texts: List<String>, icons: List<RI>, counts: IntArray, fresh: IntArray,
    note: String?, passive: Int, harvest: Int, canCast: (Int) -> Boolean,
    onCast: (Int, Int, Int) -> Unit, onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<List<Int>>(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PARCH_TOP, titleContentColor = INK, textContentColor = INK,
        icon = { RIconOf(RI.BOOK, 32.dp) },
        title = { Text("Your spellbook", fontFamily = SERIF, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (note != null) InkText(note, 12, color = INK_SOFT)
                for (k in names.indices) {
                    val n = counts[k]; if (n == 0) continue
                    Column(Modifier.padding(vertical = 4.dp).fillMaxWidth().parchment(PANEL).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RIconOf(icons[k], 30.dp); Spacer(Modifier.width(8.dp))
                            InkText("${names[k]} ×$n", 15, bold = true, modifier = Modifier.weight(1f))
                            if (k != passive && k != harvest) WoodButton("Cast", null, enabled = canCast(k)) { onCast(k, -1, -1); onDismiss() }
                        }
                        InkText(texts[k], 12, color = INK_SOFT)
                        if (k != passive && fresh[k] >= n) InkText("New: you can cast it next turn.", 11, color = INK_SOFT)
                        if (k == harvest && canCast(k)) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (r in 0..4) {
                                    val cnt = picked.count { it == r }
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(RES_COLOR[r].copy(alpha = 0.12f + 0.3f * cnt))
                                        .clickable { picked = (picked + r).takeLast(2) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ResImg(r, 22.dp); InkText(if (cnt > 0) "×$cnt" else " ", 11, bold = true)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            WoodButton("Cast ${names[k]}", RI.MOON, enabled = picked.size == 2) { onCast(k, picked[0], picked[1]); onDismiss() }
                        }
                    }
                }
                if (counts.sum() == 0) InkText("No spells yet. Buy one with ore, grain and beasts.", 13)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = INK, fontFamily = SERIF, fontWeight = FontWeight.Bold) } },
    )
}
