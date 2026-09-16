# Universal Bridge V2 checkpoint — 2026-09-16

Branch: `universal-bridge-v2-persistence-apk`

## Implemented

- Persistent `UniversalBridgeStateStore` backed by app-private SharedPreferences.
- Schema versioning and first-launch/update defaults.
- Persistent flags for APK manager, download watching and restore-on-start.
- Persistent trusted Snowworks package allowlist.
- Current build marker updated during `SnowworksApp.onCreate()`.
- New `ApkInfo` inspection model.
- New `ApkInspector` reads package id, version, file SHA-256 and signer SHA-256 certificate digests.
- APK inspection marks packages from the persistent Snowworks allowlist as trusted package ids.

## Security boundary

- API keys/tokens are not stored in `UniversalBridgeStateStore`; existing secure/token stores remain authoritative.
- A trusted package id alone is not sufficient for unattended installation. Installer phase must also enforce signer continuity before a trusted update can proceed.
- Device capture sessions remain non-restorable after process death.

## Next build step

1. Add `ApkTrustPolicy` to compare candidate signer digest against the currently installed trusted Snowworks package.
2. Add `ApkManager` for controlled Download-folder discovery.
3. Add `ApkInstaller` using Android `PackageInstaller` / user-approved install-source rules.
4. Expose read-only APK status/actions through the bridge before enabling installation actions.
