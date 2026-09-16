# Ariana Universal Bridge V2 — Smart Dialogue Routing + Provider Status UI

Date: 2026-09-16

## Integrated
- Normal `DialogueRouter.generate(...)` traffic now enters `SmartAiRouter` after app initialization.
- Explicit `/meta`, `/parallel`, `/review`, `/consensus`, `/repair` commands still keep priority.
- `DialogueRouter.generateActiveProvider(...)` is the non-recursive local/private path used by `SmartAiRouter`.
- Cloud policy remains authoritative before any Claude or Meta call.
- Code/architecture/debug tasks route to Claude when configured.
- Second-opinion tasks route to Meta when configured, then Claude/local fallback.
- Private/local/general tasks stay on Ariana's active local provider unless policy/routing says otherwise.
- `AiRouteStateStore` persists only route metadata: engine, task class, fallback flag, timestamp. No prompt, reply, or credential is stored.
- Ariana Presence Widget now displays the last selected engine as `AI · LOCAL`, `AI · CLAUDE`, `AI · META`, or `AI · MULTI` and marks fallback use.

## Safety / architecture invariants
- Ariana remains the visible identity/orchestration layer.
- Device actions are not granted by AI routing.
- Sensitive cloud-blocked content remains local.
- Provider credentials remain in encrypted provider stores.
- Smart routing cannot recursively call itself through the local provider path.

## Next
- Add full provider profile controls for simultaneous Meta + Claude configuration in settings UI.
- Add provider status to the main Home status surface, not only the Presence Widget.
- Expand route tests to cover runtime provider selection and fallback behavior.
