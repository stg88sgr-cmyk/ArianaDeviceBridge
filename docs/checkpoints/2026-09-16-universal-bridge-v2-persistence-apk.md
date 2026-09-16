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
- `ApkInstallReceiver` handles PackageInstaller callbacks and Android-required user confirmation.
- `ApkInstallStatusStore` persists started/pending/success/failure plus package, session id, Android status and message across restarts.
- `ApkBridgeStatus` provides read-only JSON snapshots for status, download list and latest-APK inspection, including `canRequestPackageInstalls()`.
- Manifest declares `REQUEST_INSTALL_PACKAGES` and registers the install-status receiver.
- `ActionPolicy` includes read-only APK status/list/inspect actions and a confirmation-gated `apk_install_latest` action.
- `LocalDeviceActionExecutor` can execute `apk_install_latest` only after consuming a matching one-time approval grant.

## Security boundary

- API keys/tokens are not stored in `UniversalBridgeStateStore`; existing secure/token stores remain authoritative.
- A trusted package id alone is insufficient. Signer continuity is required before creating an install session.
- Android may still require the system install-source approval or a visible install confirmation. The bridge does not bypass that platform boundary.
- Device capture sessions remain non-restorable after process death.

## Current build gate

GitHub Actions is triggered for every branch commit. Final acceptance waits for a green Android build of the newest head.

## Next build step

1. Wire `ApkBridgeStatus.status/list/inspectLatest` into `LocalBridgeServer` actions `apk_status`, `apk_list`, `apk_inspect_latest`.
2. Add focused tests for persisted install status and trust rejection.
3. Return `apk_install_latest` execution result through the bridge's approved-action path.
4. Run build gate and preserve the green head SHA in this checkpoint.
