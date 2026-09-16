#!/data/data/com.termux/files/usr/bin/bash
set -u

SESSIONS=(x88-bridge x88-build luna-worker x88-logs)
STATUS_FILE="$HOME/.ariana/tmux-runtime-status.json"

if ! command -v tmux >/dev/null 2>&1; then
  echo '🔴 tmux missing'
  exit 2
fi

echo "🟢 tmux $(tmux -V | awk '{print $2}')"
for name in "${SESSIONS[@]}"; do
  if tmux has-session -t "$name" 2>/dev/null; then
    panes="$(tmux list-panes -t "$name" 2>/dev/null | wc -l | tr -d ' ')"
    echo "🟢 $name running (${panes} pane)"
  else
    echo "🔴 $name missing"
  fi
done

for tool in git java node; do
  if command -v "$tool" >/dev/null 2>&1; then
    echo "🟢 $tool available"
  else
    echo "🟡 $tool missing"
  fi
done

if pgrep -f '[d]esktop-commander.*remote' >/dev/null 2>&1; then
  echo "🟢 bridge active"
else
  echo "🔴 bridge inactive"
fi

if [ -f "$STATUS_FILE" ]; then
  echo "STATUS_FILE=$STATUS_FILE"
fi
