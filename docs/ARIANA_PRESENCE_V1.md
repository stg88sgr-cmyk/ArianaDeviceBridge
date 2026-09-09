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
- Use `UtteranceProgressListener.onRangeStart(...)` when the selected TTS engine exposes spoken-text timing.
- Always keep a fallback mouth animation because Android TTS engines are not required to emit range callbacks.

### Presence controller
- `ArianaPresenceController` is the stable composition point.
- Other app code calls `say(...)` instead of addressing a vendor engine directly.
- Voice events, avatar state and later hearing/wake-word components meet here.
- Presence owns the mouth-animation fallback and translates TTS range timing into renderer-neutral mouth targets.

### Avatar
The current Ariana Avatar Master is the visual reference. The runtime path is:

1. Master reference
2. Layered PSD / art meshes
3. Live2D Cubism rig
4. VTube Studio prototype
5. Native Ariana avatar shell later

VTube Studio is a prototype host, not the long-term system boundary.

The app already exposes a renderer-neutral `AvatarState` and `AvatarDriver`. The current debug build includes a deliberately simple motion probe so eye, mouth and core-glow state can be verified on the phone before the final artwork is rigged.

## Current packages

```text
de.snowworks.ariana.presence
  ArianaPresenceController

de.snowworks.ariana.voice
  ArianaVoiceEngine

de.snowworks.ariana.avatar
  AvatarDriver
  AvatarState
  SpeechMouthPlanner

de.snowworks.ariana.avatar.live2d
  Live2DParameterMapper

de.snowworks.app.ui
  ArianaMotionPreviewView
  PreviewAvatarDriver
```

## Live2D parameter contract

`Live2DParameterMapper` maps normalized Ariana state into these model parameters:

| Ariana channel | Live2D parameter | Range |
| --- | --- | --- |
| head yaw | `ParamAngleX` | -30..30 |
| head pitch | `ParamAngleY` | -30..30 |
| head roll | `ParamAngleZ` | -30..30 |
| eye X | `ParamEyeBallX` | -1..1 |
| eye Y | `ParamEyeBallY` | -1..1 |
| left eye open | `ParamEyeLOpen` | 0..1 |
| right eye open | `ParamEyeROpen` | 0..1 |
| mouth open | `ParamMouthOpenY` | 0..1 |
| mouth form | `ParamMouthForm` | -1..1 |
| breath | `ParamBreath` | 0..1 |
| chest/core light | `ParamArianaCoreGlow` | 0..1 |

Expression IDs are currently:

```text
neutral
soft_smile
happy
focused
stern
surprised
```

These names are the contract for the Ariana Live2D model. The art/rig can change without changing app logic as long as this contract remains stable.

## Lip-sync V1

Android's system TTS API does not hand generated PCM audio back to the calling app, so the first lip-sync path is text-timed rather than amplitude-driven.

1. `ArianaVoiceEngine` stores utterance text by utterance ID.
2. If the TTS engine emits `onRangeStart`, the currently spoken text range is forwarded.
3. `SpeechMouthPlanner` derives a mouth target from German vowel shape:
   - A: open / neutral
   - E, Ä, I, Y: progressively wider
   - O, Ö, U, Ü: progressively rounder
4. `ArianaPresenceController` adds a short motion pulse so the mouth does not freeze between range callbacks.
5. If an engine emits no range callbacks, the generic speaking pulse still works.
6. Speaking also gently modulates breath and the Ariana core glow.

This is intentionally replaceable. A future PCM/phoneme-capable voice backend can drive exact visemes through the same `AvatarState` fields.

## Phase order

### P1 - Voice bridge
Status: implemented

- local system TTS adapter
- German locale (`de-DE`)
- speaking callbacks
- spoken-text range callbacks when supported
- interrupt / queue behavior
- clean shutdown

### P2 - Voice test surface
Status: implemented

- in-app Ariana Presence panel
- text field + Speak / Stop
- engine readiness and speaking state
- local Android TTS path, no cloud TTS dependency

### P3 - Avatar contract
Status: implemented

- renderer-neutral avatar state
- expression enum
- eye direction/opening
- mouth openness + mouth form
- head pose
- breathing
- glow intensity
- stable Live2D parameter mapper

### P4 - Lip-sync motion probe
Status: implemented for V1

- speaking fallback animation
- TTS range timing when available
- German vowel mouth-shape planner
- visible on-device motion probe
- voice and avatar remain decoupled

### P5 - Live2D prototype
Status: next

- cut final Ariana master artwork into riggable layers
- import into Cubism
- implement the parameter contract above
- validate blink, eye tracking, head XY/Z, hair physics and mouth movement
- connect a VTube Studio/native driver without changing presence logic

### P6 - Native Ariana shell
Status: later

- own renderer / Live2D runtime inside the Android app
- overlay or widget presence mode
- local device bridge connection
- speech input + output
- local continuity/memory surface

## Non-goals for V1

- Rooting the phone
- Binding the app to one proprietary voice engine
- Making avatar rendering a dependency of device-control code
- Replacing the existing loopback-only security model
- Pretending the temporary motion probe is the final Ariana artwork

## Acceptance criteria for current branch

A build passes the current Presence V1 gate when:

1. The app starts without requiring a cloud TTS API.
2. `ArianaPresenceController.say(...)` reaches the Android preferred TTS engine.
3. With HayaiTTS + Ramona configured as the system default, the phone speaks using that local voice.
4. `stopSpeaking()` interrupts current playback.
5. The Presence card visibly animates mouth/core state while speech is active.
6. If the engine supports text-range timing, the UI reports `Lip-Sync: Text-Timing aktiv` during speech.
7. Destroying the controller cleanly releases `TextToSpeech` and animation callbacks.
8. GitHub Actions assembles the debug APK successfully.

## Next implementation move

The next technical boundary is no longer TTS. It is the real Ariana Live2D asset. Build the layered art/rig against the fixed parameter contract above, then implement the first concrete `AvatarDriver` for that runtime.
