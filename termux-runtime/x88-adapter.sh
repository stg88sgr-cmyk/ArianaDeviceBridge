#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=runtime-common.sh
. "$SCRIPT_DIR/runtime-common.sh"
# shellcheck source=tmux-adapter-common.sh
. "$SCRIPT_DIR/tmux-adapter-common.sh"

if [ "$#" -ne 2 ]; then
  printf 'usage: %s <BUILD|BRIDGE|LOGS> <command>\n' "$0" >&2
  exit 64
fi

TARGET="$1"
COMMAND="$2"

case "$TARGET" in
  BUILD) session='x88-build' ;;
  BRIDGE) session='x88-bridge' ;;
  LOGS) session='x88-logs' ;;
  *)
    printf 'unsupported X-88 target: %s\n' "$TARGET" >&2
    exit 64
    ;;
esac

run_tmux_job "$session" "$COMMAND"
