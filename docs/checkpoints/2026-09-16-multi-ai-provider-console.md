# ARIANA Universal Bridge V2 – Multi-AI Provider Console

Date: 2026-09-16

## Persistent state

- Ariana remains the visible identity and orchestration layer.
- SmartAiRouter remains the automatic dialogue routing layer.
- CloudProviderRegistry stores Meta and Claude independently with Android Keystore AES/GCM.
- Claude is the preferred cloud specialist for code, architecture, debugging and technical analysis.
- Meta is the preferred second-opinion / alternative-reasoning provider.
- Local Ariana remains the default for ordinary, private and device-adjacent dialogue.
- CloudAiPolicy remains authoritative and can force LOCAL_ONLY.

## UI completed in this checkpoint

AiProviderSettingsActivity now exposes two independent provider sections:

### Claude
- independent endpoint
- independent model ID
- independent encrypted API key
- save
- connection test
- remove profile

### Meta
- independent endpoint
- independent model ID
- independent encrypted API key
- save
- connection test
- remove profile

Keys are never re-rendered after storage. Leaving a key field empty preserves the existing encrypted key.

## Route visibility

AiRouteStateStore persists lightweight route status only:
- LOCAL / CLAUDE / META / MULTI
- task class
- fallback flag
- updated timestamp

Prompts, replies and credentials are not stored in route state.

The presence widget already consumes this route status. The next UI integration target is the same engine badge inside X88HomeActivity without changing routing semantics.

## Recovery

The previous single-provider SecureAiProviderStore remains for backwards compatibility and migration. CloudProviderRegistry is now the canonical storage for simultaneous Meta + Claude profiles used by SmartAiRouter.
