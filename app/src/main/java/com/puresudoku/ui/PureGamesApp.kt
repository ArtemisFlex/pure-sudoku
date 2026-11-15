package com.puresudoku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.screens.GameMenuScreen
import com.puresudoku.ui.screens.GeneratingScreen
import com.puresudoku.ui.screens.HubScreen
import com.puresudoku.ui.screens.LevelScreen
import com.puresudoku.ui.screens.PlayScreen
import com.puresudoku.ui.screens.SlotsScreen
import com.puresudoku.ui.theme.PureSudokuTheme
import com.puresudoku.ui.tutorial.TutorialOverlay

/** The whole app: every screen, the backup dialogs and the how-to-play cards. Shared by Android and the browser. */
@Composable
fun PureGamesApp(vm: AppViewModel, dark: Boolean) {
    PureSudokuTheme(dark = dark) {
        // Surface supplies the theme's content colour (light text/icons in dark mode).
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BackHandler(enabled = vm.screen != Screen.Hub) { vm.back() }
            AnimatedContent(
                targetState = vm.screen,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 1.02f))
                },
                label = "screens",
            ) { s ->
                when (s) {
                    Screen.Hub -> HubScreen(vm)
                    is Screen.Category -> com.puresudoku.ui.screens.CategoryScreen(vm, s.category)
                    is Screen.Menu -> GameMenuScreen(vm, s.kind)
                    is Screen.PickLevel -> LevelScreen(vm, s.kind)
                    is Screen.Slots -> SlotsScreen(vm, s.kind, s.replaceFor)
                    is Screen.Online -> com.puresudoku.ui.screens.OnlineScreen(vm, s.kind)
                    is Screen.Generating -> GeneratingScreen(s.kind, s.difficulty)
                    Screen.Playing -> PlayScreen(vm)
                }
            }
            vm.pendingRestore?.let { (_, at) ->
                        androidx.compose.material3.AlertDialog(
                    onDismissRequest = { vm.resolvePendingRestore(false) },
                    title = { androidx.compose.material3.Text("Restore backup?") },
                    text = { androidx.compose.material3.Text("Found a backup from ${com.puresudoku.platform.formatDate(at, "MMM d, yyyy h:mm a")}. Restoring replaces the saves, stats and leaderboards here with the ones in the backup.") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.resolvePendingRestore(true) }) { androidx.compose.material3.Text("Restore") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.resolvePendingRestore(false) }) { androidx.compose.material3.Text("Keep current data") } },
                )
            }
            vm.backupMessage?.let { msg ->
                androidx.compose.runtime.LaunchedEffect(msg) { kotlinx.coroutines.delay(2600); vm.backupMessage = null }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                    androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface) {
                        androidx.compose.material3.Text(msg, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = vm.tutorialFor != null,
                enter = fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.94f),
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f),
            ) {
                // Keep the last game shown while the exit animation plays.
                val kind = remember { mutableStateOf(vm.tutorialFor) }
                vm.tutorialFor?.let { kind.value = it }
                kind.value?.let { k -> TutorialOverlay(k, Games.of(k).tutorial, onClose = vm::closeTutorial) }
            }
        }
    }
}
