# Ariana Universal Bridge V2 — Automatic AI Background Recovery

Date: 2026-09-16

## Added

- `AiProviderRecoverySupervisor`
  - process-local daemon scheduler
  - no WakeLock, foreground service or new Android permission
  - checks every 30 seconds
  - only probes already configured providers that are `RECOVERY READY`
  - separate five-minute minimum interval per Claude/Meta automatic probe
  - persists the automatic-probe timestamp before network I/O to prevent retry storms after process death
  - does not change `DialogueRouter`'s active provider
- `SnowworksApp`
  - starts the recovery supervisor after provider initialization
- `AiProviderRecoverySupervisorTest`
  - first probe allowed
  - recent probe rate-limited
  - boundary allowed
  - wall-clock rollback does not permanently suppress recovery

## Safety invariants

- No prompts or normal conversation replies are stored by recovery supervision.
- No provider is probed while its circuit is still open/cooling down.
- `AiProviderHealth.acquireAttempt()` remains authoritative and prevents simultaneous half-open probes.
- Automatic recovery cannot force-enable an unconfigured provider.
- Automatic recovery does not switch Ariana's active conversation provider.
- If Android kills the app process, this lightweight supervisor stops; persisted circuit state remains for the next app start.

## Routing remains

- Code/architecture: Claude → Meta → Local
- Second opinion: Meta → Claude → Local
- Private/local-only: Local only

## Next candidate section

Expose the supervisor status in Health UI: running state, last automatic probe per provider, and last automatic recovery result without storing response bodies.
