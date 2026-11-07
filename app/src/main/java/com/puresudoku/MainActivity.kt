package com.puresudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.puresudoku.ui.GameViewModel
import com.puresudoku.ui.Screen
import com.puresudoku.ui.screens.DifficultyScreen
import com.puresudoku.ui.screens.GameScreen
import com.puresudoku.ui.screens.GeneratingScreen
import com.puresudoku.ui.screens.HomeScreen
import com.puresudoku.ui.screens.SlotsScreen
import com.puresudoku.ui.theme.PureSudokuTheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The solve clock only runs while the app is on screen; leaving autosaves.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = vm.setForeground(true)
            override fun onPause(owner: LifecycleOwner) = vm.setForeground(false)
        })

        setContent {
            PureSudokuTheme {
                // Surface supplies the theme's content colour (light text/icons in dark mode).
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }
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
                            Screen.Home -> HomeScreen(vm)
                            Screen.PickDifficulty -> DifficultyScreen(vm)
                            is Screen.Slots -> SlotsScreen(vm, s.replaceFor)
                            is Screen.Generating -> GeneratingScreen(s.difficulty)
                            Screen.Playing -> GameScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
