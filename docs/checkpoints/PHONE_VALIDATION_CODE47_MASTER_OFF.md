# Ariana Code 47 phone validation: MASTER OFF sensor release

Date: 2026-09-16
Branch: `feature/sensor-lifecycle-hardening`
Build: `1.14.7-rc1-bootstrap` (versionCode 47)

## Real-device validation

User-confirmed on the physical phone:

1. MASTER ON
2. WAKEWORD ON
3. Android microphone privacy indicator became active
4. MASTER OFF while wakeword was still listening
5. Microphone privacy indicator disappeared and did not return

Earlier on the same Code 47 build, WAKEWORD OFF alone also released the microphone cleanly without the privacy indicator reappearing.

## Result

PASS. Code 47's sensor-lifecycle hardening prevents the delayed SpeechRecognizer retry from resurrecting microphone capture after shutdown and releases the active voice/capture paths on MASTER OFF / STOP ALL.

## Scope

This is real-device behavioral validation. Existing CI for Bootstrap, Debug, Sideload, Safe Install and Reduced Permission was green before the phone validation.
