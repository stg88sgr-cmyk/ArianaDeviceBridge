# Ariana Universal Bridge V2 checkpoint

Date: 2026-09-16
Section: Self-healing AI provider recovery

## Implemented

- SmartAiRouter now uses ordered fallback chains:
  - code / architecture: Claude -> Meta -> Local
  - second opinion: Meta -> Claude -> Local
  - private / local-only policy: Local only
- AiProviderHealth keeps exactly one half-open recovery probe after cooldown.
- Claude provider failures now participate in transient circuit-breaker health.
- Health snapshots expose recovery-probe readiness and remaining cooldown.
- A successful half-open probe resets provider health.
- A failed half-open probe reopens the circuit immediately.
- Added JVM tests covering open circuit, single recovery probe, failed probe reopen, and successful recovery reset.

## Safety / privacy

- Recovery probes occur only when a normal routed request needs that provider.
- No background cloud polling was added.
- CloudAiPolicy remains authoritative and LOCAL_ONLY still prevents cloud routing.
- Provider credentials remain in the encrypted registry.

## Next logical section

Persist lightweight recovery counters across process restarts or add a visible recovery badge to Home/Health without storing prompts or replies.
