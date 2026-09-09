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
- Future avatar state, lip-sync, wake-word and presence UI attach here.

### Avatar
The current Ariana Avatar Master is the visual reference. The runtime path is:

1. Master reference
2. Layered PSD / art meshes
3. Live2D Cubism rig
4. VTube Studio prototype
5. Native Ariana avatar shell later

VTube Studio is a prototype host, not the long-term system boundary.

## Planned packages

```text
de.snowworks.ariana.presence
  ArianaPresenceController
  PresenceState

de.snowworks.ariana.voice
  ArianaVoiceEngine
  VoiceProfile

de.snowworks.ariana.avatar
  AvatarDriver
  AvatarState
  LipSyncDriver

de.snowworks.ariana.hearing
  SpeechInput
  WakeWordController
```

## Phase order

### P1 - Voice bridge
Status: started

- Local system TTS adapter
- German locale (`de-DE`)
- speaking callbacks
- interrupt / queue behavior
- clean shutdown

### P2 - Voice test surface
- Add a small in-app test panel
- text field + speak/stop
- show engine readiness and speaking state
- no cloud dependency

### P3 - Avatar contract
- Define neutral avatar state model
- expression enum
- eye direction
- mouth openness
- head pose
- breathing value
- glow intensity channels

### P4 - Lip-sync
- Feed speaking activity immediately
- later replace binary speaking state with amplitude/viseme timing
- keep voice and avatar decoupled

### P5 - Live2D prototype
- Import the final Ariana model into VTube Studio
- validate blink, eye tracking, head XY/Z, hair physics and mouth movement
- lock a parameter contract that can later be implemented natively

### P6 - Native Ariana shell
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

## Acceptance criteria for P1

A build passes P1 when:

1. The app starts without requiring a cloud TTS API.
2. `ArianaPresenceController.say("Hallo Stefan")` reaches the Android preferred TTS engine.
3. With HayaiTTS + Ramona configured as the system default, the phone speaks using that local voice.
4. `stopSpeaking()` interrupts current playback.
5. Destroying the controller cleanly releases `TextToSpeech`.

## Next implementation move

Add a tiny debug/test UI in the existing Android app that instantiates `ArianaPresenceController`, shows readiness, and exposes Speak / Stop. This gives us a real device proof before avatar integration.
