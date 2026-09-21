# X88 V38 Health Monitor

V38 adds a central, read-only health monitor over the V32/V36/V37 runtime.

## Components

- neuro runtime health gate
- Inneres-Werden persistence health
- Evidence Engine registration and record count

## Status model

- GREEN: required component is healthy
- DEGRADED: component is usable but reports a non-fatal health issue
- BLOCKED: a required component is unavailable

The overall state uses the strictest component state.

## Safety

The monitor is observational only. It does not grant Android permissions, choose AI providers, or execute device actions.

## Verification

V38 remains IN TEST until CI passes on the stabilization branch.

Next stage: V39 Recovery.
