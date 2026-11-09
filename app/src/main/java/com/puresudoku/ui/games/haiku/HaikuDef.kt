package com.puresudoku.ui.games.haiku

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.Difficulty
import com.puresudoku.core.GameKind
import com.puresudoku.core.ScoreRules
import com.puresudoku.core.Scoring
import com.puresudoku.core.words.HaikuPrompt
import com.puresudoku.core.words.HaikuRules
import com.puresudoku.core.words.WordData
import com.puresudoku.ui.components.GameIcon
import com.puresudoku.ui.games.GameDef
import com.puresudoku.ui.session.BonusLine
import com.puresudoku.ui.session.FxIds
import com.puresudoku.ui.session.MiddleStat
import com.puresudoku.ui.session.Outcome
import com.puresudoku.ui.session.Session
import com.puresudoku.ui.theme.LocalBoardColors
import com.puresudoku.ui.theme.Poppins
import com.puresudoku.ui.tutorial.DemoStage
import com.puresudoku.ui.tutorial.DemoTargets
import com.puresudoku.ui.tutorial.TutorialStep
import com.puresudoku.ui.tutorial.demoTarget
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.random.Random

data class TileFx(val id: Long, val word: String)

class HaikuSession(
    difficulty: Difficulty,
    val prompts: List<HaikuPrompt>,
    poemIndex: Int = 0,
    lines: List<List<String>> = List(3) { emptyList() },
    poems: List<String> = emptyList(),
) : Session(GameKind.HAIKU, difficulty) {
    val cfg = HaikuRules.config(difficulty)
    private val rules = ScoreRules(cfg.basePoints, cfg.parSeconds)
    val lines = lines.map { it.toMutableList() }
    val poems = poems.toMutableList()

    var poemIndex by mutableIntStateOf(poemIndex); private set
    var active by mutableIntStateOf(0); private set
    var version by mutableIntStateOf(0); private set
    var tileErrorFx by mutableStateOf<TileFx?>(null); private set
    var lineErrorFx by mutableStateOf(0L); private set
    var poemFx by mutableStateOf(0L); private set

    val prompt get() = prompts[poemIndex]
    fun count(k: Int) = HaikuRules.lineSyllables(lines[k])
    fun used(w: String) = lines.sumOf { l -> l.count { it == w } }

    override val usesStrikes get() = false
    override val middleStat get() = MiddleStat.Counter("Poem", "${minOf(poemIndex + 1, prompts.size)}/${prompts.size}")
    override val progress: Float get() = (poemIndex + lines.sumOf { HaikuRules.lineSyllables(it) } / 17f) / prompts.size
    override val keepsakes: List<String> get() = poems
    override val wonTitle get() = "Poet's work done!"

    init { active = (0..2).firstOrNull { count(it) != HaikuRules.LINES[it] } ?: 2 }

    fun selectLine(k: Int) { active = k; version++ }

    fun addWord(w: String) {
        if (outcome != Outcome.PLAYING) return
        if (cfg.noRepeats && used(w) > 0) { tileErrorFx = TileFx(FxIds.next(), w); nudge("On this level each word can only be used once"); return }
        val target = HaikuRules.LINES[active]
        val left = target - count(active)
        val syl = WordData.syllables(w)
        if (left == 0) { tileErrorFx = TileFx(FxIds.next(), w); nudge("Line ${active + 1} is full. Tap another line, or tap a word in it to remove it"); return }
        if (syl > left) {
            tileErrorFx = TileFx(FxIds.next(), w)
            nudge("\"$w\" has $syl syllable${if (syl > 1) "s" else ""} — only $left left in line ${active + 1}")
            return
        }
        lines[active] += w
        if (count(active) == target) {
            (0..2).firstOrNull { count(it) != HaikuRules.LINES[it] }?.let { active = it }
        }
        version++
        changed()
    }

    fun removeWord(line: Int, idx: Int) {
        if (outcome != Outcome.PLAYING) return
        lines[line].removeAt(idx)
        active = line
        version++
        changed()
    }

    fun clearLine() { if (outcome == Outcome.PLAYING && lines[active].isNotEmpty()) { lines[active].clear(); version++; changed() } }

    fun finishPoem() {
        if (outcome != Outcome.PLAYING) return
        for (k in 0..2) {
            val c = count(k); val t = HaikuRules.LINES[k]
            if (c != t) { active = k; lineErrorFx = FxIds.next(); version++; nudge("Line ${k + 1} needs $t syllables — it has $c"); return }
        }
        val all = lines.flatten()
        if (cfg.needSeason && all.none { it in prompt.seasonWords }) { lineErrorFx = FxIds.next(); nudge("Use at least one ${prompt.season.lowercase()} word (the tinted tiles)"); return }
        val missing = prompt.required.filter { it !in all }
        if (missing.isNotEmpty()) { lineErrorFx = FxIds.next(); nudge("Use the starred words: ${missing.joinToString(", ")}"); return }

        val text = lines.joinToString("\n") { l -> l.joinToString(" ").replaceFirstChar { it.uppercase() } } + "\n— ${prompt.season}"
        poems += text
        score += cfg.basePoints * (10 + all.toSet().size)
        poemFx = FxIds.next()
        if (poemIndex + 1 >= prompts.size) {
            val b = Scoring.finish(rules, elapsedMs, 0)
            finish(true, listOf(BonusLine("Time bonus", b.timeBonus)))
            return
        }
        poemIndex++
        lines.forEach { it.clear() }
        active = 0
        version++
        changed()
    }

    override fun saveState(): JSONObject = JSONObject()
        .put("poemIndex", poemIndex)
        .put("lines", JSONArray().also { a -> lines.forEach { a.put(JSONArray(it)) } })
        .put("poems", JSONArray(poems))
        .put("prompts", JSONArray().also { a ->
            prompts.forEach { p -> a.put(JSONObject().put("season", p.season).put("tiles", JSONArray(p.tiles)).put("sw", JSONArray(p.seasonWords.toList())).put("req", JSONArray(p.required))) }
        })
}

