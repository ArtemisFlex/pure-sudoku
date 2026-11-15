package com.puresudoku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** True while table mode has turned the screen round for Player 2 (dialogs turn with it). */
val LocalTableFlip = androidx.compose.runtime.staticCompositionLocalOf { false }

/** How far (degrees) table mode has turned the screen right now; read during drawing only. */
val LocalTableTurn = androidx.compose.runtime.staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * Keeps a shared board or map fixed on the table while table mode turns the rest of the
 * screen (words, buttons, hands) round to face Player 2.
 */
@Composable
fun Modifier.tableUpright(): Modifier {
    val turn = LocalTableTurn.current
    return this.graphicsLayer { rotationZ = -turn() }
}

/** Each seat's colour in pass-and-play games with hidden hands: Player 1 blue, Player 2 red. */
fun seatColor(player: Int): Color = if (player == 1) Color(0xFF2E7BD6) else Color(0xFFD93A3F)

/**
 * The pass-the-phone screen for games with hidden information: covers the whole screen
 * (so nothing private shows) in the colour of the player whose turn it is, with white text.
 */
@Composable
fun HandoffScreen(player: Int, onReady: () -> Unit, color: Color = seatColor(player)) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        val flip = LocalTableFlip.current
        Box(
            Modifier.fillMaxSize().background(color).graphicsLayer { rotationZ = if (flip) 180f else 0f }.clickable(remember { MutableInteractionSource() }, null) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Player $player", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text("Your turn. Make sure only you can see the screen.", color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, textAlign = TextAlign.Center)
                Box(Modifier.height(18.dp))
                Surface(
                    Modifier.clickable(onClick = onReady), shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.18f), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                ) {
                    Text("I'm ready", Modifier.padding(horizontal = 40.dp, vertical = 14.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
