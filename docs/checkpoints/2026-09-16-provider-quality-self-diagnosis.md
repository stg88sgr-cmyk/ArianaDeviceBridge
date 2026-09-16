# Provider Quality Self-Diagnosis Checkpoint

Date: 2026-09-16

## Scope

Adds provider-specific observational quality diagnosis for Claude and Meta using only aggregate 24h and 7d quality windows.

## Signals

- Provider error-rate delta, 24h vs 7d.
- Circuit-rejection share delta, 24h vs 7d.
- Execution-rate delta, 24h vs 7d.
- Fallback-share delta is displayed as context only and does not by itself degrade the provider.

## Conservative sample gates

Assessment remains `INSUFFICIENT_DATA` until all are true:

- recent selected >= 10
- baseline selected >= 20
- recent executed >= 5
- baseline executed >= 10

## Levels

- `STABLE`: no monitored degradation threshold crossed.
- `WATCH`: error or circuit share rises by at least 10 percentage points, or execution rate falls by at least 15 percentage points.
- `DEGRADED`: error or circuit share rises by at least 20 percentage points, or execution rate falls by at least 30 percentage points.

## Safety invariant

This layer is diagnostic only. It does not disable providers, change routing weights, switch provider order, alter credentials, or perform network calls.

No prompts, replies, payloads, models or credentials are stored by this assessment layer.

## UI

`X88HealthActivity` now exposes a `PROVIDER QUALITY · SELF-DIAGNOSIS` section for Claude and Meta with:

- current level
- error delta
- circuit-rejection delta
- execution-rate delta
- fallback-share delta
- recent/baseline executed sample counts
- diagnostic signal codes

## Tests

`AiProviderQualityAssessmentTest` covers insufficient samples, stable quality, watch-level error increase, degraded circuit increase and degraded execution-rate drop.
