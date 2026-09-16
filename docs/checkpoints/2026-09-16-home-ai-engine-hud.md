# Ariana Universal Bridge V2 checkpoint — Home AI Engine HUD

Date: 2026-09-16

## Completed
- Added `HomeAiEngineIndicator` to the Android app.
- Shows the most recently routed engine directly in Ariana Home as `AI · LOCAL`, `AI · CLAUDE`, `AI · META`, or `AI · MULTI`.
- Shows `FALLBACK` when SmartAiRouter had to fall back.
- Indicator reads only `AiRouteStateStore`; it does not store or display prompts, replies, tokens, or credentials.
- Tapping the indicator opens `AiProviderSettingsActivity`.
- `SnowworksApp` now attaches/detaches the indicator through `ActivityLifecycleCallbacks` for `X88HomeActivity`.
- The existing X88HomeActivity implementation remains untouched, reducing regression risk in the large home/voice/device-control surface.

## Architecture state
Normal dialogue -> `DialogueRouter` -> `SmartAiRouter` -> Local / Claude / Meta -> `AiRouteStateStore` -> Home AI Engine HUD + Presence Widget.

## Next logical section
Add explicit provider health/availability badges (Local, Claude, Meta) and expose SmartAiRouter decisions in the Health screen without storing prompt content.
