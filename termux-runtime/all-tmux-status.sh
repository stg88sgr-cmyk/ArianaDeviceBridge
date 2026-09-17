#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
# shellcheck source=runtime-common.sh
. "$SCRIPT_DIR/runtime-common.sh"

load_runtime_env
require_cmd tmux

FAIL=0
printf '%s\n' '=== ARIANA X-88 / LUNA XXY RUNTIME STATUS ==='

for session in "${X88_SESSIONS[@]}"; do
  if session_exists "$session"; then
    printf 'GREEN  %s\n' "$session"
  else
    printf 'RED    %s\n' "$session"
    FAIL=1
  fi
done

printf '\n'
if have ss; then
  if ss -ltn 2>/dev/null | grep -Fq "$X88_BRIDGE_HOST:$X88_BRIDGE_PORT"; then
    printf 'GREEN  bridge %s:%s\n' "$X88_BRIDGE_HOST" "$X88_BRIDGE_PORT"
  else
    printf 'RED    bridge %s:%s not listening\n' "$X88_BRIDGE_HOST" "$X88_BRIDGE_PORT"
    FAIL=1
  fi
else
  printf 'RED    ss missing; bridge socket cannot be verified\n'
  FAIL=1
fi

if [ -x "$X88_BIN/x88-adapter.sh" ]; then
  if "$X88_BIN/x88-adapter.sh" BUILD 'printf X88_ADAPTER_OK' 2>/dev/null | grep -q 'X88_ADAPTER_OK'; then
    printf 'GREEN  X-88 adapter\n'
  else
    printf 'RED    X-88 adapter\n'
    FAIL=1
  fi
else
  printf 'RED    X-88 adapter missing: %s/x88-adapter.sh\n' "$X88_BIN"
  FAIL=1
fi

if [ -x "$X88_BIN/luna-adapter.sh" ]; then
  if "$X88_BIN/luna-adapter.sh" LUNA_XXY_WORKER 'printf LUNA_ADAPTER_OK' 2>/dev/null | grep -q 'LUNA_ADAPTER_OK'; then
    printf 'GREEN  Luna adapter\n'
  else
    printf 'RED    Luna adapter\n'
    FAIL=1
  fi
else
  printf 'RED    Luna adapter missing: %s/luna-adapter.sh\n' "$X88_BIN"
  FAIL=1
fi

printf '\n'
if [ "$FAIL" -eq 0 ]; then
  printf '%s\n' 'GREEN  X-88 RUNTIME GREEN'
else
  printf '%s\n' 'RED    X-88 RUNTIME NOT GREEN'
fi

exit "$FAIL"
