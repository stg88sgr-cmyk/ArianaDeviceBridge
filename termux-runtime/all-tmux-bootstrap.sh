#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=runtime-common.sh
. "$SCRIPT_DIR/runtime-common.sh"

load_runtime_env
require_cmd tmux

LOCK_DIR="$X88_RUNTIME_DIR/bootstrap.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  log "bootstrap already running; leaving existing runtime untouched"
  exit 0
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT HUP INT TERM

start_session() {
  local name="$1"
  local cmd

  if session_exists "$name"; then
    log "GREEN session already active: $name"
    return 0
  fi

  cmd="$(resolve_session_command "$name")"
  tmux new-session -d -s "$name" "${SHELL:-/data/data/com.termux/files/usr/bin/bash}" -lc "$cmd"

  if session_exists "$name"; then
    log "GREEN session started: $name"
  else
    log "RED session failed: $name" >&2
    return 1
  fi
}

FAIL=0
for session in "${X88_SESSIONS[@]}"; do
  start_session "$session" || FAIL=1
done

{
  printf 'timestamp=%s\n' "$(date -Iseconds 2>/dev/null || date)"
  printf 'bridge=%s:%s\n' "$X88_BRIDGE_HOST" "$X88_BRIDGE_PORT"
  printf 'sessions=%s\n' "${X88_SESSIONS[*]}"
} > "$X88_RUNTIME_DIR/bootstrap.state"

if [ "$FAIL" -eq 0 ]; then
  log "GREEN X-88/Luna tmux bootstrap complete"
else
  log "RED X-88/Luna tmux bootstrap incomplete" >&2
fi

exit "$FAIL"