object HaikuDef : GameDef {
    override val kind = GameKind.HAIKU
    private val rng = Random(SecureRandom().nextLong())

    override fun generate(d: Difficulty): Session {
        val used = HashSet<String>()
        val prompts = synchronized(rng) { List(HaikuRules.config(d).poems) { HaikuRules.prompt(d, rng, used).also { p -> used += p.season } } }
        return HaikuSession(d, prompts)
    }

    override fun restore(o: JSONObject): Session {
        val d = Difficulty.valueOf(o.getString("difficulty"))
        val st = o.getJSONObject("state")
        fun strs(a: JSONArray) = List(a.length()) { a.getString(it) }
        val pa = st.getJSONArray("prompts")
        val prompts = List(pa.length()) { k -> pa.getJSONObject(k).let { HaikuPrompt(it.getString("season"), strs(it.getJSONArray("tiles")), strs(it.getJSONArray("sw")).toSet(), strs(it.getJSONArray("req"))) } }
        val la = st.getJSONArray("lines")
        return HaikuSession(d, prompts, st.getInt("poemIndex"), List(3) { strs(la.getJSONArray(it)) }, strs(st.getJSONArray("poems"))).restoreCommon(o)
    }

    override fun hint(d: Difficulty) = when {
        d == Difficulty.EASY -> "Tap tiles to fill each line: 5 syllables, then 7, then 5. Tap a placed word to remove it."
        HaikuRules.config(d).required > 0 -> "Fill lines of 5, 7 and 5 syllables. Use a season word and every starred word."
        else -> "Fill lines of 5, 7 and 5 syllables, and use at least one season word (tinted)."
    }

