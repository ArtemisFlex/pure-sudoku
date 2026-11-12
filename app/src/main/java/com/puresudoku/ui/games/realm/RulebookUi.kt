package com.puresudoku.ui.games.realm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puresudoku.core.GameKind
import com.puresudoku.ui.components.gameLogoRes
import com.puresudoku.ui.games.board.glyph
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// =====================================================================  the book model

/** One block of a rulebook page. */
internal sealed class RB {
    class Chapter(val title: String) : RB()
    class Para(val text: String, val drop: Boolean = false) : RB()
    class Bullets(val items: List<String>) : RB()
    class Note(val title: String, val text: String, val warn: Boolean = false) : RB()
    class Costs(val rows: List<CostRow>) : RB()
    class Figure(val panels: List<Panel>, val caption: String, val height: Int = 180) : RB()
    class Steps(val items: List<Pair<String, String>>) : RB()
    class Table(val head: List<String>, val rows: List<List<String>>) : RB()
    class Spells(val items: List<Triple<RI, String, String>>) : RB()
    /** Painted trophies on display: (drawable, name, text). */
    class Trophies(val items: List<Triple<Int, String, String>>) : RB()
    object Terrains : RB()
    object Dice : RB()
}

internal enum class Piece { VILLAGE, TOWER, ROAD, WALL, CASTLE, KNIGHT, SPELL, CLAIM }

internal class CostRow(val name: String, val piece: Piece, val cost: IntArray?, val note: String)

internal class Panel(val scene: Scene, val label: String? = null)

internal class Book(val logo: Int, val accent: Color, val motto: String, val blocks: List<RB>)

// ---------------------------------------------------------------- mini boards for figures

/** Terrain codes for figure tiles: 0-4 the resources, then these. */
internal const val T_WASTE = 5
internal const val T_WATER = 6
internal const val T_FOG = 7
internal const val T_LAIR = 8
internal const val T_BARREN = 9

internal class SHex(
    val q: Int, val r: Int, val t: Int, val n: Int, val owner: Int, val castle: Int, val knights: Int,
    val level: Int, val half: Boolean, val dim: Boolean, val glow: Color?, val yields: IntArray?, val variant: Int,
)
/** A corner piece: level 1 village, 2 tower, 0 a glowing open spot, -1 forbidden, 3 a ley stone. */
internal class SCorner(val h: Int, val k: Int, val owner: Int, val level: Int)
/** A side piece: 0 road, 1 wall, 2 rubble, 3 a glowing open spot. */
internal class SSide(val h: Int, val k: Int, val kind: Int, val owner: Int)
internal class SArrow(val path: List<Int>, val color: Color, val dashed: Boolean, val blocked: Boolean)
internal class SMark(val h: Int, val text: String, val bg: Color, val corner: Int, val side: Int, val dx: Float, val dy: Float)

internal class Scene(
    val hexes: List<SHex>, val corners: List<SCorner>, val sides: List<SSide>, val dragons: List<Pair<Int, Float>>,
    val arrows: List<SArrow>, val marks: List<SMark>, val night: Boolean,
)

internal class SB {
    private val hexes = ArrayList<SHex>(); private val corners = ArrayList<SCorner>(); private val sides = ArrayList<SSide>()
    private val dragons = ArrayList<Pair<Int, Float>>(); private val arrows = ArrayList<SArrow>(); private val marks = ArrayList<SMark>()
    var night = false

    fun hex(q: Int, r: Int, t: Int, n: Int = 0, owner: Int = -1, castle: Int = 0, knights: Int = 0, level: Int = 0,
            half: Boolean = false, dim: Boolean = false, glow: Color? = null, yields: IntArray? = null, variant: Int = 0): Int {
        hexes += SHex(q, r, t, n, owner, castle, knights, level, half, dim, glow, yields, variant); return hexes.lastIndex
    }
    fun village(h: Int, k: Int, o: Int = 0) { corners += SCorner(h, k, o, 1) }
    fun tower(h: Int, k: Int, o: Int = 0) { corners += SCorner(h, k, o, 2) }
    fun spot(h: Int, k: Int) { corners += SCorner(h, k, 0, 0) }
    fun no(h: Int, k: Int) { corners += SCorner(h, k, 0, -1) }
    fun ley(h: Int, k: Int) { corners += SCorner(h, k, 0, 3) }
    fun road(h: Int, k: Int, o: Int = 0) { sides += SSide(h, k, 0, o) }
    fun wall(h: Int, k: Int, o: Int = 0) { sides += SSide(h, k, 1, o) }
    fun rubble(h: Int, k: Int) { sides += SSide(h, k, 2, -1) }
    fun open(h: Int, k: Int) { sides += SSide(h, k, 3, 0) }
    fun dragon(h: Int, scale: Float = 1f) { dragons += h to scale }
    fun arrow(vararg path: Int, color: Color = RIVAL, dashed: Boolean = false, blocked: Boolean = false) { arrows += SArrow(path.toList(), color, dashed, blocked) }
    fun mark(h: Int, text: String, bg: Color = INK, corner: Int = -1, side: Int = -1, dx: Float = 0f, dy: Float = 0f) { marks += SMark(h, text, bg, corner, side, dx, dy) }
    fun build() = Scene(hexes, corners, sides, dragons, arrows, marks, night)
}

