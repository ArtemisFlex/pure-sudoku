package com.puresudoku.ui.training

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.ui.games.board.DuelSession
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.accent

/** One game's teaching: how it works, then strategy for each of the six levels. */
class Course(val basics: List<String>, val levels: List<List<String>>) {
    init { require(levels.size == 6) }
}

object Lessons {
    private val all: Map<GameKind, Course> by lazy { NumberLessons.all + WordLessons.all + CardLessons.all + BoardLessons.all }
    fun of(kind: GameKind): Course? = all[kind]

    private val levelTitles = listOf("First steps", "Building habits", "Thinking ahead", "Sharper technique", "Advanced play", "Mastery")
    fun pages(kind: GameKind, d: Difficulty): List<Pair<String, List<String>>> {
        val c = of(kind) ?: return listOf("Coach" to listOf("Play this fixed game at your own pace. It isn't saved or scored."))
        return listOf("How it works" to c.basics, "${d.label}: ${levelTitles[d.ordinal]}" to c.levels[d.ordinal])
    }
}

/**
 * The training coach: lesson pages for this game and level, plus "Show me a move"
 * for games with a computer opponent. Collapses to a small pill.
 */
@Composable
fun CoachPanel(session: Session, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val pages = remember(session.kind, session.difficulty) { Lessons.pages(session.kind, session.difficulty) }
    var page by remember { mutableIntStateOf(0) }
    val duel = session as? DuelSession
    if (session.lesson != null) LaunchedEffect(session) {
        while (true) { kotlinx.coroutines.delay(250); session.checkLesson() }
    }
    if (duel != null) LaunchedEffect(duel.moves) { if (duel.guideStep == null) { duel.coachTip = null; duel.coachFrom = -1; duel.coachTo = -1 } }
    Box(modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedContent(open, label = "coach") { isOpen ->
            if (!isOpen) {
                Surface(onClick = onToggle, shape = RoundedCornerShape(50), color = session.difficulty.accent(), shadowElevation = 6.dp) {
                    Text("Coach", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            } else {
                val step = duel?.guideStep
                if (duel != null && step != null) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(session.difficulty.accent()))
                            Spacer(Modifier.width(8.dp))
                            Text("Lesson · step ${duel.guideAt + 1} of ${duel.guide?.size ?: 0}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = onToggle) { Text("Hide") }
                        }
                        Text(step.text, fontSize = 15.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(10.dp))
                        if (step.info) Button(onClick = duel::guideNext, modifier = Modifier.align(Alignment.End)) { Text("Next") }
                        else Text("Make the move ringed in gold.", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                } else if (session.lessonStep != null) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                    val ls = session.lessonStep!!
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(session.difficulty.accent()))
                            Spacer(Modifier.width(8.dp))
                            Text("Lesson · step ${session.lessonAt + 1} of ${session.lesson?.size ?: 0}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = onToggle) { Text("Hide") }
                        }
                        Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) { Text(ls.text, fontSize = 15.sp, lineHeight = 21.sp) }
                        Spacer(Modifier.height(10.dp))
                        if (ls.done == null) Button(onClick = session::lessonNext, modifier = Modifier.align(Alignment.End)) { Text("Next") }
                        else Text("Do it on the board to continue.", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                } else Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(session.difficulty.accent()))
                            Spacer(Modifier.width(8.dp))
                            Text(pages[page].first, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = onToggle) { Text("Hide") }
                        }
                        Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                            for (tip in pages[page].second) {
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text("•", fontWeight = FontWeight.Bold, color = session.difficulty.accent(), modifier = Modifier.width(14.dp))
                                    Text(tip, fontSize = 14.sp, lineHeight = 19.sp)
                                }
                            }
                        }
                        duel?.coachTip?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (pages.size > 1) {
                                OutlinedButton(onClick = { page = (page + pages.size - 1) % pages.size }) { Text("‹") }
                                Text("${page + 1}/${pages.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { page = (page + 1) % pages.size }) { Text("›") }
                            }
                            Spacer(Modifier.weight(1f))
                            if (duel != null && session.outcome == Outcome.PLAYING)
                                FilledTonalButton(onClick = duel::askCoach) { Text("Show me a move") }
                        }
                    }
                }
            }
        }
    }
}

/** End of a training game: nothing is recorded, so just offer another go. */
@Composable
fun TrainingEndCard(session: Session, onAgain: () -> Unit, onLeave: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp, modifier = Modifier.padding(24.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (session.outcome) { Outcome.WON -> "Lesson complete!"; Outcome.DRAW -> "A draw: well played"; else -> "Not this time" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (session.outcome) {
                    Outcome.WON -> "You beat the ${session.difficulty.label} training game. Try a real ${session.difficulty.label} game, or train on the next level up."
                    else -> "Training games always start the same way, so you can try again with what you learned."
                },
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text("Training games aren't saved or counted.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLeave) { Text("Menu") }
                Button(onClick = onAgain) { Text("Train again") }
            }
        }
    }
}
