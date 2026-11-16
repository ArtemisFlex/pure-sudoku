import os, re
os.chdir(r'C:\Users\abrat\PureGames')
f = 'app/src/main/java/com/puresudoku/ui/games/realm/DeedsGame.kt'
s = open(f, encoding='utf-8').read()

def rep(a, b):
    global s
    assert a in s, a[:80]
    s = s.replace(a, b, 1)

def cut(start, end, b):
    """Replace from the line containing [start] up to (not including) the line containing [end]."""
    global s
    i = s.index(start); i = s.rfind('\n', 0, i) + 1
    j = s.index(end, i); j = s.rfind('\n', 0, j) + 1
    s = s[:i] + b + s[j:]

R = 'com.puresudoku.R.drawable.'
tiles = ['spire', 'pigsty_hollow', 'loot', 'turnip_row', 'ferrymans_bridge', 'millbrook', 'fate', 'thatchwick', 'ploughmead',
         'inquisitor', 'wolfden', 'windmill', 'old_oakhurst', 'thornwick', 'troll_bridge', 'gallows_hill', 'loot', 'ravenscar',
         'colosseum', 'harbour_keep', 'fate', 'fishwife_quay', 'tidewatch', 'old_stone_bridge', 'quarry_gate', 'silver_mine', 'ironforge',
         'market', 'bishops_close', 'tithe', 'cathedral_square', 'kingsbridge', 'fate', 'throne_hill', 'levy', 'golden_keep']
tile_list = ',\n'.join('    ' + ', '.join(R + 'dd_tile_' + t for t in tiles[k:k + 3]) for k in range(0, 36, 3))

rep('private val HERO_ART = intArrayOf(com.puresudoku.R.drawable.rt_dragon, com.puresudoku.R.drawable.rt_knight_piece, com.puresudoku.R.drawable.rt_thief, com.puresudoku.R.drawable.rt_black_knight)',
    'private val HERO_ART = intArrayOf(' + ', '.join(R + 'dd_piece_' + h for h in ['dragon', 'knight', 'thief', 'black_knight']) + ')\n'
    '/** The light grey parts of each piece (cloak, banner, trim) that take the player\'s colour. */\n'
    'private val HERO_MASK = intArrayOf(' + ', '.join(R + 'dd_piece_' + h + '_mask' for h in ['dragon', 'knight', 'thief', 'black_knight']) + ')\n'
    'private val HERO_PORTRAIT = intArrayOf(' + ', '.join(R + 'dd_portrait_' + h for h in ['dragon', 'knight', 'thief', 'black_knight']) + ')\n'
    '/** Each tile\'s painting, Spire first, clockwise. */\n'
    'private val TILE_ART = intArrayOf(\n' + tile_list + ',\n)')

rep('internal fun heroTint(c: Int): ColorFilter? = if (c <= 0) null else ColorFilter.tint(lerp(Color.White, DEED_COLORS[c], 0.42f), BlendMode.Modulate)',
    'internal fun heroTint(c: Int): ColorFilter? = if (c <= 0) null else ColorFilter.tint(lerp(Color.White, DEED_COLORS[c], 0.85f), BlendMode.Modulate)')

# art bundle: paintings, masks, coins
cut('private class DeedArt(', 'private fun DrawScope.landscape(',
'''private class DeedArt(val heroes: List<ImageBitmap>, val masks: List<ImageBitmap>, val tower: ImageBitmap, val castle: ImageBitmap,
    val coinCrown: ImageBitmap, val coinBlade: ImageBitmap,
    /** Each tile's painting, drawn edge to edge. */
    val land: List<ImageBitmap>)

/**
 * Fills [r] with a square painting, zoomed so the tile is covered edge to edge: the centre of the painting,
 * cropped to the tile's shape.
 */
''')
rep('''    val w = img.width.toFloat(); val h = img.height.toFloat()
    val inR = w / 2f * 0.9f
    val outR = inR * 2f / kotlin.math.sqrt(3f)
    val k = r.height / r.width
    val a = minOf(inR, outR / (k + 1f / kotlin.math.sqrt(3f)))
    val b = a * k
    val cx = w / 2f; val cy = h / 2f''', '''    val w = img.width.toFloat(); val h = img.height.toFloat()
    val k = r.height / r.width
    // the biggest rectangle of the tile's shape inside the painting, trimmed a little so the subject fills it
    var a = w / 2f * 0.94f; var b = a * k
    if (b > h / 2f * 0.94f) { b = h / 2f * 0.94f; a = b / k }
    val cx = w / 2f; val cy = h / 2f''')

