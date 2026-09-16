# Checkpoint · Provider quality time windows

Date: 2026-09-16

## Added

- `AiProviderQualityTrendStore`
- Bounded hourly provider-quality buckets retained for the recent 7-day window.
- Separate Claude and Meta trend snapshots for 24h and 7d.
- Counters: selected, executed, successes, errors, circuit-rejected, fallback selections.
- Derived rates: success, error, fallback share, execution rate.
- Health panel now displays Claude/Meta 24h and 7d quality rows.
- Unit tests cover hour bucketing, cutoff aggregation and percentage denominators.

## Privacy / scope

No prompts, replies, provider payloads, model content or credentials are stored. These are aggregate technical counters only.

## Safety

Circuit rejections remain separate from provider execution errors so an open circuit does not degrade provider response quality metrics. No adaptive provider weighting or automatic provider disabling is introduced in this checkpoint.
