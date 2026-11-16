cd /c/Users/abrat/PureGames/art/deed-dungeon/gen
for k in 1 2 3 4 5 6; do
  echo "batch $k start $(date +%T)"
  timeout 2400 codex exec -s workspace-write --add-dir "$USERPROFILE/.codex" \
    -i ../logo_stylized_full.png -i dd_tile_spire.png -i dd_piece_knight.png -i dd_coin_crown.png \
    -o last$k.txt "$(cat batch$k.txt)" < /dev/null > run$k.log 2>&1
  echo "batch $k exit $? $(date +%T) files: $(ls dd_*.png | wc -l)"
done
