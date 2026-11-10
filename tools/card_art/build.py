import json, os, sys
from canvas import Canvas
from sprites import SPRITES
import render
from PIL import Image
ts = int(sys.argv[1]) if len(sys.argv) > 1 else 1794279223475
os.makedirs("docs", exist_ok=True); os.makedirs("out", exist_ok=True)
tiles = []
for i, (sid, (name, tags, notes, size, fn)) in enumerate(SPRITES.items()):
    c = Canvas(size, size); fn(c)
    d = c.doc(name, tags, notes, ts + i)
    json.dump(d, open(f"docs/{sid}.json", "w"))
    g = render.render(d)
    # sanity: replay matches canvas
    assert all((g[y][x] is None) == (c.g[y][x] == ".") for y in range(size) for x in range(size)), sid
    render.png(g, f"out/{sid}.png", 8 if size == 24 else 6)
    open(f"out/{sid}.svg", "w").write(render.svg(g))
    open(f"out/{sid}.xml", "w").write(render.vector(g))
    tiles.append(Image.open(f"out/{sid}.png"))
cols = 6
sheet = Image.new("RGBA", (cols * 200, ((len(tiles) + cols - 1) // cols) * 200), (236, 232, 222, 255))
for i, t in enumerate(tiles):
    sheet.paste(t, ((i % cols) * 200 + 4, (i // cols) * 200 + 4), t)
sheet.save("sheet.png")
print(len(tiles), "sprites; svg bytes:", sum(os.path.getsize(f"out/{s}.svg") for s in SPRITES))
