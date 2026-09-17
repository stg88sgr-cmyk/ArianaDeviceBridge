#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PACK_ID="x88.core-unconventional.v1"
DEST_DIR="$HOME/.ariana/packs/$PACK_ID"
STATE_FILE="$HOME/.ariana/active-pack.json"
rm -rf "$DEST_DIR"
if [ -f "$STATE_FILE" ] && grep -q "$PACK_ID" "$STATE_FILE"; then
  rm -f "$STATE_FILE"
fi
printf 'X-88 pack removed: %s\n' "$PACK_ID"
