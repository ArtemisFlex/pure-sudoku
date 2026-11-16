package com.puresudoku.ui.screens

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.puresudoku.ui.theme.LocalBoardColors
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.formatTime
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.WinSummary
import com.puresudoku.ui.components.Confetti
import com.puresudoku.ui.components.Dot
import com.puresudoku.ui.components.HSpace
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.accent

/** The shell around every game: top bar (with How to play), status bar, pause and end screens. */
@Composable
fun PlayScreen(vm: AppViewModel) {
    val session = vm.session ?: return
    val def = Games.of(session.kind)
    val outcome = session.outcome
    val playing = outcome == Outcome.PLAYING
    val duel = session as? com.puresudoku.ui.games.board.DuelSession
    // Reading version observes engine turn changes, which are not themselves Compose state.
    val actor = if (duel?.localTwoPlayer == true && playing) {
        duel.version
        duel.localActor
    } else null
    // Open-information board games need no hand-off screen: a coloured bar shows whose move it is.
    // Table mode: players sit across from each other, so the whole screen turns round for Player 2.
    val seat = if (duel?.localTwoPlayer == true && playing) { duel.version; duel.tableSeat } else null
    val flip = vm.tableMode && seat == 2 && duel?.online != true
    // Online, the guest sees boards with plain round pieces turned round, so their own side is at the bottom.
    val guestView = duel?.online == true && duel.onlineSeat == 2 && session.kind in com.puresudoku.online.turnForGuest
    val turn by androidx.compose.animation.core.animateFloatAsState(if (flip) 180f else 0f, tween(450), label = "table")
    androidx.compose.runtime.CompositionLocalProvider(com.puresudoku.ui.components.LocalTableFlip provides flip, com.puresudoku.ui.components.LocalTableTurn provides { turn }, com.puresudoku.ui.components.LocalBoardTurn provides guestView) {
    var showManual by remember(session) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = turn }) {
        val backdrop = def.background(session)
        if (backdrop != 0) {
            androidx.compose.animation.Crossfade(backdrop, animationSpec = androidx.compose.animation.core.tween(700), label = "backdrop") { res ->
                Backdrop(res)
            }
        }
        if (def.fullBleed) Box(Modifier.fillMaxSize()) { def.Play(session) }
        // The readable-text check measures the backdrop once its art is ready to draw.
        val render = if (backdrop != 0) com.puresudoku.platform.rememberBackdropRenderer(backdrop) else null
        com.puresudoku.ui.components.ShadowedText(if (render != null) backdrop else 0, com.puresudoku.platform.screenAspect(), render ?: { _, _, _ -> null }) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.puresudoku.ui.components.BackdropChrome(enabled = !def.hasHeader) {
            if (def.hasHeader) def.Header(session, com.puresudoku.ui.games.HeaderActions(
                back = vm::leaveGame, info = { vm.openTutorial(session.kind) },
                pause = { if (vm.paused) vm.unpause() else vm.pause() }, paused = vm.paused, canPause = playing,
            )) else {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::leaveGame) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to ${session.kind.title}")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        session.kind.title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(session.difficulty.accent(), 8)
                        HSpace(6)
                        Text(if (duel?.online == true) "Online · you play ${duel.localSide(duel.onlineSeat).first}" else if ((session as? com.puresudoku.ui.games.board.DuelSession)?.localTwoPlayer == true) "Two players" else (if (session.training) "Training wheels · " else "") + if (def.singleLevel) "Endless career" else session.difficulty.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { vm.openTutorial(session.kind) }) {
                    Icon(Icons.Outlined.Info, contentDescription = "How to play")
                }
                IconButton(onClick = { if (vm.paused) vm.unpause() else vm.pause() }, enabled = playing) {
                    Icon(if (vm.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = "Pause")
                }
            }
            }
            if (!def.fullBleed) {
                StatusBar(session.score, session.middleStat, session.elapsedMs, Modifier.padding(horizontal = 6.dp).padding(top = 10.dp))
                if (actor != null && duel != null) TurnBar(duel.localSide(actor), actor, Modifier.padding(horizontal = 6.dp, vertical = 8.dp), onlineSeat = duel.onlineSeat)
                else if (session.kind != com.puresudoku.core.GameKind.CRATEBARGE) Text(
                    if (duel?.online == true) "Playing a friend online. Leaving the game hands them the win." else if ((session as? com.puresudoku.ui.games.board.DuelSession)?.localTwoPlayer == true) "Take turns and pass the phone to the next player." else def.hint(session.difficulty),
                    fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            }

            var coachOpen by remember(session) { mutableStateOf(session.training) }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (!def.fullBleed) def.Play(session)
                // Online: only the player whose turn it is can act. Games with hidden cards are covered meanwhile.
                val waiting = duel != null && duel.online && playing && duel.tableSeat.let { it != null && it != duel.onlineSeat }
                // Card and strategy games stay fully viewable from your own side (they only let you act on your turn);
                // shared boards just hold back taps.
                if (waiting) OpponentsTurn(duel!!, session.kind in com.puresudoku.online.hiddenInfoGames, Modifier.matchParentSize())
                NudgePill(session, Modifier.align(Alignment.TopCenter))
                // Full-screen strategy games show their own news cards; others get the shared overlay.
                if (!def.fullBleed) com.puresudoku.ui.components.HeraldOverlay(session, def, Modifier.align(Alignment.TopCenter))
                if (session.training) com.puresudoku.ui.training.CoachPanel(
                    session, coachOpen, onToggle = { coachOpen = !coachOpen }, modifier = Modifier.align(Alignment.BottomCenter),
                )
                // Pause hides the puzzle so the clock can't be cheated.
                androidx.compose.animation.AnimatedVisibility(vm.paused, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .clickable(remember { MutableInteractionSource() }, null) { vm.unpause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Paused", style = MaterialTheme.typography.headlineMedium.copy(shadow = androidx.compose.ui.graphics.Shadow.None))
                            Spacer(Modifier.height(4.dp))
                            Text(formatTime(session.elapsedMs), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = androidx.compose.material3.LocalTextStyle.current.copy(shadow = androidx.compose.ui.graphics.Shadow.None))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = vm::unpause) { Text("Resume") }
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.material3.OutlinedButton(onClick = { showManual = true }) { Text("Manual") }
                            if (duel?.localTwoPlayer == true) {
                                Spacer(Modifier.height(24.dp))
                                TableModeToggle(vm.tableMode, vm::toggleTableMode)
                            }
                        }
                        }
                    }
                }
            }
        }
        }

        // End-of-game overlays
        if (if (duel?.online == true) duel.youWonOnline else outcome == Outcome.WON) Confetti()
        AnimatedVisibility(!playing, enter = fadeIn(tween(400, delayMillis = 650)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        AnimatedVisibility(
            !playing,
            Modifier.align(Alignment.Center),
            enter = fadeIn(tween(300, delayMillis = 700)) +
                scaleIn(spring(0.6f, Spring.StiffnessMediumLow), initialScale = 0.7f) +
                slideInVertically(tween(450, delayMillis = 700)) { it / 4 },
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            val summary = vm.winSummary
            if (session.training) com.puresudoku.ui.training.TrainingEndCard(session, onAgain = vm::playAgain, onLeave = vm::leaveGame)
            else if (outcome == Outcome.WON && summary != null) WinCard(vm, session, def, summary) else LostCard(vm, session)
        }
        if (showManual) com.puresudoku.ui.manual.GameManual(session.kind, onClose = { showManual = false })
    }
    }
}

