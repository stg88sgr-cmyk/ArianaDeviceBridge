# Ariana Universal Bridge V2 — AI Recovery Health Controls
Date: 2026-09-16

## Implemented
- Added `AiProviderRecoveryProbe` for explicit slot-specific Claude/Meta health probes.
- Probes never replace or re-register the active `DialogueRouter` provider.
- Probes respect `AiProviderHealth.acquireAttempt()` and therefore cannot bypass an active cooldown.
- A RECOVERY READY provider may consume exactly one half-open probe attempt.
- Probe success clears provider health state; transient failures update/reopen the circuit through the existing health layer.
- Probe responses are not persisted or shown; only success/error status is surfaced.

## Health UI
`X88HealthActivity` now provides:
- `Claude jetzt testen`
- `Meta jetzt testen`
- explicit states: HEALTHY, ATTENTION, COOLDOWN, RECOVERY PROBE, RECOVERY READY
- remaining cooldown seconds
- failure count and last transient provider error

## Invariant
Manual recovery diagnostics must not interrupt Ariana's active conversation provider or bypass circuit-breaker protection.
