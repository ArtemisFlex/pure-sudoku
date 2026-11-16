"""Soft colour masks for the pieces: the light, low-colour cloth and trim, with gaps filled and edges feathered."""
import os
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi')

for h in ['dragon', 'knight', 'thief', 'black_knight']:
    im = Image.open(os.path.join(HERE, f'dd_piece_{h}.png')).convert('RGBA')
    bb = im.getchannel('A').getbbox(); im = im.crop(bb)
    a = np.asarray(im).astype(np.float32) / 255.0
    rgb, alpha = a[..., :3], a[..., 3]
    mx = rgb.max(-1); mn = rgb.min(-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    # how "grey cloth" each pixel is: low colour, not too dark
    w = np.clip((0.32 - sat) / 0.16, 0, 1) * np.clip((mx - 0.30) / 0.25, 0, 1)
    m = Image.fromarray((w * 255).astype(np.uint8), 'L')
    # close small holes, then feather
    m = m.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(7)).filter(ImageFilter.GaussianBlur(2))
    w = np.asarray(m).astype(np.float32) / 255.0 * alpha
    # never tint the stone base (or the dragon's rock)
    cut = {'dragon': 0.64}.get(h, 0.80)
    rows = np.arange(w.shape[0])[:, None] / w.shape[0]
    w = w * np.clip((cut + 0.03 - rows) / 0.03, 0, 1)
    lum = np.clip(rgb.mean(-1) * 1.15, 0, 1)
    out = np.dstack([lum, lum, lum, w])
    mask = Image.fromarray((out * 255).astype(np.uint8), 'RGBA')
    mask.thumbnail((512, 512), Image.LANCZOS)
    mask.save(os.path.join(OUT, f'dd_piece_{h}_mask.webp'), 'WEBP', quality=88, method=6)
    print(h, mask.size)
