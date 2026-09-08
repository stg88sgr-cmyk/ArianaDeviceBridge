# ARIANA GI-∞ V1.6 Integration

This integration targets the current Snowworks Android tree and uses package `de.snowworks.ariana.gi`.

## Safety boundary

GI∞ only produces proposals. It does not call `ArianaCaptureService`, Android permissions, or the loopback bridge directly.

`GiBridgePolicyGate` mirrors the existing bridge boundary:

- camera/microphone/screen start: `CONFIRM`
- stop/status/current-session reads: `SAFE`
- unknown actions: `BLOCKED`

The invariant permits making a decision stricter, never weaker.

## Persistent experience memory

`AndroidEncryptedGiExperienceStore` uses AES/GCM with an Android Keystore key. The app currently has `android:allowBackup="false"`, so this SharedPreferences file is not exported through normal Android backup.

## Runtime example

```kotlin
val gi = GiRuntimeProvider.createRuntime()

val result = gi.process(
    goal = "Kamera aktivieren",
    context = "Visible user request",
    candidates = listOf(
        ThoughtCandidate(
            route = "CAMERA_START",
            proposal = "Start camera session",
            confidence = 0.98f,
            risk = 0.25f,
            goalAlignment = 1.0f,
            evidenceScore = 1.0f,
        ),
    ),
)
```

This returns an `ActionProposal` whose gate is `CONFIRM`. It does not execute the camera action.

## Release gate

GitHub Actions runs `:app:testDebugUnitTest` before `:app:assembleDebug`.
