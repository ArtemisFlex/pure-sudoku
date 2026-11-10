"""Dense pixel-art background painter: paint in RGB, quantize with ordered dithering, export stages."""
import numpy as np, random, math, json
from PIL import Image, ImageDraw

W, H = 108, 192
BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0) - 0.47


def hx(c):
    c = c.lstrip("#"); return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], float)


class BG:
    def __init__(self, seed=1, w=W, h=H):
        self.w, self.h = w, h
        self.img = np.zeros((h, w, 3))
        self.rng = random.Random(seed)
        self.stages = []
        yy, xx = np.mgrid[0:h, 0:w]
        self.yy, self.xx = yy, xx

    # ------------------------------------------------ masks
    def mask_poly(self, pts):
        m = Image.new("L", (self.w, self.h), 0); ImageDraw.Draw(m).polygon([tuple(p) for p in pts], fill=255)
        return np.array(m) > 0

    def mask_rect(self, x, y, w, h):
        m = np.zeros((self.h, self.w), bool); m[max(0, y):max(0, y + h), max(0, x):max(0, x + w)] = True; return m

    def mask_ellipse(self, cx, cy, rx, ry):
        return ((self.xx - cx) / rx) ** 2 + ((self.yy - cy) / ry) ** 2 <= 1

    # ------------------------------------------------ painting
    def fill(self, mask, c, a=1.0):
        c = hx(c) if isinstance(c, str) else c
        self.img[mask] = self.img[mask] * (1 - a) + c * a

    def vgrad(self, mask, top, bot, y0=0, y1=None):
        y1 = self.h if y1 is None else y1
        t = np.clip((self.yy - y0) / max(1, y1 - y0), 0, 1)[..., None]
        g = hx(top) * (1 - t) + hx(bot) * t
        self.img[mask] = g[mask]

    def radial(self, mask, cx, cy, r, inner, outer):
        t = np.clip(np.hypot(self.xx - cx, (self.yy - cy)) / r, 0, 1)[..., None]
        g = hx(inner) * (1 - t) + hx(outer) * t
        self.img[mask] = g[mask]

    def glow(self, cx, cy, r, c, strength=0.8, mask=None):
        d = np.hypot(self.xx - cx, self.yy - cy)
        a = np.clip(1 - d / r, 0, 1) ** 1.6 * strength
        if mask is not None: a = a * mask
        self.img = self.img + (hx(c) * a[..., None]) * 0.9
        self.img = np.clip(self.img, 0, 255)

    def shade(self, factor_map, mask=None):
        f = factor_map if mask is None else np.where(mask, factor_map, 1.0)
        self.img = np.clip(self.img * f[..., None], 0, 255)

    def vignette(self, k=0.55):
        d = np.hypot((self.xx - self.w / 2) / (self.w / 2), (self.yy - self.h / 2) / (self.h / 2))
        self.shade(1 - np.clip(d - 0.55, 0, 1) * k)

    def noise(self, mask, amt=12, seed=None):
        r = np.random.default_rng(seed if seed is not None else self.rng.randrange(1 << 30))
        n = r.normal(0, amt, (self.h, self.w, 1))
        self.img = np.where(mask[..., None], np.clip(self.img + n, 0, 255), self.img)

    def px(self, x, y, c, a=1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            c = hx(c) if isinstance(c, str) else c
            self.img[y, x] = self.img[y, x] * (1 - a) + c * a

    def line(self, x0, y0, x1, y1, c, a=1.0, width=1):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n):
            t = i / max(1, n - 1)
            x, y = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
            for dx in range(width):
                self.px(x + dx, y, c, a)

    def bricks(self, mask, base, mortar, bw=12, bh=6, var=0.18, ox=0, oy=0, hi=1.15, lo=0.75):
        b = hx(base); m = hx(mortar)
        out = self.img.copy()
        for y in range(self.h):
            row = (y - oy) // bh
            off = (bh and (row % 2) * bw // 2)
            for x in range(self.w):
                if not mask[y, x]: continue
                lx = (x - ox + off) % bw; ly = (y - oy) % bh
                if lx == 0 or ly == 0:
                    out[y, x] = m
                else:
                    key = (row, (x - ox + off) // bw)
                    rr = random.Random(hash(key) ^ 0x5bd1e995)
                    f = 1 + rr.uniform(-var, var)
                    c = b * f
                    if ly == 1: c = c * hi
                    if ly == bh - 1 or lx == bw - 1: c = c * lo
                    out[y, x] = c
        self.img = np.clip(out, 0, 255)

    def stamp(self, grid, x0, y0, scale=1, tint=None):
        for y, row in enumerate(grid):
            for x, c in enumerate(row):
                if c is None: continue
                col = hx(c)
                if tint is not None: col = col * tint
                for sy in range(scale):
                    for sx in range(scale):
                        self.px(x0 + x * scale + sx, y0 + y * scale + sy, col)

    def stage(self):
        self.stages.append(self.img.copy())

    # ------------------------------------------------ export
    def quantize(self, colors=56):
        final = Image.fromarray(self.img.astype(np.uint8))
        pal_img = final.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        pal = np.array(pal_img.getpalette()[:colors * 3], float).reshape(-1, 3)
        spread = 22.0

        def q(arr):
            a = arr + (BAYER[self.yy % 4, self.xx % 4] * spread)[..., None]
            d = ((a[:, :, None, :] - pal[None, None, :, :]) ** 2).sum(-1)
            return d.argmin(-1)
        return pal, [q(s) for s in self.stages + [self.img]]

    def export(self, name, png_path, doc_path, sid, tags, notes, ts):
        pal, idxs = self.quantize()
        used = sorted(set(np.unique(idxs[-1])))
        keys = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&*+-/:;<=>?@^_|"
        kmap = {i: keys[n] for n, i in enumerate(used)}
        final = idxs[-1]
        # PNG (indexed, 1x) for the app
        im = Image.fromarray(final.astype(np.uint8), "P")
        flat = []
        for i in range(len(pal)): flat += [int(v) for v in pal[i]]
        im.putpalette(flat + [0] * (768 - len(flat)))
        im.save(png_path, optimize=True)
        im.convert("RGB").resize((self.w * 4, self.h * 4), Image.NEAREST).save(png_path.replace(".png", "_x4.png"))
        # Sprite Studio doc: each stage is a rows chunk of the pixels it changed (final palette)
        chunks = {}
        prev = None
        for si, st in enumerate(idxs):
            st = np.vectorize(lambda v: v if v in kmap else min(used, key=lambda u: ((pal[u] - pal[v]) ** 2).sum()))(st) if si < len(idxs) - 1 else st
            rows = []
            for y in range(self.h):
                s = "".join(" " if prev is not None and prev[y, x] == st[y, x] else kmap[st[y, x]] for x in range(self.w))
                rows.append(s.rstrip())
            chunks[f"{si:03d}"] = [["rows", 0, 0, rows]]
            prev = st
        doc = {"name": name, "w": self.w, "h": self.h, "fps": 8, "status": "done", "tags": tags, "notes": notes,
               "palette": {kmap[i]: "#%02x%02x%02x" % tuple(int(v) for v in pal[i]) for i in used},
               "frames": {"0": {"chunks": chunks}}, "createdAt": ts, "updatedAt": ts}
        json.dump(doc, open(doc_path, "w"))
