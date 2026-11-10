package com.puresudoku

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.puresudoku.data.ThemeMode
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.Screen
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.screens.GameMenuScreen
import com.puresudoku.ui.screens.GeneratingScreen
import com.puresudoku.ui.screens.HubScreen
import com.puresudoku.ui.screens.LevelScreen
import com.puresudoku.ui.screens.PlayScreen
import com.puresudoku.ui.screens.SlotsScreen
import com.puresudoku.ui.tutorial.TutorialOverlay
import com.puresudoku.ui.theme.PureSudokuTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.puresudoku.core.words.WordData.init { name -> assets.open("words/$name").reader() }
        Thread { runCatching { com.puresudoku.core.words.WordData.lexicon; com.puresudoku.core.words.WordData.clues } }.start()
        enableEdgeToEdge()

        // The solve clock only runs while the app is on screen; leaving autosaves.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = vm.setForeground(true)
            override fun onPause(owner: LifecycleOwner) = vm.setForeground(false)
        })

        setContent {
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep status/navigation bar icons readable when the in-app theme overrides the system one.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
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
                            is Screen.Generating -> GeneratingScreen(s.kind, s.difficulty)
                            Screen.Playing -> PlayScreen(vm)
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
    }
}