internal fun scene(block: SB.() -> Unit): Scene = SB().apply(block).build()

internal val GOOD = Color(0xFF2E7D32)
internal val BAD = Color(0xFFC62828)
internal val GOLDEN = Color(0xFFC9972F)

// =====================================================================  the screen

/** Which games have a rulebook. */
internal object Rulebooks {
    fun has(kind: GameKind) = kind in setOf(GameKind.REALMFORGE, GameKind.REALM_DRAGON, GameKind.REALM_NIGHT, GameKind.REALM_ASH, GameKind.WISTERIA, GameKind.ASHFALL, GameKind.KREASHUN, GameKind.VEILGUARD)
    fun of(kind: GameKind): Book? = when (kind) {
        GameKind.REALMFORGE, GameKind.REALM_DRAGON, GameKind.REALM_NIGHT -> RulebookText.eiseria(kind)
        GameKind.REALM_ASH -> RulebookText.eiseriaAshfall()
        GameKind.WISTERIA -> RulebookText.wisteria()
        GameKind.ASHFALL -> RulebookText.ashfall()
        GameKind.KREASHUN -> RulebookText.kreashun()
        GameKind.VEILGUARD -> RulebookText.veilguard()
        else -> null
    }
}

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII")

/** A full-screen illustrated rulebook. */
@Composable
fun RulebookScreen(kind: GameKind, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val book = remember(kind) { Rulebooks.of(kind) } ?: return
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val art = rememberRealmArt()
    val knight = ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_knight_piece)
    val water = ImageBitmap.imageResource(com.puresudoku.R.drawable.rt_water)
    val chapters = remember(book) { book.blocks.withIndex().filter { it.value is RB.Chapter }.map { it.index to (it.value as RB.Chapter).title } }
    val chapterNo = remember(book) { chapters.withIndex().associate { (i, c) -> c.first to i } }
    val showTop by remember { derivedStateOf { list.firstVisibleItemIndex > 1 } }
    val pageShape = RoundedCornerShape(18.dp)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A1A0E), Color(0xFF120B05))))
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            // wooden title band
            Row(
                Modifier.fillMaxWidth().wood(RoundedCornerShape(14.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close the rulebook", tint = Color(0xFFF6E3A1))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("THE RULEBOOK", color = Color(0xFFF6E3A1).copy(alpha = 0.8f), fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = SERIF, fontWeight = FontWeight.Bold)
                    Text(kind.title, color = Color(0xFFFFF4DE), fontSize = 19.sp, fontFamily = SERIF, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { scope.launch { list.animateScrollToItem(1) } }, contentAlignment = Alignment.Center) {
                    Text("☰", color = Color(0xFFF6E3A1), fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.fillMaxSize().clip(pageShape)
                        .background(Brush.verticalGradient(listOf(PARCH_TOP, Color(0xFFF1E2BE), PARCH_BOT)))
                        .border(2.dp, GOLD, pageShape)
                        .padding(4.dp)
                        .border(0.8.dp, GOLD.copy(alpha = 0.6f), RoundedCornerShape(15.dp)),
                ) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item { Cover(book, kind) }
                        item { Contents(chapters, book.accent) { i -> scope.launch { list.animateScrollToItem(2 + i) } } }
                        itemsIndexed(book.blocks) { i, b ->
                            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                                Block(b, book.accent, chapterNo[i], art, knight, water)
                            }
                        }
                        item { Colophon(book.accent) }
                    }
                }
                if (showTop) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(14.dp).size(44.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFF2D4466), Color(0xFF0F1D33))))
                            .border(2.dp, Brush.linearGradient(listOf(Color(0xFFF6E3A1), GOLD)), CircleShape)
                            .clickable { scope.launch { list.animateScrollToItem(1) } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Back to contents", tint = Color(0xFFF6E3A1)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- page furniture

@Composable
private fun Ornament(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val y = size.height / 2; val cx = size.width / 2; val half = min(size.width * 0.38f, 150.dp.toPx())
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, GOLD), cx - half, cx - 14.dp.toPx()), Offset(cx - half, y), Offset(cx - 14.dp.toPx(), y), 1.4.dp.toPx())
        drawLine(Brush.horizontalGradient(listOf(GOLD, Color.Transparent), cx + 14.dp.toPx(), cx + half), Offset(cx + 14.dp.toPx(), y), Offset(cx + half, y), 1.4.dp.toPx())
        val d = 6.dp.toPx()
        val dia = Path().apply { moveTo(cx, y - d); lineTo(cx + d, y); lineTo(cx, y + d); lineTo(cx - d, y); close() }
        drawPath(dia, accent); drawPath(dia, GOLD, style = Stroke(1.2.dp.toPx()))
        for (s in listOf(-1f, 1f)) drawCircle(GOLD, 2.dp.toPx(), Offset(cx + s * 10.5f.dp.toPx(), y))
    }
}

