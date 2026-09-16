# Ariana Universal Bridge V2 — Provider Lifecycle + Router History

Date: 2026-09-16

## Completed

- Added compact persistent route transition history to `AiRouteStateStore`.
- History stores metadata only: engine, task class, fallback flag, timestamp.
- No prompts, replies, credentials, API keys, message contents or device data are stored.
- History is bounded to 12 transitions.
- `X88HealthActivity` shows the 6 most recent transitions in reverse chronological order.
- Existing current-route status and recovery supervisor telemetry remain intact.

## Provider lifecycle hygiene

- `AiProviderRecoverySupervisor.clearTelemetry(context, slot)` exists for per-provider telemetry cleanup.
- Attempted to wire provider-registry clear to circuit + telemetry cleanup, but the repository write interface blocked that registry modification. The existing provider delete behavior remains unchanged rather than forcing an uncertain write.

## Safety invariants

- Ariana remains the user-facing identity/controller.
- Cloud provider routing remains subordinate to `CloudAiPolicy`.
- Recovery/routing diagnostics never persist conversation content or credentials.
- Device actions remain outside the AI routing layer and continue through existing action policy / approval controls.

## Next target

- Add focused tests for bounded route history and state-transition deduplication.
- Revisit lifecycle cleanup only through a safe writable path.
