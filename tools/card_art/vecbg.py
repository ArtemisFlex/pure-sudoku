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
# Ambient scenes only: no grids, cells, pegs, lights, islands or tiles that could be mistaken for the game.

def scatter(a, n, fn, x0=0, y0=0, x1=W, y1=H):
    return "".join(fn(a.rng.uniform(x0, x1), a.rng.uniform(y0, y1), i) for i in range(n))

def ribbon(y, amp, period, phase, thick):
    top = [(x, y + amp * math.sin(x / period * 2 * math.pi + phase)) for x in range(-10, W + 16, 8)]
    bot = [(x, y + thick + amp * math.sin(x / period * 2 * math.pi + phase + 0.6)) for x in range(W + 10, -16, -8)]
    return poly(top + bot)

def blob(cx, cy, r, rng, k=7):
    pts = []
    for i in range(k * 4):
        t = i / (k * 4) * 2 * math.pi
        rr = r * (1 + 0.12 * math.sin(3 * t + rng.uniform(0, 0.3)) + 0.06 * math.sin(5 * t))
        pts.append((cx + rr * math.cos(t), cy + rr * math.sin(t)))
    return poly(pts)

def gear(cx, cy, r, teeth):
    pts = []
    for i in range(teeth * 4):
        t = i / (teeth * 4) * 2 * math.pi
        rr = r if (i % 4) in (0, 1) else r * 0.84
        pts.append((cx + rr * math.cos(t), cy + rr * math.sin(t)))
    return poly(pts) + circle(cx, cy, r * 0.35)

@scene("vbg_sudoku")
def sudoku(a):
    a.bg(a.lin(0, 0, W, H, (0, "#EEF4FD"), (1, "#C6D8F4")))
    a.path(circle(300, 120, 150), "#2F6FD6", 0.08); a.path(circle(40, 420, 190), "#2F6FD6", 0.07); a.path(circle(260, 600, 120), "#79A8F2", 0.12)
    for k in range(5):
        a.path(wave(250 + k * 22, 26, 420, k * 0.5), stroke="#2F6FD6", sw=1.5, salpha=0.14)

