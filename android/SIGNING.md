# ARIANA X88 stable debug signing

The Android debug APK can use a stable private keystore supplied only through GitHub Actions secrets. The keystore must never be committed to this repository.

Required repository secrets:

- `ARIANA_DEBUG_KEYSTORE_B64` — Base64 encoded JKS/PKCS12 keystore
- `ARIANA_DEBUG_STORE_PASSWORD` — keystore password
- `ARIANA_DEBUG_KEY_ALIAS` — key alias
- `ARIANA_DEBUG_KEY_PASSWORD` — key password

When all signing values are available, the workflow decodes the keystore into the runner's temporary directory and Gradle signs the debug build with it. If they are absent, the workflow still builds with the normal ephemeral Android debug key, but those APKs are not suitable for reliable in-place updates across GitHub-hosted runners.

Never paste the keystore or passwords into source files, issues, pull requests, Actions logs, or commit history.
