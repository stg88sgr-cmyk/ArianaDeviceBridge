# Checkpoint · AI Provider Health + Router Telemetry · 2026-09-16

## Scope

This checkpoint adds read-only health visibility for Ariana Universal Bridge V2 multi-AI routing.

## Added

- `AiHealthReporter`
  - combines the active local provider id
  - reads encrypted Meta/Claude provider presence via `CloudProviderRegistry`
  - reads in-process remote circuit-breaker state from `AiProviderHealth`
  - reads last lightweight route state from `AiRouteStateStore`
  - performs no network request and stores no prompts/replies/credentials
- `X88HealthActivity`
  - adds `AI ROUTER HEALTH` section
  - shows LOCAL active provider id
  - shows CLAUDE configuration/model/circuit/failure state
  - shows META configuration/model/circuit/failure state
  - shows last selected engine, task class and fallback state
  - shows timestamp of the last route
  - adds direct navigation to the AI provider console

## Health meanings

- `READY`: configured and no tracked transient failures
- `ATTENTION`: transient failure count exists but circuit is still closed
- `CIRCUIT OPEN`: provider temporarily blocked by the circuit breaker
- `HALF-OPEN PROBE`: cooldown expired and one recovery probe is in flight
- `NICHT KONFIGURIERT`: no encrypted provider profile exists for that engine

## Security

Health reporting is read-only. It does not probe providers, expose API keys or persist prompt/reply content.
