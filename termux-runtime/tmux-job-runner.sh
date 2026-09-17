#!/data/data/com.termux/files/usr/bin/bash
set -u

if [ "$#" -ne 3 ]; then
  printf 'usage: %s <command-file> <output-file> <rc-file>\n' "$0" >&2
  exit 64
fi

COMMAND_FILE="$1"
OUTPUT_FILE="$2"
RC_FILE="$3"
RC_TMP="${RC_FILE}.tmp.$$"

umask 077
mkdir -p "$(dirname -- "$OUTPUT_FILE")" "$(dirname -- "$RC_FILE")"

rc=0
if [ ! -r "$COMMAND_FILE" ]; then
  printf 'adapter command file is not readable: %s\n' "$COMMAND_FILE" > "$OUTPUT_FILE"
  rc=66
else
  set +e
  # Commands run in a subshell so `exit` inside a job cannot kill the runner
  # before the result code is persisted.
  (
    # shellcheck disable=SC1090
    . "$COMMAND_FILE"
  ) > "$OUTPUT_FILE" 2>&1
  rc=$?
fi

printf '%s\n' "$rc" > "$RC_TMP"
mv -f "$RC_TMP" "$RC_FILE"
rm -f "$COMMAND_FILE"
exit "$rc"
