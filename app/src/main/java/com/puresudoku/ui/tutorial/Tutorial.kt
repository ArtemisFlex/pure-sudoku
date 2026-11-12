package com.puresudoku.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.GameKind
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * One how-to-play card: a caption and a live demo playing the real game UI.
 * [demo] receives whether this card is the one on screen (so only it animates).
 */
class TutorialStep(
    val title: String,
    val body: String,
    val demo: @Composable (active: Boolean) -> Unit,
)

/** Full-screen deck of how-to-play cards. Swipe or use the buttons. */
@Composable
fun TutorialOverlay(kind: GameKind, steps: List<TutorialStep>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == steps.lastIndex
    val hasBook = com.puresudoku.ui.games.realm.Rulebooks.has(kind)
    var showBook by remember(kind) { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HOW TO PLAY", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                    Text(kind.title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                if (hasBook) {
                    Surface(
                        onClick = { showBook = true },
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF3A2410),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC9972F)),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = Color(0xFFF6E3A1), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rulebook", color = Color(0xFFF6E3A1), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = 14.dp,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                val dist = abs(offset).coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = 1f - 0.08f * dist
                                scaleX = s; scaleY = s
                                alpha = 1f - 0.45f * dist
                                rotationZ = offset.coerceIn(-1f, 1f) * -2.5f
                            },
                    ) {
                        StepCard(steps[page], page, steps.size, active = pager.currentPage == page && !pager.isScrollInProgress)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose, modifier = Modifier.width(96.dp)) { Text(if (last) "" else "Skip", color = Color.White) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(steps.size) { i ->
                        val selected = i == pager.currentPage
                        val w by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dot")
                        val c by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.4f), label = "dotc")
                        Box(Modifier.padding(horizontal = 3.dp).size(w, 8.dp).clip(RoundedCornerShape(50)).background(c))
                    }
                }
                Button(
                    onClick = { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.width(96.dp),
                ) { Text(if (last) "Play" else "Next") }
            }
        }
        if (showBook) com.puresudoku.ui.games.realm.RulebookScreen(kind, onClose = { showBook = false })
    }
}

@Composable
private fun StepCard(step: TutorialStep, index: Int, total: Int, active: Boolean) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            step.demo(active)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "STEP ${index + 1} OF $total", fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(step.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(step.body, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
