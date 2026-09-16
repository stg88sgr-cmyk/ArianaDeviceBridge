# Ariana Universal Bridge V2 · Persistent AI Provider Recovery

Date: 2026-09-16

## Stable state

Provider recovery metadata now survives Android app/process restarts.

Persisted per provider:
- provider id
- consecutive transient failure count
- wall-clock cooldown deadline
- last transient error code

Not persisted:
- prompts
- replies
- API keys or credentials
- half-open request-in-flight state

## Runtime restore

`SnowworksApp.onCreate()` initializes `AiProviderHealth` before dialogue/provider activation.

On restore, the persisted wall-clock cooldown deadline is converted back into the process-local monotonic clock domain. If the cooldown already elapsed while the app was stopped, the provider becomes recovery-probe-ready rather than silently resetting to healthy.

## Self-healing semantics

- transient failures increment the persistent counter
- threshold opens the circuit
- process restart does not erase the cooldown
- after cooldown exactly one half-open probe is allowed
- success removes persistent health state
- failed recovery probe reopens the circuit and persists the new cooldown
- non-transient configuration/auth errors do not poison the circuit and clear stale transient state

## Security

This health persistence contains no conversation content and no credentials. Provider profile secrets remain in their separate encrypted provider stores.
