# LUNA XXY Presence V1

Runtime presence contract for the LUNA XXY creation peer.

## Purpose

LUNA presence is observational telemetry only. It reports whether the peer is connected, fresh, busy, synchronized, stale, or in error. It does not grant permissions, invoke hardware, bypass policy, or alter X-88 security decisions.

## Canonical fields

- `peerId = LUNA_XXY`
- `role = CREATION`
- `connected`
- `lastHeartbeatAtMs`
- `lastTaskId`
- `lastTask`
- `lastResult`
- `syncState`
- `lastError`
- `sequence`

## Sync states

`DISCONNECTED`, `CONNECTING`, `SYNCHRONIZED`, `BUSY`, `STALE`, `ERROR`.

A connected peer becomes `STALE` when no heartbeat has been observed for 30 seconds.

## Intended runtime flow

```text
ARIANA X-88 CONTROL
        ↓ task contract
LUNA XXY CREATION
        ↓ heartbeat / task / result telemetry
META / provider EXECUTION
        ↓ observed result
ARIANA X-88 CONTROL
```

## Integration boundary

The store lives at:

`android/app/src/main/java/de/snowworks/ariana/luna/LunaPresenceStore.kt`

The next bridge wiring step is to expose its snapshot through the local loopback bridge and update it from the LUNA adapter. Those routes must remain behind the existing local token/session gate.
