package com.puresudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.GameKind
import com.puresudoku.online.OnlineMatch
import com.puresudoku.online.OnlineMatch.Stage
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.games.Games

/** Play a friend online: host a game and share its code, or type in a friend's code. */
@Composable
fun OnlineScreen(vm: AppViewModel, kind: GameKind) {
    val stage = OnlineMatch.stage
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar("Play online", vm::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Games.of(kind).Icon(72.dp)
            Spacer(Modifier.height(10.dp))
            Text(kind.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 380.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when (stage) {
                    Stage.IDLE -> Choose(kind)
                    Stage.SETTING_UP -> Busy("Getting a room code…")
                    Stage.WAITING -> Waiting(OnlineMatch.code, kind)
                    Stage.JOINING -> Busy("Finding game ${OnlineMatch.code}…")
                    Stage.CONNECTING -> Busy("Connecting to your friend…")
                    Stage.PLAYING -> Busy("Starting the game…")
                    Stage.ERROR -> {
                        Text(OnlineMatch.error, textAlign = TextAlign.Center, fontSize = 16.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { vm.openOnline(kind) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choose(kind: GameKind) {
    var code by remember { mutableStateOf("") }
    Text(
        "Play a friend on another phone or computer. Host a game and send them your invite link, or join using a room code.",
        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = { OnlineMatch.host(kind) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Host a game", fontSize = 17.sp) }
    Text("You go first.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(28.dp))
    Text("Have a code?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    val ready = code.length == 4
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(4) },
            singleLine = true,
            placeholder = { Text("ABCD", fontFamily = FontFamily.Monospace) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (ready) OnlineMatch.join(code) }),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { OnlineMatch.join(code) }, enabled = ready, modifier = Modifier.height(56.dp)) { Text("Join") }
    }
    Text("Your friend goes first.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Waiting(code: String, kind: GameKind) {
    val clip = LocalClipboardManager.current
    val share = com.puresudoku.platform.rememberInviteShare()
    val url = com.puresudoku.online.GameInvite(kind, code).url()
    var copied by remember { mutableStateOf(false) }
    Text("Your room code", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            code, fontSize = 52.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 10.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { share(url) }, Modifier.fillMaxWidth()) { Text("Share invite link") }
    TextButton(onClick = { clip.setText(AnnotatedString(url)); copied = true }) {
        Text(if (copied) "Link copied" else "Copy invite link")
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp))
        Text("Waiting for your friend to join…")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Your friend opens the link to join this game. The link works while the room is waiting, up to 15 minutes. You can also share the code above.",
        textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Busy(text: String) {
    Spacer(Modifier.height(24.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(text)
}
