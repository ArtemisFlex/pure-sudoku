package com.puresudoku.ui.screens

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
    Box(Modifier.fillMaxSize()) {
        val backdrop = def.background(session)
        if (backdrop != 0) {
            androidx.compose.animation.Crossfade(backdrop, animationSpec = androidx.compose.animation.core.tween(700), label = "backdrop") { res ->
                Box(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.imageResource(androidx.compose.ui.graphics.ImageBitmap.Companion, res), null, Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                    )
                    // a scrim keeps text and cards readable over the art
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f)))
                }
            }
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                        Text(session.difficulty.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { vm.openTutorial(session.kind) }) {
                    Icon(Icons.Outlined.Info, contentDescription = "How to play")
                }
                IconButton(onClick = { if (vm.paused) vm.unpause() else vm.pause() }, enabled = playing) {
                    Icon(if (vm.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = "Pause")
                }
            }
            StatusBar(session.score, session.middleStat, session.elapsedMs, Modifier.padding(horizontal = 6.dp).padding(top = 10.dp))
            Text(
                def.hint(session.difficulty),
                fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                def.Play(session)
                NudgePill(session, Modifier.align(Alignment.TopCenter))
                // Pause hides the puzzle so the clock can't be cheated.
                androidx.compose.animation.AnimatedVisibility(vm.paused, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .clickable(remember { MutableInteractionSource() }, null) { vm.unpause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Paused", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(formatTime(session.elapsedMs), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = vm::unpause) { Text("Resume") }
                        }
                    }
                }
            }
        }

        // End-of-game overlays
        if (outcome == Outcome.WON) Confetti()
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
            if (outcome == Outcome.WON && summary != null) WinCard(vm, session, def, summary) else LostCard(vm, session)
        }
    }
}

/** Shows why a move did nothing, with a short buzz. Strikes buzz too. */
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
        Text(session.lostTitle, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(session.lostMessage, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        StatLine("Time played", formatTime(session.elapsedMs))
        StatLine("Score", session.score.toString(), bold = true)
        Spacer(Modifier.height(20.dp))
        EndButtons(vm, session)
    }
}

@Composable
private fun EndButtons(vm: AppViewModel, session: Session) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::leaveGame) { Text("Menu") }
        Button(onClick = vm::playAgain) { Text("New ${session.difficulty.label}") }
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
