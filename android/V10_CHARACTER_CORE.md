# ARIANA X-88 v10 · Character Core

This branch combines the v9.2 startup-hardened runtime with Character Core v3.

## Runtime additions

- Character/Profile entry on the existing X-88 home screen
- identity-locked scene presets and prompt composition
- local reference selection and local gallery metadata
- provider-neutral image-generation interface

## Preserved controls

- Master switch and STOP ALL behavior
- confirmation-bound device actions
- non-exported feature activities
- local-first storage and loopback bridge boundaries

## Release gate

The debug workflow must run both `:app:testDebugUnitTest` and `:app:assembleDebug`. The resulting APK is not promoted until the workflow succeeds and its signing certificate is reported.
