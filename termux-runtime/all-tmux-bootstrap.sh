#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=runtime-common.sh
. "$SCRIPT_DIR/runtime-common.sh"

load_runtime_env
require_cmd tmux

LOCK_DIR="$X88_RUNTIME_DIR/bootstrap.lock"

current_boot_id() {
  if [ -r /proc/sys/kernel/random/boot_id ]; then
    cat /proc/sys/kernel/random/boot_id
  else
    printf '%s\n' unknown
  fi
}

bootstrap_owner_alive() {
  local pid="$1"
  local stored_boot_id="$2"
  local boot_id
  local cmdline

  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
  boot_id="$(current_boot_id)"

  # A lock from another Android/Linux boot is always stale.
  if [ "$stored_boot_id" != "$boot_id" ]; then
    return 1
  fi

  kill -0 "$pid" 2>/dev/null || return 1

  # Guard against PID reuse. An unrelated process with the same PID must not
  # keep X-88 permanently locked after a crash.
  if [ -r "/proc/$pid/cmdline" ]; then
    cmdline="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
    [[ "$cmdline" == *all-tmux-bootstrap.sh* ]] || return 1
  fi

  return 0
}

write_lock_owner() {
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
  current_boot_id > "$LOCK_DIR/boot_id"
}

remove_known_lock_files() {
  rm -f -- "$LOCK_DIR/pid" "$LOCK_DIR/boot_id"
  rmdir -- "$LOCK_DIR" 2>/dev/null
}

acquire_bootstrap_lock() {
  local existing_pid=''
  local existing_boot_id=''

  if mkdir "$LOCK_DIR" 2>/dev/null; then
    write_lock_owner
    return 0
  fi

  if [ -r "$LOCK_DIR/pid" ]; then
    existing_pid="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
  fi
  if [ -r "$LOCK_DIR/boot_id" ]; then
    existing_boot_id="$(cat "$LOCK_DIR/boot_id" 2>/dev/null || true)"
  fi

  if bootstrap_owner_alive "$existing_pid" "$existing_boot_id"; then
    return 1
  fi

  log "recovering stale bootstrap lock"
  if ! remove_known_lock_files; then
    log "RED stale bootstrap lock contains unexpected files: $LOCK_DIR" >&2
    return 2
  fi

  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    # Another bootstrap may have won the recovery race.
    return 1
  fi
  write_lock_owner
  return 0
}

# Invoked indirectly by trap.
# shellcheck disable=SC2317
cleanup_bootstrap_lock() {
  # Only remove the lock when this process still owns it.
  if [ -r "$LOCK_DIR/pid" ] && [ "$(cat "$LOCK_DIR/pid" 2>/dev/null || true)" = "$$" ]; then
    remove_known_lock_files || true
  fi
}

if acquire_bootstrap_lock; then
  trap cleanup_bootstrap_lock EXIT HUP INT TERM
else
  lock_status=$?
  if [ "$lock_status" -eq 1 ]; then
    log "bootstrap already running; leaving existing runtime untouched"
    exit 0
  fi
  exit "$lock_status"
fi

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
  printf 'bridge_owner=%s\n' "$X88_BRIDGE_OWNER"
  printf 'sessions=%s\n' "${X88_SESSIONS[*]}"
} > "$X88_RUNTIME_DIR/bootstrap.state"

if [ "$FAIL" -eq 0 ]; then
  log "GREEN X-88/Luna tmux bootstrap complete"
else
  log "RED X-88/Luna tmux bootstrap incomplete" >&2
fi

exit "$FAIL"
