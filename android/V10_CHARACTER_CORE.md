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

## Canonical install package

The durable phone package is `de.snowworks.app.stable`. It uses the pinned Ariana X-88 certificate and can coexist with earlier debug or sideload packages whose temporary certificates cannot be upgraded safely.

## Release gate

The debug and stable workflows run unit tests before APK assembly. Stable artifacts are published only with the pinned signing identity; otherwise a verified local-signing input is exported.
