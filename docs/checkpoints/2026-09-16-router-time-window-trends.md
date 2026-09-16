# Ariana X-88 checkpoint — Router time-window trends

Date: 2026-09-16

## Added
- `AiRouterTrendStore` with bounded hourly aggregates.
- Rolling diagnostics for the last 24 hours and last 7 days.
- Counts per engine: LOCAL, CLAUDE, META, MULTI.
- Fallback and error rates per window.
- Health UI now shows lifetime metrics plus 24h/7d trends.
- Tests cover hour bucketing, cutoff aggregation, and empty windows.

## Privacy / scope
- No prompts, replies, provider payloads, credentials, or device content are stored.
- Trend storage contains only bounded hourly counters.
- Buckets are trimmed to approximately seven days of history.

## Architecture
`AiRouterMetricsStore.record()` remains the single post-route metrics entry point and forwards each completed routing result to `AiRouterTrendStore.record()`. This keeps lifetime and windowed metrics aligned while avoiding per-provider-attempt inflation.
