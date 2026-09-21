#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ALLOWED=(x88-bridge x88-build luna-worker x88-logs)
force=false
targets=()

is_allowed() {
  local needle="$1"
  local item
  for item in "${ALLOWED[@]}"; do
    [ "$item" = "$needle" ] && return 0
  done
  return 1
}

usage() {
  echo 'Usage: x88-tmux-stop.sh [--force] <session> [session...]'
  echo 'Allowed: x88-bridge x88-build luna-worker x88-logs'
}

[ "$#" -gt 0 ] || { usage; exit 1; }
for arg in "$@"; do
  case "$arg" in
    --force) force=true ;;
    -h|--help) usage; exit 0 ;;
    *)
      is_allowed "$arg" || { echo "[X88][DENIED] unknown session: $arg" >&2; exit 2; }
      targets+=("$arg")
      ;;
  esac
done

[ "${#targets[@]}" -gt 0 ] || { usage; exit 1; }
if [ "${#targets[@]}" -gt 1 ] && [ "$force" != true ]; then
  echo '[X88][DENIED] multiple-session stop requires --force' >&2
  exit 3
fi

for name in "${targets[@]}"; do
  if tmux has-session -t "$name" 2>/dev/null; then
    tmux send-keys -t "$name" C-c >/dev/null 2>&1 || true
    sleep 0.3
    tmux kill-session -t "$name"
    echo "[X88][OK] stopped: $name"
  else
    echo "[X88][SKIP] missing: $name"
  fi
done
