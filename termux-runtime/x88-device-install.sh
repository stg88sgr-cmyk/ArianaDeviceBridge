#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# X-88 local device installer for Termux.
# Uses Android's supported Wireless Debugging / ADB path. No protection bypasses.

log() { printf '[X88-INSTALL] %s\n' "$*"; }
die() { printf '[X88-INSTALL][ERROR] %s\n' "$*" >&2; exit 1; }

APK="${1:-${X88_APK:-}}"
[[ -n "$APK" ]] || die "Usage: $0 /path/to/x88-*.apk"
[[ -f "$APK" ]] || die "APK not found: $APK"

if ! command -v adb >/dev/null 2>&1; then
  log "adb missing. Installing Termux android-tools..."
  pkg install android-tools -y
fi

PROFILE="debug"
case "$(basename "$APK")" in
  *safeinstall*) PROFILE="safeinstall" ;;
  *bootstrap*) PROFILE="bootstrap" ;;
  *sideload*) PROFILE="sideload" ;;
esac

case "$PROFILE" in
  debug)       PACKAGE="de.snowworks.app.debug" ;;
  safeinstall) PACKAGE="de.snowworks.app.safe" ;;
  bootstrap)   PACKAGE="de.snowworks.app.bootstrap" ;;
  sideload)    PACKAGE="de.snowworks.app.sideload" ;;
esac

log "Profile: $PROFILE"
log "Package: $PACKAGE"
log "APK SHA256: $(sha256sum "$APK" | awk '{print $1}')"

adb start-server >/dev/null

# If already connected, reuse it.
if ! adb devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  log "No authorized ADB device connected."
  log "On the phone open: Developer options -> Wireless debugging -> Pair device with pairing code."

  PAIR_ENDPOINT="${X88_ADB_PAIR:-}"
  if [[ -z "$PAIR_ENDPOINT" ]]; then
    read -r -p "Pairing IP:PORT: " PAIR_ENDPOINT
  fi

  PAIR_CODE="${X88_ADB_CODE:-}"
  if [[ -z "$PAIR_CODE" ]]; then
    read -r -p "Pairing code: " PAIR_CODE
  fi

  printf '%s\n' "$PAIR_CODE" | adb pair "$PAIR_ENDPOINT" || die "ADB pairing failed"

  CONNECT_ENDPOINT="${X88_ADB_CONNECT:-}"
  if [[ -z "$CONNECT_ENDPOINT" ]]; then
    log "Now read the normal 'IP address & Port' shown on the Wireless debugging screen."
    read -r -p "Connect IP:PORT: " CONNECT_ENDPOINT
  fi

  adb connect "$CONNECT_ENDPOINT" || die "ADB connect failed"
fi

adb devices

log "Installing APK with Android package manager via authorized ADB..."
adb install -r "$APK" || die "Installation failed. Check the phone for a required Android confirmation."

log "Verifying package..."
adb shell pm path "$PACKAGE" | grep -q '^package:' || die "Package not found after install: $PACKAGE"

log "Launching X-88..."
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

log "Installed and launched: $PACKAGE"
log "No Google/Samsung protection was disabled or bypassed."
