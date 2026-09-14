# ARIANA X-88 stable signing

## Goal

Keep `de.snowworks.app.safe` updateable on the phone without uninstalling every build.

The current 1.7.3-safe APK was installed from CI with an ephemeral debug certificate. Android cannot accept a future APK signed with a different key as an in-place update. Therefore the migration to the permanent key requires **one final uninstall/reinstall**. After that, all stable-safe APKs must use the same signing key forever.

## Repository secrets

Configure these four GitHub Actions repository secrets:

- `ARIANA_DEBUG_KEYSTORE_B64`
- `ARIANA_DEBUG_STORE_PASSWORD`
- `ARIANA_DEBUG_KEY_ALIAS`
- `ARIANA_DEBUG_KEY_PASSWORD`

The names are retained for compatibility with the existing Gradle signing configuration. They now represent the permanent X-88 signing identity used by the stable-safe workflow.

Never commit the keystore, passwords, or base64 keystore payload to the repository.

## Permanent signing identity

Expected certificate SHA-256 for the v3 Snowworks X-88 stable signing kit:

`0A:24:B7:47:5A:DF:23:21:84:23:3E:50:A2:BE:36:8E:CF:55:AF:98:1A:39:48:F3:06:2E:12:CD:C5:7D:30:AB`

Keep an offline backup of the private keystore. Losing it prevents future APKs from updating the same installed Android app identity.

## Build workflow

Run:

`Android Stable Safe APK`

This workflow:

1. refuses to build if any signing secret is missing,
2. reconstructs the keystore only inside the GitHub runner,
3. tolerates whitespace and an accidentally copied `ARIANA_DEBUG_KEYSTORE_B64=` prefix,
4. runs unit tests,
5. builds the reduced-permission `safeinstall` variant,
6. verifies package id `de.snowworks.app.safe`,
7. verifies the exact permanent signing certificate,
8. creates an APK SHA-256 report,
9. uploads the signed APK artifact.

## One-time phone migration

When the first stable-signed APK is ready:

1. back up anything important from the current test app,
2. uninstall the current `1.7.3-safe` build,
3. install the stable-signed `1.7.4-safe` APK,
4. open X-88 and run the normal smoke test,
5. record the installed certificate fingerprint,
6. do not replace the permanent signing key again.

Every later stable build can then update the installed app in place if the package id and signing key remain unchanged.
