# ARIANA X88 stable Android signing

ARIANA X88 debug and sideload APKs can use one stable private keystore supplied only through GitHub Actions repository secrets. The keystore and its passwords must never be committed to this repository.

## Why this is required

Android only allows an installed app to be updated in place when the new APK is signed with the same signing identity. GitHub-hosted runners otherwise create an ephemeral Android debug key, so builds from different runs may not update each other.

The Play-Protect-compatible `sideload` variant therefore uses the same stable signing configuration as the debug build whenever the secrets below are present.

## Required GitHub Actions repository secrets

- `ARIANA_DEBUG_KEYSTORE_B64` — Base64 encoded JKS/PKCS12 keystore
- `ARIANA_DEBUG_STORE_PASSWORD` — keystore password
- `ARIANA_DEBUG_KEY_ALIAS` — key alias
- `ARIANA_DEBUG_KEY_PASSWORD` — private-key password

## GitHub setup

1. Open the `ArianaDeviceBridge` repository.
2. Open **Settings**.
3. Open **Secrets and variables** -> **Actions**.
4. Create the four repository secrets listed above.
5. Run the **Android Sideload APK** workflow again.
6. Confirm the generated `signing-certificate.txt` reports `Signing mode: enabled`.
7. Keep an offline/private backup of the keystore. Losing it means future APKs cannot update the existing installation in place.

When all signing values are available, the workflow decodes the keystore only into the runner's temporary directory and Gradle signs the APK with it. If the secrets are absent, the workflow can still produce a temporary test build using the normal Android debug key, but that APK is not a durable update identity.

Never paste the keystore or passwords into source files, issues, pull requests, Actions logs, commit history, screenshots, or public chat messages.
