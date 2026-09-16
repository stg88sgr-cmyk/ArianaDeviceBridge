#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
BIN_DIR="${X88_BIN:-$HOME/bin}"
BOOT_DIR="$HOME/.termux/boot"
ARIANA_HOME="${X88_HOME:-$HOME/.ariana}"
LUNA_HOME="${LUNA_HOME:-$HOME/.luna}"

mkdir -p "$BIN_DIR" "$BOOT_DIR" "$ARIANA_HOME/logs" "$LUNA_HOME/logs"

for file in runtime-common.sh all-tmux-bootstrap.sh all-tmux-status.sh x88-final-gate.sh; do
  install -m 700 "$SCRIPT_DIR/$file" "$BIN_DIR/$file"
done

cat > "$BOOT_DIR/x88-autostart.sh" <<'BOOT'
#!/data/data/com.termux/files/usr/bin/bash
set -u
sleep 8
mkdir -p "$HOME/.ariana/logs"
{
  printf '=== X-88 BOOT %s ===\n' "$(date -Iseconds 2>/dev/null || date)"
  "$HOME/bin/all-tmux-bootstrap.sh"
  "$HOME/bin/all-tmux-status.sh" || true
} >> "$HOME/.ariana/logs/boot.log" 2>&1
BOOT
chmod 700 "$BOOT_DIR/x88-autostart.sh"

ENV_FILE="$ARIANA_HOME/x88-runtime.env"
if [ ! -e "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<'ENV'
# Optional runtime command overrides.
# Example:
# X88_BRIDGE_CMD='exec "$HOME/bin/x88-real-bridge.sh"'
# LUNA_XXY_WORKER_CMD='exec "$HOME/bin/luna-xxy-real-worker.sh"'
X88_BRIDGE_HOST=127.0.0.1
X88_BRIDGE_PORT=8765
ENV
  chmod 600 "$ENV_FILE"
fi

printf '%s\n' 'Installed:'
printf '  %s\n' "$BIN_DIR/runtime-common.sh" "$BIN_DIR/all-tmux-bootstrap.sh" "$BIN_DIR/all-tmux-status.sh" "$BIN_DIR/x88-final-gate.sh"
printf '  %s\n' "$BOOT_DIR/x88-autostart.sh"

MISSING=0
for cmd in tmux ss; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf 'MISSING prerequisite: %s\n' "$cmd" >&2
    MISSING=1
  fi
done

if [ "$MISSING" -ne 0 ]; then
  printf '%s\n' 'Install prerequisites in Termux with: pkg install tmux iproute2 -y' >&2
  exit 2
fi

printf '%s\n' 'Runtime files are installed. Starting idempotent preflight...'
"$BIN_DIR/x88-final-gate.sh"
