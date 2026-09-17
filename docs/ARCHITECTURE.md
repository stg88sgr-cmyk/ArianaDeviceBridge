# ARIANA X-88 Architecture

Status: canonical for `x88-evolution-g2`

## System layers

```text
Android / One UI
    |
    +-- Snowworks / ARIANA X-88 Android app
    |     +-- X88 Core
    |     +-- Capability Broker
    |     +-- Execution / Lock Gates
    |     +-- UI + Device Grants
    |     +-- Capture / Wakeword services
    |     +-- AI Provider Router
    |
    +-- Local bridge boundary
    |     +-- loopback-only interfaces where applicable
    |     +-- authenticated actions
    |     +-- validation / rate limits / audit
    |
    +-- Termux runtime
    |     +-- idempotent tmux workers
    |     +-- build / logs / adapters
    |
    +-- Luna experimental layer
          +-- isolated mutations
          +-- no direct production promotion
```

## Governance pipeline

```text
ARIANA ARCHITECT
  -> REQUIREMENTS
  -> CODEX BUILD
  -> REAL REPOSITORY
  -> UNIT + LINT + ASSEMBLE
  -> REVIEW
  -> REPAIR if required
  -> FINAL BUILD
  -> DEVICE gates where required
  -> ARIANA APPROVAL
```

## Current toolchain

- Android API / compileSdk: 36
- targetSdk: 36
- Android Gradle Plugin: 8.9.1
- Gradle wrapper: 8.11.1
- JDK: 17
- Android project root: `android/`

## Invariants

1. The repository is the source of truth.
2. Existing working architecture is extended, not replaced by a second shadow tree.
3. Build claims require CI/local evidence.
4. Device-only gates are marked `DEVICE_REQUIRED` until actually exercised.
5. Android runtime permissions and system approval surfaces remain user/platform controlled.
6. Local bridge exposure must be minimal and authenticated.
7. Emergency stop, master disable and execution gates are fail-closed.
8. Experimental mutations cannot bypass production review gates.
9. Neural/emotion layers may influence proposals and risk policy but cannot silently modify production code.
10. Emotion state is software telemetry, not a claim of biological feeling.

## Future extension points

- `core-neural`: structured build/review learning and bug-risk estimates.
- `core-emotion`: deterministic state model for FLOW/STRESS/CURIOSITY/LOYALTY.
- `core-memory`: durable project memory if separately approved.
- deeper Binder/service boundaries only when supported by a concrete Android deployment model.

## Evidence model

Every accepted build records:
- commit SHA;
- CI run result;
- unit/lint status;
- APK path;
- APK SHA-256;
- review status;
- device test status;
- unresolved blockers.