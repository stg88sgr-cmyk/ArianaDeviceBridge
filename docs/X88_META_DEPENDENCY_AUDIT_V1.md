# X-88 Meta Dependency Audit v1

## Scope

Audit the current Presence v2 / multi-AI implementation for concrete Meta-specific coupling before changing runtime behavior.

## Findings

### 1. No Meta SDK dependency was found in the inspected Gradle configuration

`android/app/build.gradle.kts` declares AndroidX, Material, Lifecycle and Google MediaPipe GenAI dependencies. No Meta/Facebook SDK artifact is declared in that file.

This is evidence against a direct compile-time Meta SDK dependency, not proof that the repository contains no Meta integration.

### 2. Meta is currently coupled at the application/provider-routing layer

`android/app/src/main/java/de/snowworks/ariana/bridge/MultiAiRouter.kt` contains Meta-specific behavior and naming:

- `Mode.META`
- `metaProviderId`
- `callMeta(...)`
- `META_TIMEOUT_MS`
- `remoteProviderId(...)` returning an ID beginning with `meta-ai:`
- comments describing the configured remote provider as a Meta Model API / Muse Spark provider.

The implementation actually calls the generic `HttpsDialogueProvider`, rather than a Meta SDK.

### 3. Meta endpoint/model are hard-coded in the settings UI

`android/app/src/main/java/de/snowworks/app/ui/AiProviderSettingsActivity.kt` contains the Meta endpoint `https://api.meta.ai/v1/chat/completions`, model `muse-spark-1.3`, and Meta-specific preset/test labels.

This is the clearest concrete provider lock-in currently observed.

### 4. The stored HTTPS provider configuration is shared

`SecureAiProviderStore.Config` stores endpoint, model and API key. `AiProviderManager` uses that configuration for the active HTTPS provider, while `MultiAiRouter.callMeta(...)` also consumes it as the Meta reviewer configuration.

This means the data model does not yet express the intended distinction between Ariana primary provider and optional external reviewer.

### 5. Local Ariana already has an independent activation path

`SnowworksApp` first attempts `LoopbackArianaProviderManager.activateIfAvailable()`, then `LocalAiProviderManager.activateConfigured(this)`, and only then `AiProviderManager.activateConfigured(this)`. Therefore the inspected boot path already prefers local Ariana and does not require Meta to boot the core.

### 6. Presence itself is not shown to require Meta

The inspected Presence v2 release gate describes widget, wakeword, conversation mode, microphone foreground service, STOP ALL and MASTER OFF behavior. Those capabilities are Android-local and are not represented as Meta SDK calls.

## Dependency map

Current observed flow:

`X88 / Presence` → `DialogueRouter` → local provider when available

Optional multi-AI path:

`DialogueRouter` → `MultiAiRouter` → `callMeta(...)` → shared `SecureAiProviderStore` → generic `HttpsDialogueProvider` → configured HTTPS endpoint

The Meta-specific coupling is concentrated in the optional multi-AI reviewer path and its settings/persistence semantics.

## Recommended next isolation step

Do not remove Meta yet.

Introduce an explicit provider-neutral external-reviewer boundary so that:

- local Ariana remains the primary/core provider;
- external reviewers are represented by an adapter interface;
- Meta is one adapter/configuration, not a special identity inside the router;
- reviewer absence produces a bounded local-only result;
- provider credentials remain isolated from Ariana core state;
- existing DialogueRouter behavior remains compatible during migration.

The change should be implemented as a small, reversible step with focused unit tests before any merge.

## Verification status

This document records repository evidence from the `feature/presence-v2-meta-integration` line. It does not claim that every historical branch or unindexed source file is free of Meta references.

No runtime behavior is changed by this audit document.