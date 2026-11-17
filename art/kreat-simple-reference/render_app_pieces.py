"""Render the editable Kreat SVGs into the shared Android/web drawable resources."""
from pathlib import Path

import resvg_py

root = Path(__file__).resolve().parent
destination = root.parents[1] / "app/src/main/res/drawable-nodpi"
for source in sorted((root / "svg-pieces").glob("*.svg")):
    name = source.stem.replace("-", "_")
    (destination / f"kreat_simple_{name}.png").write_bytes(
        resvg_py.svg_to_bytes(svg_path=str(source), width=256, height=256)
    )
