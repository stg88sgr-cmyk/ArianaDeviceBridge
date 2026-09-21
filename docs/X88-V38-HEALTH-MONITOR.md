# X-88 V38 Health Monitor

V38 introduces a small, deterministic health aggregation layer.

Components publish one health check keyed by component name. The monitor aggregates them:
- HEALTHY when all registered checks are healthy
- DEGRADED when at least one check is degraded and none is unavailable
- UNAVAILABLE when any check is unavailable

An empty monitor is not considered healthy.

V38 is deliberately observational. It does not execute device actions, grant permissions, or repair components automatically.
