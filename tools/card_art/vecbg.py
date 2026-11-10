"""Vector (SVG + Android VectorDrawable) backgrounds for the non-fantasy card games and every number/word game."""
import math, random, os, sys

W, H = 360, 640

def f(v): return ("%.1f" % v).rstrip("0").rstrip(".")

def rect(x, y, w, h, r=0):
    if r <= 0: return f"M{f(x)},{f(y)}h{f(w)}v{f(h)}h{f(-w)}z"
    r = min(r, w / 2, h / 2)
    return (f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)}v{f(h - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(r)}"
            f"h{f(-(w - 2 * r))}a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(-r)}v{f(-(h - 2 * r))}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(-r)}z")

def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z"

def poly(pts, close=True):
    s = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    return s + ("z" if close else "")

def rot(pts, cx, cy, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]

def rrect_rot(x, y, w, h, deg):
    return poly(rot([(x, y), (x + w, y), (x + w, y + h), (x, y + h)], x + w / 2, y + h / 2, deg))

def line(x0, y0, x1, y1): return f"M{f(x0)},{f(y0)}L{f(x1)},{f(y1)}"

def wave(y, amp, period, phase=0, x0=-10, x1=W + 10, step=6):
    pts = [(x, y + amp * math.sin((x / period) * 2 * math.pi + phase)) for x in range(x0, x1 + step, step)]
    return poly(pts, close=False)

def band(y, amp, period, phase, bottom=H + 10):
    pts = [(x, y + amp * math.sin((x / period) * 2 * math.pi + phase)) for x in range(-10, W + 16, 6)]
    return poly(pts + [(W + 10, bottom), (-10, bottom)])

def plus(cx, cy, s, t):
    return rect(cx - s, cy - t / 2, 2 * s, t) + rect(cx - t / 2, cy - s, t, 2 * s)

def heart(cx, cy, s):
    return (f"M{f(cx)},{f(cy + s * 0.9)}C{f(cx - s * 1.3)},{f(cy)} {f(cx - s * 0.9)},{f(cy - s)} {f(cx)},{f(cy - s * 0.35)}"
            f"C{f(cx + s * 0.9)},{f(cy - s)} {f(cx + s * 1.3)},{f(cy)} {f(cx)},{f(cy + s * 0.9)}z")

def spade(cx, cy, s):
    return (f"M{f(cx)},{f(cy - s)}C{f(cx + s * 1.3)},{f(cy)} {f(cx + s * 0.8)},{f(cy + s * 0.9)} {f(cx + s * 0.1)},{f(cy + s * 0.45)}"
            f"L{f(cx + s * 0.35)},{f(cy + s * 1.1)}L{f(cx - s * 0.35)},{f(cy + s * 1.1)}L{f(cx - s * 0.1)},{f(cy + s * 0.45)}"
            f"C{f(cx - s * 0.8)},{f(cy + s * 0.9)} {f(cx - s * 1.3)},{f(cy)} {f(cx)},{f(cy - s)}z")

def diamond(cx, cy, s): return poly([(cx, cy - s), (cx + s * 0.75, cy), (cx, cy + s), (cx - s * 0.75, cy)])

def club(cx, cy, s):
    return circle(cx, cy - s * 0.45, s * 0.42) + circle(cx - s * 0.48, cy + s * 0.15, s * 0.42) + circle(cx + s * 0.48, cy + s * 0.15, s * 0.42) + \
        poly([(cx - s * 0.12, cy), (cx + s * 0.12, cy), (cx + s * 0.35, cy + s * 1.05), (cx - s * 0.35, cy + s * 1.05)])


