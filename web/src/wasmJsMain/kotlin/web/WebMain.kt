package com.puresudoku.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.puresudoku.core.words.WordData
import com.puresudoku.data.ThemeMode
import com.puresudoku.platform.WebBackupHost
import com.puresudoku.platform.WebStore
import com.puresudoku.ui.AppViewModel
import com.puresudoku.ui.PureGamesApp
import com.puresudoku.ui.games.board.isWorkerContext
import com.puresudoku.ui.games.board.startAiWorker
import com.puresudoku.ui.theme.loadPoppins
import com.puresudoku.web.res.Res
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.w3c.fetch.Response

private val FALLBACK_FONTS = listOf("noto_sans_symbols2_ui.ttf", "noto_sans_math_ui.ttf", "noto_sans_sc_pieces.ttf", "noto_emoji_ui.ttf")
private val WORD_FILES = listOf("lexicon.tsv", "clues.tsv", "thesaurus.tsv", "themes.tsv", "pairs.tsv", "haiku.tsv")

private suspend fun fetchText(url: String): String {
    val r = window.fetch(url).await<Response>()
    if (!r.ok) error("Could not load $url (${r.status})")
    return r.text().await<JsString>().toString()
}

@JsFun("(name, text) => { const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], {type: 'application/json'})); a.download = name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000); }")
private external fun downloadText(name: String, text: String)

@JsFun("(done) => { const i = document.createElement('input'); i.type = 'file'; i.accept = '.json,application/json,text/plain'; i.onchange = () => { const f = i.files && i.files[0]; if (!f) return; f.text().then(t => done(t), () => done(null)); }; i.click(); }")
private external fun pickTextFile(done: (String?) -> Unit)

@JsFun("(cb) => document.addEventListener('visibilitychange', () => cb(document.visibilityState === 'visible'))")
private external fun onVisibility(cb: (Boolean) -> Unit)

@JsFun("(cb) => window.addEventListener('pagehide', () => cb())")
private external fun onPageHide(cb: () -> Unit)

@JsFun("() => new URL(window.location.href).searchParams.get('game')")
private external fun inviteGame(): String?
@JsFun("() => new URL(window.location.href).searchParams.get('room')")
private external fun inviteRoom(): String?
@JsFun("() => { const u = new URL(window.location.href); u.searchParams.delete('game'); u.searchParams.delete('room'); history.replaceState(history.state, '', u.pathname + u.search + u.hash); }")
private external fun consumeInvite()

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    if (isWorkerContext()) { startAiWorker(); return }
    MainScope().launch {
        // Fonts and word lists load before the first frame: the shared code reads them synchronously.
        val words = WORD_FILES.map { name -> async { name to runCatching { fetchText("words/$name") }.getOrDefault("") } }
        val medium = async { Res.readBytes("font/poppins_medium.ttf") }
        val bold = async { Res.readBytes("font/poppins_bold.ttf") }
        val fallbacks = FALLBACK_FONTS.map { f -> async { runCatching { Res.readBytes("font/$f") }.getOrNull() } }
        runCatching { loadPoppins(medium.await(), bold.await(), fallbacks.mapNotNull { it.await() }) }.onFailure { println("Fonts: $it") }
        val text = words.associate { it.await() }
        WordData.init { name -> text[name] ?: error("Missing word file $name") }

        val vm = AppViewModel(WebStore("pg."), WebBackupHost())
        val invite = com.puresudoku.online.GameInvite.parse(inviteGame(), inviteRoom())
        if (invite != null) { consumeInvite(); vm.joinInvite(invite) }
        vm.pickBackupFolder = {
            downloadText("puregames-backup-" + com.puresudoku.platform.formatDate(com.puresudoku.platform.nowMillis(), "yyyy-MMM-d") + ".json", vm.exportBackup())
            vm.backupMessage = "Backup file downloaded"
        }
        vm.pickBackupFile = { pickTextFile { vm.onBackupText(it) } }
        // The clock only runs while the page is showing; hiding or closing it saves.
        onVisibility { vm.setForeground(it) }
        onPageHide { vm.setForeground(false) }

        com.puresudoku.ui.games.board.AiWorker.prewarm()
        document.getElementById("loading")?.remove()
        ComposeViewport(document.body!!) {
            // Register the fallback fonts so text with suits, arrows or piece names finds its glyphs.
            val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
            var fontsReady by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                for (f in com.puresudoku.ui.theme.fallbackFamilies) runCatching { resolver.preload(f) }.onFailure { println("Font fallback: $it") }
                fontsReady = true
            }
            if (!fontsReady) return@ComposeViewport
            val dark = when (vm.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PureGamesApp(vm, dark)
        }
    }
}
