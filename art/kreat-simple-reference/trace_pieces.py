from pathlib import Path
from tempfile import TemporaryDirectory
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import vtracer
import resvg_py
import xml.etree.ElementTree as ET
import re

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / 'kreat-pieces-reference.png'
DEST = ROOT / 'svg-pieces'
PREVIEW = ROOT / 'kreat-svg-pieces-preview.png'
DEST.mkdir(exist_ok=True)
# Two unit types per row; each type has ivory and onyx specimens.
rows = [(0, 205), (245, 400), (435, 586), (610, 782), (800, 986)]
cols = [(170, 425), (460, 700), (800, 1065), (1080, 1350)]
names = [('rabble','spearman'), ('crossbowman','light-horse'), ('heavy-horse','elephant'), ('catapult','trebuchet'), ('dragon','king')]
source = np.array(Image.open(SOURCE).convert('RGB'))
font_path = Path('C:/Windows/Fonts/arial.ttf')
font = ImageFont.truetype(str(font_path), 19) if font_path.exists() else ImageFont.load_default()
canvas = Image.new('RGB', (1400, 5 * 220 + 90), '#f2ede4')
draw = ImageDraw.Draw(canvas)
draw.text((35, 20), 'Kreat SVG pieces - ivory/gold and onyx/blue', fill='#2a211a', font=font)

with TemporaryDirectory(prefix='kreat-svg-') as tmp:
    tmp = Path(tmp)
    for ri, (y0,y1) in enumerate(rows):
        for ci, (x0,x1) in enumerate(cols):
            name = names[ri][ci // 2]
            side = ('ivory','onyx')[ci % 2]
            rgb = source[y0:y1,x0:x1].copy()
            # The sheet's pale background is nearly uniform. Identify background connected
            # to the crop boundary, preserving light details enclosed by the dark outline.
            bg = np.median(np.concatenate([rgb[0],rgb[-1],rgb[:,0],rgb[:,-1]]),axis=0)
            delta = np.linalg.norm(rgb.astype(np.float32)-bg,axis=2)
            is_bg = (delta < 48).astype(np.uint8)
            count, labels = cv2.connectedComponents(is_bg, connectivity=4)
            edge_ids = np.unique(np.concatenate([labels[0],labels[-1],labels[:,0],labels[:,-1]]))
            outside = np.isin(labels, edge_ids[edge_ids != 0])
            alpha = np.where(outside, 0, 255).astype(np.uint8)
            # Discard any disconnected flecks caused by antialiasing against the paper.
            n, comps, stats, _ = cv2.connectedComponentsWithStats((alpha > 0).astype(np.uint8), 8)
            if n < 2: raise RuntimeError(f'No subject: {side} {name}')
            biggest = 1 + np.argmax(stats[1:, cv2.CC_STAT_AREA])
            alpha[comps != biggest] = 0
            # These openings are enclosed by the piece outlines, so the ordinary
            # edge-connected background removal cannot see them. Seeds identify paper
            # regions in the fixed source-sheet crops, without erasing ivory highlights.
            openings = {
                ('ivory', 'spearman'): [(192, 158)],
                ('onyx', 'spearman'): [(132, 157)],
                ('ivory', 'crossbowman'): [(192, 86)],
                ('onyx', 'crossbowman'): [(167, 86)],
                ('ivory', 'catapult'): [(91, 100), (150, 123)],
                ('onyx', 'catapult'): [(77, 100), (135, 123)],
                ('ivory', 'trebuchet'): [(80, 98), (104, 130), (147, 130), (138, 100)],
                ('onyx', 'trebuchet'): [(75, 98), (98, 130), (142, 130), (133, 100)],
            }
            hole_ids = [labels[y, x] for x, y in openings.get((side, name), [])]
            if any(i == 0 or i in edge_ids for i in hole_ids):
                raise RuntimeError(f'Invalid opening seed: {side} {name}')
            holes = np.isin(labels, hole_ids).astype(np.uint8) if hole_ids else np.zeros(alpha.shape, dtype=np.uint8)
            if hole_ids:
                holes = cv2.dilate(holes, np.ones((3, 3), dtype=np.uint8), iterations=1)
                alpha[holes != 0] = 0
            ys, xs = np.where(alpha > 0)
            if not len(xs): raise RuntimeError(f'Empty subject: {side} {name}')
            l,r = max(0,xs.min()-2), min(rgb.shape[1],xs.max()+3)
            t,b = max(0,ys.min()-2), min(rgb.shape[0],ys.max()+3)
            rgba = np.dstack((rgb,alpha))[t:b,l:r]
            piece = Image.fromarray(rgba, 'RGBA')
            piece.thumbnail((224,224), Image.Resampling.LANCZOS)
            art = Image.new('RGBA', (256,256), (0,0,0,0))
            art.alpha_composite(piece, ((256-piece.width)//2, 240-piece.height))
            src = tmp / 'piece.png'; raw_svg = tmp / 'piece.svg'
            art.save(src)
            vtracer.convert_image_to_svg_py(str(src),str(raw_svg))
            tree = ET.fromstring(raw_svg.read_text(encoding='utf-8'))
            paths = list(tree)
            if len(paths) < 2: raise RuntimeError(f'Unexpected trace: {side} {name}')
            # VTracer paths are ordered back to front. Split into three consecutive SVG
            # groups without reordering them, so the painted result remains unchanged.
            cuts = (1, max(2, (len(paths)*3)//4))
            groups = [paths[:cuts[0]],paths[cuts[0]:cuts[1]],paths[cuts[1]:]]
            root = ET.Element('svg', {'xmlns':'http://www.w3.org/2000/svg','viewBox':'0 0 256 256','width':'256','height':'256','role':'img','aria-label':f'{side} {name}'})
            ET.SubElement(root,'title').text = f'{side.title()} {name.replace("-", " ").title()}'
            for label, shapes in zip(('silhouette','paint','details'),groups):
                group = ET.SubElement(root,'g',{'id':label})
                for shape in shapes:
                    shape.tag = shape.tag.split('}')[-1]
                    # Subpixel precision is sufficient at a 256-unit viewBox; VTracer's
                    # full float precision makes the SVGs unnecessarily large.
                    for attr in ('d', 'transform'):
                        if attr in shape.attrib:
                            shape.set(attr, re.sub(r'(?<![A-Za-z0-9_.])[-+]?\d+\.\d+',
                                lambda m: f'{float(m.group()):.2f}'.rstrip('0').rstrip('.'), shape.get(attr)))
                    group.append(shape)
            target=DEST / f'{side}-{name}.svg'
            target.write_text(ET.tostring(root,encoding='unicode'),encoding='utf-8')
            rendered = Image.open(__import__('io').BytesIO(resvg_py.svg_to_bytes(svg_path=str(target),width=160,height=160))).convert('RGBA')
            x = 70 + ci * 335; y = 60 + ri * 220
            canvas.paste(rendered,(x,y),rendered)
            draw.text((x+10,y+162),f'{side} {name}',fill='#2a211a',font=font)
            print(f'{side:5} {name:13} {len(paths):3} paths')
canvas.save(PREVIEW)
print('Preview:', PREVIEW)
