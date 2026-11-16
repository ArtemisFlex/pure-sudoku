style = '''The attached images set the art style for the medieval board game "Deed or Dungeon": the logo, an approved board tile (the Spire), an approved game piece (the Knight) and an approved coin. Match them exactly: glossy, richly painted high-detail fantasy illustration, bold readable shapes, ornate gold filigree accents, deep navy blue, royal red and warm gold, dramatic warm lighting, premium mobile game art.

Using the built-in image generation tool, make ONE call per image below, then copy each final image into the current working directory with the exact filename given. Never overwrite a file that already exists. No text, letters or numbers in any image.
'''
tile = 'Square 1024x1024 board tile painting, full-bleed scene with no frame or border, main subject in the centre third (the game crops the edges): '
piece = ('1024x1024 with a TRANSPARENT background: a single game piece, a painted miniature on the same round ornate gold-trimmed stone base as the approved Knight, '
         'three-quarter view, centred, same scale and lighting as the Knight. Paint cloaks, banners and trim in light neutral grey (not coloured) so the game can tint them: ')
portrait = '1024x1024 square portrait, head and shoulders, painted in the same style, rich navy-and-gold vignette background, matching the game piece: '
batches = {
    1: [('dd_tile_inquisitor.png', tile + '"The Inquisitor": a grim hooded inquisitor in dark red robes holding a lantern and a long scroll of charges, torch-lit stone hall; he must not look like an armoured knight.'),
        ('dd_tile_colosseum.png', tile + '"The Colosseum": a grand stone arena seen from above at an angle, red banners, sunlit sand floor with iron grates showing torch-lit dungeon cells beneath.'),
        ('dd_tile_market.png', tile + '"The Royal Market": colourful striped market stalls in a cobbled square, a merchant with potions, keys, shields and scrolls on display.'),
        ('dd_tile_fate.png', tile + '"Fate": a glowing mystical swirl of purple and gold magic with a single ornate fortune card floating at its centre (card face blank or with a symbol, no text).'),
        ('dd_tile_loot.png', tile + '"Loot": an iron-banded wooden treasure chest, lid cracked open, golden light and coins spilling out, on a dungeon floor.'),
        ('dd_tile_tithe.png', tile + '"The Abbey\'s Tithe": a stone abbey doorway with a monk holding out a golden collection plate full of coins.'),
        ('dd_tile_levy.png', tile + '"The King\'s Levy": a tax collector\'s table with stacks of gold coins, a ledger and a red wax royal crown seal.'),
        ('dd_tile_windmill.png', tile + '"The Windmill": a tall wooden windmill on a grassy rise above golden fields.')],
    2: [('dd_tile_silver_mine.png', tile + '"The Silver Mine": a timber-framed mine entrance in a rocky mountainside, carts of glinting silver ore, lanterns.'),
        ('dd_tile_ferrymans_bridge.png', tile + '"Ferryman\'s Bridge": a wooden ferry raft pulled on ropes across a river, a hooded ferryman with a lantern.'),
        ('dd_tile_troll_bridge.png', tile + '"Troll Bridge": an old stone bridge over a stream with a big mossy troll lurking in the shadow beneath it.'),
        ('dd_tile_old_stone_bridge.png', tile + '"Old Stone Bridge": a mossy arched stone bridge over a calm river, ivy and wildflowers.'),
        ('dd_tile_kingsbridge.png', tile + '"Kingsbridge": a grand stone bridge lined with red-and-gold royal banners and lampposts, leading to a gatehouse.'),
        ('dd_tile_pigsty_hollow.png', tile + '"Pigsty Hollow": a muddy swampy hollow with pigs, a leaning wooden shack, puddles.'),
        ('dd_tile_turnip_row.png', tile + '"Turnip Row": soggy rows of turnips in a marshy field with a scarecrow.'),
        ('dd_tile_millbrook.png', tile + '"Millbrook": a brook winding through green farmland with a small water wheel mill.')],
    3: [('dd_tile_thatchwick.png', tile + '"Thatchwick": a cosy village of thatched cottages with chimney smoke.'),
        ('dd_tile_ploughmead.png', tile + '"Ploughmead": golden ploughed fields with a pair of oxen pulling a plough.'),
        ('dd_tile_wolfden.png', tile + '"Wolfden": a dark forest at dusk with glowing wolf eyes in the shadows between the trees.'),
        ('dd_tile_old_oakhurst.png', tile + '"Old Oakhurst": a single giant ancient oak tree with sunbeams in a forest glade.'),
        ('dd_tile_thornwick.png', tile + '"Thornwick": a small forest village choked with thorny briars and brambles.'),
        ('dd_tile_gallows_hill.png', tile + '"Gallows Hill": a purple heather moor with a lone wooden gallows on a hill and crows, stormy sky.'),
        ('dd_tile_ravenscar.png', tile + '"Ravenscar": a jagged sea-cliff crag with ravens circling, a ruined watchtower on top.'),
        ('dd_tile_harbour_keep.png', tile + '"Harbour Keep": a small fortified stone harbour keep with ships moored, teal sea.')],
    4: [('dd_tile_fishwife_quay.png', tile + '"Fishwife Quay": a busy fish market on a wooden quay, baskets of fish, nets, boats.'),
        ('dd_tile_tidewatch.png', tile + '"Tidewatch": a lighthouse tower on rocks with waves crashing, glowing lamp.'),
        ('dd_tile_quarry_gate.png', tile + '"Quarry Gate": a grey stone quarry with a wooden crane lifting a block and a gate.'),
        ('dd_tile_ironforge.png', tile + '"Ironforge": a mountain forge built into rock, glowing furnace and anvil, sparks.'),
        ('dd_tile_bishops_close.png', tile + '"Bishop\'s Close": a walled cloister garden with arches, a fountain and roses.'),
        ('dd_tile_cathedral_square.png', tile + '"Cathedral Square": a grand gothic cathedral front with a rose window, crimson banners.'),
        ('dd_tile_throne_hill.png', tile + '"Throne Hill": a green hill crowned by a golden-roofed throne hall with royal banners.'),
        ('dd_tile_golden_keep.png', tile + '"Golden Keep": a gilded white-and-gold castle keep glowing in sunlight.')],
    5: [('dd_piece_dragon.png', piece + 'a coiled red dragon with spread wings perched on the base; its wing membranes in light neutral grey.'),
        ('dd_piece_thief.png', piece + 'the Veilnight Thief, a hooded rogue with twin daggers in a dynamic pose; hood and cloak in light neutral grey.'),
        ('dd_piece_black_knight.png', piece + 'the Black Knight, a menacing knight in dark spiked armour with a great sword; cloak and shield face in light neutral grey.'),
        ('dd_tower.png', '1024x1024 with a TRANSPARENT background: a small round stone watchtower with a red pennant, gold trim, centred, readable at tiny size.'),
        ('dd_castle.png', '1024x1024 with a TRANSPARENT background: a compact fortified stone castle with three towers and red banners, gold trim, centred, readable at tiny size.'),
        ('dd_coin_blade.png', '1024x1024 with a TRANSPARENT background: a single thick coin seen face-on, matching the approved crown coin in shape and ornate gold rim, but with a deep red enamel field stamped with a raised silver sword, centred, filling most of the frame.')],
    6: [('dd_portrait_dragon.png', portrait + 'the red dragon, fierce and regal.'),
        ('dd_portrait_knight.png', portrait + 'the knight, same face and armour as the approved Knight piece, helm under arm.'),
        ('dd_portrait_thief.png', portrait + 'the Veilnight Thief, hooded with glinting eyes in shadow.'),
        ('dd_portrait_black_knight.png', portrait + 'the Black Knight, dark spiked helm with a glowing visor slit.')],
}
for k, items in batches.items():
    txt = style + '\n' + '\n\n'.join(f'{i + 1}. {f} - {d}' for i, (f, d) in enumerate(items)) + '\n\nWhen done, list the files you saved.\n'
    open(f'batch{k}.txt', 'w', encoding='utf-8').write(txt)
print('ok')
