# ARIANA X-88 Presence v2 Release Gate

Presence v2 is developed above the frozen Phone Core v1 tag `phone-core-v1.0.0`.
The installed Phone Core v1 remains unchanged until the consolidated RC1 device test.

## RC1 candidate
- Version: `1.14.0-rc1-bootstrap`
- Version code: `37`
- Candidate commit: `6c54bad5bee89a53820ab562fe8b29d282ad95d8`
- Bootstrap run: `34983039857`
- Bootstrap artifact: `10402123479`
- Artifact ZIP digest: `sha256:3a50520383ca3ea104b15a9324c21ed169c797903f0e23c0c5b4daee24ef525e`
- APK size: `58890204` bytes
- APK SHA256: `68a8df5a966259e2b570a0d36af379e07d6e3aad34f8455f6837c862be0797bf`

## Internally proven
- [x] Integrated home-screen widget is part of the existing ARIANA app.
- [x] Widget publishes Core, dialog and Wakeword state without redraw spam.
- [x] Widget tap opens the existing ARIANA activity.
- [x] Widget `SPRECHEN` routes into the existing Conversation Mode.
- [x] Ariana master portrait is rendered in the widget through a binder-safe scaled bitmap.
- [x] Wakeword is explicit opt-in and requires microphone permission.
- [x] Wakeword foreground service declares microphone service type.
- [x] Wakeword and Conversation Mode are mutually paused to avoid dual recognizers.
- [x] Wakeword resumes after dialogue/manual TALK return paths when still enabled.
- [x] STOP ALL and MASTER OFF disable Wakeword.
- [x] Wakeword policy rejects `Ariana` alone and a phrase without the name.
- [x] Android Debug smoke/security/build/signing/upload gate passes.
- [x] Bootstrap unit/manifest/build/signing/upload gate passes.
- [x] Sideload build passes.

## Required device E2E before promotion
- [ ] Widget can be added from the Samsung home-screen widget picker.
- [ ] Widget shows current Core, dialog and Wakeword state after app use.
- [ ] Widget tap opens ARIANA without duplicate activities.
- [ ] Widget `SPRECHEN` starts Conversation Mode from an explicit user tap.
- [ ] Wakeword can be switched ON/OFF visibly in the app.
- [ ] Foreground notification remains visible while Wakeword is active.
- [ ] `Ariana, rede mit mir` starts Conversation Mode while ARIANA is foregrounded.
- [ ] TALK and Conversation Mode do not cause recognizer-busy loops.
- [ ] `Gespräch beenden` returns to READY and Wakeword resumes when enabled.
- [ ] STOP ALL and MASTER OFF stop Wakeword and its microphone foreground service.
- [ ] Widget and conversation history still behave correctly after activity restart.

## Promotion rule
Install and test only this consolidated, stably signed Code-37 Bootstrap candidate.
Do not call Presence v2 finished until every required device E2E checkbox passes.
No additional APK churn unless a failed device gate requires a consolidated fix.
