# ARIANA X-88 Phone Core v1 Release Gate

Status date: 2026-09-15
Development branch: `feature/x88-character-v3`
Promoted device build: `1.13.0-rc1-bootstrap / code 36`
Release tag: `phone-core-v1.0.0`
Tested commit: `8c4204677555021fb092622dcbceac419fed90b3`
APK SHA-256: `2257daa06e16c4ad9a39702b67d68bb569a8f039ad690a9177f9f382d3b0382d`

## Release policy

No intermediate APKs are promoted to the phone. Changes accumulate in the repository and must pass this gate before one final installation candidate is produced.

## CI-proven gates

- [x] Full local Android unit-test suite passes in the Bootstrap workflow.
- [x] Conversation voice-recovery regression tests pass.
- [x] Conversation-stop routing tests pass.
- [x] Free-dialogue routing remains distinct from device commands.
- [x] DialogueRouter unavailable-provider, sanitizing, typed-error, empty-reply and blank-input tests pass.
- [x] Human-approval store tests are included in the full unit-test gate.
- [x] Bootstrap APK assembles successfully.
- [x] Stable Snowworks bootstrap signing is required by CI.
- [x] Bootstrap manifest source gate passes.
- [x] Merged Bootstrap manifest gate passes.
- [x] Bootstrap artifact uploads successfully.

Reference CI run: `34971030836`
Reference gate commit: `8c4204677555021fb092622dcbceac419fed90b3`

## Device E2E gates still required

These MUST NOT be marked passed from CI alone.

- [x] Verify final candidate versionCode on the physical phone.
- [x] Start Conversation Mode and complete first spoken turn.
- [x] Complete second spoken turn without pressing TALK.
- [x] Confirm SQLite contains both user/assistant turn pairs.
- [x] Say `Gespräch beenden` and confirm Conversation Mode returns to START/READY.
- [x] Confirm transient silence / NO_MATCH does not terminate the session.
- [x] Confirm local Core health is online during the test.
- [x] Confirm avatar state visibly follows listening / thinking / speaking.
- [x] Confirm conversation history remains visible after activity restart.

## Promotion rule

Only after every Device E2E checkbox is explicitly evidenced may a build be called `Phone Core v1` and promoted as the single installation candidate. CI success by itself is not device proof.


## Device E2E evidence

- Installed package verified as `de.snowworks.app.bootstrap versionCode:36`.
- Core health during test: `generation 6`, branch `guardian`, status `ok`.
- SQLite evidence after baseline ID 35: IDs 36/37 first turn and 38/39 second turn.
- Second spoken turn completed without TALK.
- Device owner confirmed `START/READY`, persisted history after restart, and visible avatar state changes.
- Promotion decision: PASS. The exact tested binary is ARIANA X-88 Phone Core v1.0.0.
