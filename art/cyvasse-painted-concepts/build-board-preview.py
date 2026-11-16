"""Compose a Cyvasse board art preview from the generated source images.

Eight-by-eight illustrative setup; each player chooses their own placements in the game.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent
WATER = {8, 19, 27, 36, 44, 55}
FOREST = {10, 17, 30, 33, 46, 53}
MOUNTAINS = {16, 22, 23, 40, 41, 47}
KEEPS = {11: 1, 52: 0}
ONYX = {2: "rabble", 3: "king", 4: "rabble", 10: "elephant", 12: "dragon",
        18: "light-horse", 19: "spearman", 20: "crossbowman"}
PIECES = {**{c: (p, 1) for c, p in ONYX.items()}, **{63-c: (p, 0) for c, p in ONYX.items()}}
RESAMPLE = Image.Resampling.LANCZOS
SIZE = 125
ORIGIN = (127, 127)

def tile(name):
    return Image.open(ROOT / f"tile-{name}.png").convert("RGB").resize((SIZE, SIZE), RESAMPLE)

tiles = {name: tile(name) for name in ("grass", "water", "forest", "mountain", "keep")}
board = Image.open(ROOT / "board-concept.png").convert("RGB").resize((1254, 1254), RESAMPLE)
for cell in range(64):
    kind = "water" if cell in WATER else "forest" if cell in FOREST else "grass"
    board.paste(tiles[kind], (ORIGIN[0] + cell % 8 * SIZE, ORIGIN[1] + cell // 8 * SIZE))
draw = ImageDraw.Draw(board, "RGBA")
for i in range(9):
    x = ORIGIN[0] + i * SIZE
    y = ORIGIN[1] + i * SIZE
    draw.line((x, ORIGIN[1], x, ORIGIN[1] + 1000), fill=(72, 51, 28, 160), width=2)
    draw.line((ORIGIN[0], y, ORIGIN[0] + 1000, y), fill=(72, 51, 28, 160), width=2)
board.save(ROOT / "board-terrain-preview.png")

example = board.convert("RGBA")
for cell in MOUNTAINS | set(KEEPS):
    x, y = ORIGIN[0] + cell % 8 * SIZE, ORIGIN[1] + cell // 8 * SIZE
    example.paste(tiles["keep" if cell in KEEPS else "mountain"], (x, y))
    d = ImageDraw.Draw(example)
    d.rectangle((x, y, x + SIZE - 1, y + SIZE - 1), outline=(72, 51, 28, 180), width=2)
    if cell in KEEPS:
        d.ellipse((x + 3, y + 3, x + 18, y + 18), fill=(35, 33, 38) if KEEPS[cell] else (245, 235, 211), outline=(204, 161, 66), width=2)

for cell, (name, side) in PIECES.items():
    piece = Image.open(ROOT / ("onyx-blue" if side else "ivory") / f"{name}.png").convert("RGBA")
    box = piece.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox()
    piece = piece.crop(box)
    piece.thumbnail((75, 82), RESAMPLE)
    x = ORIGIN[0] + cell % 8 * SIZE + (SIZE - piece.width) // 2
    y = ORIGIN[1] + cell // 8 * SIZE + (SIZE - piece.height) // 2
    shadow = Image.new("RGBA", piece.size, (0, 0, 0, 0))
    shadow.putalpha(piece.getchannel("A").filter(ImageFilter.GaussianBlur(2)).point(lambda v: v // 2))
    example.alpha_composite(shadow, (x + 2, y + 3))
    example.alpha_composite(piece, (x, y))
example.convert("RGB").save(ROOT / "board-example-preview.png")
