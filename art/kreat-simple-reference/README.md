# Kreat SVG piece references

`svg-pieces/` contains 20 standalone SVGs: ten Kreat units in ivory/gold and onyx/blue. Each SVG has a 256 x 256 viewBox and three ordered groups (`silhouette`, `paint`, `details`). The groups are consecutive ranges of the automatic trace's back-to-front paths; they help organize the artwork but are not three single paths or fully separated semantic layers. Backgrounds are transparent.

`kreat-svg-pieces-preview.png` shows every SVG at a larger size. `kreat-board-size-preview.png` is a simulated view at 60- and 40-pixel tile sizes. Transparent PNG copies of these vectors are used for the game's optional simple art style.

To regenerate from the generated raster reference, install Python packages `numpy`, `opencv-python`, `Pillow`, `vtracer`, and `resvg-py`, then run `python art/kreat-simple-reference/trace_pieces.py` followed by `python art/kreat-simple-reference/render_board_preview.py` from the project root. The tracer uses image-space crop regions specific to the supplied reference sheet.

`transparent-openings-preview.png` shows both colors of the catapult, trebuchet, spearman, and crossbowman against a contrasting background. Their enclosed background openings are removed in the SVGs and in the repeatable tracing script.

After editing the SVGs, run `python art/kreat-simple-reference/render_app_pieces.py` to regenerate their 256-pixel Android and web resources. This requires `resvg-py`.
