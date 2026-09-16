# Universal Bridge V2 - Smart Multi-AI Routing Checkpoint

Date: 2026-09-16

## Implemented

- Native Claude Anthropic Messages adapter remains integrated.
- Added encrypted `CloudProviderRegistry` with independent persistent slots for Meta and Claude.
- Existing active provider configuration remains backward compatible.
- `AiProviderManager.configure()` now remembers provider-specific cloud profiles automatically.
- Recovery restoration also refreshes the provider registry.
- Added `SmartAiRouter` with role-aware routing:
  - sensitive/private/offline requests -> local Ariana provider
  - code/architecture/debugging -> Claude when configured
  - second-opinion/alternative reasoning -> Meta when configured
  - ordinary dialogue -> local Ariana provider
- Cloud policy remains authoritative and can force local-only routing.
- Health/circuit-breaker state is applied independently to Meta and Claude calls.
- Automatic fallback returns to local Ariana when a cloud specialist is unavailable.
- Added unit coverage for task classification.

## Architecture invariant

Ariana remains the user-facing identity and orchestration layer. Meta and Claude are external specialist providers. Provider replies do not imply device actions; device actions continue to require bridge-side execution and policy checks.

## Persistence behavior

Saving Meta or Claude through the provider settings stores the selected configuration in the legacy active slot and also records the provider in its encrypted named registry slot. This lets both cloud providers coexist after each has been configured at least once.

## Next recommended section

Expose smart-routing status and provider-slot readiness in the Android UI, then route selected dialogue intents through `SmartAiRouter.route()` so automatic specialist selection becomes available from normal Ariana conversation flow.
