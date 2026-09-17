#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PACK_ID="x88.core-unconventional.v1"
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
DEST_DIR="$HOME/.ariana/packs/$PACK_ID"
SNAP_DIR="$HOME/.ariana/snapshots"
TOKEN_FILE="$HOME/.ariana_token"

if [ ! -s "$TOKEN_FILE" ]; then
  echo "FATAL: ~/.ariana_token fehlt oder ist leer. Installation abgebrochen."
  exit 2
fi

mkdir -p "$SNAP_DIR" "$HOME/.ariana/packs"
STAMP="$(date +%Y%m%d-%H%M%S)"
if [ -d "$DEST_DIR" ]; then
  tar -C "$(dirname "$DEST_DIR")" -czf "$SNAP_DIR/${PACK_ID}-${STAMP}.tar.gz" "$(basename "$DEST_DIR")"
fi

TMP_DIR="${DEST_DIR}.tmp.$$"
rm -rf "$TMP_DIR"
mkdir -p "$TMP_DIR"
cp -R "$SRC_DIR"/. "$TMP_DIR"/

python - "$TMP_DIR/manifest.json" <<'PY'
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
data = json.loads(p.read_text(encoding='utf-8'))
assert data['id'] == 'x88.core-unconventional.v1'
assert data['policy']['fail_closed'] is True
assert data['policy']['token_required'] is True
print('manifest: OK')
PY

rm -rf "$DEST_DIR"
mv "$TMP_DIR" "$DEST_DIR"

cat > "$HOME/.ariana/active-pack.json" <<EOF
{
  "active_pack": "$PACK_ID",
  "installed_at": "$STAMP",
  "path": "$DEST_DIR"
}
EOF

printf 'X-88 pack installed: %s\n' "$DEST_DIR"
printf 'Rollback snapshots: %s\n' "$SNAP_DIR"
printf 'No system permission was granted or bypassed by this installer.\n'
