#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/.ariana"
LOG_DIR="$ROOT/logs"
JOB_DIR="$ROOT/jobs"
mkdir -p "$LOG_DIR" "$JOB_DIR"

sanitize() {
  sed -E 's/(Bearer[[:space:]]+|token=|sk-|ghp_)[A-Za-z0-9_.-]{8,}/[REDACTED]/gI'
}

ensure_runtime() {
  if ! tmux has-session -t x88-build 2>/dev/null || ! tmux has-session -t luna-worker 2>/dev/null; then
    "$HOME/bin/x88-tmux-bootstrap.sh" >/dev/null
  fi
}

send_literal() {
  local session="$1" command="$2"
  tmux send-keys -t "$session" -l "$command"
  tmux send-keys -t "$session" C-m
}

status_cmd() {
  "$HOME/bin/x88-tmux-status.sh" | sanitize
  if [ -f "$ROOT/tmux-runtime-status.json" ]; then
    echo '---JSON---'
    cat "$ROOT/tmux-runtime-status.json" | sanitize
  fi
}

build_cmd() {
  local project="${1:-${X88_PROJECT_DIR:-$HOME/ArianaDeviceBridge}}"
  local task="${2:-assembleDebug}"
  [[ "$task" =~ ^[A-Za-z0-9:_-]{1,80}$ ]] || { echo '[X88][DENIED] invalid Gradle task' >&2; exit 4; }
  local resolved
  resolved="$(cd "$project" 2>/dev/null && pwd -P)" || { echo '[X88][ERR] project not found' >&2; exit 5; }
  [[ "$resolved" == "$HOME"/* ]] || { echo '[X88][DENIED] project must stay under HOME' >&2; exit 6; }

  local gradle_dir="$resolved"
  [ -x "$gradle_dir/gradlew" ] || gradle_dir="$resolved/android"
  [ -x "$gradle_dir/gradlew" ] || { echo '[X88][ERR] gradlew not found' >&2; exit 7; }

  ensure_runtime
  local id="build-$(date -u +%Y%m%dT%H%M%SZ)-$$"
  local script="$JOB_DIR/$id.sh" result="$JOB_DIR/$id.result" log="$LOG_DIR/$id.log"
  printf '#!/data/data/com.termux/files/usr/bin/bash\nset -o pipefail\ncd %q\n./gradlew %q --no-daemon 2>&1 | tee -a %q\nrc=${PIPESTATUS[0]}\nprintf "%%s\\n" "$rc" > %q\nexit "$rc"\n' "$gradle_dir" "$task" "$log" "$result" > "$script"
  chmod 700 "$script"
  printf -v launch 'bash %q' "$script"
  send_literal x88-build "$launch"
  printf '{"accepted":true,"job_id":"%s","result_file":"%s","log_file":"%s"}\n' "$id" "$result" "$log"
}

logs_cmd() {
  local session="${1:-all}" lines="${2:-100}"
  [[ "$lines" =~ ^[0-9]+$ ]] || exit 8
  [ "$lines" -ge 1 ] && [ "$lines" -le 500 ] || exit 8
  local sessions=(x88-bridge x88-build luna-worker x88-logs)
  local name
  if [ "$session" != all ]; then
    case "$session" in x88-bridge|x88-build|luna-worker|x88-logs) sessions=("$session");; *) exit 9;; esac
  fi
  for name in "${sessions[@]}"; do
    echo "===== $name ====="
    tmux capture-pane -t "$name" -p -S "-$lines" 2>/dev/null | sanitize || echo '(missing)'
  done
}

luna_cmd() {
  local action="${1:-status}"
  ensure_runtime
  case "$action" in
    status) tmux capture-pane -t luna-worker -p -S -100 2>/dev/null | sanitize ;;
    start)
      [ -n "${X88_LUNA_CMD:-}" ] || { echo '[X88][CONFIG] set X88_LUNA_CMD first' >&2; exit 10; }
      send_literal luna-worker "$X88_LUNA_CMD"
      echo '[X88][OK] Luna worker command submitted'
      ;;
    stop) tmux send-keys -t luna-worker C-c; echo '[X88][OK] Luna worker interrupt sent' ;;
    restart)
      [ -n "${X88_LUNA_CMD:-}" ] || { echo '[X88][CONFIG] set X88_LUNA_CMD first' >&2; exit 10; }
      tmux send-keys -t luna-worker C-c; sleep 0.5; send_literal luna-worker "$X88_LUNA_CMD"
      echo '[X88][OK] Luna worker restarted'
      ;;
    *) echo '[X88][DENIED] Luna action must be start|stop|restart|status' >&2; exit 11 ;;
  esac
}

case "${1:-help}" in
  STATUS) status_cmd ;;
  BUILD) shift; build_cmd "$@" ;;
  LOGS) shift; logs_cmd "$@" ;;
  LUNA_WORKER) shift; luna_cmd "$@" ;;
  CONTRACT) cat "$ROOT/x88-termux-adapter.contract.json" ;;
  *) echo 'Commands: STATUS | BUILD [project] [task] | LOGS [session|all] [1..500] | LUNA_WORKER start|stop|restart|status | CONTRACT' ;;
esac