@Composable
private fun Cover(book: Book, kind: GameKind) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(gameLogoRes(kind)), contentDescription = kind.title, modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 200.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(6.dp))
        Ornament(book.accent)
        Text("THE COMPLETE RULES", fontFamily = SERIF, fontWeight = FontWeight.Bold, color = book.accent, fontSize = 13.sp, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Text(book.motto, fontFamily = SERIF, fontStyle = FontStyle.Italic, color = INK_SOFT, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (r in 0..4) ResImg(r, 30.dp) }
    }
}

@Composable
private fun Contents(chapters: List<Pair<Int, String>>, accent: Color, onJump: (Int) -> Unit) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 10.dp)
            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .border(1.dp, GOLD.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("Contents", fontFamily = SERIF, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = INK, modifier = Modifier.align(Alignment.CenterHorizontally))
        Ornament(accent)
        for ((n, c) in chapters.withIndex()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onJump(c.first) }.padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ROMAN.getOrElse(n) { "${n + 1}" }, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = accent, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                Text(c.second, fontFamily = SERIF, color = INK, fontSize = 15.5.sp, modifier = Modifier.weight(1f))
                Text("›", fontFamily = SERIF, color = GOLD, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Colophon(accent: Color) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ornament(accent)
        Text("Here end the rules. May your banners fly high.", fontFamily = SERIF, fontStyle = FontStyle.Italic, color = INK_SOFT, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

private fun rich(text: String, drop: Color?): AnnotatedString = buildAnnotatedString {
    var t = text
    if (drop != null && t.isNotEmpty()) {
        withStyle(SpanStyle(fontSize = 40.sp, color = drop, fontWeight = FontWeight.Bold, fontFamily = SERIF)) { append(t[0]) }
        t = t.substring(1)
    }
    t.split("**").forEachIndexed { i, s -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = INK)) { append(s) } else append(s) }
}

@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier, drop: Color? = null, size: Float = 15.5f, color: Color = INK) {
    Text(rich(text, drop), modifier = modifier, fontFamily = SERIF, color = color, fontSize = size.sp, lineHeight = (size * 1.45f).sp)
}

