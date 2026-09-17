#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

run_tmux_job() {
  if [ "$#" -ne 2 ]; then
    printf 'run_tmux_job requires <session> <command>\n' >&2
    return 64
  fi

  local session="$1"
  local command="$2"
  local max_bytes="${X88_ADAPTER_MAX_BYTES:-4096}"
  local timeout_seconds="${X88_ADAPTER_TIMEOUT:-15}"
  local command_bytes
  local token
  local job_dir
  local command_file
  local output_file
  local rc_file
  local runner
  local shell_command
  local polls
  local i
  local rc

  require_cmd tmux

  if ! session_exists "$session"; then
    printf 'adapter target session is not running: %s\n' "$session" >&2
    return 69
  fi

  command_bytes="$(printf '%s' "$command" | wc -c | tr -d '[:space:]')"
  if [ "$command_bytes" -eq 0 ] || [ "$command_bytes" -gt "$max_bytes" ]; then
    printf 'adapter command length must be 1..%s bytes\n' "$max_bytes" >&2
    return 65
  fi

  if ! [[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
    printf 'invalid X88_ADAPTER_TIMEOUT: %s\n' "$timeout_seconds" >&2
    return 65
  fi

  umask 077
  token="$$-${RANDOM}-${RANDOM}"
  job_dir="$X88_RUNTIME_DIR/jobs"
  mkdir -p "$job_dir"
  command_file="$job_dir/$token.command.sh"
  output_file="$job_dir/$token.output"
  rc_file="$job_dir/$token.rc"
  runner="$X88_BIN/tmux-job-runner.sh"

  if [ ! -x "$runner" ]; then
    printf 'tmux job runner missing: %s\n' "$runner" >&2
    return 69
  fi

  printf '%s\n' "$command" > "$command_file"
  chmod 600 "$command_file"

  printf -v shell_command '%q %q %q %q' "$runner" "$command_file" "$output_file" "$rc_file"
  if ! tmux new-window -d -t "$session:" -n "job-${token: -12}" "$shell_command"; then
    rm -f "$command_file" "$output_file" "$rc_file"
    printf 'failed to create tmux job window in %s\n' "$session" >&2
    return 70
  fi

  polls=$((timeout_seconds * 10))
  for ((i = 0; i < polls; i++)); do
    if [ -f "$rc_file" ]; then
      break
    fi
    sleep 0.1
  done

  if [ ! -f "$rc_file" ]; then
    printf 'adapter job timed out after %ss in %s\n' "$timeout_seconds" "$session" >&2
    return 124
  fi

  rc="$(cat "$rc_file")"
  if ! [[ "$rc" =~ ^[0-9]+$ ]]; then
    printf 'adapter produced invalid result code: %s\n' "$rc" >&2
    return 70
  fi

  if [ -f "$output_file" ]; then
    cat "$output_file"
  fi
  rm -f "$output_file" "$rc_file"

  return "$rc"
}
