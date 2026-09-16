#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REQUIRE_BOOT=0

if [ "${1:-}" = "--require-boot" ]; then
  REQUIRE_BOOT=1
elif [ -n "${1:-}" ]; then
  printf 'usage: %s [--require-boot]\n' "$0" >&2
  exit 64
fi

printf '%s\n' '=== ARIANA X-88 FINAL RUNTIME GATE ==='

FAIL=0
if "$SCRIPT_DIR/all-tmux-bootstrap.sh"; then
  printf '%s\n' 'GREEN  bootstrap'
else
  printf '%s\n' 'RED    bootstrap'
  FAIL=1
fi

printf '\n'
if "$SCRIPT_DIR/all-tmux-status.sh"; then
  printf '%s\n' 'GREEN  runtime health'
else
  printf '%s\n' 'RED    runtime health'
  FAIL=1
fi

printf '\n'
BOOT_LOG="${X88_HOME:-$HOME/.ariana}/logs/boot.log"
if [ "$REQUIRE_BOOT" -eq 1 ]; then
  if [ -s "$BOOT_LOG" ] && grep -q 'X-88 BOOT' "$BOOT_LOG"; then
    printf '%s\n' 'GREEN  reboot/autostart evidence present'
  else
    printf 'RED    reboot/autostart evidence missing: %s\n' "$BOOT_LOG"
    FAIL=1
  fi
else
  printf '%s\n' 'INFO   reboot evidence not required in preflight mode'
fi

printf '\n'
if [ "$FAIL" -eq 0 ]; then
  printf '%s\n' 'GREEN  FINAL GATE PASSED'
else
  printf '%s\n' 'RED    FINAL GATE FAILED'
fi

exit "$FAIL"