@Composable
private fun Block(b: RB, accent: Color, chapter: Int?, art: RealmArt, knight: ImageBitmap, water: ImageBitmap) {
    when (b) {
        is RB.Chapter -> Column(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CHAPTER ${ROMAN.getOrElse(chapter ?: 0) { "" }}", fontFamily = SERIF, fontWeight = FontWeight.Bold, color = accent, fontSize = 12.sp, letterSpacing = 3.sp)
            Text(b.title, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = INK, fontSize = 25.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Ornament(accent)
        }
        is RB.Para -> BodyText(b.text, Modifier.fillMaxWidth().padding(top = if (b.drop) 14.dp else 6.dp, bottom = 6.dp), drop = if (b.drop) accent else null)
        is RB.Bullets -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            for (it in b.items) Row(Modifier.padding(vertical = 3.dp)) {
                Canvas(Modifier.padding(top = 8.dp, end = 10.dp).size(8.dp)) {
                    val c = center; val d = size.minDimension / 2
                    drawPath(Path().apply { moveTo(c.x, c.y - d); lineTo(c.x + d, c.y); lineTo(c.x, c.y + d); lineTo(c.x - d, c.y); close() }, accent)
                }
                BodyText(it, Modifier.weight(1f), size = 15f)
            }
        }
        is RB.Note -> {
            val tone = if (b.warn) BAD else accent
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    .background(tone.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(1.2.dp, tone.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RIcon(22.dp) { c, s -> actionIcon(if (b.warn) RI.FIRE else RI.SCROLL, c, s) }
                    Spacer(Modifier.width(8.dp))
                    Text(b.title, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = tone, fontSize = 15.sp)
                }
                Spacer(Modifier.height(4.dp))
                BodyText(b.text, size = 14.5f)
            }
        }
        is RB.Costs -> Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .border(1.dp, GOLD.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            b.rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(0.8.dp).background(GOLD.copy(alpha = 0.4f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(40.dp)) { pieceIcon(r.piece, art, knight) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = INK, fontSize = 15.5.sp)
                        if (r.note.isNotEmpty()) Text(r.note, fontFamily = SERIF, color = INK_SOFT, fontSize = 12.5.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    if (r.cost == null) Text("varies", fontFamily = SERIF, fontStyle = FontStyle.Italic, color = INK_SOFT, fontSize = 13.sp)
                    else Column(horizontalAlignment = Alignment.End) {
                        val icons = (0..4).flatMap { k -> List(r.cost[k]) { k } }
                        for (row in icons.chunked(5)) Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { for (k in row) ResImg(k, 20.dp) }
                    }
                }
            }
        }
        is RB.Figure -> Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1F3A4D), Color(0xFF16303F))), RoundedCornerShape(12.dp))
                    .border(2.dp, Brush.linearGradient(listOf(Color(0xFFF6E3A1), GOLD, Color(0xFF8A6420))), RoundedCornerShape(12.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val flow by androidx.compose.animation.core.rememberInfiniteTransition(label = "flow").animateFloat(0f, 1f,
                    androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "flow")
                for (p in b.panels) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(b.height.dp)) { drawScene(p.scene, art, knight, water, flow) }
                    if (p.label != null) Text(p.label, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = Color(0xFFF6E3A1), fontSize = 12.5.sp,
                        textAlign = TextAlign.Center, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
                }
            }
            Text(rich(b.caption, null), fontFamily = SERIF, fontStyle = FontStyle.Italic, color = INK_SOFT, fontSize = 13.5.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp))
        }
        is RB.Steps -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            b.items.forEachIndexed { i, (t, x) ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.85f), accent)))
                            .border(1.5.dp, GOLD, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", fontFamily = SERIF, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = INK, fontSize = 16.sp)
                        BodyText(x, size = 14.5f)
                    }
                }
            }
        }
        is RB.Table -> Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, GOLD.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        ) {
            Row(Modifier.fillMaxWidth().background(accent).padding(horizontal = 10.dp, vertical = 7.dp)) {
                b.head.forEachIndexed { i, h ->
                    Text(h, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.5.sp,
                        modifier = Modifier.weight(if (i == 0) 1.5f else 1f), textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
                }
            }
            b.rows.forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().background(if (r % 2 == 0) Color.White.copy(alpha = 0.32f) else Color.Transparent).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    row.forEachIndexed { i, c ->
                        Text(rich(c, null), fontFamily = SERIF, color = INK, fontSize = 14.sp, lineHeight = 18.sp,
                            modifier = Modifier.weight(if (i == 0) 1.5f else 1f), textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        is RB.Spells -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            for ((icon, name, text) in b.items) Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .border(1.dp, GOLD.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF8EED6)).border(1.5.dp, GOLD, CircleShape), contentAlignment = Alignment.Center) {
                    RIconOf(icon, 30.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = INK, fontSize = 15.5.sp)
                    BodyText(text, size = 14f)
                }
            }
        }
        is RB.Trophies -> Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((res, name, text) in b.items) Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A1A1A), Color(0xFF120B0B))), RoundedCornerShape(12.dp))
                    .border(2.dp, Brush.linearGradient(listOf(Color(0xFFF6E3A1), GOLD, Color(0xFF8A6420))), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(150.dp)
                    .background(Brush.radialGradient(listOf(Color(0xFFB71C1C).copy(alpha = 0.45f), Color.Transparent))), contentAlignment = Alignment.Center) {
                    Image(painterResource(res), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                }
                Text(name, fontFamily = SERIF, fontWeight = FontWeight.Bold, color = Color(0xFFF6E3A1), fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp))
                Text(text, fontFamily = SERIF, color = Color(0xFFE8DCC4), fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        RB.Terrains -> TerrainGallery(art)
        RB.Dice -> DiceChart(accent)
    }
}

