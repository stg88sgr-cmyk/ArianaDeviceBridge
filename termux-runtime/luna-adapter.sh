#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=runtime-common.sh
. "$SCRIPT_DIR/runtime-common.sh"
# shellcheck source=tmux-adapter-common.sh
. "$SCRIPT_DIR/tmux-adapter-common.sh"

if [ "$#" -ne 2 ]; then
  printf 'usage: %s <WORKER|LUNA_XXY_WORKER|BRIDGE|BUILD|LOGS> <command>\n' "$0" >&2
  exit 64
fi

TARGET="$1"
COMMAND="$2"

case "$TARGET" in
  WORKER) session='luna-worker' ;;
  LUNA_XXY_WORKER) session='luna-xxy-worker' ;;
  BRIDGE) session='luna-bridge' ;;
  BUILD) session='luna-build' ;;
  LOGS) session='luna-logs' ;;
  *)
    printf 'unsupported Luna target: %s\n' "$TARGET" >&2
    exit 64
    ;;
esac

run_tmux_job "$session" "$COMMAND"
