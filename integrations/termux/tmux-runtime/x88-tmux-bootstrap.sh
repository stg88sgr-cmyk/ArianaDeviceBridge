#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

SESSIONS=(x88-bridge x88-build luna-worker x88-logs)
STATE_DIR="$HOME/.ariana"
LOG_DIR="$STATE_DIR/logs"
STATUS_FILE="$STATE_DIR/tmux-runtime-status.json"
BRIDGE_CMD="${X88_BRIDGE_CMD:-npx @wonderwhy-er/desktop-commander@latest remote}"
mkdir -p "$STATE_DIR" "$LOG_DIR" "$HOME/bin"

if ! command -v tmux >/dev/null 2>&1; then
  echo "[X88][ERR] tmux fehlt. Installiere: pkg install tmux" >&2
  exit 1
fi

status_of() { command -v "$1" >/dev/null 2>&1 && printf available || printf missing; }
session_state() { tmux has-session -t "$1" 2>/dev/null && printf running || printf missing; }
bridge_pids() { pgrep -f '[d]esktop-commander.*remote' 2>/dev/null || true; }

created=()
for name in "${SESSIONS[@]}"; do
  if tmux has-session -t "$name" 2>/dev/null; then
    printf '[X88][OK] session exists: %s\n' "$name"
  else
    tmux new-session -d -s "$name" -c "$HOME"
    tmux set-option -t "$name" remain-on-exit on >/dev/null 2>&1 || true
    created+=("$name")
    printf '[X88][OK] session created: %s\n' "$name"
  fi
done

if [ -z "$(bridge_pids)" ]; then
  printf '[X88] starting bridge in x88-bridge\n'
  tmux send-keys -t x88-bridge C-c >/dev/null 2>&1 || true
  tmux send-keys -t x88-bridge -l "$BRIDGE_CMD"
  tmux send-keys -t x88-bridge C-m
  sleep 1
else
  printf '[X88][OK] bridge already active\n'
fi

bridge_active=false
bridge_location=none
if [ -n "$(bridge_pids)" ]; then
  bridge_active=true
  bridge_location=process
fi

created_json='[]'
if [ "${#created[@]}" -gt 0 ]; then
  printf -v items '"%s",' "${created[@]}"
  created_json="[${items%,}]"
fi

tmux_pid="$(tmux display-message -p '#{pid}' 2>/dev/null || printf unknown)"
cat > "$STATUS_FILE" <<EOF
{
  "runtime": "tmux",
  "project": "ARIANA X-88",
  "updated_utc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "tmux_server_pid": "$tmux_pid",
  "tmux_version": "$(tmux -V | awk '{print $2}')",
  "sessions": {
    "x88-bridge": "$(session_state x88-bridge)",
    "x88-build": "$(session_state x88-build)",
    "luna-worker": "$(session_state luna-worker)",
    "x88-logs": "$(session_state x88-logs)"
  },
  "created_this_run": $created_json,
  "health": {
    "tmux": "available",
    "git": "$(status_of git)",
    "java": "$(status_of java)",
    "node": "$(status_of node)",
    "bridge_active": $bridge_active,
    "bridge_location": "$bridge_location"
  },
  "paths": {
    "status_file": "$STATUS_FILE",
    "log_dir": "$LOG_DIR"
  }
}
EOF

printf '[X88][OK] runtime status: %s\n' "$STATUS_FILE"
tmux ls
