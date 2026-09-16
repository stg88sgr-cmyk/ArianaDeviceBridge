# Ariana Universal Bridge V2 checkpoint

Date: 2026-09-16
Section: Provider-specific quality metrics

## Implemented
- Added `AiProviderQualityStore` for Claude and Meta.
- Metrics are provider-specific and contain counters only.
- Stored dimensions:
  - selected
  - executed network calls
  - successes
  - execution errors
  - circuit-rejected selections
  - fallback selections
- Derived metrics:
  - success rate over executed calls
  - error rate over executed calls
  - fallback share over selections
  - execution rate over selections
- Circuit-open rejection is deliberately separated from provider execution quality.
- SmartAiRouter records provider selection and outcomes at the actual Claude/Meta call sites.
- X88 Health panel shows a `PROVIDER QUALITY · LIFETIME` section.
- Added JVM tests for zero-total handling and ratio calculations.

## Privacy / safety
- No prompts, replies, provider payloads, model responses or credentials are stored.
- No provider weighting or automatic disabling is enabled by this section.
- Metrics remain diagnostic only.

## Next safe step
- Add recent provider-specific time windows before considering any adaptive routing policy.
