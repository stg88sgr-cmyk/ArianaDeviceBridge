# Ariana X-88 checkpoint — Router trend self-diagnosis

Date: 2026-09-16

## Added
- `AiRouterTrendAssessment` compares recent 24h aggregate routing behaviour against the 7d aggregate baseline.
- Levels: `INSUFFICIENT_DATA`, `STABLE`, `WATCH`, `DEGRADED`.
- Conservative sample gates: at least 10 recent routes and 20 baseline routes before raising trend signals.
- `WATCH` at >=10 percentage-point increase in error or fallback rate.
- `DEGRADED` at >=20 percentage-point increase in error or fallback rate.
- Health UI shows level, signed error/fallback deltas, sample counts and diagnostic signal codes.
- The assessment is read-only. It does not disable, switch or quarantine providers automatically.

## Privacy
Only aggregated router counters are used. No prompts, replies, provider response bodies or credentials are stored by this layer.

## Tests
`AiRouterTrendAssessmentTest` covers insufficient sample handling, stable small changes, WATCH threshold and DEGRADED threshold.

## Next candidate
Add provider-specific reliability scoring from technical outcomes only, but keep any automatic routing weight changes gated behind sufficient samples and existing circuit-breaker safety.
