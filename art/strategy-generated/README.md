# Generated strategy-game artwork

These are full-resolution source images generated for this project from the existing Aegwaspell buildings as style references. The original files are retained here; resized copies used by Android are in `app/src/main/res/drawable-nodpi`.

- `wonder_*`: six wonders, wired to the board and build buttons in both Aegwaspell modes.
- `wall_neutral`, `road_neutral`: neutral edge pieces. The wall is used by Wisteria, Kreashun, Veilguard and their rulebook wall diagrams tinted blue or red for ownership, with owner-color markers drawn over it. The road is used by both Aegwaspell modes. Eiseria continues to use its existing red and blue road pieces to preserve ownership cues.
- `ui_book`, `ui_end`, `ui_earth`, `ui_moon`, `ui_fire`, `ui_crown`, `ui_shield`, `ui_ley`, `ui_sun`, plus `ui_trade_simple`, `ui_scroll_simple`, `ui_dice_simple` and `ui_relic_simple`: painted shared action icons. Screens requesting custom icon tints retain the original drawn icon.
- `piece_wall`, `piece_road`, `ui_button`, `ui_trade`, `ui_scroll`, `ui_dice`, `ui_relic`: alternatives retained as source art, not integrated. The decorative wall mixes player colors; the ornate button and other detailed alternatives need adaptation and in-context sizing before use.

The game still has code-drawn icons and UI controls. The Kreashun tutorial has been checked on an emulator; assets and rendering changes still need visual checks across the remaining titles, themes and accessibility settings.
