# ARIANA PRESENCE DEVICE PROOF

This is the short real-device acceptance pass for Ariana Presence V1 on Stefan's Android phone.

## Preconditions

- HayaiTTS installed and selected as Android's preferred TTS engine.
- Ramona selected as the German `de-DE` voice inside HayaiTTS.
- Latest debug APK from branch `feature/ariana-presence-v1` installed.

## Proof sequence

1. Open the Snowworks / Ariana device app.
2. Open the `Ariana Presence` section.
3. Wait until voice status reports ready.
4. Read the live diagnostics line. It shows the active Android TTS package plus speech/timing counters.
5. Tap `Presence-Selbsttest`. The app loads and speaks this vowel-rich phrase automatically:

   `Ariana prüft Auge, Atem und Ausdruck. Esel, Igel, Ofen, Uhu, Ähre, Öl und Übung.`

6. While speech is playing, verify:
   - mouth opens and changes form,
   - core glow pulses,
   - idle motion continues but becomes calmer,
   - blink remains natural,
   - no visual jumps occur when speech starts or stops.
7. Watch the diagnostics line while the phrase is spoken:
   - `lip-text-timing` means the active TTS engine is supplying usable Android range callbacks,
   - `lip-fallback` means the safe pulse fallback is active,
   - both are valid and should keep the mouth moving.
8. After speech completes, verify:
   - mouth returns to rest,
   - core returns to idle intensity,
   - breathing / blink / gaze continue,
   - diagnostics return to `lip-idle`,
   - no repeating callbacks remain active.
9. Tap `Stop` during a second run and confirm speech stops immediately and the avatar returns to idle.

## Diagnostics interpretation

`PresenceDiagnostics` exposes:

- `voiceReady`
- `voiceEnginePackage`
- `utterancesStarted`
- `rangeCallbacksObserved` (lifetime counter for this engine session)
- `currentUtteranceRangeCallbacks` (current utterance only)
- `speaking`
- derived `lipSyncMode`

Expected lip-sync modes:

- `IDLE` when Ariana is not speaking.
- `TEXT_TIMING` while speaking if the current utterance receives Android range callbacks.
- `FALLBACK_PULSE` while speaking if the current utterance receives no range callbacks.

The mode decision is utterance-local. A previous well-timed phrase cannot accidentally make a later fallback phrase appear as text-timed.

## Pass criteria

Presence V1 passes the phone proof when:

1. the selected local Android TTS engine speaks the test phrase,
2. `utterancesStarted` increments,
3. avatar motion remains alive before, during and after speech,
4. lip-sync uses either text timing or the safe fallback without freezing,
5. Stop interrupts speech and returns the avatar to idle,
6. closing the screen leaves no stuck speaking state or animation loop.

## Failure notes to capture

If something fails, record only these facts:

- Android version / device model
- active TTS engine package
- whether voice audio played
- `utterancesStarted`
- `rangeCallbacksObserved`
- `currentUtteranceRangeCallbacks`
- visible lip-sync mode
- exact symptom

That is enough to diagnose the next code change without re-running the whole setup maze.
