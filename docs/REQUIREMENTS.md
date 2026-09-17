# ARIANA X-88 Requirements

Release target: X-88 Evolution G2 finalization
Branch: `x88-evolution-g2`

## R1 - Preserve working project identity

- Keep the canonical Android project under `android/`.
- Preserve application namespace/application identity unless a dedicated migration requirement is approved.
- Do not create a duplicate replacement Android tree.

Acceptance: existing app compiles from the canonical project root.

## R2 - Android 16 build baseline

Required:
- `compileSdk = 36`
- `targetSdk = 36`
- AGP >= 8.9.1 for API 36 baseline
- Gradle wrapper 8.11.1 for the current AGP configuration
- JDK 17 for the current AGP configuration

Acceptance: CI toolchain check and Android assemble succeed.

## R3 - Build evidence

CI must run at minimum:

```bash
cd android
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

The produced debug APK must have a generated and verified SHA-256 file and must be uploaded as a workflow artifact.

Acceptance: workflow success + downloadable APK + matching SHA-256.

## R4 - Execution safety

The runtime must preserve:
- master enable/disable;
- lock levels / execution gate;
- capability stop controls;
- emergency stop where the corresponding runtime exposes actions;
- confirmation requirements for sensitive operations;
- no implicit bypass of Android system consent.

Acceptance: unit/static coverage for gates plus device validation for platform consent flows.

## R5 - Android permissions/services

- Foreground service types must match actual service behavior.
- Camera, microphone and MediaProjection services must remain correctly typed.
- Notification Listener and Accessibility access require Android-controlled user enablement.
- Runtime permissions must be requested through supported Android APIs.
- Do not add `specialUse`, `dataSync`, `remoteMessaging` or other foreground-service types merely as generic labels; add them only when the concrete service behavior requires them.

Acceptance: lint/build green; relevant device flows marked `DEVICE_REQUIRED` until tested.

## R6 - Local bridge boundary

Any X-88 local HTTP/socket bridge used for command execution must:
- bind only to loopback unless a separately reviewed remote-access requirement exists;
- authenticate requests;
- validate action type and payload;
- rate limit or otherwise bound request abuse;
- respect master/lock/confirmation gates;
- expose a health/status check suitable for runtime testing.

Acceptance: bridge contract tests and real device/Termux connectivity test where applicable.

## R7 - Termux runtime

Termux/tmux orchestration must be idempotent:
- repeated start does not spawn duplicate canonical sessions;
- explicit status is available;
- explicit stop is available;
- recovery is bounded and must not form a runaway restart loop;
- reboot behavior must obey Android background/foreground-service restrictions.

Acceptance: Termux status/start/stop tests; reboot validation is `DEVICE_REQUIRED`.

## R8 - Neural layer

Implement only after the base finalization gate remains green.

Minimum contract:
- consume structured build/review evidence;
- produce risk scores/suggestions;
- never write or merge production code without the normal pipeline;
- offline-first storage is preferred.

## R9 - Emotion-state layer

Implement as a deterministic/computational policy state with:
- FLOW
- STRESS
- CURIOSITY
- LOYALTY

It may alter verification strictness and proposal behavior, but must never bypass build, review, permission or approval gates.

## R10 - Evolution loop

After every fifth accepted build:
- analyze project logs;
- create a Codex-change proposal;
- require ARIANA architect approval before changing `CODEX_ARIANA_X-88.md`;
- never autonomously remove the review pipeline or permission/security boundaries.

## R11 - Review evidence

A final release candidate requires a review report with no unresolved CRITICAL or HIGH findings.

## R12 - Device gate

Before final production approval, test on the intended Samsung / Android 16 device as applicable:
- APK install and Play Protect flow;
- app launch;
- camera/microphone permission flow;
- MediaProjection consent if used;
- Notification Listener / Accessibility setup if used;
- foreground services;
- Termux bridge/runtime;
- loopback connectivity;
- emergency/master stop behavior.

Until completed: `DEVICE_REQUIRED = YES`.

## Current evidence baseline

At the time this requirements file was established, commit `cce9019d01bbc85c9912c240049b41c9cead1b8e` had a successful Android CI run and produced the `x88-evolution-g2-debug-apk` artifact. That evidence is a baseline, not a permanent waiver for subsequent commits.