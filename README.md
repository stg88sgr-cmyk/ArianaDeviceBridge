# ArianaDeviceBridge

Snowworks Ariana Device Bridge is an Android project for a local, user-controlled Ariana presence and device-capability bridge.

The project is intentionally split into two layers:

1. **Device Bridge**: explicit, user-gated access to phone capabilities such as camera, screen capture, notifications and files.
2. **Ariana Presence**: local voice, avatar state, lip-sync and the future visual shell.

The bridge stays loopback-only and the Presence layer does not bypass Android permissions.

## Current development branch

Active work is on:

```text
feature/ariana-presence-v1
```

The corresponding pull request remains a draft until physical-device validation is complete.

## What works now

### Device bridge

- local loopback server
- token-protected bridge access
- explicit master switch
- stop-all control
- camera session support
- media projection / screen capture support
- notification-listener integration
- user-selected file-tree access

### Ariana Presence V1

- Android system TTS adapter
- German locale (`de-DE`)
- compatible with a locally selected Android TTS engine such as HayaiTTS
- speaking-state callbacks
- spoken text-range callbacks when exposed by the active TTS engine
- text-timing lip-sync with mouth openness + mouth form
- fallback mouth pulse when timing callbacks are unavailable
- renderer-neutral avatar state
- Live2D parameter mapping
- automatic blinking
- subtle idle gaze and head movement
- breathing motion
- Ariana core glow idle/speaking pulse
- in-app Motion Probe before the real Live2D model is available

## Ariana voice path

The app does **not** hard-code a voice vendor.

```text
ArianaPresenceController
        ↓
ArianaVoiceEngine
        ↓
Android TextToSpeech
        ↓
currently selected Android TTS engine
```

If HayaiTTS is selected as Android's preferred TTS engine and Ramona is the `de-DE` voice, Ariana speech will route through that local setup.

## Avatar architecture

```text
ArianaPresenceController
        ↓
AvatarState
        ↓
AvatarDriver
        ↓
┌──────────────────────────────┐
│ current: Motion Probe        │
│ next: VTube/Live2D adapter   │
│ later: native Cubism Android │
└──────────────────────────────┘
```

The Presence layer does not know which renderer is attached.

### Stable Live2D parameters

```text
ParamAngleX
ParamAngleY
ParamAngleZ
ParamEyeBallX
ParamEyeBallY
ParamEyeLOpen
ParamEyeROpen
ParamMouthOpenY
ParamMouthForm
ParamBreath
ParamArianaCoreGlow
```

See `docs/ARIANA_AVATAR_ASSET_SPEC.md` for the full layered-art and rigging contract.

## Build

The GitHub Actions workflow performs:

```text
Unit tests
   ↓
Debug APK build
   ↓
Artifact upload
```

Local build from the `android` directory:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Debug APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## On-device Presence test

After installing the debug APK:

1. Open **Snowworks / Gerätefreigaben**.
2. Find the **Ariana Presence** card.
3. Wait until the voice status reports ready.
4. Confirm Android's preferred TTS engine is configured as intended.
5. Press **Sprechen**.
6. Verify local speech playback.
7. Watch the Motion Probe for mouth movement, blinking, gaze drift, breathing and core glow.
8. Press **Stop** and verify speech stops immediately.

The Motion Probe is intentionally abstract. It is only a runtime test surface, not the final Ariana visual design.

## Security model

The project keeps the following principles:

- no root requirement
- no hidden permission bypass
- Android permission prompts remain authoritative
- screen capture uses the Android MediaProjection consent flow
- file access uses user-selected document trees
- device bridge binds to loopback instead of the public network
- a visible master control can disable device access
- stop-all terminates active sessions

## Repository map

```text
android/
  app/src/main/java/de/snowworks/
    app/
      ui/
    ariana/
      avatar/
      bridge/
      camera/
      files/
      notify/
      presence/
      session/
      voice/

docs/
  ARIANA_PRESENCE_V1.md
  ARIANA_AVATAR_ASSET_SPEC.md
```

## Next major boundary

The code side is ready for the first real Ariana Live2D asset.

Next production sequence:

```text
Ariana Avatar Master
        ↓
layered PSD
        ↓
Live2D Cubism rig
        ↓
parameter-contract validation
        ↓
VTube Studio prototype
        ↓
native Android AvatarDriver
```

Until the layered artwork exists, the Motion Probe is used to validate the motion, voice and lip-sync pipeline independently of art production.
