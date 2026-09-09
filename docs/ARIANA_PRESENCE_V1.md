# ARIANA PRESENCE V1

## Goal

Turn ArianaDeviceBridge from a capability bridge into a coherent on-device presence layer without coupling the project to one TTS vendor or one avatar runtime.

## Decisions

### Voice
- Use Android's system `TextToSpeech` API as the stable boundary.
- Keep HayaiTTS selected as the preferred Android TTS engine on the phone.
- Keep Ramona as the current `de-DE` default voice.
- Do not hard-code the HayaiTTS package name into the app.
- Route all speech through `ArianaVoiceEngine` so the voice backend can be swapped later.

### Presence controller
- `ArianaPresenceController` is the first stable composition point.
- Other app code should call `say(...)` instead of addressing a vendor engine directly.
- Voice state, lip-sync, idle motion and future hearing/wake-word components attach here.

### Avatar
The current Ariana Avatar Master is the visual reference. The runtime path is:

1. Master reference
2. Layered PSD / art meshes
3. Live2D Cubism rig
4. VTube Studio prototype
5. Native Ariana avatar shell later

VTube Studio is a prototype host, not the long-term system boundary.

The exact art-layer and rigging contract is locked in `docs/ARIANA_AVATAR_ASSET_SPEC.md`.

## Current package layout

```text
de.snowworks.ariana.presence
  ArianaPresenceController

de.snowworks.ariana.voice
  ArianaVoiceEngine

de.snowworks.ariana.avatar
  AvatarDriver
  AvatarState
  SpeechMouthPlanner
  IdleMotionPlanner

de.snowworks.ariana.avatar.live2d
  Live2DParameterMapper
```

## Implemented phases

### P1 - Voice bridge
Status: implemented

- local system TTS adapter
- German locale (`de-DE`)
- speaking callbacks
- spoken-range callbacks where supported by the active Android TTS engine
- interrupt / queue behavior
- clean shutdown

### P2 - Voice test surface
Status: implemented

- in-app Ariana Presence card
- text field + Speak / Stop
- engine readiness and speaking state
- no cloud TTS dependency

### P3 - Avatar contract
Status: implemented

Renderer-neutral state covers:

- expression
- eye direction
- independent eye openness
- mouth openness
- mouth form
- head X/Y/Z pose
- breathing
- Ariana core glow
- speaking state

### P4 - Lip-sync
Status: first functional pass

- Android TTS spoken-range callbacks are converted into German vowel mouth targets
- mouth openness and mouth form are both emitted
- fallback mouth pulse keeps motion alive if the TTS engine does not expose range timing
- speaking also modulates breath and core glow

### P4.5 - Autonomous idle motion
Status: implemented

`IdleMotionPlanner` provides subtle renderer-neutral life while the avatar is visible:

- automatic blinking
- small gaze drift
- small head yaw/pitch/roll drift
- breathing cycle
- idle core-light pulse
- reduced idle head/gaze amplitude while speaking

Manual look/head pose remains the base pose; idle motion is layered on top instead of overwriting it.

### P5 - Live2D prototype
Status: contract ready, real art rig pending

- parameter mapping is fixed by `Live2DParameterMapper`
- custom core parameter is `ParamArianaCoreGlow`
- Motion Probe visualizes the runtime state in the Android app before the real model exists
- full layered-art specification is in `ARIANA_AVATAR_ASSET_SPEC.md`

### P6 - Native Ariana shell
Status: planned

- own renderer / Live2D runtime inside the Android app
- overlay or widget presence mode
- local device bridge connection
- speech input + output
- local continuity/memory surface

## Stable Live2D parameter IDs

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

## Expression IDs

```text
neutral
soft_smile
happy
focused
stern
surprised
```

## Quality gate

GitHub Actions runs unit tests before the debug APK build. Current tests cover:

- German mouth-shape mapping
- Live2D parameter range/mapping
- idle motion output bounds
- blink close/reopen behavior
- reduced idle motion while speaking

## Non-goals for V1

- rooting the phone
- binding the app to one proprietary voice engine
- making avatar rendering a dependency of device-control code
- replacing the existing loopback-only security model

## Current acceptance state

Already proven in CI:

1. unit tests pass
2. Android debug APK assembles
3. APK artifact uploads successfully
4. Presence code remains separate from the device-control/security layer

Still requires physical-device proof:

1. HayaiTTS/Ramona routing on the phone
2. quality of spoken-range callbacks from the selected TTS engine
3. visual feel of idle movement on the real Ariana Live2D rig
4. final native-renderer performance on the target phone

## Next implementation boundary

The next hard dependency is the real layered Ariana artwork. The code side is now ready for the art/rigging stage: the model can be cut and rigged against `ARIANA_AVATAR_ASSET_SPEC.md` without changing the Presence architecture.
