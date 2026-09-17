#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONTROL_DECK="$REPO_ROOT/control-deck/server.py"

if ! command -v python >/dev/null 2>&1; then
  echo "python fehlt. In Termux: pkg install python -y" >&2
  exit 1
fi

if [[ ! -f "$CONTROL_DECK" ]]; then
  echo "Control Deck nicht gefunden: $CONTROL_DECK" >&2
  exit 1
fi

export X88_CONTROL_DECK_PORT="${X88_CONTROL_DECK_PORT:-8788}"
export X88_BRIDGE_URL="${X88_BRIDGE_URL:-http://127.0.0.1:8765}"

cd "$REPO_ROOT"
exec python "$CONTROL_DECK"
