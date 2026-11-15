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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.unit.dp
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
    private val holder: com.puresudoku.platform.AndroidAppViewModel by viewModels()
    private val vm: AppViewModel get() = holder.vm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.puresudoku.core.words.WordData.init { name -> assets.open("words/$name").reader().use { it.readText() } }
        Thread { runCatching { com.puresudoku.core.words.WordData.lexicon; com.puresudoku.core.words.WordData.clues } }.start()
        enableEdgeToEdge()

        // Backup folder lives outside the app, so it survives uninstalling.
        val folderPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                vm.onBackupFolderPicked(uri.toString())
            }
        }
        val filePicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.onBackupFilePicked(uri.toString())
        }
        vm.pickBackupFolder = { folderPicker.launch(null) }
        vm.pickBackupFile = { filePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }

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
            com.puresudoku.ui.PureGamesApp(vm, dark)
        }
    }
}
