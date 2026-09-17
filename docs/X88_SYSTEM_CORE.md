# X88 System Core

Status: Phase 1 foundation
Branch: `x88-evolution-g2`
Android tree: `android/`

## Goal

X88 is a system-control layer that runs alongside Samsung One UI instead of pretending to be, cloning, or modifying Samsung's update channel. X88 owns its own versioning, capability model, AI routing, and update lifecycle.

## Layer model

```text
Samsung One UI / Android
        |
        +-- normal platform UI and OTA
        |
        +-- X88 UI
             |
             +-- X88 Core
             +-- Capability Broker
             +-- Ariana Control
             +-- Luna Worker
             +-- AI Router
             +-- System Dashboard
             +-- X88 Update Manager
```

## Phase 1 invariants

1. No live `/system` mutation.
2. No dependency on disabling Verified Boot.
3. No blanket SELinux permissive mode.
4. X88 version identity is independent from One UI and Android versions.
5. Existing bridge/runtime code remains usable while deeper system integration is prepared.
6. Device-destructive or irreversible steps are DEVICE_REQUIRED and never part of normal CI.

## Canonical core components

```text
de.snowworks.ariana.system
  X88Version
  X88UpdateChannel
  X88CoreState          (next)
  X88CapabilityBroker   (next)
  X88SessionManager     (next)
  X88EmergencyStop      (next)
  X88UpdateManager      (next)
```

Later system-image integration:

```text
AOSP / custom system image
  X88SystemService
  Binder/AIDL boundary
  privapp permission allowlist
  minimal SELinux domain
  boot integration
```

## Update model

X88 update channels:

- `STABLE`: release builds intended for normal use.
- `BETA`: release candidates and wider testing.
- `DEV`: active development builds.

The X88 updater must verify metadata and artifact integrity before offering installation. Platform OTA and X88 updates remain separate.

## Release gates

A state is not green because source files exist. A green gate requires evidence.

```text
SOURCE        compile/static checks pass
UNIT          unit tests pass
ANDROID_BUILD Gradle Android build succeeds
ARTIFACT      expected APK/artifact exists
HASH          artifact digest recorded
RUNTIME       core starts and reports healthy state
DEVICE        explicit device test completed
```

## Current phase

The first committed slice establishes independent X88 version identity and update channels. The next implementation slice is the state/capability boundary. UI and additional generators remain downstream consumers of this core.