/** Shows why a move did nothing, with a short buzz. Strikes buzz too. */
/** Whose move it is in pass-and-play: the side's own colour, edge to edge. */
@Composable
private fun TurnBar(side: Pair<String, androidx.compose.ui.graphics.Color>, actor: Int, modifier: Modifier = Modifier, onlineSeat: Int = 0) {
    androidx.compose.animation.AnimatedContent(side, modifier.fillMaxWidth(), label = "turn") { (n, c) ->
        val ink = if (c.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFF1B1B1F) else androidx.compose.ui.graphics.Color.White
        Box(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(c)
                .border(1.5.dp, androidx.compose.ui.graphics.Color(0x33000000), androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (onlineSeat != 0) (if (actor == onlineSeat) "Your move" else "Opponent's move") + if (n.startsWith("Player")) "" else " · $n"
                else if (n.startsWith("Player")) "$n to move" else "$n to move · Player $actor", color = ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun NudgePill(session: Session, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val fx = session.nudgeFx
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fx?.id) {
        if (fx != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            shown = fx.text
            delay(2000)
            shown = null
        }
    }
    var lastStrikes by remember { mutableIntStateOf(session.strikes) }
    LaunchedEffect(session.strikes) {
        if (session.strikes > lastStrikes) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        lastStrikes = session.strikes
    }
    AnimatedVisibility(
        shown != null, modifier.padding(top = 4.dp),
        enter = fadeIn(tween(150)) + slideInVertically(tween(220)) { -it },
        exit = fadeOut(tween(250)),
    ) {
        Text(
            shown ?: "",
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier
                .background(LocalBoardColors.current.error.copy(alpha = 0.95f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EndCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp, shadowElevation = 12.dp,
        modifier = Modifier.padding(28.dp).widthIn(max = 380.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun WinCard(vm: AppViewModel, session: Session, def: GameDef, s: WinSummary) {
    EndCard {
        def.Icon(64.dp)
        Spacer(Modifier.height(12.dp))
        Text(session.wonTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        Text("SOLVE TIME", fontSize = 11.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatTime(s.timeMs), fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (s.record.newBestTime) Badge("New fastest ${session.difficulty.label} time!")
        Spacer(Modifier.height(16.dp))
        for (b in s.bonus) StatLine(b.label, "+${b.value}")
        StatLine("Final score", s.score.toString(), bold = true)
        if (s.rank in 1..10) Badge(if (s.rank == 1) "#1 on your leaderboard!" else "#${s.rank} on your leaderboard")
        else if (s.record.newBestScore) Badge("New high score!")
        Spacer(Modifier.height(10.dp))
        StatLine("Best time", formatTime(s.record.stats.bestTimeMs))
        StatLine("Average time", formatTime(s.record.stats.averageTimeMs))
        StatLine("${session.difficulty.label} solves", s.record.stats.wins.toString())
        Spacer(Modifier.height(20.dp))
        EndButtons(vm, session)
    }
}

@Composable
private fun LostCard(vm: AppViewModel, session: Session) {
    EndCard {
        val draw = session.outcome == Outcome.DRAW
        val duel = session as? com.puresudoku.ui.games.board.DuelSession
        val local = duel?.localTwoPlayer == true
        val online = duel?.online == true
        val won = session.outcome == Outcome.WON
        // Online games keep pass-and-play results (won = seat 1 won), so "you" depends on your seat.
        val youWon = online && duel!!.youWonOnline
        Text(
            when {
                online -> if (draw) "It's a draw" else if (youWon) "You win!" else "Your opponent wins"
                draw -> session.drawTitle
                local && won -> session.wonTitle
                else -> session.lostTitle
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                online -> if (draw) "A draw: ${duel!!.message.substringBefore(" ·")}." else duel!!.message.substringBefore(" ·").let { if (it.endsWith(".") || it.endsWith("!")) it else "$it." }
                local -> "Pass-and-play match complete."
                draw -> session.drawMessage
                else -> session.lostMessage
            },
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        StatLine("Time played", formatTime(session.elapsedMs))
        if (!local && !online) StatLine("Score", session.score.toString(), bold = true)
        Spacer(Modifier.height(20.dp))
        EndButtons(vm, session)
    }
}

@Composable
private fun EndButtons(vm: AppViewModel, session: Session) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::leaveGame) { Text("Menu") }
        val duel = session as? com.puresudoku.ui.games.board.DuelSession
        Button(onClick = vm::playAgain) { Text(if (duel?.online == true) "New online game" else if (duel?.localTwoPlayer == true) "Play again" else "New ${session.difficulty.label}") }
    }
}

@Composable
private fun StatLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSecondary,
        modifier = Modifier.padding(top = 6.dp)
            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}


/** Pixel-art backdrops (PNG) stay crisp with nearest-neighbour scaling; vector backdrops scale smoothly. */
@Composable
private fun Backdrop(res: Int) {
    val isBitmap = com.puresudoku.platform.isBitmapDrawable(res)
    if (isBitmap) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.graphics.ImageBitmap.imageResource(res), null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        )
    } else {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res), null, Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}

/** Pause-menu switch between hand-off mode and table mode (the screen turns round for Player 2). */
@Composable
private fun TableModeToggle(on: Boolean, toggle: () -> Unit) {
    androidx.compose.material3.Surface(
        Modifier.padding(horizontal = 24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable(onClick = toggle),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(if (on) "Table mode" else "Hand-off mode", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    if (on) "Lay the device flat between you. Words and buttons turn to face whoever's turn it is; the board stays put." else "Pass the device to each player in turn.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Switch(checked = on, onCheckedChange = { toggle() })
        }
    }
}

/**
 * Shown online while the other player takes their turn. Boards stay visible (taps are held back);
 * games with hidden cards are covered so your hand isn't on show while you wait.
 */
@Composable
private fun OpponentsTurn(duel: com.puresudoku.ui.games.board.DuelSession, viewOnly: Boolean, modifier: Modifier) {
    if (!viewOnly) {
        Box(modifier.clickable(remember { MutableInteractionSource() }, null) { duel.nudge("It's your opponent's turn") })
        return
    }
    // A small note at the bottom; the game underneath stays visible and scrollable.
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            color = com.puresudoku.ui.components.seatColor(3 - duel.onlineSeat),
            shadowElevation = 4.dp,
            modifier = Modifier.padding(bottom = 18.dp),
        ) {
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Opponent's turn", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }
}
