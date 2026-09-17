# ARIANA X88 -> Ariana XX88Y Transformation

## Real source

- Repository: `stg88sgr-cmyk/ArianaDeviceBridge`
- Base branch: `x88-evolution-g2`
- Observed base commit: `809d336670c27c99b1a8b50de40da02e43b9eeab`
- Android project root: `android/`
- Android namespace/applicationId: `de.snowworks.app`
- compileSdk/targetSdk: 36
- Java/Kotlin target: 17

## Execution loop

```text
ARIANA OBSERVE(real tree)
  -> LUNA MUTATE(real migration plan with real paths)
  -> META VERIFY(real Gradle build/test/lint)
  -> REPAIR only from observed failures
  -> OBSERVED_RESULT
  -> ARIANA
```

No stage may claim success without an observed result.

## Existing real modules reused

The transformation does not replace working code. It consolidates existing modules behind stable contracts.

- UI: `android/app/src/main/java/de/snowworks/app/ui/`
- Existing system core: `android/app/src/main/java/de/snowworks/ariana/system/`
- Bridge / AI routing: `android/app/src/main/java/de/snowworks/ariana/bridge/`
- App Prompter: `android/app/src/main/java/de/snowworks/ariana/prompter/`
- Image providers: `android/app/src/main/java/de/snowworks/ariana/image/`
- Voice: `android/app/src/main/java/de/snowworks/ariana/voice/`
- Luna: `android/app/src/main/java/de/snowworks/ariana/luna/`
- Termux runtime: `termux-runtime/`
- Control deck: `control-deck/`

## New XX88Y consolidation layer

Path: `android/app/src/main/java/de/snowworks/ariana/xx88y/`

- `Models.kt` canonical UI/runtime state models
- `Contracts.kt` adapter/provider/worker contracts
- `X88StateHolder.kt` central state and audit stream
- `X88ActionBus.kt` fail-closed Action Bus with SAFE/CONFIRM/BLOCKED policy

Tests:

- `android/app/src/test/java/de/snowworks/ariana/xx88y/X88ActionBusTest.kt`

## Initial truth states

Capabilities that have not been connected through a concrete adapter remain `NOT_IMPLEMENTED`.
Providers without a reachable endpoint remain disconnected/offline.
Build verification remains `UNKNOWN` until CI or local Gradle returns a real result.
No UI action is allowed to infer success from a button press.

## Migration phases

1. Canonical state/contracts and fail-closed Action Bus. **Implemented in this branch.**
2. Read-only adapters over existing `system`, `bridge`, App Prompter, image, voice and Termux components.
3. Bind `X88HomeActivity` and status screens to canonical `X88State` without removing existing functionality.
4. Bind real action handlers one capability at a time.
5. Expose LUNA pipeline stages from observed build/test/repair events.
6. Run `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` and installation-profile builds.
7. Repair only observed failures, then record artifact hashes and final observed result.

## Verification contract

The existing `.github/workflows/android-build-x88-g2.yml` is the canonical CI verifier for pull requests into `x88-evolution-g2`. It runs unit tests, lint, and debug/safeinstall/bootstrap/sideload APK builds and produces SHA-256 evidence.