@scene("vbg_cage_math")
def cage_math(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FFF4E6"), (1, "#FFD2A8")))
    for k, (y, c, al) in enumerate(((120, "#F4A261", 0.25), (230, "#E76F51", 0.16), (390, "#F4A261", 0.2), (520, "#E9C46A", 0.3))):
        a.path(ribbon(y, 28, 300 + k * 40, k, 46), c, al)

@scene("vbg_kakuro")
def kakuro(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FFE8CC"), (0.45, "#FFC8A2"), (1, "#C96F4A")))
    a.path(circle(260, 190, 64), "#FFF1D6", 0.8)
    a.path(band(330, 22, 360, 0.4), "#E8946A", 0.6)
    a.path(band(420, 26, 300, 2.0), "#C9714A", 0.7)
    a.path(band(520, 18, 260, 3.4), "#9E4F33", 0.75)

@scene("vbg_picture_logic")
def picture_logic(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#F6F2FF"), (1, "#DCD0F6")))
    for (x, y, r, c, al) in ((90, 140, 90, "#B39DDB", 0.35), (270, 260, 110, "#F48FB1", 0.25), (120, 430, 120, "#80DEEA", 0.25), (290, 560, 90, "#FFCC80", 0.3)):
        a.path(blob(x, y, r, a.rng), c, al)
    for k in range(4):
        y = 120 + k * 140
        a.path(f"M20,{y} C120,{y - 40} 220,{y + 50} 340,{y - 10}", stroke="#7E57C2", sw=10 - k * 2, salpha=0.12)

@scene("vbg_hashi")
def hashi(a):
    a.bg(a.lin(0, 0, 0, 300, (0, "#FFE7C2"), (1, "#BDEBF7")))
    a.path(circle(250, 270, 50), "#FFF4D6", 0.9)
    a.path(rect(0, 290, W, 350), a.lin(0, 290, 0, H, (0, "#5FB3D9"), (1, "#1E5F8C")))
    a.path("".join(wave(y, 2 + (y - 290) / 80, 50 + (y - 290) / 4, y / 13, step=4) for y in range(300, H, 18)), stroke="#FFFFFF", sw=1.5, salpha=0.3)
    a.path(rect(200, 296, 100, 3, 1.5) + rect(215, 306, 70, 3, 1.5), "#FFF4D6", 0.7)
    a.path(poly([(70, 286), (82, 250), (84, 286)]) + poly([(86, 286), (86, 256), (98, 286)]), "#FFFFFF", 0.85)
    a.path(poly([(62, 288), (104, 288), (96, 296), (70, 296)]), "#6B4A2A", 0.8)

@scene("vbg_grid_recall")
def grid_recall(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#1C1238"), (1, "#3B2770")))
    a.path(ribbon(140, 40, 380, 0.5, 70), a.lin(0, 100, 0, 260, (0, "#7CF5D400"), (0.5, "#7CF5D455"), (1, "#7CF5D400")))
    a.path(ribbon(230, 50, 320, 2.1, 90), a.lin(0, 180, 0, 360, (0, "#B48AE600"), (0.5, "#B48AE655"), (1, "#B48AE600")))
    for _ in range(70):
        x, y = a.rng.uniform(0, W), a.rng.uniform(0, H); r = a.rng.choice([0.8, 1, 1.2, 1.8])
        a.path(circle(x, y, r), "#FFFFFF", a.rng.uniform(0.3, 0.8))

@scene("vbg_code_breaker")
def code_breaker(a):
    a.bg(a.rad(180, 300, 560, (0, "#1F4A4F"), (1, "#0B1E21")))
    for (x, y, r, t, al) in ((60, 110, 90, 12, 0.10), (250, 200, 60, 9, 0.08), (300, 470, 130, 16, 0.08), (70, 560, 70, 10, 0.10)):
        a.path(gear(x, y, r, t), stroke="#8FD3C8", sw=2, salpha=al * 2.2)
    a.path(circle(180, 330, 52), stroke="#8FD3C8", sw=3, salpha=0.18)
    a.path(circle(180, 318, 12) + poly([(172, 324), (188, 324), (194, 360), (166, 360)]), "#8FD3C8", 0.12)

@scene("vbg_lumen")
def lumen(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#2A2E5A"), (1, "#0A0C1A")))
    for (x0, w, al) in ((60, 50, 0.07), (140, 70, 0.05), (230, 40, 0.07), (300, 60, 0.05)):
        a.path(poly([(x0, -10), (x0 + w, -10), (x0 + w * 2.2, H), (x0 - w * 0.6, H)]), "#FFE9A8", al)
    a.path(circle(180, -40, 160), a.rad(180, -40, 160, (0, "#FFE9A855"), (1, "#FFE9A800")))
    a.path(band(560, 14, 300, 1.2), "#05060E", 0.8)

@scene("vbg_sliding_tiles")
def sliding_tiles(a):
    a.bg(a.lin(0, 0, W, H, (0, "#DDF7F1"), (1, "#7FCFC0")))
    for k in range(7):
        y = -120 + k * 120
        a.path(poly([(-20, y + 160), (W + 20, y), (W + 20, y + 46), (-20, y + 206)]), "#FFFFFF" if k % 2 else "#0F5E52", 0.12 if k % 2 else 0.06)

# ============================================================ word games

@scene("vbg_crossword")
def crossword(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#F7F3EA"), (1, "#E8DEC8")))
    a.path("".join(line(24, y, W - 24, y) for y in range(60, H - 30, 26)), stroke="#9FB4C7", sw=1, salpha=0.35)
    a.path(line(54, 0, 54, H), stroke="#E57373", sw=1.5, salpha=0.35)
    for (x, y, r) in ((270, 120, 46), (90, 520, 38)):
        a.path(circle(x, y, r), stroke="#8D6E63", sw=5, salpha=0.18); a.path(circle(x + 3, y + 2, r - 6), stroke="#8D6E63", sw=2, salpha=0.1)
    a.path(poly(rot([(160, 330), (330, 330), (330, 342), (160, 342)], 245, 336, -28)), "#F2C24B", 0.55)
    a.path(poly(rot([(330, 330), (348, 336), (330, 342)], 245, 336, -28)), "#5D4037", 0.6)

@scene("vbg_word_search")
def word_search(a):
    a.bg(a.lin(0, 0, W, H, (0, "#FFF8E1"), (1, "#FFDCA0")))
    a.path(circle(170, 280, 110), stroke="#8A6A2A", sw=14, salpha=0.14)
    a.path(circle(170, 280, 96), "#FFFFFF", 0.25)
    a.path(poly(rot([(262, 360), (300, 360), (300, 500), (262, 500)], 281, 430, -40)), "#8A6A2A", 0.14)
    a.path(f"M120,230 A70,70 0 0 1 180,200", stroke="#FFFFFF", sw=8, salpha=0.6)
    a.path(scatter(a, 18, lambda x, y, i: circle(x, y, a.rng.choice([3, 5, 8]))), "#FFB74D", 0.25)

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

@scene("vbg_word_wheel")
def word_wheel(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FFF3D6"), (0.6, "#FFC27A"), (1, "#F08A5D")))
    for k in range(9):
        pts = [(180 + (20 + t * 1.1) * math.cos(t / 9 + k * 0.7), 300 + (20 + t * 1.1) * math.sin(t / 9 + k * 0.7)) for t in range(0, 260, 4)]
        a.path(poly(pts, close=False), stroke="#FFFFFF" if k % 2 else "#E65100", sw=3, salpha=0.25 if k % 2 else 0.12)
    a.path(circle(180, 300, 26), "#FFF8E1", 0.7)

# ============================================================ board games (ambient, nothing board-like)

def mountains(a, base, pts_list, col, alpha):
    a.path(poly(pts_list + [(W + 10, H + 10), (-10, H + 10)]), col, alpha)

@scene("vbg_chess")
def chess_bg(a):
    a.bg(a.lin(0, 0, W, H, (0, "#ECE8E1"), (1, "#C9C2B8")))
    for k in range(9):
        y0 = a.rng.uniform(-40, H)
        d = f"M-20,{f(y0)} C{f(a.rng.uniform(60, 140))},{f(y0 + a.rng.uniform(-80, 80))} {f(a.rng.uniform(180, 260))},{f(y0 + a.rng.uniform(-60, 120))} {W + 20},{f(y0 + a.rng.uniform(-40, 160))}"
        a.path(d, stroke="#8E8778", sw=a.rng.choice([1, 1.5, 2.5]), salpha=0.25)
    a.path(rect(0, 0, W, 16), "#B8975A", 0.6); a.path(rect(0, 16, W, 3), "#E8CF8E", 0.6)
    a.path(rect(0, H - 19, W, 3), "#E8CF8E", 0.6); a.path(rect(0, H - 16, W, 16), "#B8975A", 0.6)
    a.path(circle(300, 90, 160), a.rad(300, 90, 160, (0, "#FFF6DC66"), (1, "#FFF6DC00")))

@scene("vbg_checkers")
def checkers_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FFE0B2"), (1, "#E57373")))
    for k in range(-4, 12):
        a.path(poly([(k * 60, -10), (k * 60 + 26, -10), (k * 60 - 300, H + 10), (k * 60 - 326, H + 10)]), "#FFFFFF", 0.10)
    for (x, y, r) in ((60, 120, 50), (300, 260, 70), (110, 520, 60)):
        a.path(circle(x, y, r), "#C62828", 0.14); a.path(circle(x, y, r * 0.7), stroke="#FFFFFF", sw=3, salpha=0.25)

@scene("vbg_go")
def go_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#F4EFE6"), (1, "#DCD4C6")))
    a.path(circle(255, 165, 52), "#C0392B", 0.75)
    mountains(a, 0, [(-10, 380), (60, 300), (120, 340), (200, 250), (280, 330), (370, 290)], "#9AA0A6", 0.35)
    mountains(a, 0, [(-10, 450), (80, 380), (160, 430), (240, 360), (370, 420)], "#6B7177", 0.4)
    mountains(a, 0, [(-10, 540), (100, 480), (200, 520), (300, 470), (370, 500)], "#3E4347", 0.45)
    a.path("M-10,90 C60,100 110,80 170,110", stroke="#3B2A1E", sw=6)
    needles = ""
    for x0, y0 in ((40, 95), (90, 92), (140, 100), (165, 108)):
        for k in range(7):
            ang = -math.pi / 2 + (k - 3) * 0.32
            needles += line(x0, y0, x0 + 18 * math.cos(ang), y0 + 18 * math.sin(ang) + 4)
    a.path(needles, stroke="#2F5D3A", sw=2.2, salpha=0.9)

@scene("vbg_gomoku")
def gomoku_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#E8F5E9"), (1, "#A5D6A7")))
    for (x, w, al) in ((30, 16, 0.35), (80, 12, 0.25), (250, 18, 0.35), (310, 13, 0.3), (190, 9, 0.18)):
        a.path(rect(x, -10, w, H + 20, w / 2), "#558B2F", al)
        a.path("".join(rect(x - 1, y, w + 2, 3) for y in range(40 + int(x) % 50, H, 110)), "#33691E", al + 0.1)
        for y in range(70 + int(x) % 60, H, 140):
            a.path(poly([(x + w, y), (x + w + 40, y - 18), (x + w + 8, y + 4)]), "#7CB342", al + 0.15)

@scene("vbg_shogi")
def shogi_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#1E2A4A"), (1, "#0E1528")))
    for k, r in enumerate((24, 17, 10)):
        d = ""
        for row in range(-1, 28):
            for col in range(-1, 9):
                cx = col * 48 + (24 if row % 2 else 0); cy = row * 24 + 20
                d += f"M{f(cx - r)},{f(cy)}a{r},{r} 0 0 1 {2 * r},0"
        a.path(d, stroke="#8FA8D8", sw=1.4, salpha=0.22 - k * 0.04)
    a.path(circle(270, 110, 46), "#F2C24B", 0.35)

@scene("vbg_xiangqi")
def xiangqi_bg(a):
    a.bg(a.rad(180, 260, 560, (0, "#B3261E"), (1, "#5A0E0A")))
    for (cx, cy, s) in ((70, 130, 1.0), (280, 320, 1.3), (110, 520, 0.9)):
        d = (f"M{f(cx - 40 * s)},{f(cy)} C{f(cx - 40 * s)},{f(cy - 22 * s)} {f(cx - 10 * s)},{f(cy - 22 * s)} {f(cx - 8 * s)},{f(cy - 6 * s)} "
             f"C{f(cx - 6 * s)},{f(cy - 30 * s)} {f(cx + 30 * s)},{f(cy - 30 * s)} {f(cx + 26 * s)},{f(cy - 4 * s)} "
             f"C{f(cx + 46 * s)},{f(cy - 10 * s)} {f(cx + 50 * s)},{f(cy + 12 * s)} {f(cx + 30 * s)},{f(cy + 10 * s)}")
        a.path(d, stroke="#F2C24B", sw=3, salpha=0.4)
    for x in (60, 300):
        a.path(line(x, 0, x, 40), stroke="#2A0A08", sw=2)
        a.path(f"M{x - 22},62 a22,24 0 1,0 44,0a22,24 0 1,0 -44,0z", "#E53935"); a.path(rect(x - 14, 34, 28, 6, 2) + rect(x - 14, 84, 28, 6, 2), "#F2C24B")
        a.path(circle(x, 62, 40), a.rad(x, 62, 40, (0, "#FFB30055"), (1, "#FFB30000")))

@scene("vbg_backgammon")
def backgammon_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#0F2A3F"), (0.6, "#3A4E6B"), (1, "#C98B4B")))
    for _ in range(40):
        a.path(circle(a.rng.uniform(0, W), a.rng.uniform(0, 300), a.rng.choice([0.8, 1.2, 1.6])), "#FFFFFF", a.rng.uniform(0.4, 0.9))
    a.path(circle(270, 110, 34), "#F6E7B8"); a.path(circle(284, 100, 30), "#0F2A3F")
    a.path(band(520, 20, 360, 0.4), "#B8763A"); a.path(band(560, 14, 280, 2.2), "#8F5426")
    for (x, h0) in ((70, 170), (300, 140)):
        a.path(f"M{x},{560} C{x + 6},{560 - h0 / 2} {x - 4},{560 - h0 * 0.8} {x + 8},{560 - h0}", stroke="#2A1A10", sw=7)
        for k in range(6):
            ang = -math.pi + k * math.pi / 5
            a.path(f"M{x + 8},{560 - h0} q{f(30 * math.cos(ang))},{f(-10 + 30 * math.sin(ang))} {f(55 * math.cos(ang))},{f(20 + 40 * math.sin(ang))}", stroke="#2A1A10", sw=5)

@scene("vbg_mancala")
def mancala_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#FFD180"), (0.55, "#FF8A50"), (1, "#7B2E14")))
    a.path(circle(180, 330, 90), "#FFE9A8", 0.85)
    a.path(band(470, 10, 400, 0.2), "#5A2410")
    a.path(rect(250, 330, 10, 150, 3), "#2A0F06")
    a.path(f"M190,340 C220,300 300,300 330,335 C300,328 220,328 190,340z", "#2A0F06")
    a.path(f"M200,330 C230,280 290,285 320,325", stroke="#2A0F06", sw=6)
    for (x, y) in ((40, 200), (70, 220), (300, 180)):
        a.path(f"M{x},{y} l8,-4 l8,4", stroke="#3A1408", sw=2)

@scene("vbg_mahjong")
def mahjong_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#DDEFE3"), (1, "#8CC3A3")))
    a.path(circle(180, 250, 130), "#F4F1E6", 0.85)
    a.path(circle(180, 250, 130), stroke="#5B3A1E", sw=10, salpha=0.6)
    a.path(poly([(60, 280), (120, 240), (180, 270), (240, 230), (300, 270), (300, 380), (60, 380)]), "#9AB8A8", 0.5)
    a.path(f"M120,160 C160,150 200,170 240,150", stroke="#5B3A1E", sw=3, salpha=0.5)
    for x in (40, 320):
        a.path(line(x, 0, x, 90), stroke="#5B3A1E", sw=2)
        a.path(rect(x - 16, 90, 32, 44, 12), "#C62828"); a.path(rect(x - 16, 88, 32, 5, 2) + rect(x - 16, 132, 32, 5, 2), "#3A2410")
        a.path(circle(x, 112, 38), a.rad(x, 112, 38, (0, "#FFC10755"), (1, "#FFC10700")))
    a.path(band(540, 8, 200, 0.5), "#5E8C73", 0.6)

@scene("vbg_morris")
def morris_bg(a):
    a.bg(a.lin(0, 0, 0, H, (0, "#BFE3F5"), (0.55, "#E8F4D9"), (1, "#7FAE5A")))
    a.path(circle(80, 90, 26), "#FFFFFF", 0.8); a.path(circle(110, 82, 32), "#FFFFFF", 0.8); a.path(circle(140, 92, 22), "#FFFFFF", 0.8)
    a.path(rect(200, 220, 120, 140), "#8D8A86"); a.path("".join(rect(200 + k * 20, 206, 12, 16) for k in range(6)), "#8D8A86")
    a.path(rect(236, 150, 48, 80), "#7A7772"); a.path("".join(rect(236 + k * 16, 138, 10, 14) for k in range(3)), "#7A7772")
    a.path(rect(252, 300, 18, 60, 9), "#4A3B2E")
    a.path(line(260, 138, 260, 100), stroke="#4A3B2E", sw=2); a.path(poly([(260, 100), (290, 110), (260, 120)]), "#C62828")
    a.path(band(360, 18, 420, 0.4), "#8BC34A"); a.path(band(420, 22, 320, 2.1), "#689F38"); a.path(band(500, 16, 380, 3.3), "#558B2F")

@scene("vbg_tictactoe")
def tictactoe_bg(a):
    a.bg(a.lin(0, 0, W, H, (0, "#FFF9E6"), (1, "#FFE0EC")))
    for _ in range(14):
        x, y = a.rng.uniform(20, 340), a.rng.uniform(20, 620); r = a.rng.uniform(6, 12)
        pts = []
        for k in range(10):
            ang = -math.pi / 2 + k * math.pi / 5; rr = r if k % 2 == 0 else r * 0.45
            pts.append((x + rr * math.cos(ang), y + rr * math.sin(ang)))
        a.path(poly(pts), a.rng.choice(["#FFB74D", "#F06292", "#64B5F6", "#81C784"]), 0.45)
    for (x, y, rot_) in ((70, 180, -20), (290, 420, 15)):
        a.path(poly(rot([(x - 30, y), (x + 30, y - 12), (x - 10, y + 4)], x, y, rot_)), "#FFFFFF", 0.9)
        a.path(poly(rot([(x - 30, y), (x + 30, y - 12), (x - 10, y + 4), (x - 6, y + 14)], x, y, rot_), close=False), stroke="#90A4AE", sw=1.5)
    a.path("M40,560 C100,520 160,600 220,560 S320,520 340,560", stroke="#90A4AE", sw=2, salpha=0.5)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "vbg"
    os.makedirs(out, exist_ok=True)
    for i, (sid, fn) in enumerate(SCENES.items()):
        a = Art(seed=i + 7); fn(a)
        open(f"{out}/{sid}.svg", "w").write(a.svg())
        open(f"{out}/{sid}.xml", "w").write(a.vector())
        print(sid, os.path.getsize(f"{out}/{sid}.xml"))
