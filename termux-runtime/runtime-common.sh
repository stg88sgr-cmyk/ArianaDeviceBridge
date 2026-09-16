#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

X88_HOME="${X88_HOME:-$HOME/.ariana}"
LUNA_HOME="${LUNA_HOME:-$HOME/.luna}"
X88_BIN="${X88_BIN:-$HOME/bin}"
X88_RUNTIME_DIR="${X88_RUNTIME_DIR:-$X88_HOME/runtime}"
X88_LOG_DIR="${X88_LOG_DIR:-$X88_HOME/logs}"
X88_BRIDGE_HOST="${X88_BRIDGE_HOST:-127.0.0.1}"
X88_BRIDGE_PORT="${X88_BRIDGE_PORT:-8765}"

X88_SESSIONS=(
  x88-bridge
  x88-build
  x88-logs
  luna-worker
  luna-bridge
  luna-build
  luna-xxy-worker
  luna-logs
)

mkdir -p "$X88_RUNTIME_DIR" "$X88_LOG_DIR" "$LUNA_HOME"

log() {
  printf '[%s] %s\n' "$(date -Iseconds 2>/dev/null || date)" "$*"
}

have() {
  command -v "$1" >/dev/null 2>&1
}

require_cmd() {
  if ! have "$1"; then
    printf 'missing required command: %s\n' "$1" >&2
    return 1
  fi
}

session_exists() {
  tmux has-session -t "$1" 2>/dev/null
}

idle_loop='while :; do sleep 3600; done'

resolve_session_command() {
  case "$1" in
    x88-bridge)
      printf '%s' "${X88_BRIDGE_CMD:-if [ -x \"$X88_BIN/x88-bridge-start.sh\" ]; then exec \"$X88_BIN/x88-bridge-start.sh\"; else $idle_loop; fi}"
      ;;
    x88-build)
      printf '%s' "${X88_BUILD_CMD:-$idle_loop}"
      ;;
    x88-logs)
      printf '%s' "${X88_LOGS_CMD:-touch \"$X88_LOG_DIR/runtime.log\"; exec tail -n 200 -F \"$X88_LOG_DIR/runtime.log\"}"
      ;;
    luna-worker)
      printf '%s' "${LUNA_WORKER_CMD:-if [ -x \"$X88_BIN/luna-worker-start.sh\" ]; then exec \"$X88_BIN/luna-worker-start.sh\"; else $idle_loop; fi}"
      ;;
    luna-bridge)
      printf '%s' "${LUNA_BRIDGE_CMD:-if [ -x \"$X88_BIN/luna-bridge-start.sh\" ]; then exec \"$X88_BIN/luna-bridge-start.sh\"; else $idle_loop; fi}"
      ;;
    luna-build)
      printf '%s' "${LUNA_BUILD_CMD:-$idle_loop}"
      ;;
    luna-xxy-worker)
      printf '%s' "${LUNA_XXY_WORKER_CMD:-if [ -x \"$X88_BIN/luna-xxy-worker-start.sh\" ]; then exec \"$X88_BIN/luna-xxy-worker-start.sh\"; else $idle_loop; fi}"
      ;;
    luna-logs)
      printf '%s' "${LUNA_LOGS_CMD:-mkdir -p \"$LUNA_HOME/logs\"; touch \"$LUNA_HOME/logs/runtime.log\"; exec tail -n 200 -F \"$LUNA_HOME/logs/runtime.log\"}"
      ;;
    *)
      return 64
      ;;
  esac
}

load_runtime_env() {
  local env_file="${X88_RUNTIME_ENV:-$X88_HOME/x88-runtime.env}"
  if [ -r "$env_file" ]; then
    # shellcheck disable=SC1090
    . "$env_file"
  fi
}
