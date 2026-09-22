# X88 Android V36

Typed Android adapter layer for Ariana/X88.

- Android imports stay inside this module.
- X88 core remains platform-neutral.
- Control policy is deny-by-default.
- Every action request carries actor + reason.
- Execution returns an audit identifier.
- V36 does not dispatch Intents, network calls, WorkManager jobs, or accessibility actions.

This is the boundary for future Samsung/Android capabilities.
