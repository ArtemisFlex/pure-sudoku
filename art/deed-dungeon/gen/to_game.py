"""Turns the generated Deed or Dungeon art in this folder into app drawables (webp, sized for phones)."""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')

def save(im, name, size):
    im = im.copy()
    im.thumbnail((size, size), Image.LANCZOS)
    im.save(os.path.join(OUT, name + '.webp'), 'WEBP', quality=88, method=6)

def trim(im):
    bb = im.getchannel('A').getbbox()
    return im.crop(bb) if bb else im

def tint_mask(im):
    """The light, nearly colourless parts of a piece (cloak, banner, trim) that take the player's colour."""
    px = im.load()
    m = Image.new('RGBA', im.size, (0, 0, 0, 0))
    mp = m.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 40:
                continue
            mx, mn = max(r, g, b), min(r, g, b)
            sat = (mx - mn) / mx if mx else 0
            if sat < 0.16 and mx > 95:
                # keep the shading: the grey's own brightness, so folds still read once tinted
                mp[x, y] = (mx, mx, mx, a)
    return m

done = []
for f in sorted(os.listdir(HERE)):
    if not (f.startswith('dd_') and f.endswith('.png')):
        continue
    name = f[:-4]
    im = Image.open(os.path.join(HERE, f)).convert('RGBA')
    if name.startswith('dd_tile_'):
        save(im.convert('RGB'), name, 512)
    elif name.startswith('dd_portrait_'):
        save(im.convert('RGB'), name, 384)
    elif name.startswith('dd_piece_'):
        im = trim(im)
        save(im, name, 512)
        save(tint_mask(im), name + '_mask', 512)   # same size as the piece, so they line up
    else:
        save(trim(im), name, 384)
    done.append(name)
print(len(done), 'assets:', ', '.join(done))
