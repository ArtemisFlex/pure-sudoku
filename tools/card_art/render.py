"""Tiny Sprite Studio op renderer + exporters (PNG preview, SVG, Android VectorDrawable)."""
import json, sys, os
from PIL import Image

def hexc(c):
    c = c.lstrip('#')
    if len(c) == 3: c = ''.join(ch*2 for ch in c)
    if len(c) == 6: c += 'ff'
    return tuple(int(c[i:i+2], 16) for i in (0, 2, 4, 6))

def render(doc, frame="0"):
    w, h, pal = doc["w"], doc["h"], doc["palette"]
    g = [[None]*w for _ in range(h)]
    def col(c):
        if c in (".", "~"): return None
        return pal[c] if c in pal else c
    def put(x, y, c):
        if 0 <= x < w and 0 <= y < h: g[y][x] = col(c)
    fr = doc["frames"][frame]
    for key in sorted(fr["chunks"]):
        for op in fr["chunks"][key]:
            t = op[0]
            if t == "px": put(op[1], op[2], op[3])
            elif t == "pts":
                for x, y in op[2]: put(x, y, op[1])
            elif t == "rect":
                for y in range(op[2], op[2]+op[4]):
                    for x in range(op[1], op[1]+op[3]): put(x, y, op[5])
            elif t == "line":
                x0, y0, x1, y1, c = op[1:]
                dx, dy = abs(x1-x0), -abs(y1-y0); sx = 1 if x0 < x1 else -1; sy = 1 if y0 < y1 else -1; e = dx+dy
                while True:
                    put(x0, y0, c)
                    if x0 == x1 and y0 == y1: break
                    e2 = 2*e
                    if e2 >= dy: e += dy; x0 += sx
                    if e2 <= dx: e += dx; y0 += sy
            elif t == "rows":
                x0, y0, rows = op[1], op[2], op[3]
                for j, row in enumerate(rows):
                    for i, ch in enumerate(row):
                        if ch in (". "): continue
                        if ch == "~": put(x0+i, y0+j, ".")
                        else: put(x0+i, y0+j, ch)
            elif t == "mirror":
                for y in range(h):
                    for x in range(w//2): g[y][w-1-x] = g[y][x]
            elif t == "outline":
                c = col(op[1]); eight = len(op) > 2 and op[2] == "8"
                src = [r[:] for r in g]
                nb = [(1,0),(-1,0),(0,1),(0,-1)] + ([(1,1),(1,-1),(-1,1),(-1,-1)] if eight else [])
                for y in range(h):
                    for x in range(w):
                        if src[y][x] is None and any(0<=x+a<w and 0<=y+b<h and src[y+b][x+a] is not None for a,b in nb):
                            g[y][x] = c
            elif t == "replace":
                a, b = col(op[1]), col(op[2])
                for y in range(h):
                    for x in range(w):
                        if g[y][x] == a: g[y][x] = b
            elif t == "erase":
                for y in range(op[2], op[2]+op[4]):
                    for x in range(op[1], op[1]+op[3]): put(x, y, ".")
            else:
                raise ValueError("unsupported op " + t)
    return g

def png(g, path, scale=8):
    h, w = len(g), len(g[0])
    im = Image.new("RGBA", (w, h), (0,0,0,0))
    for y in range(h):
        for x in range(w):
            if g[y][x]: im.putpixel((x, y), hexc(g[y][x]))
    im.resize((w*scale, h*scale), Image.NEAREST).save(path)

def runs(g):
    """color -> list of (x, y, len) horizontal runs"""
    out = {}
    for y, row in enumerate(g):
        x = 0
        while x < len(row):
            c = row[x]
            if c is None: x += 1; continue
            s = x
            while x < len(row) and row[x] == c: x += 1
            out.setdefault(c, []).append((s, y, x - s))
    return out

def path_d(rs):
    return "".join(f"M{x},{y}h{n}v1h-{n}z" for x, y, n in rs)

def svg(g):
    h, w = len(g), len(g[0])
    body = "".join(f'<path fill="{c}" d="{path_d(rs)}"/>' for c, rs in runs(g).items())
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" shape-rendering="crispEdges">{body}</svg>\n'

def vector(g):
    h, w = len(g), len(g[0])
    def argb(c):
        r, gg, b, a = hexc(c); return f"#{a:02X}{r:02X}{gg:02X}{b:02X}"
    body = "".join(f'\n    <path android:fillColor="{argb(c)}" android:pathData="{path_d(rs)}"/>' for c, rs in runs(g).items())
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="{w*4}dp" android:height="{h*4}dp"\n'
            f'    android:viewportWidth="{w}" android:viewportHeight="{h}">{body}\n</vector>\n')
