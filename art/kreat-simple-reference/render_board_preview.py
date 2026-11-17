from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import io, resvg_py
root=Path('art/kreat-simple-reference')
order=['catapult','light-horse','spearman','crossbowman','heavy-horse','king','dragon','trebuchet','elephant','rabble']
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
im=Image.new('RGB',(760,300),'#f1ece2'); d=ImageDraw.Draw(im)
d.text((20,10),'Kreat SVGs on board tiles — 60 px and 40 px',font=font,fill='#282019')
for top,cell in [(45,60),(200,40)]:
 for row,side in enumerate(('onyx','ivory')):
  for col,name in enumerate(order):
   x=20+col*cell;y=top+row*cell
   d.rectangle((x,y,x+cell-1,y+cell-1),fill=('#80934a' if (row+col)%2 else '#ead6b8'))
   png=resvg_py.svg_to_bytes(svg_path=str(root/'svg-pieces'/f'{side}-{name}.svg'),width=cell,height=cell)
   piece=Image.open(io.BytesIO(png)).convert('RGBA'); im.paste(piece,(x,y),piece)
im.save(root/'kreat-board-size-preview.png')
print(root/'kreat-board-size-preview.png')
