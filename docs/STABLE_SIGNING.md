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

Expected certificate SHA-256 for the generated Snowworks X-88 stable signing kit:

`D9:EB:94:97:F5:CA:8F:58:BC:6E:5E:D4:39:4C:D6:6D:AC:31:5B:D1:53:4D:98:3C:7F:65:EA:78:DA:E4:89:83`

Keep an offline backup of the private keystore. Losing it prevents future APKs from updating the same installed Android app identity.

## Build workflow

Run:

`Android Stable Safe APK`

This workflow:

1. refuses to build if any signing secret is missing,
2. reconstructs the keystore only inside the GitHub runner,
3. runs unit tests,
4. builds the reduced-permission `safeinstall` variant,
5. verifies package id `de.snowworks.app.safe`,
6. reports the APK signing certificate,
7. creates an APK SHA-256 report,
8. uploads the signed APK artifact.

## One-time phone migration

When the first stable-signed APK is ready:

1. back up anything important from the current test app,
2. uninstall the current `1.7.3-safe` build,
3. install the stable-signed `1.7.4-safe` APK,
4. open X-88 and run the normal smoke test,
5. record the installed certificate fingerprint,
6. do not replace the permanent signing key again.

Every later stable build can then update the installed app in place if the package id and signing key remain unchanged.
