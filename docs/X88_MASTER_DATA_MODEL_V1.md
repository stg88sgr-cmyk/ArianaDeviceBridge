# ARIANA X-88 Master Data Model v1

Status: DRAFT
Date: 2026-09-20
Purpose: define a stable vocabulary for X-88 data before additional implementation.

## Record envelope

Every persistent record should conceptually carry: id, type, createdAt, updatedAt, source, optional confidence, sensitivity, persistence, version and payload.

Secrets themselves must not be stored in ordinary project records. Store only safe references to platform secret stores.

## Domains

Identity: name, persona configuration, presentation mode, supported roles.

Preferences: user-selected voice, UI, language, visual preferences and explicit settings.

Memory: structured facts, decisions, project knowledge, conversation summaries and provenance.

Session: current conversation/session state, active mode, timestamps and safe lifecycle markers.

Inneres Werden: numeric learning/reflection state derived from experience, values, principles, evidence quality, coherence and growth. This is a software model, not a claim of subjective feeling.

Values / Principles: explicit project rules, user-approved constraints, safety principles and architecture invariants.

Capability: capability identifier, state, permission status, policy, last transition and owning component.

Provider: provider identifier, adapter type, availability, health, configuration reference and policy status. Never treat a provider as the source of Ariana identity.

Device: Android package/runtime state, app version, build evidence, supported capabilities and non-sensitive health indicators.

Conversation: turns with role, text metadata, timestamp, provider/model metadata and retention policy.

Media: asset id, type, source, generation metadata, dimensions/duration, checksum and storage reference.

Diagnostics: build/test/runtime events with severity, component, timestamp and sanitized details.

Approval / Audit: proposal id, action class, exact reviewed payload fingerprint, user decision, timestamp and expiry. Approval must remain bound to the reviewed payload.

Release Evidence: commit, branch, workflow/run, artifact, SHA-256, test/lint/build results, review status and device evidence.

## Persistence rules

- Transient runtime state must not automatically become long-term memory.
- Long-term memory requires an explicit storage path and provenance.
- Provider responses should retain provider metadata where needed for reproducibility.
- Secrets, bearer tokens and private signing material are never written into ordinary logs or data records.
- Safety-critical state must fail closed on invalid or missing data.

## Data flow

INPUT -> sanitize/classify -> session state -> optional memory retrieval -> provider/local reasoning -> Inneres Werden evidence update -> response/proposal -> policy gate -> optional external action -> diagnostics + provenance.

## Compatibility rule

New fields are additive by default. Removing or changing the meaning of a persisted field requires a migration plan and regression coverage.
