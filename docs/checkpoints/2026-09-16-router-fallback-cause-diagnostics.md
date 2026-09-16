# Ariana Universal Bridge V2 — Router fallback-cause diagnostics

Date: 2026-09-16

## Stable additions

- SmartAiRouter.Result now carries `fallbackReason` as technical metadata only.
- Code/architecture route records the failed or unavailable path across CLAUDE -> META -> LOCAL.
- Second-opinion route records the failed or unavailable path across META -> CLAUDE -> LOCAL.
- Reasons use internal status/error codes only, for example `CLAUDE_CIRCUIT_OPEN > META_NOT_CONFIGURED`.
- AiRouteStateStore persists the current fallback cause and includes it in bounded route history.
- Route history remains capped at 12 entries.
- X88HealthActivity displays the current fallback cause and cause metadata for recent routing transitions.
- No prompt text, response text, API keys, credentials or provider secrets are stored in route diagnostics.
- Tests cover fallback-reason transition detection and duplicate suppression.

## Safety / privacy invariants

- CloudAiPolicy remains authoritative for local-only content.
- Device actions remain outside cloud provider control and continue through existing action policy/approval layers.
- Diagnostic persistence contains only engine, task class, fallback flag, fallback cause code and timestamp.
