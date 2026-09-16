# Checkpoint · Router Success Metrics · 2026-09-16

## Scope

Added lightweight aggregate routing metrics for Ariana's Smart AI Router.

## Metrics

- total published routes
- LOCAL route count
- CLAUDE route count
- META route count
- MULTI route count
- fallback count and fallback percentage
- error count and error percentage

## Privacy / persistence

The metrics store contains counters only. It never stores prompts, replies, API keys, credentials, or message bodies.

## Integration

`AiRouteStateStore.publish(...)` records exactly one aggregate metric sample per completed published routing result. Internal provider attempts are not counted as separate user routes.

`X88HealthActivity` exposes a `ROUTER METRICS` block alongside current route state, fallback reason, route history, provider health, and recovery supervisor telemetry.

## Tests

`AiRouterMetricsStoreTest` covers percentage behavior including the zero-total case and expected ratios.
