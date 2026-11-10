import math, json, sys, os
import numpy as np
from bgkit import BG, hx, W, H
from canvas import Canvas
from sprites import SPRITES
import render

SCENES = {}

def scene(sid, name, game, notes):
    def deco(fn):
        SCENES[sid] = (name, game, notes, fn); return fn
    return deco

_cache = {}
def sprite(sid):
    if sid not in _cache:
        name, tags, notes, size, fn = SPRITES[sid]
        c = Canvas(size, size); fn(c)
        _cache[sid] = render.render(c.doc(name, tags, notes, 0))
    return _cache[sid]

def stars(b, mask, n, cols=("#ffffff", "#c8d8ff", "#fff1b0")):
    for _ in range(n):
        x, y = b.rng.randrange(W), b.rng.randrange(H)
        if mask[y, x]:
            b.px(x, y, b.rng.choice(cols), b.rng.uniform(0.4, 1))
            if b.rng.random() < 0.08:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)): b.px(x + dx, y + dy, cols[0], 0.35)

def wall_torch(b, x, y, glow="#ff9a3c", r=34):
    b.glow(x + 3, y + 2, r, glow, 0.75)
    b.fill(b.mask_rect(x + 2, y + 6, 3, 9), "#4a2e1e")
    b.fill(b.mask_rect(x + 1, y + 5, 5, 2), "#6b7486")
    for (dx, dy, c) in [(3, 0, "#ffe066"), (2, 1, "#f08a24"), (3, 1, "#ffe066"), (4, 1, "#f08a24"), (2, 2, "#f08a24"), (3, 2, "#fff4b0"),
                        (4, 2, "#f08a24"), (2, 3, "#d4431f"), (3, 3, "#f08a24"), (4, 3, "#d4431f"), (3, 4, "#d4431f")]:
        b.px(x + dx, y + dy, c)

def corridor(b, back, wall, floor, ceil, wall_tex, floor_tex, depth_dark=0.45):
    """One-point perspective corridor. back=(x,y,w,h) of the far wall."""
    bx, by, bw, bh = back
    left = b.mask_poly([(0, 0), (bx, by), (bx, by + bh), (0, H)])
    right = b.mask_poly([(W, 0), (bx + bw, by), (bx + bw, by + bh), (W, H)])
    top = b.mask_poly([(0, 0), (W, 0), (bx + bw, by), (bx, by)])
    bottom = b.mask_poly([(0, H), (W, H), (bx + bw, by + bh), (bx, by + bh)])
    farm = b.mask_rect(bx, by, bw, bh)
    wall_tex(left); wall_tex(right)
    b.vgrad(top, ceil[0], ceil[1], 0, by)
    floor_tex(bottom)
    wall_tex(farm)
    # darker towards the vanishing point
    cx, cy = bx + bw / 2, by + bh / 2
    d = np.hypot((b.xx - cx) / W, (b.yy - cy) / H)
    b.shade(np.clip(0.35 + d * 1.6, depth_dark, 1.0))
    b.shade(np.full((H, W), 0.55), farm)
    for (x0, y0, x1, y1) in ((0, 0, bx, by), (W - 1, 0, bx + bw, by), (0, H - 1, bx, by + bh), (W - 1, H - 1, bx + bw, by + bh)):
        b.line(x0, y0, x1, y1, "#000000", 0.45)
    b.fill(b.mask_rect(bx, by, bw, bh) & ~b.mask_rect(bx + 1, by + 1, bw - 2, bh - 2), "#000000", 0.4)
    return left, right, top, bottom, farm

# ======================================================================= card-table games