cut('@Composable\nprivate fun rememberDeedArt(): DeedArt {', '// =====================================================================  session',
'''@Composable
private fun rememberDeedArt(): DeedArt {
    @Composable fun img(id: Int) = ImageBitmap.imageResource(id)
    val heroes = HERO_ART.map { img(it) }
    val masks = HERO_MASK.map { img(it) }
    val tower = img(com.puresudoku.R.drawable.dd_tower)
    val castle = img(com.puresudoku.R.drawable.dd_castle)
    val crown = img(com.puresudoku.R.drawable.dd_coin_crown)
    val blade = img(com.puresudoku.R.drawable.dd_coin_blade)
    val landImgs = TILE_ART.distinct().associateWith { img(it) }
    return remember(heroes, tower, landImgs) { DeedArt(heroes, masks, tower, castle, crown, blade, TILE_ART.map { landImgs.getValue(it) }) }
}

''')

# the coin: painted coin on a ring in the owner's colour
cut('/** The two-sided coin: a crown on gold', '/** A small dark label',
'''/** The two-sided coin: the painted crown or blade coin, ringed in white and the owner's colour. */
private fun DrawScope.deedCoin(art: DeedArt, c: Offset, r: Float, face: Int, owner: Color, dim: Boolean = false) {
    val a = if (dim) 0.45f else 1f
    drawCircle(Color.Black.copy(alpha = 0.45f * a), r * 1.16f, c + Offset(0f, r * 0.14f))
    drawCircle(Color.White.copy(alpha = a), r * 1.14f, c)
    drawCircle(owner.copy(alpha = a), r * 1.06f, c)
    art(if (face == Deeds.COIN_CROWN) art.coinCrown else art.coinBlade, c, r * 2f, alpha = a)
}

''')

# pieces: painting plus tinted cloth
rep('''                    art(art.heroes[g.hero[p]], ctr - Offset(0f, box * 0.06f), box, heroTint(g.color[p]))''',
    '''                    art(art.heroes[g.hero[p]], ctr - Offset(0f, box * 0.06f), box)
                    heroTint(g.color[p])?.let { art(art.masks[g.hero[p]], ctr - Offset(0f, box * 0.06f), box, it) }''')

# tiles: the painting carries the scene; keep labels, bands, prices, owner marks
cut('    when {\n        corner -> {', '    if (!corner) drawLine(',
'''    when {
        corner -> CORNER_LABEL[t]?.let { tag(it, Offset(r.center.x, r.bottom - e * 0.2f), e * 0.26f) }
        kind == Deeds.LAND -> drawRect(DOMAIN_COLORS[Deeds.GROUP[t]], bandRect.topLeft, bandRect.size)
        kind == Deeds.BRIDGE -> drawRect(Color(0xFF3F6E8C), bandRect.topLeft, bandRect.size)
        kind == Deeds.MILL -> drawRect(Color(0xFF6D4C41), bandRect.topLeft, bandRect.size)
    }
''')
rep('        deedCoin(coinAt, e * 0.22f, g.face(t), oc, dim = g.pawned[t] == 1)', '        deedCoin(art, coinAt, e * 0.24f, g.face(t), oc, dim = g.pawned[t] == 1)')

# Compose images: piece with tinted cloth; portraits on the player cards
cut('@Composable\nprivate fun HeroImg(', '@Composable\nprivate fun PlayerCard(',
'''@Composable
private fun HeroImg(hero: Int, color: Int, size: Dp, modifier: Modifier = Modifier) {
    if (hero < 0) { Box(modifier.size(size)); return }
    Box(modifier.size(size)) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_ART[hero]), null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        heroTint(color)?.let {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_MASK[hero]), null, Modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Fit, colorFilter = it)
        }
    }
}

@Composable
private fun Portrait(hero: Int, size: Dp, modifier: Modifier = Modifier) {
    if (hero < 0) { Box(modifier.size(size)); return }
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(HERO_PORTRAIT[hero]), null, modifier.size(size), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}

''')
rep('''        Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f), col.copy(alpha = 0.25f)))).border(1.5.dp, col, CircleShape), contentAlignment = Alignment.Center) {
            HeroImg(g.hero[p], g.color[p], 34.dp)
        }''', '''        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, col, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Portrait(g.hero[p], 40.dp)
        }''')
rep('''                            Canvas(Modifier.size(30.dp).clip(CircleShape).clickable(enabled = canAct && g.canFlip(t)) { s.act(Act.FLIP, t) }) {
                                deedCoin(center, size.minDimension * 0.4f, g.coin[t], s.seatColor(p))
                            }''', '''                            Box(Modifier.size(34.dp).clip(CircleShape).background(s.seatColor(p)).clickable(enabled = canAct && g.canFlip(t)) { s.act(Act.FLIP, t) }, contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(if (g.coin[t] == Deeds.COIN_CROWN) com.puresudoku.R.drawable.dd_coin_crown else com.puresudoku.R.drawable.dd_coin_blade), null, Modifier.size(30.dp))
                            }''')
open(f, 'w', encoding='utf-8').write(s)
print('wired')
