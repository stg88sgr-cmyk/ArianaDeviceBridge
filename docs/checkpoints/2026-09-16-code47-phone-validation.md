# Code 47 Phone Validation

Date: 2026-09-16
Branch: `feature/sensor-lifecycle-hardening`
Validated build: `1.14.7-rc1-bootstrap` / versionCode 47
Base head used for build: `b3d92eec31c3b1bddb3d34925ce490933f7ca5b6`

## Change set

- Added process-wide voice shutdown coordination for active `ArianaVoiceController` instances.
- Hardened delayed SpeechRecognizer retry so a stale retry cannot recreate a recognizer after stop/master-off.
- Hardened microphone capture teardown so `AudioRecord` is stopped and released deterministically.
- Preserved wakeword/capture separation while making STOP ALL and MASTER OFF close both paths.

## CI

All relevant variants passed after the final dependency correction:

- Android Bootstrap APK: success
- Android Debug APK: success
- Android Sideload APK: success
- Android Safe Install APK: success
- Android Reduced Permission APK: success

Bootstrap artifact: `snowworks-ariana-bootstrap`
Workflow run: `35090017869`
Artifact id: `10444425090`
Artifact digest: `sha256:28597fc8845fd5e27ecc4ac63fed02647f975b2b6121c38bb0b437444152e84a`
Installed APK SHA-256: `204e5503ef2954534aad2258d6251b7583b733cf0275fa6ce7cf91beac3883be`

## Phone test result

User confirmed the first microphone lifecycle hardening test works on-device:

1. MASTER ON
2. WAKEWORD ON
3. Android microphone privacy indicator becomes active
4. WAKEWORD OFF
5. Microphone privacy indicator clears and does not reappear

Result: PASS

## Next validation

Run the harder teardown case:

1. MASTER ON
2. WAKEWORD ON and wait until microphone privacy indicator is active
3. Switch MASTER OFF while wakeword is still listening
4. Verify microphone indicator clears promptly and does not return
5. Verify wakeword remains disabled and no capture session remains active

Do not merge this branch until the harder master-off teardown case is phone-validated.