@scene("bg-solitaire", "Card Table", "solitaire", "Green baize under a warm lamp.")
def felt(b):
    m = np.ones((H, W), bool)
    b.radial(m, W / 2, H * 0.42, 120, "#3f8f4a", "#173d22")
    b.stage()
    b.noise(m, 7)
    for y in range(0, H, 8):
        for x in range(0, W, 8):
            if (x // 8 + y // 8) % 2 == 0: b.fill(b.mask_rect(x + 3, y + 3, 2, 2), "#ffffff", 0.05)
    suits = [[".x.x.", "xxxxx", "xxxxx", ".xxx.", "..x.."], ["..x..", ".xxx.", "xxxxx", ".xxx.", "..x.."],
             ["..x..", ".xxx.", "xxxxx", "..x..", ".xxx."], [".xx..", ".xx..", "xxxx.", "xxxx.", ".x..."][:0] or ["..x..", "x.x.x", "xxxxx", "..x..", ".xxx."]]
    for j, y in enumerate(range(18, H - 18, 22)):
        for i, x in enumerate(range(8 + (j % 2) * 11, W - 6, 22)):
            g = suits[(i + j) % 4]
            for yy, row in enumerate(g):
                for xx, ch in enumerate(row):
                    if ch == "x": b.px(x + xx, y + yy, "#0c2a16", 0.35)
    b.glow(30, 150, 18, "#ffffff", 0.0)
    b.stage()
    for (y, h_) in ((0, 10), (H - 10, 10)):
        r = b.mask_rect(0, y, W, h_)
        b.vgrad(r, "#8f5a33", "#4a2c1a", y, y + h_)
        for x in range(0, W, 3): b.line(x, y, x + 1, y + h_, "#c98d52", 0.12)
    b.fill(b.mask_rect(0, 10, W, 1), "#f2c24b", 0.6); b.fill(b.mask_rect(0, H - 11, W, 1), "#f2c24b", 0.6)
    b.stage()
    b.glow(W / 2, -10, 110, "#fff2c0", 0.35)
    b.vignette(0.7)
    b.stage()

@scene("bg-blackjack", "Casino Floor", "blackjack", "Velvet walls, a gold-railed felt table and the house lamp.")
def casino(b):
    wall = b.mask_rect(0, 0, W, 70)
    b.vgrad(wall, "#3a0d18", "#6e1426", 0, 70)
    for x in range(0, W, 12):
        for y in range(4, 64, 12):
            b.fill(b.mask_poly([(x + 6, y), (x + 10, y + 5), (x + 6, y + 10), (x + 2, y + 5)]), "#e8c26a", 0.16)
    b.fill(b.mask_rect(0, 58, W, 12), "#2a0a12")
    for x in range(0, W, 9): b.fill(b.mask_rect(x + 1, 60, 7, 8), "#4a1220")
    b.fill(b.mask_rect(0, 58, W, 1), "#e8c26a", 0.8)
    b.stage()
    table = b.mask_ellipse(W / 2, 200, 120, 140)
    b.radial(table, W / 2, 130, 110, "#2f8a4c", "#0f3b22")
    b.noise(table, 6)
    rail = b.mask_ellipse(W / 2, 200, 120, 140) & ~b.mask_ellipse(W / 2, 200, 115, 135)
    b.fill(rail, "#5a3524")
    rail2 = b.mask_ellipse(W / 2, 200, 117, 137) & ~b.mask_ellipse(W / 2, 200, 116, 136)
    b.fill(rail2, "#e8c26a", 0.8)
    arc = b.mask_ellipse(W / 2, 200, 80, 100) & ~b.mask_ellipse(W / 2, 200, 79, 99)
    b.fill(arc, "#f2c24b", 0.55)
    b.stage()
    # chip stacks
    for (x, y, c, n) in ((12, 150, "#c62828", 5), (19, 156, "#1565c0", 3), (86, 152, "#2e7d32", 4), (93, 158, "#212121", 6), (80, 160, "#c62828", 2)):
        for k in range(n):
            yy = y - k * 2
            b.fill(b.mask_ellipse(x, yy, 4, 1.6), c)
            b.px(x - 3, yy, "#ffffff", 0.7); b.px(x + 2, yy, "#ffffff", 0.7)
        b.fill(b.mask_ellipse(x, y - n * 2 + 1, 4, 1.5), "#ffffff", 0.25)
    b.stage()
    b.fill(b.mask_poly([(40, 0), (68, 0), (60, 14), (48, 14)]), "#1a1a1a")
    b.fill(b.mask_rect(46, 13, 16, 2), "#e8c26a")
    b.glow(W / 2, 18, 90, "#ffe2a0", 0.55)
    b.vignette(0.8)
    b.stage()

@scene("bg-gofish", "Under the Pond", "go-fish", "Sunbeams, kelp, bubbles and a few curious fish.")
def underwater(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#7fd8ef", "#0b3a66", 0, H)
    b.stage()
    for k in range(6):
        x0 = 10 + k * 20
        b.fill(b.mask_poly([(x0, 0), (x0 + 8, 0), (x0 - 30, H), (x0 - 44, H)]), "#e8fbff", 0.08)
    b.stage()
    sand = b.mask_poly([(0, 165), (30, 160), (60, 166), (85, 158), (W, 163), (W, H), (0, H)])
    b.vgrad(sand, "#d8c38a", "#8a7446", 158, H)
    for y in range(162, H, 4):
        for x in range(0, W, 7): b.px(x + (y // 4) % 3, y, "#f0e2b0", 0.6)
    for (x, y, rx, ry) in ((14, 168, 9, 6), (92, 165, 11, 7), (60, 176, 6, 4)):
        b.fill(b.mask_ellipse(x, y, rx, ry), "#4d5b6a"); b.fill(b.mask_ellipse(x - 2, y - 2, rx * 0.6, ry * 0.5), "#7a8a99")
    b.stage()
    for (x0, hgt, ph) in ((8, 70, 0), (22, 50, 1), (78, 80, 2), (98, 60, 3), (66, 40, 4)):
        for y in range(H - 25, H - 25 - hgt, -1):
            t = (H - 25 - y)
            x = x0 + 3 * math.sin(t / 7 + ph)
            b.px(int(x), y, "#2f8a3a"); b.px(int(x) + 1, y, "#5aa548"); b.px(int(x) + 2, y, "#2f6b3a")
            if t % 9 == 0: b.px(int(x) + 3, y, "#5aa548"); b.px(int(x) - 1, y + 1, "#5aa548")
    b.stage()
    for (x, y, c, d) in ((30, 60, "#ff8a3d", 1), (70, 95, "#ffd23d", -1), (44, 120, "#ff6f61", 1), (82, 40, "#ffa53d", -1)):
        b.fill(b.mask_ellipse(x, y, 5, 3), c)
        tail = [(x - 5 * d, y), (x - 9 * d, y - 3), (x - 9 * d, y + 3)]
        b.fill(b.mask_poly(tail), c)
        b.px(x + 3 * d, y - 1, "#1a1420")
        b.fill(b.mask_ellipse(x, y + 1, 4, 1), "#ffffff", 0.25)
    for _ in range(26):
        x, y, r = b.rng.randrange(W), b.rng.randrange(150), b.rng.choice([1, 1, 2, 2, 3])
        ring = b.mask_ellipse(x, y, r + 0.5, r + 0.5) & ~b.mask_ellipse(x, y, r - 0.5, r - 0.5)
        b.fill(ring, "#e8fbff", 0.6); b.px(x - 1, y - 1, "#ffffff", 0.8)
    b.vignette(0.5)
    b.stage()

@scene("bg-cuttle", "Ink Abyss", "cuttle", "Deep water, curling tentacles and glowing motes.")
def abyss(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#2b1a4a", "#07040f", 0, H)
    b.noise(m, 5)
    b.stage()
    for (x0, ph, w0, col) in ((10, 0.0, 7, "#4a2a6e"), (96, 1.7, 8, "#3b2160"), (40, 3.1, 6, "#56307a"), (74, 4.4, 5, "#45285f")):
        pts = []
        for y in range(H + 5, 50, -2):
            t = (H - y) / 140
            x = x0 + math.sin(t * 5 + ph) * 16 * t
            wdt = max(1.0, w0 * (1 - t * 0.85))
            b.fill(b.mask_ellipse(x, y, wdt, 1.6), col)
            if int(y) % 8 == 0 and wdt > 2: b.fill(b.mask_ellipse(x + wdt * 0.6, y, 1.2, 1.2), "#b48ae6", 0.7)
    b.stage()
    for _ in range(40):
        x, y = b.rng.randrange(W), b.rng.randrange(H)
        c = b.rng.choice(["#6ef0ff", "#ff7ad9", "#b48ae6"])
        b.glow(x, y, b.rng.choice([3, 4, 6]), c, 0.6); b.px(x, y, "#ffffff", 0.9)
    b.glow(W / 2, -20, 100, "#5a8cff", 0.25)
    b.vignette(0.6)
    b.stage()

# ======================================================================= Scoundrel

def dungeon_base(b, red=False):
    stone = "#5b4f63" if not red else "#5a2a24"
    mortar = "#2a2230" if not red else "#1e0b0a"
    corridor(b, (34, 64, 40, 62), None, None, ("#1a1420", "#2a2230") if not red else ("#120505", "#2a0c08"),
             lambda mk: b.bricks(mk, stone, mortar, 12, 6),
             lambda mk: (b.vgrad(mk, "#2a2230" if not red else "#1c0806", "#4a3b52" if not red else "#4a1a10", 126, H),
                         b.bricks(mk, "#4a3e52" if not red else "#3a1610", "#1a1420" if not red else "#120404", 18, 9, ox=4)))

@scene("bg-scoundrel", "Dungeon Hall", "scoundrel", "Torchlit stone corridor leading deeper.")
def scoundrel(b):
    dungeon_base(b)
    b.stage()
    arch = b.mask_ellipse(54, 82, 12, 14) | b.mask_rect(42, 82, 25, 44)
    b.fill(arch, "#07050a")
    b.fill(b.mask_ellipse(54, 82, 14, 16) & ~b.mask_ellipse(54, 82, 12, 14) & (b.yy <= 82), "#8a7a92")
    b.fill((b.mask_rect(40, 82, 2, 44) | b.mask_rect(67, 82, 2, 44)), "#8a7a92")
    b.stage()
    wall_torch(b, 14, 70); wall_torch(b, 88, 70)
    for (x, y) in ((22, 170), (80, 176)):
        b.stamp(sprite("skeleton"), x, y - 10, 1, tint=0.75) if False else None
        b.fill(b.mask_ellipse(x, y, 3, 2.5), "#e2d6bc"); b.px(x - 1, y, "#1a1420"); b.px(x + 1, y, "#1a1420")
        b.fill(b.mask_rect(x + 4, y + 1, 6, 1), "#a99c84")
    # cobwebs
    for (cx, cy, sx, sy) in ((0, 0, 1, 1), (W - 1, 0, -1, 1)):
        for k in range(0, 18, 4): b.line(cx, cy + k * sy * 0 + k, cx + (18 - k) * sx, cy, "#c8c8d8", 0.35)
        for r in (6, 11, 16):
            for t in np.linspace(0, math.pi / 2, 12):
                b.px(int(cx + math.cos(t) * r * sx), int(cy + math.sin(t) * r * sy), "#c8c8d8", 0.4)
    b.stage()
    b.vignette(0.7)
    b.stage()

@scene("bg-scoundrel-boss", "The Beast's Lair", "scoundrel", "Shown when a King or Ace monster is in the room.")
def scoundrel_boss(b):
    dungeon_base(b, red=True)
    b.stage()
    lava = b.mask_poly([(0, 170), (40, 165), (70, 172), (W, 166), (W, H), (0, H)])
    b.vgrad(lava, "#ffd04a", "#c2401f", 165, H)
    for y in range(168, H, 3):
        for x in range(0, W, 5): b.px(x + (y * 7) % 5, y, "#fff4b0", 0.5)
    b.glow(W / 2, H, 90, "#ff5a1f", 0.55)
    b.stage()
    dark = b.mask_ellipse(54, 90, 16, 20) | b.mask_rect(38, 90, 32, 36)
    b.fill(dark, "#050101")
    for ex in (46, 62):
        b.glow(ex, 92, 9, "#ff2a1a", 0.9)
        b.fill(b.mask_ellipse(ex, 92, 3, 1.5), "#ffe066"); b.fill(b.mask_rect(ex, 91, 1, 3), "#1a0400")
    b.stage()
    for _ in range(45):
        x, y = b.rng.randrange(W), b.rng.randrange(40, 170)
        b.px(x, y, b.rng.choice(["#ffb03a", "#ff6a2a", "#ffe066"]), b.rng.uniform(0.5, 1))
    wall_torch(b, 12, 70, "#ff5a2a"); wall_torch(b, 90, 70, "#ff5a2a")
    b.vignette(0.8)
    b.stage()

# ======================================================================= Dungeon Delver (floors)

def cave_mask(b, seed, edge=26, wobble=9):
    rr = np.random.default_rng(seed)
    left = np.cumsum(rr.normal(0, 1.2, H)); left = left - left.mean()
    right = np.cumsum(rr.normal(0, 1.2, H)); right = right - right.mean()
    lx = np.clip(edge + left * wobble / (abs(left).max() + 1e-6), 4, 50)
    rx = np.clip(W - edge + right * wobble / (abs(right).max() + 1e-6), 58, W - 4)
    return (b.xx < lx[:, None]) | (b.xx > rx[:, None])

def rock(b, mask, base, dark, light):
    b.fill(mask, base)
    b.noise(mask, 14)
    edge = mask & ~np.roll(mask, 1, axis=1)
    b.fill(edge, light, 0.5)
    for _ in range(140):
        x, y = b.rng.randrange(W), b.rng.randrange(H)
        if mask[y, x]: b.fill(b.mask_ellipse(x, y, b.rng.uniform(1.5, 4), b.rng.uniform(1, 2.5)) & mask, b.rng.choice([dark, light]), 0.45)

def stairs(b, col, hi, top=70, steps=9):
    for k in range(steps):
        y = top + k * 12; w = 22 + k * 6
        b.fill(b.mask_rect(W // 2 - w // 2, y, w, 12), col)
        b.fill(b.mask_rect(W // 2 - w // 2, y, w, 2), hi)

@scene("bg-delver-1", "Delver: The Descent", "dungeon-delver", "Upper floors: daylight fades down the steps.")
def delver1(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#3a3446", "#141019", 0, H)
    b.stage()
    b.fill(b.mask_ellipse(W / 2, 6, 26, 22), "#d8ecff")
    b.glow(W / 2, 4, 90, "#fff4d0", 0.65)
    stairs(b, "#4a4256", "#8a80a0")
    b.stage()
    cm = cave_mask(b, 3)
    rock(b, cm, "#4a4256", "#2a2432", "#7a7090")
    b.stage()
    for (x, y) in ((24, 120), (80, 104)):
        b.glow(x, y, 16, "#ffcf6a", 0.7); b.fill(b.mask_rect(x - 2, y - 2, 4, 5), "#ffe066"); b.fill(b.mask_rect(x - 2, y - 4, 4, 2), "#2a2432")
    b.vignette(0.6)
    b.stage()

@scene("bg-delver-2", "Delver: Crystal Mines", "dungeon-delver", "Middle floors: glowing crystals and old rails.")
def delver2(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#0e1a2e", "#05070f", 0, H)
    b.stage()
    for k in range(H // 6):
        y = 60 + k * 6
        if y >= H: break
        w = 8 + k * 2
        b.fill(b.mask_rect(W // 2 - w, y, 2 * w, 2), "#3a2a20", 0.9)
    b.line(W // 2 - 6, 60, W // 2 - 40, H, "#8a8fa0", 1, 2); b.line(W // 2 + 6, 60, W // 2 + 40, H, "#8a8fa0", 1, 2)
    b.stage()
    cm = cave_mask(b, 7, 30)
    rock(b, cm, "#1e2a40", "#0c1220", "#3a4a66")
    b.stage()
    for _ in range(14):
        x = b.rng.choice([b.rng.randrange(2, 30), b.rng.randrange(78, 106)]); y = b.rng.randrange(20, 185)
        c = b.rng.choice(["#6ef0ff", "#b48aff", "#7affc8"])
        hgt = b.rng.randrange(6, 14)
        for dx in (-3, 0, 3):
            hh = hgt - abs(dx) * 2
            b.fill(b.mask_poly([(x + dx - 2, y), (x + dx, y - hh), (x + dx + 2, y)]), c)
            b.line(x + dx - 1, y - 1, x + dx, y - hh + 2, "#ffffff", 0.6)
        b.glow(x, y - 4, 22, c, 0.45)
    b.vignette(0.6)
    b.stage()

@scene("bg-delver-3", "Delver: Molten Depths", "dungeon-delver", "Deepest floor: lava falls and ash.")
def delver3(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#2a0c08", "#0a0302", 0, H)
    b.stage()
    for x0 in (38, 66):
        fall = b.mask_rect(x0, 0, 6, 150)
        b.vgrad(fall, "#ffe066", "#e0501f", 0, 150)
        for y in range(0, 150, 4): b.px(x0 + (y // 4) % 6, y, "#fff4b0", 0.7)
        b.glow(x0 + 3, 90, 30, "#ff6a2a", 0.4)
    pool = b.mask_ellipse(W / 2, 165, 50, 14)
    b.vgrad(pool, "#ffd04a", "#c2401f", 150, 180)
    b.glow(W / 2, 165, 70, "#ff5a1f", 0.5)
    b.stage()
    cm = cave_mask(b, 11, 24)
    rock(b, cm, "#3a1610", "#1a0604", "#7a2e18")
    b.fill(b.mask_poly([(0, 178), (30, 172), (54, 182), (80, 174), (W, 178), (W, H), (0, H)]), "#1a0604")
    b.stage()
    for _ in range(60):
        x, y = b.rng.randrange(W), b.rng.randrange(H)
        b.px(x, y, b.rng.choice(["#ffb03a", "#ff6a2a", "#7a6a66"]), b.rng.uniform(0.4, 1))
    b.vignette(0.7)
    b.stage()

# ======================================================================= Regicide (castle, by enemy rank)

def castle_wall(b, y_top, col="#4a4a5e", mortar="#22222e"):
    wall = b.mask_rect(0, y_top, W, H - y_top)
    b.bricks(wall, col, mortar, 14, 7)
    for x in range(0, W, 14): b.fill(b.mask_rect(x, y_top - 8, 8, 8), col)
    b.bricks(b.mask_rect(0, y_top - 8, W, 8) & np.isin(b.xx % 14, range(8)), col, mortar, 8, 8)

@scene("bg-regicide-jack", "Castle Gate", "regicide", "Facing the Jacks: the moonlit gatehouse.")
def regicide_jack(b):
    sky = np.ones((H, W), bool)
    b.vgrad(sky, "#0b1030", "#3a2a5e", 0, 110)
    stars(b, b.mask_rect(0, 0, W, 90), 120)
    b.fill(b.mask_ellipse(80, 26, 10, 10), "#f6f0d0"); b.fill(b.mask_ellipse(84, 23, 9, 9), "#0b1030", 0.0)
    b.fill(b.mask_ellipse(77, 24, 2, 2), "#d8d0b0"); b.fill(b.mask_ellipse(83, 30, 3, 2), "#d8d0b0")
    b.glow(80, 26, 30, "#c8d0ff", 0.35)
    b.stage()
    for (x, w_, t) in ((-4, 26, 60), (86, 26, 56)):
        b.bricks(b.mask_rect(x, t, w_, H - t), "#4a4a5e", "#22222e", 10, 6)
        b.fill(b.mask_poly([(x - 3, t), (x + w_ // 2, t - 22), (x + w_ + 3, t)]), "#5a2a6e")
    castle_wall(b, 96)
    b.stage()
    gate = b.mask_ellipse(54, 132, 18, 18) | b.mask_rect(36, 132, 37, 60)
    b.fill(gate, "#0a0810")
    for x in range(38, 72, 5): b.fill(b.mask_rect(x, 116, 2, 76) & gate, "#6b7486")
    for y in range(124, H, 8): b.fill(b.mask_rect(36, y, 37, 2) & gate, "#6b7486")
    b.fill(b.mask_ellipse(54, 132, 20, 20) & ~b.mask_ellipse(54, 132, 18, 18) & (b.yy < 140), "#8a8aa0")
    wall_torch(b, 24, 128); wall_torch(b, 80, 128)
    b.stage()
    b.vignette(0.6)
    b.stage()

@scene("bg-regicide-queen", "Royal Hall", "regicide", "Facing the Queens: banners and candlelight.")
def regicide_queen(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#2a1a3a", "#120a1a", 0, 130)
    b.bricks(b.mask_rect(0, 0, W, 130), "#3a2a4a", "#1a1022", 16, 8, var=0.1)
    b.stage()
    fl = b.mask_rect(0, 130, W, H - 130)
    for y in range(130, H):
        for x in range(W):
            sq = ((x // 9) + ((y - 130) // 6)) % 2
            b.img[y, x] = hx("#d8d0c0") if sq else hx("#2a2230")
    b.shade(np.clip(0.5 + (b.yy - 130) / 120, 0.5, 1), fl)
    b.stage()
    for x in (6, 94):
        b.fill(b.mask_rect(x, 0, 8, 130), "#8a7a92"); b.fill(b.mask_rect(x + 1, 0, 2, 130), "#b8aac2"); b.fill(b.mask_rect(x + 6, 0, 2, 130), "#5a4a62")
    for x in (24, 66):
        ban = b.mask_poly([(x, 10), (x + 18, 10), (x + 18, 74), (x + 9, 66), (x, 74)])
        b.vgrad(ban, "#7c3fb8", "#4b1e6b", 10, 74)
        b.fill(b.mask_rect(x, 10, 18, 3), "#e8c26a")
        b.fill(b.mask_poly([(x + 4, 38), (x + 4, 30), (x + 7, 34), (x + 9, 28), (x + 11, 34), (x + 14, 30), (x + 14, 38)]), "#f2c24b")
    b.stage()
    b.fill(b.mask_rect(53, 0, 2, 14), "#4a4a4a")
    b.fill(b.mask_ellipse(54, 18, 16, 4), "#c9a24b")
    for x in range(40, 70, 5):
        b.fill(b.mask_rect(x, 12, 1, 4), "#fff4b0"); b.px(x, 11, "#ffe066")
    b.glow(54, 16, 70, "#ffd890", 0.5)
    b.vignette(0.6)
    b.stage()

@scene("bg-regicide-king", "Throne Room", "regicide", "Facing the Kings: the corrupted throne.")
def regicide_king(b):
    m = np.ones((H, W), bool)
    b.vgrad(m, "#2a0e14", "#120408", 0, H)
    b.bricks(b.mask_rect(0, 0, W, 120), "#3a1a22", "#16060a", 14, 7, var=0.1)
    b.stage()
    win = b.mask_ellipse(54, 30, 16, 16) | b.mask_rect(38, 30, 33, 34)
    b.fill(win, "#1a1020")
    for y in range(14, 64):
        for x in range(38, 71):
            if win[y, x] and (x % 6 != 0 and y % 7 != 0):
                b.img[y, x] = hx(["#c62828", "#283593", "#f2c24b", "#2e7d32", "#7c3fb8"][((x // 6) + (y // 7) * 2) % 5])
    b.glow(54, 40, 50, "#ff8a6a", 0.35)
    b.stage()
    b.fill(b.mask_poly([(40, H), (68, H), (60, 112), (48, 112)]), "#9a1a24")
    b.fill(b.mask_poly([(42, H), (44, H), (49, 112), (48, 112)]), "#f2c24b", 0.7)
    b.fill(b.mask_poly([(66, H), (64, H), (59, 112), (60, 112)]), "#f2c24b", 0.7)
    for k, y in enumerate((112, 118, 124)):
        b.fill(b.mask_rect(30 - k * 4, y, 48 + k * 8, 6), "#5a4a52"); b.fill(b.mask_rect(30 - k * 4, y, 48 + k * 8, 1), "#8a7a82")
    b.stage()
    th = b.mask_poly([(44, 112), (44, 74), (49, 66), (54, 72), (59, 66), (64, 74), (64, 112)])
    b.fill(th, "#c9a24b"); b.fill(b.mask_rect(47, 80, 14, 26), "#7a1420"); b.fill(b.mask_rect(44, 100, 20, 4), "#a8742a")
    b.px(49, 66, "#ff4a4a"); b.px(59, 66, "#ff4a4a"); b.px(54, 72, "#6ef0ff")
    for x in (10, 88):
        b.fill(b.mask_rect(x, 0, 10, 140), "#4a2a32"); b.fill(b.mask_rect(x + 1, 0, 2, 140), "#7a4a52")
    wall_torch(b, 22, 84, "#ff6a3a"); wall_torch(b, 80, 84, "#ff6a3a")
    b.vignette(0.7)
    b.stage()

# ======================================================================= Dungeon Solitaire (tomb)

def tomb(b, escape=False):
    sand = lambda mk: (b.bricks(mk, "#9a7a4a", "#4a3820", 16, 8, var=0.12))
    corridor(b, (36, 70, 36, 56), None, None, ("#1e160c", "#3a2c18"), sand,
             lambda mk: (b.vgrad(mk, "#3a2c18", "#6a5434", 126, H), b.noise(mk, 8)))
    b.stage()
    # glyph carvings on the side walls
    glyphs = ["eye", "ankh", "bird", "wave", "sun"]
    for (x, y) in [(6, 40), (6, 70), (6, 100), (92, 50), (92, 80), (92, 110), (16, 130), (84, 130)]:
        g = b.rng.choice(glyphs); c = "#3a2810"
        if g == "eye": b.fill(b.mask_ellipse(x + 4, y + 3, 4, 2), c); b.px(x + 4, y + 3, "#c9a24b")
        elif g == "ankh": b.fill(b.mask_ellipse(x + 4, y + 2, 2, 2.5) & ~b.mask_ellipse(x + 4, y + 2, 1, 1.4), c); b.fill(b.mask_rect(x + 3, y + 4, 2, 6), c); b.fill(b.mask_rect(x + 1, y + 5, 6, 1), c)
        elif g == "bird": b.fill(b.mask_poly([(x, y + 6), (x + 6, y + 2), (x + 8, y + 4), (x + 4, y + 7)]), c)
        elif g == "wave":
            for k in range(8): b.px(x + k, y + 3 + (1 if k % 4 < 2 else 0), c)
        else: b.fill(b.mask_ellipse(x + 4, y + 4, 3, 3), c)
    b.stage()
    if escape:
        b.fill(b.mask_rect(40, 72, 28, 54), "#fff6d8")
        b.glow(54, 100, 70, "#fff0c0", 0.8)
        for _ in range(60):
            x, y = b.rng.randrange(20, 90), b.rng.randrange(40, 170)
            b.px(x, y, "#fff6d8", b.rng.uniform(0.3, 0.8))
    else:
        sarc = b.mask_poly([(46, 126), (48, 96), (54, 90), (60, 96), (62, 126)])
        b.fill(sarc, "#c9a24b"); b.fill(b.mask_ellipse(54, 98, 4, 5), "#e8c26a"); b.fill(b.mask_rect(50, 106, 8, 2), "#2a5aa8")
        b.fill(b.mask_rect(50, 112, 8, 2), "#2a5aa8")
        b.glow(54, 110, 26, "#ffd070", 0.3)
        wall_torch(b, 14, 78); wall_torch(b, 88, 78)
    b.vignette(0.75)
    b.stage()

@scene("bg-tomb", "Tomb of Four Kings", "dungeon-solitaire", "Delving: sandstone halls and a royal sarcophagus.")
def tomb_delve(b): tomb(b)

@scene("bg-tomb-escape", "The Way Out", "dungeon-solitaire", "Retreating: daylight at the end of the tunnel.")
def tomb_escape(b): tomb(b, escape=True)

# ======================================================================= Joker Jailbreak

@scene("bg-jailbreak", "The Cell", "joker-jailbreak", "Moonlight through the bars, chains and straw.")
def jail(b):
    wall = np.ones((H, W), bool)
    b.bricks(wall, "#5a5f6a", "#26292f", 13, 7)
    b.stage()
    fl = b.mask_rect(0, 150, W, H - 150)
    b.vgrad(fl, "#3a3a40", "#5a5660", 150, H)
    b.bricks(fl, "#4a4a52", "#26262c", 20, 8, ox=5)
    for _ in range(90):
        x, y = b.rng.randrange(W), b.rng.randrange(166, H)
        b.line(x, y, x + b.rng.choice([-3, -2, 2, 3]), y - 1, b.rng.choice(["#d8b860", "#a88a40"]), 0.9)
    b.stage()
    win = b.mask_rect(38, 22, 32, 26)
    b.vgrad(win, "#1a2850", "#2a3a6a", 22, 48)
    stars(b, win, 25)
    b.fill(b.mask_ellipse(62, 30, 4, 4), "#f6f0d0")
    for x in range(41, 70, 6): b.fill(b.mask_rect(x, 22, 2, 26), "#2a2c32"); b.fill(b.mask_rect(x, 22, 1, 26), "#6b7486")
    b.fill(b.mask_rect(36, 20, 36, 2), "#3a3c44"); b.fill(b.mask_rect(36, 48, 36, 2), "#3a3c44")
    beam = b.mask_poly([(38, 48), (70, 48), (96, 178), (40, 178)])
    b.fill(beam, "#c8d8ff", 0.13)
    b.stage()
    for x0 in (14, 92):
        for y in range(0, 90, 4):
            b.fill(b.mask_ellipse(x0, y + 2, 2, 2.4) & ~b.mask_ellipse(x0, y + 2, 1, 1.2), "#8a8fa0")
        b.fill(b.mask_ellipse(x0, 94, 5, 4) & ~b.mask_ellipse(x0, 94, 3, 2.4), "#8a8fa0")
    b.fill(b.mask_rect(80, 168, 12, 12), "#6a4a2a"); b.fill(b.mask_rect(80, 168, 12, 2), "#8a8fa0"); b.fill(b.mask_rect(80, 176, 12, 1), "#8a8fa0")
    b.vignette(0.7)
    b.stage()

if __name__ == "__main__":
    ts = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    os.makedirs("bg", exist_ok=True)
    only = sys.argv[2:] or list(SCENES)
    for i, sid in enumerate(only):
        name, game, notes, fn = SCENES[sid]
        b = BG(seed=hash(sid) & 0xffff)
        fn(b)
        b.export(name, f"bg/{sid}.png", f"bg/{sid}.json", sid, ["background", game], notes, ts + i)
        print(sid, os.path.getsize(f"bg/{sid}.png"), os.path.getsize(f"bg/{sid}.json"))
