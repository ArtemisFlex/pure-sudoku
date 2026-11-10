"""Procedural pixel canvas that records stages as Sprite Studio `rows` chunks."""
import math

PAL = {
    "o": "#2a1d2e", "k": "#4a3b52", "x": "#1a1420",
    "g": "#2f6b3a", "G": "#5aa548", "L": "#a6dc6a",
    "b": "#2f4f8f", "B": "#4f86d6", "C": "#a8d8ff",
    "r": "#8e2a35", "R": "#d4434a", "P": "#ff9b8a",
    "y": "#a8742a", "Y": "#f2c24b", "W": "#fff4b0",
    "s": "#6b7486", "S": "#aeb8c8", "T": "#f0f4fa",
    "w": "#5a3524", "n": "#8f5a33", "N": "#c98d52",
    "e": "#e2d6bc", "E": "#a99c84",
    "p": "#3e2460", "V": "#6f3fa8", "v": "#b48ae6",
    "f": "#f08a24", "F": "#ffe066", "d": "#b8381c",
    "h": "#f0c39a", "H": "#c48660",
    "m": "#3a2e3e", "M": "#6a5c70", "Q": "#948aa0",
}


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.g = [["."] * w for _ in range(h)]
        self.snap = [r[:] for r in self.g]
        self.chunks = []

    # ---- primitives
    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h: self.g[y][x] = c

    def get(self, x, y):
        return self.g[y][x] if 0 <= x < self.w and 0 <= y < self.h else "."

    def rect(self, x, y, w, h, c):
        for j in range(y, y + h):
            for i in range(x, x + w): self.px(i, j, c)

    def ellipse(self, cx, cy, rx, ry, c, only=None):
        for y in range(self.h):
            for x in range(self.w):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                    if only is None or self.g[y][x] in only: self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0); sx = 1 if x0 < x1 else -1; sy = 1 if y0 < y1 else -1; e = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1: break
            e2 = 2 * e
            if e2 >= dy: e += dy; x0 += sx
            if e2 <= dx: e += dx; y0 += sy

    def poly(self, pts, c):
        """Fill polygon (pixel centers)."""
        for y in range(self.h):
            yc = y + 0.5
            xs = []
            for i in range(len(pts)):
                (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % len(pts)]
                if (y0 <= yc < y1) or (y1 <= yc < y0):
                    xs.append(x0 + (yc - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for a, b in zip(xs[::2], xs[1::2]):
                for x in range(self.w):
                    if a <= x + 0.5 <= b: self.px(x, y, c)

    def rows(self, x, y, rows):
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch == " " or ch == ".": continue
                self.px(x + i, y + j, "." if ch == "~" else ch)

    def mirror(self):
        for y in range(self.h):
            for x in range(self.w // 2): self.g[y][self.w - 1 - x] = self.g[y][x]

    def shade(self, cond, c):
        """Recolor pixels where cond(x, y, cur) is true."""
        for y in range(self.h):
            for x in range(self.w):
                if cond(x, y, self.g[y][x]): self.g[y][x] = c

    def recolor_edge(self, mat, c, side="br"):
        """Pixels of material set `mat` that touch a non-`mat` pixel on the given side get c."""
        src = [r[:] for r in self.g]
        d = {"b": (0, 1), "r": (1, 0), "t": (0, -1), "l": (-1, 0)}
        for y in range(self.h):
            for x in range(self.w):
                if src[y][x] in mat:
                    for s in side:
                        a, b = d[s]
                        nx, ny = x + a, y + b
                        n = src[ny][nx] if 0 <= nx < self.w and 0 <= ny < self.h else "."
                        if n not in mat: self.g[y][x] = c; break

    def outline(self, c="o", eight=False):
        src = [r[:] for r in self.g]
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if eight else [])
        for y in range(self.h):
            for x in range(self.w):
                if src[y][x] == "." and any(0 <= x + a < self.w and 0 <= y + b < self.h and src[y + b][x + a] != "." for a, b in nb):
                    self.g[y][x] = c

    # ---- staging
    def stage(self):
        """Record pixels changed since the last stage as one rows op chunk."""
        rows = []
        ys = [y for y in range(self.h) if self.g[y] != self.snap[y]]
        if not ys: return
        y0, y1 = min(ys), max(ys)
        xs = [x for y in range(y0, y1 + 1) for x in range(self.w) if self.g[y][x] != self.snap[y][x]]
        x0, x1 = min(xs), max(xs)
        for y in range(y0, y1 + 1):
            s = ""
            for x in range(x0, x1 + 1):
                if self.g[y][x] == self.snap[y][x]: s += " "
                else: s += "~" if self.g[y][x] == "." else self.g[y][x]
            rows.append(s.rstrip())
        self.chunks.append([["rows", x0, y0, rows]])
        self.snap = [r[:] for r in self.g]

    def doc(self, name, tags, notes, ts):
        used = {ch for r in self.g for ch in r if ch != "."} | {ch for ck in self.chunks for op in ck for r in op[3] for ch in r if ch not in " ~."}
        return {
            "name": name, "w": self.w, "h": self.h, "fps": 8, "status": "done", "tags": tags, "notes": notes,
            "palette": {k: v for k, v in PAL.items() if k in used},
            "frames": {"0": {"chunks": {f"{i:03d}": ck for i, ck in enumerate(self.chunks)}}},
            "createdAt": ts, "updatedAt": ts,
        }
