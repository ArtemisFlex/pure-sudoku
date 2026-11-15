package com.puresudoku.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameCategory
import com.puresudoku.core.GameKind
import com.puresudoku.platform.nowMillis
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.games.Games
import com.puresudoku.ui.games.HeaderActions
import com.puresudoku.ui.games.board.DuelSession
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.PureSudokuTheme
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Saves live in the browser's local storage, one game per kind. */
private object WebSaves {
    private fun key(kind: GameKind) = "puregames.save.${kind.name}"
    fun load(kind: GameKind): String? = runCatching { window.localStorage.getItem(key(kind)) }.getOrNull()
    fun save(s: Session) {
        val json = runCatching { s.toJson().toString() }.onFailure { println("save failed: $it") }.getOrNull() ?: return
        runCatching { window.localStorage.setItem(key(s.kind), json) }
    }
    fun clear(kind: GameKind) { runCatching { window.localStorage.removeItem(key(kind)) } }
}

private sealed interface Screen {
    data object Hub : Screen
    data class Levels(val kind: GameKind) : Screen
    data class Play(val session: Session) : Screen
}

@Composable
fun WebApp() {
    PureSudokuTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var screen by remember { mutableStateOf<Screen>(Screen.Hub) }
            fun start(s: Session, restored: Boolean) {
                s.onChanged = { WebSaves.save(s) }
                s.onFinished = { WebSaves.clear(s.kind) }
                screen = Screen.Play(s)
                if (restored) (s as? DuelSession)?.resume()
                WebSaves.save(s)
            }
            when (val sc = screen) {
                Screen.Hub -> Hub { screen = Screen.Levels(it) }
                is Screen.Levels -> Levels(sc.kind, back = { screen = Screen.Hub },
                    resume = { json -> runCatching { Games.of(sc.kind).restore(JSONObject(json)) }.getOrNull()?.let { start(it, true) } },
                    new = { d -> start(Games.of(sc.kind).generate(d), false) })
                is Screen.Play -> Play(sc.session) { WebSaves.save(sc.session); screen = Screen.Levels(sc.session.kind) }
            }
        }
    }
}

@Composable
private fun Hub(open: (GameKind) -> Unit) {
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                Text("PureGames", style = MaterialTheme.typography.headlineLarge)
                Text("Browser preview: the Android app's own games, running in the page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (cat in listOf(GameCategory.STRATEGY) + GameCategory.entries.filter { it != GameCategory.STRATEGY }) {
            val defs = Games.all.filter { it.kind.category == cat }
            if (defs.isEmpty()) continue
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 14.dp)) {
                    Text(cat.title, style = MaterialTheme.typography.titleLarge)
                    Text(cat.blurb, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(defs, key = { it.kind.name }) { def ->
                Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open(def.kind) }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        def.Icon(64.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(def.kind.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        Text(def.kind.skill, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Levels(kind: GameKind, back: () -> Unit, resume: (String) -> Unit, new: (Difficulty) -> Unit) {
    val def = Games.of(kind)
    val saved = remember(kind) { WebSaves.load(kind) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                def.Icon(40.dp)
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(kind.title, style = MaterialTheme.typography.headlineSmall)
                    Text(kind.tagline, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (saved != null) {
                Button(onClick = { resume(saved) }, Modifier.fillMaxWidth()) { Text("Continue saved game") }
                Spacer(Modifier.height(12.dp))
            }
            for (d in Difficulty.entries) {
                OutlinedButton(onClick = { new(d) }, Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(d.label + " · " + def.levelInfo(d), style = MaterialTheme.typography.titleSmall)
                        Text(def.levelBlurb(d), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Play(session: Session, leave: () -> Unit) {
    val def: GameDef = Games.of(session.kind)
    val playing = session.outcome == Outcome.PLAYING
    LaunchedEffect(session) {
        var last = nowMillis()
        while (true) {
            delay(100)
            val now = nowMillis()
            if (session.outcome != Outcome.PLAYING) break
            val dt = now - last; last = now
            session.elapsedMs += dt
            session.onTick(dt)
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val backdrop = def.background(session)
        val art = if (backdrop != 0) backdropArt(backdrop) else null to null
        // Pixel-art backdrops stay crisp with nearest-neighbour scaling; vector backdrops scale smoothly.
        art.first?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None) }
        art.second?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        // Measure the backdrop for readable text only once its art has actually loaded.
        val loaded = (art.first?.width ?: 0) > 1 || (art.second?.intrinsicSize?.width?.let { it.isFinite() && it > 1f } == true)
        val aspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        if (def.fullBleed) Box(Modifier.fillMaxSize()) { def.Play(session) }
        com.puresudoku.ui.components.ShadowedText(if (loaded) backdrop else 0, aspect, { _, w, h -> renderBackdrop(art.first, art.second, w, h) }) {
        // On a wide screen the game keeps a phone-like column, centred.
        Column(Modifier.fillMaxSize().widthIn(max = 640.dp).padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.puresudoku.ui.components.BackdropChrome(enabled = !def.hasHeader) {
            if (def.hasHeader) def.Header(session, HeaderActions(back = leave, info = {}, pause = {}, paused = false, canPause = false))
            else Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(session.kind.title, style = MaterialTheme.typography.titleLarge)
                    Text("${session.difficulty.label} · ${formatTime(session.elapsedMs)} · score ${session.score}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(48.dp))
            }
            if (!def.fullBleed) Text(def.hint(session.difficulty), fontSize = 13.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (!def.fullBleed) def.Play(session)
                if (session !is com.puresudoku.ui.games.realm.RealmSession) com.puresudoku.ui.components.HeraldOverlay(session, def, Modifier.align(Alignment.TopCenter))
            }
        }
        }
        if (!playing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Card(Modifier.padding(24.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (session.outcome == Outcome.WON) "You won!" else "Game over", style = MaterialTheme.typography.headlineSmall)
                    Text("Score ${session.score} · ${formatTime(session.elapsedMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = leave) { Text("Back to levels") }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    return "$m:" + (s % 60).toString().padStart(2, '0')
}