    override fun levelInfo(d: Difficulty) = HaikuRules.config(d).let { "${it.poems} poems · ${it.tiles} tiles" }
    override fun levelBlurb(d: Difficulty) = when (d) {
        Difficulty.EASY -> "Any words, just count syllables."
        Difficulty.MEDIUM -> "Include a season word."
        Difficulty.HARD -> "Season word, each word once."
        Difficulty.EXPERT -> "Fewer tiles, one must-use word."
        Difficulty.MASTER -> "Two must-use words."
        Difficulty.EXTREME -> "Tiny tray, three must-use words."
    }

    @Composable
    override fun Play(session: Session) = HaikuLayout(session as HaikuSession, null)

    @Composable
    override fun Icon(size: Dp) = GameIcon(kind, size)

    private fun demo(d: Difficulty = Difficulty.EASY, required: List<String> = emptyList()) = HaikuSession(
        d, listOf(HaikuPrompt("Spring",
            listOf("soft", "rain", "on", "petal", "the", "sparrow", "wakes", "morning", "cherry", "blossom", "falls", "a", "old", "pond"),
            setOf("rain", "petal", "sparrow", "cherry", "blossom", "morning"), required)),
    )

    override val tutorial: List<TutorialStep> = listOf(
        TutorialStep("5, 7, 5", "A haiku has three lines of 5, 7 and 5 syllables. Tap word tiles to add them to the highlighted line. The meter counts syllables for you.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                wait(400)
                for (w in listOf("soft", "rain", "on", "petal")) tap("tile-$w") { s.addWord(w) }
                wait(400)
                highlight(listOf("line-0"), 1300)
            }) { t -> HaikuLayout(s, t) }
        },
        TutorialStep("Finish the poem", "Fill the 7-syllable middle line and the last line of 5, then tap Finish poem. Your haiku are saved on this game's menu.") { active ->
            var s by remember { mutableStateOf(demo()) }
            DemoStage(active, script = {
                s = demo()
                for (w in listOf("soft", "rain", "on", "petal")) s.addWord(w)
                wait(500)
                for (w in listOf("the", "sparrow", "wakes", "the", "morning")) tap("tile-$w") { s.addWord(w) }
                for (w in listOf("cherry", "blossom", "falls")) tap("tile-$w") { s.addWord(w) }
                wait(500)
                tap("finish") { s.finishPoem() }
                wait(1500)
            }) { t -> HaikuLayout(s, t) }
        },
        TutorialStep("Rules get stricter", "Too many syllables and the tile shakes. From Medium up, use a season word (tinted tiles). Higher levels add starred must-use words and allow each word only once.") { active ->
            var s by remember { mutableStateOf(demo(Difficulty.EXPERT, listOf("blossom"))) }
            DemoStage(active, script = {
                s = demo(Difficulty.EXPERT, listOf("blossom"))
                wait(400)
                for (w in listOf("the", "soft", "rain", "on")) tap("tile-$w") { s.addWord(w) }
                tap("tile-blossom") { s.addWord("blossom") }
                wait(900)
                tap("placed-0-3") { s.removeWord(0, 3) }
                tap("tile-blossom") { s.addWord("blossom") }
                wait(1400)
            }) { t -> HaikuLayout(s, t) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HaikuLayout(s: HaikuSession, targets: DemoTargets?) {
    @Suppress("UNUSED_VARIABLE") val v = s.version
    val colors = LocalBoardColors.current
    val p = s.prompt
    val lineShake = remember { Animatable(0f) }
    LaunchedEffect(s.lineErrorFx) { if (s.lineErrorFx != 0L) lineShake.animateTo(0f, keyframes { durationMillis = 380; -10f at 50; 9f at 110; -6f at 170; 3f at 240; 0f at 380 }) }
    val poemPop = remember { Animatable(1f) }
    LaunchedEffect(s.poemFx) { if (s.poemFx != 0L) { poemPop.snapTo(0.9f); poemPop.animateTo(1f, spring(0.45f, Spring.StiffnessMediumLow)) } }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${p.season.uppercase()} · POEM ${minOf(s.poemIndex + 1, s.prompts.size)} OF ${s.prompts.size}", fontSize = 12.sp, letterSpacing = 1.3.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (p.required.isNotEmpty()) Text("Must use: " + p.required.joinToString("  ") { "★ $it" }, fontSize = 13.sp, color = colors.note, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        // the poem
        Column(
            Modifier.fillMaxWidth().offset { IntOffset(lineShake.value.dp.roundToPx(), 0) }
                .graphicsLayer { scaleX = poemPop.value; scaleY = poemPop.value }
                .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (k in 0..2) PoemLine(s, k, targets)
        }
        Spacer(Modifier.height(10.dp))
        // tray
        FlowRow(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            for (w in p.tiles) Tile(s, w, targets)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = s::clearLine) { Text("Clear line") }
            Button(onClick = s::finishPoem, modifier = Modifier.demoTarget(targets, "finish")) { Text("Finish poem") }
        }
        Spacer(Modifier.height(if (targets == null) 16.dp else 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoemLine(s: HaikuSession, k: Int, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val target = HaikuRules.LINES[k]
    val c = s.count(k)
    val isActive = s.active == k && s.outcome == Outcome.PLAYING
    val border by animateColorAsState(if (isActive) colors.entered else colors.thinLine, label = "line")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).demoTarget(targets, "line-$k")
            .clip(RoundedCornerShape(12.dp)).border(if (isActive) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(remember { MutableInteractionSource() }, null) { s.selectLine(k) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            s.lines[k].forEachIndexed { idx, w ->
                val pop = remember(w, idx) { Animatable(0.6f) }
                LaunchedEffect(w, idx) { pop.animateTo(1f, spring(0.45f, Spring.StiffnessMedium)) }
                Text(w, fontFamily = Poppins, fontSize = 17.sp, color = colors.given,
                    modifier = Modifier.demoTarget(targets, "placed-$k-$idx").graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clip(RoundedCornerShape(8.dp)).background(colors.related)
                        .clickable(remember { MutableInteractionSource() }, null) { s.removeWord(k, idx) }
                        .padding(horizontal = 7.dp, vertical = 2.dp))
            }
            if (s.lines[k].isEmpty()) Text(if (isActive) "tap words below…" else "", fontSize = 14.sp, color = colors.note)
        }
        // syllable meter
        Column(horizontalAlignment = Alignment.End) {
            Text("$c/$target", fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = if (c == target) colors.entered else colors.note)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(target) { i ->
                    val on = i < c
                    val col by animateColorAsState(if (on) colors.entered else colors.thinLine, tween(150), label = "dot")
                    Box(Modifier.size(6.dp).clip(CircleShape).background(col))
                }
            }
        }
    }
}

@Composable
private fun Tile(s: HaikuSession, w: String, targets: DemoTargets?) {
    val colors = LocalBoardColors.current
    val p = s.prompt
    val spent = s.cfg.noRepeats && s.used(w) > 0
    val season = w in p.seasonWords && s.cfg.needSeason
    val req = w in p.required
    val shake = remember { Animatable(0f) }
    val err = remember { Animatable(0f) }
    val fx = s.tileErrorFx
    LaunchedEffect(fx?.id) {
        if (fx != null && fx.word == w) {
            err.snapTo(1f)
            shake.animateTo(0f, keyframes { durationMillis = 380; -9f at 50; 8f at 110; -6f at 170; 3f at 240; 0f at 380 })
            err.animateTo(0f, tween(400))
        }
    }
    val bg = when { err.value > 0f -> colors.errorBg; season -> colors.sameDigit; else -> MaterialTheme.colorScheme.surface }
    Row(
        Modifier.demoTarget(targets, "tile-$w")
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .graphicsLayer { alpha = if (spent) 0.35f else 1f }
            .clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (req) Modifier.border(2.dp, colors.entered, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null) { s.addWord(w) }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (req) Text("★ ", color = colors.entered, fontSize = 14.sp)
        Text(w, fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = if (err.value > 0f) colors.error else colors.given)
        Spacer(Modifier.width(4.dp))
        Text("${WordData.syllables(w)}", fontSize = 11.sp, color = colors.note)
    }
}
