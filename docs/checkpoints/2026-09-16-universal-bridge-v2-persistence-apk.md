# Universal Bridge V2 checkpoint — 2026-09-16

Branch: `universal-bridge-v2-persistence-apk`
PR: `#37`

## Implemented

- Persistent `UniversalBridgeStateStore` backed by app-private SharedPreferences.
- Schema versioning and first-launch/update defaults.
- Persistent flags for APK manager, download watching and restore-on-start.
- Persistent trusted Snowworks package allowlist.
- Current build marker updated during `SnowworksApp.onCreate()`.
- `ApkInfo` inspection model.
- `ApkInspector` reads package id, version, file SHA-256 and signer SHA-256 certificate digests.
- `ApkTrustPolicy` enforces package allowlist plus signer continuity against the installed Snowworks app.
- `ApkManager` discovers APK downloads through `MediaStore.Downloads` on Android 10+ and stages them into app-private storage.
- `ApkInstaller` creates a trusted Android `PackageInstaller` session and never installs an APK that failed trust evaluation.
- `ApkInstallReceiver` handles PackageInstaller status and Android-required user confirmation.
- Manifest declares `REQUEST_INSTALL_PACKAGES` and registers the install-status receiver.
- `ActionPolicy` now includes read-only APK status/list/inspect actions and a confirmation-gated `apk_install_latest` action.
- `LocalDeviceActionExecutor` can execute `apk_install_latest` only after consuming a matching one-time approval grant.

## Security boundary

- API keys/tokens are not stored in `UniversalBridgeStateStore`; existing secure/token stores remain authoritative.
- A trusted package id alone is insufficient. Signer continuity is required before creating an install session.
- Android may still require the system install-source approval or a visible install confirmation. The bridge does not bypass that platform boundary.
- Device capture sessions remain non-restorable after process death.

## Current build gate

GitHub Actions has been triggered for the current branch head. Final acceptance waits for a green Android build.

## Next build step

1. Wire `apk_status`, `apk_list`, and `apk_inspect_latest` into `LocalBridgeServer` JSON responses.
2. Surface install-source readiness via `PackageManager.canRequestPackageInstalls()`.
3. Add install result persistence for last session/status/error.
4. Add tests around trust rejection and APK staging.