@Composable
private fun TerrainGallery(art: RealmArt) {
    val names = listOf("Enchanted forest", "Dwarven mountain", "Elven fields", "Griffin meadow", "Crystal spires", "Dragon's Waste")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        for (row in (0..5).chunked(3)) Row(Modifier.fillMaxWidth()) {
            for (t in row) Column(Modifier.weight(1f).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(86.dp)) {
                    val u = min(size.width / sqrt3, size.height / 2f) * 0.98f
                    val img = art.byTerrain[t][0]
                    val tw = sqrt3 * u; val th = 2 * u
                    drawImage(img, dstOffset = IntOffset((center.x - tw / 2).toInt(), (center.y - th / 2).toInt()), dstSize = IntSize(tw.toInt(), th.toInt()), filterQuality = FilterQuality.Medium)
                    drawPath(hexAt(center, u), GOLDEN, style = Stroke(u * 0.05f))
                }
                Text(names[t], fontFamily = SERIF, fontWeight = FontWeight.Bold, color = INK, fontSize = 12.5.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (t < 5) { ResImg(t, 16.dp); Spacer(Modifier.width(3.dp)) }
                    Text(if (t < 5) RES_SHORT[t] else "Nothing", fontFamily = SERIF, color = INK_SOFT, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DiceChart(accent: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
        .border(1.dp, GOLD.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("How often each number is rolled (out of 36)", fontFamily = SERIF, fontWeight = FontWeight.Bold, color = INK, fontSize = 13.5.sp)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = 11; val gap = size.width / n; val bw = gap * 0.66f
            val base = size.height - 22.dp.toPx(); val top = 18.dp.toPx(); val unit = (base - top) / 6f
            for (i in 0 until n) {
                val v = i + 2; val ways = 6 - kotlin.math.abs(7 - v)
                val x = gap * i + (gap - bw) / 2; val h = ways * unit
                val col = when (v) { 6, 8 -> BAD; 7 -> Color(0xFF5D4037); else -> accent }
                drawRoundRect(col.copy(alpha = 0.9f), Offset(x, base - h), Size(bw, h), CornerRadius(bw * 0.18f))
                drawRoundRect(GOLD, Offset(x, base - h), Size(bw, h), CornerRadius(bw * 0.18f), style = Stroke(1.dp.toPx()))
                glyph("$ways", Offset(x + bw / 2, base - h - 8.dp.toPx()), 11.sp.toPx(), INK_SOFT, bold = false)
                glyph("$v", Offset(x + bw / 2, base + 11.dp.toPx()), 14.sp.toPx(), if (v == 6 || v == 8) BAD else INK)
            }
        }
    }
}

// =====================================================================  drawing

private fun hexCorner(c: Offset, u: Float, k: Int): Offset {
    val a = Math.toRadians(60.0 * k - 90.0)
    return Offset(c.x + u * cos(a).toFloat(), c.y + u * sin(a).toFloat())
}

private fun hexAt(c: Offset, u: Float, shrink: Float = 1f) = Path().apply {
    for (k in 0..5) { val p = c + (hexCorner(c, u, k) - c) * shrink; if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

private fun DrawScope.pic(img: ImageBitmap, c: Offset, w: Float, lift: Float = 0.5f) {
    val h = w * img.height / img.width
    drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - h * lift).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Medium)
}

private fun DrawScope.rbCastle(p: Offset, u: Float, color: Color) {
    val s = 0.2f * u
    for (dx in listOf(-1.05f, 1.05f)) {
        val tp = Path().apply {
            moveTo(p.x + dx * s - s * 0.38f, p.y + s); lineTo(p.x + dx * s - s * 0.38f, p.y - s * 1.2f); lineTo(p.x + dx * s, p.y - s * 1.75f)
            lineTo(p.x + dx * s + s * 0.38f, p.y - s * 1.2f); lineTo(p.x + dx * s + s * 0.38f, p.y + s); close()
        }
        drawPath(tp, Color.Black.copy(alpha = 0.35f), style = Stroke(s * 0.45f)); drawPath(tp, color); drawPath(tp, Color.White, style = Stroke(s * 0.12f))
    }
    tower(p, u * 0.9f, color)
}

private fun DrawScope.wallLine(a: Offset, b: Offset, u: Float, owner: Int) {
    val d = (b - a) * 0.1f
    drawLine(Color(0xFF2B2B2B), a + d, b - d, u * 0.2f, StrokeCap.Round)
    drawLine(Color(0xFFB8B2A6), a + d, b - d, u * 0.14f, StrokeCap.Round)
    drawLine(if (owner == 0) YOU else RIVAL, a + d, b - d, u * 0.05f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.08f, u * 0.06f)))
}

private fun DrawScope.roadPiece(art: RealmArt, a: Offset, b: Offset, owner: Int) {
    val img = if (owner == 0) art.roadYou else art.roadRival
    val mid = (a + b) / 2f; val len = (b - a).getDistance() * 0.74f; val hh = len * img.height / img.width
    rotate(Math.toDegrees(atan2((b - a).y.toDouble(), (b - a).x.toDouble())).toFloat(), mid) {
        drawImage(img, dstOffset = IntOffset((mid.x - len / 2).toInt(), (mid.y - hh / 2).toInt()), dstSize = IntSize(len.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
    }
}

/** A cost-table icon for each piece. */
private fun DrawScope.pieceIcon(p: Piece, art: RealmArt, knight: ImageBitmap) {
    val c = center; val s = size.minDimension
    when (p) {
        Piece.VILLAGE -> pic(art.houseYou, c, s * 0.8f)
        Piece.TOWER -> tower(c + Offset(0f, s * 0.1f), s * 1.6f, YOU)
        Piece.ROAD -> roadPiece(art, c + Offset(-s * 0.55f, s * 0.3f), c + Offset(s * 0.55f, -s * 0.3f), 0)
        Piece.WALL -> wallLine(c + Offset(-s * 0.5f, s * 0.3f), c + Offset(s * 0.5f, -s * 0.3f), s * 0.9f, 0)
        Piece.CASTLE -> rbCastle(c + Offset(0f, s * 0.12f), s * 1.5f, YOU)
        Piece.KNIGHT -> pic(knight, c, s * 0.62f)
        Piece.SPELL -> actionIcon(RI.SCROLL, c, s * 0.85f)
        Piece.CLAIM -> {
            val u = s * 0.48f
            val img = art.byTerrain[2][0]
            drawImage(img, dstOffset = IntOffset((c.x - sqrt3 * u / 2).toInt(), (c.y - u).toInt()), dstSize = IntSize((sqrt3 * u).toInt(), (2 * u).toInt()), filterQuality = FilterQuality.Medium)
            drawPath(hexAt(c, u, 0.9f), YOU, style = Stroke(u * 0.12f))
        }
    }
}

/** Draws a figure's mini board, fitted to the canvas. */
internal fun DrawScope.drawScene(sc: Scene, art: RealmArt, knight: ImageBitmap, water: ImageBitmap, flow: Float = 0.3f) {
    if (sc.hexes.isEmpty()) return
    val xs = sc.hexes.map { sqrt3 * (it.q + it.r / 2f) }; val ys = sc.hexes.map { 1.5f * it.r }
    val minX = xs.min() - sqrt3 / 2; val maxX = xs.max() + sqrt3 / 2; val minY = ys.min() - 1f; val maxY = ys.max() + 1f
    val u = min(size.width / (maxX - minX + 0.3f), size.height / (maxY - minY + 0.75f))
    val ox = size.width / 2 - (minX + maxX) / 2 * u; val oy = size.height / 2 - (minY + maxY) / 2 * u
    fun hc(h: Int) = Offset(ox + xs[h] * u, oy + ys[h] * u)
    fun corner(h: Int, k: Int) = hexCorner(hc(h), u, ((k % 6) + 6) % 6)
    fun sideEnds(h: Int, k: Int) = corner(h, k) to corner(h, k + 1)
    fun sideMid(h: Int, k: Int) = sideEnds(h, k).let { (a, b) -> (a + b) / 2f }

    // tiles
    val tw = sqrt3 * u + 2; val th = 2 * u + 2
    for ((i, h) in sc.hexes.withIndex()) {
        val c = hc(i)
        val img = when (h.t) {
            T_WATER -> water
            T_FOG -> art.fog[h.variant % art.fog.size]
            T_WASTE, T_LAIR, T_BARREN -> art.byTerrain[5][0]
            else -> art.byTerrain[h.t].let { it[h.variant % it.size] }
        }
        drawImage(img, dstOffset = IntOffset((c.x - tw / 2).toInt(), (c.y - th / 2).toInt()), dstSize = IntSize(tw.toInt(), th.toInt()), filterQuality = FilterQuality.Medium)
        if (h.t == T_BARREN) drawPath(hexAt(c, u), Color(0xFF3B2A1A).copy(alpha = 0.35f))
    }
    if (sc.night) for ((i, h) in sc.hexes.withIndex()) {
        drawPath(hexAt(hc(i), u), Color(0xFF0B1030).copy(alpha = 0.45f))
        if (h.t == 4) drawPath(hexAt(hc(i), u, 0.95f), Color(0xFFB388FF).copy(alpha = 0.3f))
    }
    // owner tint and gold borders
    for ((i, h) in sc.hexes.withIndex()) {
        val c = hc(i)
        if (h.owner >= 0) drawPath(hexAt(c, u, 0.99f), (if (h.owner == 0) YOU else RIVAL).copy(alpha = 0.2f))
        if (h.dim) drawPath(hexAt(c, u), Color.Black.copy(alpha = 0.4f))
        if (h.t != T_WATER) {
            drawPath(hexAt(c, u), Color(0xFF4A3510), style = Stroke(u * 0.08f))
            drawPath(hexAt(c, u), Brush.linearGradient(listOf(Color(0xFFF6E3A1), GOLDEN, Color(0xFFF2D27A)), start = c - Offset(u, u), end = c + Offset(u, u)), style = Stroke(u * 0.05f))
        }
        if (h.owner >= 0) drawPath(hexAt(c, u, 0.9f), if (h.owner == 0) YOU else RIVAL, style = Stroke(u * 0.07f))
        if (h.glow != null) {
            drawPath(hexAt(c, u, 0.86f), h.glow.copy(alpha = 0.35f), style = Stroke(u * 0.2f))
            drawPath(hexAt(c, u, 0.86f), h.glow, style = Stroke(u * 0.07f))
        }
    }
    // number tokens, yields and tile pieces
    for ((i, h) in sc.hexes.withIndex()) {
        val c = hc(i)
        val busy = h.castle > 0 || h.level > 0 || h.knights > 0 || h.half
        if (h.n > 0) {
            val tc = if (busy) c + Offset(-u * 0.42f, -u * 0.05f) else c + Offset(0f, u * 0.05f)
            val r = u * (if (busy) 0.22f else 0.27f)
            drawCircle(Color.Black.copy(alpha = 0.25f), r, tc + Offset(0f, u * 0.03f))
            drawCircle(Color(0xFFFFF4DE), r, tc)
            val hot = h.n == 6 || h.n == 8
            glyph("${h.n}", tc - Offset(0f, r * 0.15f), r * 1.0f, if (hot) BAD else Color(0xFF2B2B2B))
            val pips = 6 - kotlin.math.abs(7 - h.n)
            for (k in 0 until pips) drawCircle(if (hot) BAD else Color(0xFF2B2B2B), r * 0.075f, tc + Offset((k - (pips - 1) / 2f) * r * 0.21f, r * 0.58f))
        }
        h.yields?.let { y ->
            val icons = (0..4).flatMap { r -> List(y[r]) { r } }
            val s = u * 0.34f; val w = icons.size * s * 0.92f
            val y0 = c.y + u * 0.42f
            drawRoundRect(Color(0xFFF8EED6).copy(alpha = 0.92f), Offset(c.x - w / 2 - s * 0.15f, y0 - s * 0.6f), Size(w + s * 0.3f, s * 1.2f), CornerRadius(s * 0.6f))
            icons.forEachIndexed { k, r ->
                val ic = art.icons[r]; val p = Offset(c.x - w / 2 + s * 0.46f + k * s * 0.92f, y0)
                val iw = s * 0.9f; val ih = iw * ic.height / ic.width
                drawImage(ic, dstOffset = IntOffset((p.x - iw / 2).toInt(), (p.y - ih / 2).toInt()), dstSize = IntSize(iw.toInt(), ih.toInt()), filterQuality = FilterQuality.Medium)
            }
        }
        val col = if (h.owner == 1) RIVAL else YOU
        when (h.level) {
            1 -> pic(if (h.owner == 1) art.houseRival else art.houseYou, c + Offset(0f, -u * 0.12f), u * 0.62f)
            2 -> tower(c + Offset(0f, -u * 0.12f), u * 1.3f, col)
            3 -> rbCastle(c + Offset(0f, -u * 0.08f), u * 1.25f, col)
        }
        if (h.castle > 0) {
            val cp = c + Offset(u * 0.2f, -u * 0.12f)
            rbCastle(cp, u * 1.1f, col)
            for (k in 0 until 3) {
                val hp = cp + Offset((k - 1) * u * 0.16f, u * 0.34f)
                drawCircle(Color.Black.copy(alpha = 0.5f), u * 0.07f, hp)
                drawCircle(if (k < h.castle) Color(0xFFE53935) else Color(0xFF5D4037), u * 0.052f, hp)
            }
        } else if (h.half) glyph("½", c + Offset(u * 0.2f, -u * 0.12f), u * 0.36f, Color.White)
        if (h.knights > 0) {
            val kh = u * 0.46f; val kw = kh * knight.width / knight.height; val base = c.y + u * 0.84f
            for (k in 0 until h.knights) {
                val x = c.x + (if (h.n > 0) u * 0.2f else 0f) + (k - (h.knights - 1) / 2f) * kw * 0.9f
                drawImage(knight, dstOffset = IntOffset((x - kw / 2).toInt(), (base - kh).toInt()), dstSize = IntSize(kw.toInt(), kh.toInt()), filterQuality = FilterQuality.Medium)
            }
        }
    }
    // roads, walls, rubble, open sides
    for (s in sc.sides) {
        val (a, b) = sideEnds(s.h, s.k)
        when (s.kind) {
            0 -> roadPiece(art, a, b, s.owner)
            1 -> wallLine(a, b, u, s.owner)
            2 -> {
                val d = (b - a) * 0.1f
                drawLine(Color(0xFF3E2723), a + d, b - d, u * 0.14f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.07f, u * 0.09f)))
                drawLine(Color(0xFF8D8478), a + d, b - d, u * 0.08f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.07f, u * 0.09f)))
            }
            else -> {
                val d = (b - a) * 0.18f
                drawLine(Color(0xFFFFF59D), a + d, b - d, u * 0.11f, StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u * 0.12f, u * 0.08f)))
            }
        }
    }
    // corners
    for (cn in sc.corners) {
        val p = corner(cn.h, cn.k)
        when (cn.level) {
            1 -> pic(if (cn.owner == 1) art.houseRival else art.houseYou, p, u * 0.55f, lift = 0.62f)
            2 -> tower(p, u * 1.05f, if (cn.owner == 1) RIVAL else YOU)
            0 -> { drawCircle(Color(0xFFFFF59D).copy(alpha = 0.35f), u * 0.2f, p); drawCircle(Color(0xFFFFF59D), u * 0.17f, p, style = Stroke(u * 0.06f)) }
            -1 -> {
                drawCircle(BAD, u * 0.15f, p); drawCircle(Color.White, u * 0.15f, p, style = Stroke(u * 0.03f))
                val d = u * 0.075f
                drawLine(Color.White, p + Offset(-d, -d), p + Offset(d, d), u * 0.04f, StrokeCap.Round)
                drawLine(Color.White, p + Offset(-d, d), p + Offset(d, -d), u * 0.04f, StrokeCap.Round)
            }
            3 -> {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFD1B3FF).copy(alpha = 0.9f), Color(0xFF9C5CFF).copy(alpha = 0.45f), Color.Transparent), center = p, radius = u * 0.42f), u * 0.42f, p)
                val dia = Path().apply { moveTo(p.x, p.y - u * 0.2f); lineTo(p.x + u * 0.13f, p.y); lineTo(p.x, p.y + u * 0.2f); lineTo(p.x - u * 0.13f, p.y); close() }
                drawPath(dia, Color(0xFF6A2FE0)); drawPath(dia, Color.White, style = Stroke(u * 0.025f))
            }
        }
    }
    // dragons
    for ((h, scl) in sc.dragons) {
        val c = hc(h)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF2A1A).copy(alpha = 0.75f), Color(0xFFD50000).copy(alpha = 0.35f), Color.Transparent), center = c, radius = u * 0.9f * scl), u * 0.9f * scl, c)
        val img = art.dragon; val hh = u * 1.35f * scl; val w = hh * img.width / img.height
        drawImage(img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - hh / 2).toInt()), dstSize = IntSize(w.toInt(), hh.toInt()), filterQuality = FilterQuality.Medium)
    }
    // arrows: the same flowing arrows as on the board, tile by tile
    for (ar in sc.arrows) {
        val pts = ar.path.map { hc(it) }
        for (k in 1 until pts.size) flowArrow(pts[k - 1], pts[k], u, ar.color, flow, if (ar.dashed) 0.6f else 0.75f)
    }
    // labels
    for (m in sc.marks) {
        val base = when { m.corner >= 0 -> corner(m.h, m.corner); m.side >= 0 -> sideMid(m.h, m.side); else -> hc(m.h) }
        val p = base + Offset(m.dx * u, m.dy * u)
        val ts = u * 0.27f; val w = maxOf(ts * 1.25f, m.text.length * ts * 0.6f + ts * 0.6f); val hgt = ts * 1.35f
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(p.x - w / 2, p.y - hgt / 2 + u * 0.03f), Size(w, hgt), CornerRadius(hgt / 2))
        drawRoundRect(m.bg, Offset(p.x - w / 2, p.y - hgt / 2), Size(w, hgt), CornerRadius(hgt / 2))
        drawRoundRect(Color(0xFFF6E3A1), Offset(p.x - w / 2, p.y - hgt / 2), Size(w, hgt), CornerRadius(hgt / 2), style = Stroke(u * 0.025f))
        glyph(m.text, p, ts, Color.White)
    }
}
