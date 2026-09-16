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
- `ApkInstallStatusStore` persists started/pending/success/failure state across app restarts.
- Manifest declares `REQUEST_INSTALL_PACKAGES` and registers the install-status receiver.
- `ActionPolicy` includes read-only `apk_status`, `apk_list`, `apk_inspect_latest` and confirmation-gated `apk_install_latest`.
- `LocalDeviceActionExecutor` can execute `apk_install_latest` only after consuming a matching one-time approval grant.
- `LocalBridgeServer` exposes token-protected read-only APK endpoints:
  - `GET /v2/apk/status`
  - `GET /v2/apk/list`
  - `GET /v2/apk/inspect/latest`
- The legacy `/action` endpoint also supports `apk_status`, `apk_list`, and `apk_inspect_latest`.
- `/state` embeds the current APK status snapshot.
- `ApkInstallSourceController` reports install-source readiness and creates the Android system settings intent for this exact app.
- `ApkHealthCheck` verifies install-source readiness, Download discovery, staging, APK parsing and signer trust without starting an install.
- `ApkStatusActivity` shows human-readable APK status, latest inspection result, last install state and a dedicated APK self-test.
- Health UI now links directly to the APK Manager and includes APK health in the combined release-gate self-test.

## Security boundary

- API keys/tokens are not stored in `UniversalBridgeStateStore`; existing secure/token stores remain authoritative.
- A trusted package id alone is insufficient. Signer continuity is required before creating an install session.
- Android may still require the system install-source approval or a visible install confirmation. The bridge does not bypass that platform boundary.
- `Installationsquelle freigeben` opens Android's app-scoped system settings; it does not silently grant the permission.
- APK HTTP endpoints remain loopback-only and require the existing local bridge token.
- Device capture sessions remain non-restorable after process death.

## Current build gate

GitHub Actions is triggered for each branch update. Final acceptance waits for a green Android build on the latest branch head.

## Next build step

1. Validate current branch head in CI and fix compile/runtime-contract issues if present.
2. Add APK route checks directly to `BridgeSelfTest` so `/v2/apk/status`, `/list`, `/inspect/latest` are covered end-to-end.
3. After green CI, promote PR #37 from draft and merge only after the release gate is clean.
