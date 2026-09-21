# X88 V37 Evidence Engine

V37 adds a bounded, machine-readable evidence layer over the stabilized V32/V36 runtime.

## Evidence captured

- runtime boot and health events
- integrity signals
- training/result signals
- selected internal telemetry

Each record contains a deterministic SHA-256 digest and a compact identifier.

## Safety boundary

The engine is observational. It has no output channels and cannot issue Android/device actions.

Evidence is deliberately compact. It does not persist prompts, replies, credentials, permissions, or raw conversation text.

## Verification

V37 remains **IN TEST** until Android/neuro CI passes for the stabilization branch.

Next stage: V38 central X88 Health Monitor.
