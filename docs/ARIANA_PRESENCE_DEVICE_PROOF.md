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
4. Confirm the active Android TTS engine package through Presence diagnostics.
5. Trigger the Presence self-test phrase:

   `Ariana prüft Auge, Atem und Ausdruck. Esel, Igel, Ofen, Uhu, Ähre, Öl und Übung.`

6. While speech is playing, verify:
   - mouth opens and changes form,
   - core glow pulses,
   - idle motion continues but becomes calmer,
   - blink remains natural,
   - no visual jumps occur when speech starts or stops.
7. After speech completes, verify:
   - mouth returns to rest,
   - core returns to idle intensity,
   - breathing / blink / gaze continue,
   - no repeating callbacks remain active.

## Diagnostics interpretation

`PresenceDiagnostics` exposes:

- `voiceReady`
- `voiceEnginePackage`
- `utterancesStarted`
- `rangeCallbacksObserved`
- `speaking`
- derived `lipSyncMode`

Expected lip-sync modes:

- `IDLE` when Ariana is not speaking.
- `TEXT_TIMING` while speaking if the selected TTS engine emits Android range callbacks.
- `FALLBACK_PULSE` while speaking if no text-range callbacks are emitted.

Both speaking modes are valid. `TEXT_TIMING` gives better vowel-shaped motion; `FALLBACK_PULSE` prevents a frozen mouth on engines without range timing.

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
- visible lip-sync mode
- exact symptom

That is enough to diagnose the next code change without re-running the whole setup maze.
