# Ariana X-88 · AI Recovery Supervisor Telemetry

Date: 2026-09-16

## Implemented
- `AiProviderRecoverySupervisor.status(context)` exposes process-local supervisor state.
- Per-provider telemetry for Claude and Meta now includes:
  - last automatic probe attempt
  - last successful automatic probe
  - last automatic probe error code
  - next earliest automatic probe time
- Telemetry stores metadata only. No prompts, replies, or credentials are written.
- `X88HealthActivity` shows a dedicated `AI RECOVERY SUPERVISOR` block.
- Health shows RUNNING/STOPPED, tick cadence, minimum retry gap, and per-provider timestamps/errors.
- Automatic recovery remains process-local, permission-neutral, and rate-limited.
- Added JVM tests for next-eligible scheduling, rate-limit boundary, first-use eligibility, and wall-clock rollback handling.

## Invariants
- Circuit breaker remains authoritative.
- Automatic probes do not switch the active Ariana dialogue provider.
- No wake lock, foreground service, or new Android permission.
- No prompt or response body persistence.
- Persistent provider health survives process restart; in-flight network requests do not.

## Next stabilization
- Clear obsolete supervisor/circuit metadata when a provider profile is explicitly removed.
- Verify CI and fix any compile/test regression before further expansion.
