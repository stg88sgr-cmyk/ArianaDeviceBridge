# ARIANA X-88 Phone Core v1 Release Gate

Status date: 2026-09-15
Development branch: `feature/x88-character-v3`
Installed baseline reported by device owner: `1.12.1 / code 35`

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

Reference CI run: `34969452921`
Reference gate commit: `0dac66e9e64b66d3d74a8e71a66838cfb2fccc69`

## Device E2E gates still required

These MUST NOT be marked passed from CI alone.

- [ ] Verify final candidate versionCode on the physical phone.
- [ ] Start Conversation Mode and complete first spoken turn.
- [ ] Complete second spoken turn without pressing TALK.
- [ ] Confirm SQLite contains both user/assistant turn pairs.
- [ ] Say `Gespräch beenden` and confirm Conversation Mode returns to START/READY.
- [ ] Confirm transient silence / NO_MATCH does not terminate the session.
- [ ] Confirm local Core health is online during the test.
- [ ] Confirm avatar state visibly follows listening / thinking / speaking.
- [ ] Confirm conversation history remains visible after activity restart.

## Promotion rule

Only after every Device E2E checkbox is explicitly evidenced may a build be called `Phone Core v1` and promoted as the single installation candidate. CI success by itself is not device proof.