class Art:
    def __init__(self, seed=1):
        self.items = []; self.rng = random.Random(seed)

    def lin(self, x1, y1, x2, y2, *stops): return ("linear", (x1, y1, x2, y2), stops)
    def rad(self, cx, cy, r, *stops): return ("radial", (cx, cy, r), stops)

    def bg(self, fill): self.path(rect(0, 0, W, H), fill)

    def path(self, d, fill=None, alpha=1.0, stroke=None, sw=0.0, salpha=1.0):
        if d: self.items.append((d, fill, alpha, stroke, sw, salpha))

    # ---------------------------------------------- export
    def svg(self):
        defs, body = [], []
        for i, (d, fill, a, st, sw, sa) in enumerate(self.items):
            attrs = []
            if isinstance(fill, tuple):
                gid = f"g{i}"
                kind, geo, stops = fill
                ss = "".join(f'<stop offset="{o}" stop-color="{c[:7]}" stop-opacity="{int(c[7:9], 16) / 255 if len(c) > 7 else 1:.3f}"/>' for o, c in stops)
                if kind == "linear":
                    defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{geo[0]}" y1="{geo[1]}" x2="{geo[2]}" y2="{geo[3]}">{ss}</linearGradient>')
                else:
                    defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{geo[0]}" cy="{geo[1]}" r="{geo[2]}">{ss}</radialGradient>')
                attrs.append(f'fill="url(#{gid})"')
            else:
                attrs.append(f'fill="{fill or "none"}"')
            if a != 1: attrs.append(f'fill-opacity="{a}"')
            if st: attrs.append(f'stroke="{st}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"' + (f' stroke-opacity="{sa}"' if sa != 1 else ""))
            body.append(f'<path d="{d}" {" ".join(attrs)}/>')
        return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}"><defs>{"".join(defs)}</defs>{"".join(body)}</svg>\n'

    def vector(self):
        def argb(c):
            c = c.lstrip("#"); a = c[6:8] if len(c) > 6 else "FF"
            return f"#{a}{c[:6]}".upper()
        out = []
        for (d, fill, a, st, sw, sa) in self.items:
            attrs = [f'android:pathData="{d}"']
            inner = ""
            if isinstance(fill, tuple):
                kind, geo, stops = fill
                items = "".join(f'<item android:offset="{o}" android:color="{argb(c)}"/>' for o, c in stops)
                if kind == "linear":
                    g = f'<gradient android:type="linear" android:startX="{geo[0]}" android:startY="{geo[1]}" android:endX="{geo[2]}" android:endY="{geo[3]}">{items}</gradient>'
                else:
                    g = f'<gradient android:type="radial" android:centerX="{geo[0]}" android:centerY="{geo[1]}" android:gradientRadius="{geo[2]}">{items}</gradient>'
                inner = f'<aapt:attr name="android:fillColor">{g}</aapt:attr>'
            elif fill:
                attrs.append(f'android:fillColor="{argb(fill)}"')
            if a != 1: attrs.append(f'android:fillAlpha="{a}"')
            if st:
                attrs += [f'android:strokeColor="{argb(st)}"', f'android:strokeWidth="{sw}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
                if sa != 1: attrs.append(f'android:strokeAlpha="{sa}"')
            out.append(f'    <path {" ".join(attrs)}>{inner}</path>' if inner else f'    <path {" ".join(attrs)}/>')
        return ('<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
                f'    android:width="{W}dp" android:height="{H}dp" android:viewportWidth="{W}" android:viewportHeight="{H}">\n' + "\n".join(out) + "\n</vector>\n")


SCENES = {}
def scene(sid):
    def deco(fn): SCENES[sid] = fn; return fn
    return deco

# ============================================================ card games (non-fantasy)

@scene("vbg_solitaire")
def solitaire(a):
    a.bg(a.rad(180, 260, 520, (0, "#3E9A55"), (1, "#123E22")))
    lat = "".join(line(x, 0, x + 640, 640) + line(x + 640, 0, x, 640) for x in range(-640, 360, 40))
    a.path(lat, stroke="#FFFFFF", sw=1, salpha=0.05)
    a.path(rect(14, 14, W - 28, H - 28, 22), stroke="#E8C26A", sw=2, salpha=0.35)
    for i, deg in enumerate((-30, -15, 0, 15, 30)):
        a.path(poly(rot([(150, 470), (210, 470), (210, 556), (150, 556)], 180, 640, deg)), "#FFFFFF", 0.10, "#FFFFFF", 1.5, 0.25)
    a.path(heart(180, 90, 16), "#FFFFFF", 0.08); a.path(spade(70, 300, 14), "#FFFFFF", 0.06); a.path(diamond(290, 300, 16), "#FFFFFF", 0.06)
    a.path(club(180, 380, 14), "#FFFFFF", 0.05)

@scene("vbg_blackjack")
def blackjack(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#4A0E1A"), (0.25, "#6E1426"), (0.251, "#0F4A2A"), (1, "#0A2E1A")))
    a.path(rect(0, 150, W, 10), "#E8C26A", 0.55)
    a.path(rect(0, 160, W, H - 160), a.rad(180, 160, 520, (0, "#2F8A4C"), (1, "#0A2E1A")))
    for r in (200, 290):
        a.path(f"M{180 - r},160 A{r},{r} 0 0 0 {180 + r},160", stroke="#F2C24B", sw=3, salpha=0.45)
    for x in range(0, W, 36):
        a.path(diamond(x + 18, 75, 10), "#E8C26A", 0.12)
    for (x, y, c) in ((44, 560, "#C62828"), (70, 590, "#1565C0"), (300, 570, "#2E7D32"), (318, 600, "#212121"), (40, 600, "#E8C26A")):
        for k in range(4):
            a.path(circle(x, y - k * 6, 18), c); a.path(circle(x, y - k * 6, 12), stroke="#FFFFFF", sw=2, salpha=0.6)
    a.path(poly([(150, 0), (210, 0), (196, 26), (164, 26)]), "#111111")
    a.path(circle(180, 30, 120), a.rad(180, 30, 120, (0, "#FFE2A022"), (1, "#FFE2A000")))

@scene("vbg_gofish")
def gofish(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#8FE0F2"), (1, "#0B3A66")))
    for i, x in enumerate(range(-40, 360, 70)):
        a.path(poly([(x, 0), (x + 30, 0), (x - 120, H), (x - 170, H)]), "#FFFFFF", 0.06)
    a.path(band(560, 10, 180, 0), "#D8C38A"); a.path(band(585, 8, 140, 1), "#B89F64")
    for x0, ph in ((40, 0), (90, 1), (270, 2), (320, 3)):
        d = "M" + " L".join(f"{f(x0 + 10 * math.sin(t / 22 + ph))},{f(600 - t)}" for t in range(0, 230, 8))
        a.path(d, stroke="#2F8A3A", sw=7, salpha=0.9); a.path(d, stroke="#6FC15A", sw=2, salpha=0.8)
    for (x, y, s, c, dr) in ((110, 220, 1.0, "#FF8A3D", 1), (250, 330, 0.8, "#FFD23D", -1), (150, 440, 0.7, "#FF6F61", 1), (290, 150, 0.6, "#FFA53D", -1)):
        a.path(f"M{f(x - 24 * s)},{f(y)}a{f(24 * s)},{f(14 * s)} 0 1,0 {f(48 * s)},0a{f(24 * s)},{f(14 * s)} 0 1,0 {f(-48 * s)},0z", c)
        a.path(poly([(x - 22 * s * dr, y), (x - 40 * s * dr, y - 14 * s), (x - 40 * s * dr, y + 14 * s)]), c)
        a.path(circle(x + 12 * s * dr, y - 3 * s, 3 * s), "#1A1420")
    for _ in range(30):
        x, y, r = a.rng.uniform(0, W), a.rng.uniform(0, 520), a.rng.choice([3, 4, 6, 8])
        a.path(circle(x, y, r), stroke="#E8FBFF", sw=1.5, salpha=0.7)

@scene("vbg_cuttle")
def cuttle(a):
    a.bg(a.rad(180, 200, 600, (0, "#3A2266"), (1, "#0A0614")))
    for r in range(40, 700, 46):
        a.path(circle(180, 200, r), stroke="#B48AE6", sw=1.5, salpha=0.12)
    for (x0, ph, w0, col) in ((20, 0, 26, "#5A3486"), (330, 1.8, 30, "#4A2A72"), (130, 3.0, 20, "#6A3E96"), (250, 4.3, 18, "#55307E")):
        pts_l, pts_r = [], []
        for y in range(H + 20, 180, -14):
            t = (H - y) / 460
            x = x0 + math.sin(t * 5 + ph) * 60 * t
            w = max(2, w0 * (1 - t * 0.9))
            pts_l.append((x - w, y)); pts_r.append((x + w, y))
            if int(y) % 42 == 0 and w > 6: a.path(circle(x + w * 0.5, y, w * 0.25), "#D4B8F5", 0.6)
        a.path(poly(pts_l + pts_r[::-1]), col, 0.85)
    for _ in range(40):
        x, y = a.rng.uniform(0, W), a.rng.uniform(0, H); c = a.rng.choice(["#6EF0FF", "#FF7AD9", "#B48AE6"])
        r = a.rng.choice([6, 8, 12])
        a.path(circle(x, y, r), a.rad(x, y, r, (0, c + "CC"), (1, c + "00"))); a.path(circle(x, y, 1.5), "#FFFFFF")

@scene("vbg_jailbreak")
def jailbreak(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#4A515C"), (1, "#22262C")))
    bricks = ""
    for row, y in enumerate(range(0, 520, 28)):
        for x in range(-40 + (row % 2) * 30, W, 60): bricks += rect(x + 2, y + 2, 56, 24, 3)
    a.path(bricks, "#5D6570", 0.55)
    a.path(rect(0, 520, W, 120), a.lin(0, 520, 0, H, (0, "#3A3A40"), (1, "#55525C")))
    a.path(rect(110, 70, 140, 120, 6), a.lin(0, 70, 0, 190, (0, "#1A2850"), (1, "#2E4072")))
    a.path(circle(210, 108, 18), "#F6F0D0")
    for _ in range(14): a.path(circle(a.rng.uniform(116, 244), a.rng.uniform(76, 184), 1.3), "#FFFFFF", 0.8)
    for x in range(124, 250, 24): a.path(rect(x, 66, 7, 128, 3), "#2A2C32"); a.path(rect(x, 66, 2, 128), "#8A93A3")
    a.path(poly([(110, 190), (250, 190), (340, 600), (130, 600)]), "#C8D8FF", 0.08)
    for x in range(-20, W, 30): a.path(rect(x, 0, 12, H, 6), stroke="#111418", sw=1, salpha=0.0)
    for x0 in (40, 320):
        links = "".join(f"M{x0 - 6},{y}a6,9 0 1,0 12,0a6,9 0 1,0 -12,0z" for y in range(0, 300, 16))
        a.path(links, stroke="#9AA3B0", sw=3)

# ============================================================ number games

def scatter(a, n, fn, x0=0, y0=0, x1=W, y1=H):
    return "".join(fn(a.rng.uniform(x0, x1), a.rng.uniform(y0, y1), i) for i in range(n))

@scene("vbg_sudoku")
def sudoku(a):
    a.bg(a.lin(0, 0, W, H, (0, "#EAF1FC"), (1, "#C9DAF5")))
    g = ""
    for k in range(10):
        p = 30 + k * 300 / 9
        g += line(p, 170, p, 470) + line(30, 170 + k * 300 / 9, 330, 170 + k * 300 / 9)
    a.path(poly(rot([(30, 170), (330, 170), (330, 470), (30, 470)], 180, 320, -8)), "#FFFFFF", 0.35)
    a.path(" ".join(poly(rot([(x0, y0), (x1, y1)], 180, 320, -8), close=False) for x0, y0, x1, y1 in
                    [(30 + k * 300 / 9, 170, 30 + k * 300 / 9, 470) for k in range(10)] + [(30, 170 + k * 300 / 9, 330, 170 + k * 300 / 9) for k in range(10)]),
           stroke="#2F6FD6", sw=1.5, salpha=0.18)
    a.path(" ".join(poly(rot([(x0, y0), (x1, y1)], 180, 320, -8), close=False) for x0, y0, x1, y1 in
                    [(30 + k * 100, 170, 30 + k * 100, 470) for k in range(4)] + [(30, 170 + k * 100, 330, 170 + k * 100) for k in range(4)]),
           stroke="#2F6FD6", sw=4, salpha=0.22)
    cells = "".join(poly(rot([(30 + c * 33.33 + 3, 170 + r * 33.33 + 3), (30 + c * 33.33 + 30, 170 + r * 33.33 + 3), (30 + c * 33.33 + 30, 170 + r * 33.33 + 30), (30 + c * 33.33 + 3, 170 + r * 33.33 + 30)], 180, 320, -8))
                    for (r, c) in [(1, 1), (2, 6), (4, 4), (6, 2), (7, 7), (0, 8), (8, 0)])
    a.path(cells, "#2F6FD6", 0.22)
    a.path(scatter(a, 40, lambda x, y, i: rect(x, y, 10, 10, 2)), "#2F6FD6", 0.08)

@scene("vbg_cage_math")
def cage_math(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FFF4E6"), (1, "#FFD9B0")))
    syms = ""
    for i in range(36):
        x, y = a.rng.uniform(10, 350), a.rng.uniform(10, 630); s = a.rng.uniform(8, 18); k = i % 4
        if k == 0: syms += plus(x, y, s, s * 0.35)
        elif k == 1: syms += rect(x - s, y - s * 0.17, 2 * s, s * 0.35)
        elif k == 2: syms += poly(rot([(x - s, y - s * 0.17), (x + s, y - s * 0.17), (x + s, y + s * 0.17), (x - s, y + s * 0.17)], x, y, 45)) + poly(rot([(x - s, y - s * 0.17), (x + s, y - s * 0.17), (x + s, y + s * 0.17), (x - s, y + s * 0.17)], x, y, -45))
        else: syms += rect(x - s, y - s * 0.17, 2 * s, s * 0.35) + circle(x, y - s * 0.6, s * 0.22) + circle(x, y + s * 0.6, s * 0.22)
    a.path(syms, "#E07A1F", 0.16)
    cage = ""
    for (x, y, w, h) in ((40, 120, 110, 70), (150, 120, 70, 140), (220, 120, 100, 70), (40, 190, 110, 70), (220, 190, 100, 140), (40, 260, 180, 70)):
        for k in range(0, int(w), 14): cage += rect(x + k, y, 8, 3) + rect(x + k, y + h - 3, 8, 3)
        for k in range(0, int(h), 14): cage += rect(x, y + k, 3, 8) + rect(x + w - 3, y + k, 3, 8)
    a.path(cage, "#B5541A", 0.35)
    a.path(rect(40, 120, 280, 210, 6), "#FFFFFF", 0.25)

@scene("vbg_kakuro")
def kakuro(a):
    a.bg(a.lin(0, 0, W, H, (0, "#FFE9D6"), (1, "#F2B98C")))
    tri = ""
    for r in range(18):
        for c in range(10):
            if (r * 7 + c * 3) % 5 == 0:
                x, y = c * 40 - 20 + (r % 2) * 20, r * 40
                tri += poly([(x, y), (x + 36, y), (x, y + 36)])
    a.path(tri, "#8A3A12", 0.16)
    grid = "".join(rect(c * 40 - 20 + (r % 2) * 20 + 2, r * 40 + 2, 32, 32, 4) for r in range(18) for c in range(10) if (r + c) % 3 == 0)
    a.path(grid, "#FFFFFF", 0.22)
    diag = "".join(line(c * 40 - 20 + (r % 2) * 20, r * 40, c * 40 + 16 + (r % 2) * 20, r * 40 + 36) for r in range(18) for c in range(10) if (r * 7 + c * 3) % 5 == 0)
    a.path(diag, stroke="#FFFFFF", sw=1.5, salpha=0.5)

@scene("vbg_picture_logic")
def picture_logic(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#F4F0FF"), (1, "#D9CCF7")))
    art = ["..XX...XX..", ".XXXX.XXXX.", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X....."]
    s = 26; x0 = (W - 11 * s) / 2; y0 = 190
    on = "".join(rect(x0 + c * s + 2, y0 + r * s + 2, s - 4, s - 4, 3) for r, row in enumerate(art) for c, ch in enumerate(row) if ch == "X")
    off = "".join(rect(x0 + c * s + 2, y0 + r * s + 2, s - 4, s - 4, 3) for r, row in enumerate(art) for c, ch in enumerate(row) if ch != "X")
    a.path(off, "#FFFFFF", 0.45); a.path(on, "#7C4BB0", 0.35)
    clues = "".join(rect(x0 + c * s + 8, y0 - 40 - k * 14, 10, 8, 2) for c in range(11) for k in range(a.rng.randint(1, 3)))
    clues += "".join(rect(x0 - 26 - k * 16, y0 + r * s + 9, 10, 8, 2) for r in range(9) for k in range(a.rng.randint(1, 3)))
    a.path(clues, "#7C4BB0", 0.3)
    a.path(scatter(a, 50, lambda x, y, i: rect(x, y, 12, 12, 2), 0, 480, W, H), "#7C4BB0", 0.08)
    a.path(scatter(a, 30, lambda x, y, i: rect(x, y, 12, 12, 2), 0, 0, W, 130), "#7C4BB0", 0.08)

@scene("vbg_hashi")
def hashi(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#BDEBF7"), (1, "#3A8FC2")))
    a.path("".join(wave(y, 4, 60, y / 30) for y in range(20, H, 34)), stroke="#FFFFFF", sw=1.5, salpha=0.25)
    isl = [(60, 90), (200, 90), (310, 90), (60, 250), (140, 250), (310, 250), (140, 400), (250, 400), (60, 540), (250, 540), (310, 470)]
    br = [(0, 1, 2), (1, 2, 1), (0, 3, 1), (3, 4, 2), (4, 6, 1), (6, 7, 2), (2, 5, 2), (5, 10, 1), (7, 9, 1), (3, 8, 2), (8, 9, 1)]
    bridges = ""
    for i, j, n in br:
        (x0, y0), (x1, y1) = isl[i], isl[j]
        for k in range(n):
            o = (k - (n - 1) / 2) * 7
            bridges += line(x0 + (o if x0 == x1 else 0), y0 + (o if y0 == y1 else 0), x1 + (o if x0 == x1 else 0), y1 + (o if y0 == y1 else 0))
    a.path(bridges, stroke="#7A4A22", sw=4, salpha=0.7)
    a.path("".join(circle(x, y, 22) for x, y in isl), "#F6E7C1")
    a.path("".join(circle(x, y, 22) for x, y in isl), stroke="#7A4A22", sw=3, salpha=0.8)
    a.path("".join(circle(x - 6, y - 6, 6) for x, y in isl), "#FFFFFF", 0.5)

@scene("vbg_grid_recall")
def grid_recall(a):
    a.bg(a.rad(180, 320, 520, (0, "#5B3F9E"), (1, "#1C1238")))
    s = 52
    for r in range(12):
        for c in range(7):
            x, y = c * s + 2, r * s + 8
            lvl = a.rng.random()
            if lvl > 0.86: a.path(rect(x + 4, y + 4, s - 8, s - 8, 10), "#FFD86E", 0.55); a.path(rect(x - 6, y - 6, s + 12, s + 12, 16), "#FFD86E", 0.08)
            elif lvl > 0.6: a.path(rect(x + 4, y + 4, s - 8, s - 8, 10), "#B48AE6", 0.3)
            else: a.path(rect(x + 4, y + 4, s - 8, s - 8, 10), "#FFFFFF", 0.06)

@scene("vbg_code_breaker")
def code_breaker(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#3A2A20"), (1, "#1E140E")))
    a.path(rect(40, 30, 280, 580, 18), a.lin(0, 30, 0, 610, (0, "#8A5A34"), (1, "#5A3524")))
    a.path(rect(40, 30, 280, 580, 18), stroke="#C98D52", sw=2, salpha=0.6)
    cols = ["#E53935", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#FB8C00"]
    for r in range(10):
        y = 70 + r * 52
        for k in range(4):
            if r < 7: a.path(circle(90 + k * 46, y, 15), cols[(r * 3 + k * 5) % 6]); a.path(circle(85 + k * 46, y - 5, 4), "#FFFFFF", 0.5)
            else: a.path(circle(90 + k * 46, y, 9), "#2A1A10", 0.8)
        for k in range(4):
            px, py = 268 + (k % 2) * 16, y - 8 + (k // 2) * 16
            if r < 7: a.path(circle(px, py, 5), ["#FFFFFF", "#111111", "#2A1A10"][(r + k) % 3])
            else: a.path(circle(px, py, 3), "#2A1A10", 0.8)
    a.path(rect(40, 560, 280, 50, 18), "#2A1A10", 0.6)

@scene("vbg_lumen")
def lumen(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#12162A"), (1, "#05070F")))
    s = 60
    for r in range(11):
        for c in range(6):
            cx, cy = c * s + 30, r * s + 20
            on = a.rng.random() > 0.55
            if on:
                a.path(circle(cx, cy, 40), a.rad(cx, cy, 40, (0, "#FFE58A88"), (1, "#FFE58A00")))
                a.path(circle(cx, cy, 14), "#FFE58A"); a.path(circle(cx - 4, cy - 4, 5), "#FFFFFF", 0.8)
            else:
                a.path(circle(cx, cy, 14), "#2A3050"); a.path(circle(cx, cy, 14), stroke="#3E4670", sw=2)

@scene("vbg_sliding_tiles")
def sliding_tiles(a):
    a.bg(a.lin(0, 0, W, H, (0, "#D7F5EF"), (1, "#7CCFC0")))
    s = 70
    for r in range(10):
        for c in range(6):
            if (r, c) in ((3, 2), (6, 4)): continue
            x, y = c * s - 30 + (r % 2) * 10, r * s - 20
            a.path(rect(x + 4, y + 8, s - 10, s - 10, 12), "#0F5E52", 0.18)
            a.path(rect(x + 4, y + 4, s - 10, s - 10, 12), "#FFFFFF", 0.45 if (r + c) % 3 else 0.7)
    a.path(rect(2 * s - 30 + 10 + 4 + 18, 3 * s - 20 + 4, s - 10, s - 10, 12), stroke="#0F5E52", sw=3, salpha=0.35)
    a.path(poly([(2 * s + 50, 3 * s + 15), (2 * s + 70, 3 * s + 15), (2 * s + 60, 3 * s + 5)]), "#0F5E52", 0.0)

# ============================================================ word games

@scene("vbg_crossword")
def crossword(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#F7F3EA"), (1, "#E6DCC6")))
    s = 30
    blk, wht = "", ""
    for r in range(22):
        for c in range(13):
            x, y = c * s - 15, r * s - 10
            if (r * 5 + c * 3 + (r * c) % 4) % 7 == 0: blk += rect(x, y, s, s)
            else: wht += rect(x + 1, y + 1, s - 2, s - 2)
    a.path(wht, "#FFFFFF", 0.6); a.path(blk, "#1F2A37", 0.22)
    nums = "".join(rect(c * s - 12, r * s - 7, 5, 6) for r in range(22) for c in range(13) if (r * 3 + c * 7) % 11 == 0)
    a.path(nums, "#1F2A37", 0.25)
    a.path(rect(0, 0, W, H), a.lin(0, 0, 0, H, (0, "#F7F3EA00"), (0.5, "#F7F3EA66"), (1, "#F7F3EA00")))

@scene("vbg_word_search")
def word_search(a):
    a.bg(a.lin(0, 0, W, H, (0, "#FFF8E1"), (1, "#FFE0A3")))
    dots = "".join(rect(c * 28 + 10, r * 28 + 12, 8, 10, 2) for r in range(23) for c in range(13))
    a.path(dots, "#8A6A2A", 0.14)
    for (x0, y0, x1, y1, col) in ((24, 96, 220, 96, "#FF8A65"), (66, 180, 66, 400, "#4FC3F7"), (130, 240, 300, 410, "#81C784"), (300, 120, 160, 260, "#BA68C8"),
                                  (40, 500, 320, 500, "#FFD54F"), (250, 450, 250, 620, "#F06292")):
        a.path(line(x0, y0, x1, y1), stroke=col, sw=22, salpha=0.4)

@scene("vbg_haiku")
def haiku(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FDE2E4"), (0.55, "#FFF1E6"), (1, "#E2ECE9")))
    a.path(circle(250, 170, 70), "#F28482", 0.55)
    a.path(poly([(-10, 470), (70, 360), (130, 420), (210, 300), (300, 400), (370, 350), (370, 640), (-10, 640)]), "#9AA7B8", 0.6)
    a.path(poly([(-10, 530), (90, 450), (170, 510), (260, 440), (370, 520), (370, 640), (-10, 640)]), "#6B7A8F", 0.6)
    a.path(band(590, 6, 120, 0), "#4A5568", 0.5)
    a.path("M-10,120 C60,140 110,110 170,150 C200,170 230,175 260,170", stroke="#5A3524", sw=7)
    a.path("M100,128 C120,90 150,80 170,70", stroke="#5A3524", sw=4)
    blossoms = ""
    for (x, y) in ((40, 128), (88, 120), (130, 125), (170, 150), (215, 168), (150, 78), (120, 98), (250, 172), (70, 140), (190, 160)):
        for k in range(5):
            ang = k * 72 * math.pi / 180
            blossoms += circle(x + 6 * math.cos(ang), y + 6 * math.sin(ang), 5)
    a.path(blossoms, "#F7A8B8"); a.path("".join(circle(x, y, 2.5) for x, y in ((40, 128), (88, 120), (130, 125), (170, 150), (215, 168), (150, 78), (120, 98), (250, 172), (70, 140), (190, 160))), "#C2185B")
    a.path(scatter(a, 14, lambda x, y, i: f"M{f(x)},{f(y)}a5,3 30 1,0 10,0a5,3 30 1,0 -10,0z", 0, 200, W, 560), "#F7A8B8", 0.7)

@scene("vbg_alliteration")
def alliteration(a):
    a.bg(a.lin(0, 0, W, H, (0, "#FFE5D9"), (1, "#FFB4A2")))
    for cx, cy in ((40, 200), (320, 470)):
        for k, r in enumerate(range(30, 400, 34)):
            a.path(f"M{cx},{cy - r} A{r},{r} 0 0 1 {cx},{cy + r}" if cx < 180 else f"M{cx},{cy - r} A{r},{r} 0 0 0 {cx},{cy + r}",
                   stroke="#E5383B", sw=6 - k * 0.4, salpha=max(0.05, 0.4 - k * 0.03))
    a.path(scatter(a, 22, lambda x, y, i: circle(x, y, a.rng.choice([4, 6, 9]))), "#FFFFFF", 0.5)

@scene("vbg_synonyms")
def synonyms(a):
    a.bg(a.lin(0, 0, W, H, (0, "#E0F2F1"), (0.5, "#E0F2F1"), (0.5, "#FCE4EC"), (1, "#FCE4EC")))
    a.path(poly([(0, H), (W, 0), (W, H)]), "#FCE4EC")
    a.path(poly([(0, 0), (W, 0), (0, H)]), "#E0F2F1")
    a.path(circle(140, 280, 120), "#26A69A", 0.25); a.path(circle(220, 360, 120), "#EC407A", 0.25)
    a.path(circle(140, 280, 120), stroke="#26A69A", sw=3, salpha=0.5); a.path(circle(220, 360, 120), stroke="#EC407A", sw=3, salpha=0.5)
    a.path(rect(90, 270, 40, 10, 5) + rect(90, 290, 40, 10, 5), "#00695C", 0.45)
    a.path(rect(230, 350, 50, 10, 5), "#AD1457", 0.45)
    a.path(rect(250, 330, 10, 50, 5), "#AD1457", 0.0)
    a.path(scatter(a, 30, lambda x, y, i: rect(x, y, a.rng.uniform(20, 60), 7, 3.5)), "#37474F", 0.06)

@scene("vbg_rhyme_time")
def rhyme_time(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#E8EAF6"), (1, "#C5CAE9")))
    for base in (120, 330, 520):
        a.path("".join(wave(base + k * 14, 18, 300, base / 40) for k in range(5)), stroke="#3949AB", sw=1.5, salpha=0.35)
        for i in range(6):
            x = 30 + i * 56; y = base + 28 + 14 * math.sin((x / 300) * 2 * math.pi + base / 40) - (i % 3) * 14
            a.path(f"M{f(x - 8)},{f(y)}a8,6 -20 1,0 16,0a8,6 -20 1,0 -16,0z", "#3949AB", 0.55)
            a.path(rect(x + 6, y - 36, 2.5, 36), "#3949AB", 0.55)
            if i % 2 == 0: a.path(f"M{f(x + 8.5)},{f(y - 36)}c8,6 12,10 6,20", stroke="#3949AB", sw=2.5, salpha=0.55)

@scene("vbg_word_ladder")
def word_ladder(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#BBDEFB"), (1, "#FFF3E0")))
    a.path(circle(70, 90, 40), "#FFFFFF", 0.7); a.path(circle(100, 80, 30), "#FFFFFF", 0.7); a.path(circle(280, 160, 34), "#FFFFFF", 0.6)
    rails = line(90, H + 20, 230, -20) + line(170, H + 20, 310, -20)
    a.path(rails, stroke="#8D6E63", sw=12)
    rungs = ""
    for k in range(16):
        t = k / 15
        y = H + 20 - t * (H + 40)
        x = 90 + t * 140
        rungs += line(x, y, x + 80, y)
    a.path(rungs, stroke="#A1887F", sw=8)
    a.path("".join(rect(90 + (k / 15) * 140 + 26, H + 20 - (k / 15) * (H + 40) - 26, 28, 20, 4) for k in range(1, 15, 2)), "#FFFFFF", 0.6)

@scene("vbg_word_wheel")
def word_wheel(a):
    a.bg(a.rad(180, 320, 500, (0, "#FFF8E1"), (1, "#FFCC80")))
    cx, cy, R, r = 180, 320, 170, 58
    cols = ["#FFB74D", "#FFA726", "#FF9800", "#FB8C00", "#FFB74D", "#FFA726", "#FF9800"]
    for k in range(7):
        a0, a1 = (k / 7) * 2 * math.pi - math.pi / 2, ((k + 1) / 7) * 2 * math.pi - math.pi / 2
        p = poly([(cx + r * math.cos(a0), cy + r * math.sin(a0)), (cx + R * math.cos(a0), cy + R * math.sin(a0))] +
                 [(cx + R * math.cos(a0 + (a1 - a0) * t / 8), cy + R * math.sin(a0 + (a1 - a0) * t / 8)) for t in range(1, 9)] +
                 [(cx + r * math.cos(a1), cy + r * math.sin(a1))])
        a.path(p, cols[k], 0.45)
        mx, my = cx + (R + r) / 2 * math.cos((a0 + a1) / 2), cy + (R + r) / 2 * math.sin((a0 + a1) / 2)
        a.path(rect(mx - 9, my - 11, 18, 22, 4), "#FFFFFF", 0.55)
    a.path(circle(cx, cy, R), stroke="#E65100", sw=4, salpha=0.4)
    a.path(circle(cx, cy, r - 4), "#E65100", 0.45)
    a.path(rect(cx - 12, cy - 14, 24, 28, 5), "#FFFFFF", 0.7)
    for k in range(7):
        ang = (k / 7) * 2 * math.pi - math.pi / 2
        a.path(line(cx + r * math.cos(ang), cy + r * math.sin(ang), cx + R * math.cos(ang), cy + R * math.sin(ang)), stroke="#FFFFFF", sw=3, salpha=0.8)
    a.path(scatter(a, 24, lambda x, y, i: circle(x, y, 3)), "#E65100", 0.2)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "vbg"
    os.makedirs(out, exist_ok=True)
    for i, (sid, fn) in enumerate(SCENES.items()):
        a = Art(seed=i + 7); fn(a)
        open(f"{out}/{sid}.svg", "w").write(a.svg())
        open(f"{out}/{sid}.xml", "w").write(a.vector())
        print(sid, os.path.getsize(f"{out}/{sid}.xml"))
