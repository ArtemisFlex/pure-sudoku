package com.puresudoku.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.puresudoku.ui.components.ActionRow
import com.puresudoku.ui.components.DigitBoard
import com.puresudoku.ui.components.NumberPad
import com.puresudoku.ui.components.StatusBar
import com.puresudoku.ui.session.DigitGridSession
import com.puresudoku.ui.tutorial.DemoTargets

/** Standard play layout for the digit games: board, actions, number pad. */
@Composable
fun DigitPlay(
    session: DigitGridSession,
    decoration: (@Composable BoxScope.(Int) -> Unit)? = null,
    blocked: (@Composable BoxScope.(Int) -> Unit)? = null,
) {
    @Suppress("UNUSED_VARIABLE") val v = session.boardVersion
    val spec = session.spec
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Fit the board above the controls, whatever its shape.
    val aspect = spec.width / spec.height.toFloat()
    val boardW = min(min(maxWidth, 520.dp), (maxHeight - 150.dp) * aspect)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        DigitBoard(session, Modifier.width(boardW), decoration = decoration, blocked = blocked)
        Spacer(Modifier.weight(1f))
        ActionRow(session.canUndo, session.notesMode, session::undo, session::erase, session::toggleNotesMode)
        Spacer(Modifier.height(10.dp))
        NumberPad(spec.maxDigit, if (spec.showRemaining) session::remaining else null, session.notesMode, session::input)
        Spacer(Modifier.height(14.dp))
    }
    }
}

/** Compact version of the play screen used inside how-to-play cards. */
@Composable
fun DigitDemoLayout(
    session: DigitGridSession,
    targets: DemoTargets,
    showStatus: Boolean = true,
    showActions: Boolean = true,
    showPad: Boolean = true,
    decoration: (@Composable BoxScope.(Int) -> Unit)? = null,
    blocked: (@Composable BoxScope.(Int) -> Unit)? = null,
) {
    @Suppress("UNUSED_VARIABLE") val v = session.boardVersion
    val spec = session.spec
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val chrome = (if (showStatus) 52.dp else 0.dp) + (if (showActions) 62.dp else 0.dp) + (if (showPad) 58.dp else 0.dp)
        val aspect = spec.width / spec.height.toFloat()
        val boardW = min(maxWidth, (maxHeight - chrome) * aspect)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            if (showStatus) StatusBar(session.score, session.middleStat, session.elapsedMs, targets = targets)
            DigitBoard(session, Modifier.width(boardW), targets = targets, decoration = decoration, blocked = blocked)
            if (showActions) ActionRow(session.canUndo, session.notesMode, session::undo, session::erase, session::toggleNotesMode, targets = targets)
            if (showPad) NumberPad(spec.maxDigit, if (spec.showRemaining) session::remaining else null, session.notesMode, session::input, targets = targets)
        }
    }
}
