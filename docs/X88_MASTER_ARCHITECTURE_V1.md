# ARIANA X-88 Master Architecture v1

Status: DRAFT / integration blueprint
Date: 2026-09-20
Purpose: consolidate the existing Ariana/X-88 architecture into one inspectable map without replacing the canonical repository tree.

## 1. System layers

USER / UI -> Ariana conversation, widget, avatar/presence -> explicit approvals and visible state.

CONTROL PLANE -> ArianaStateHolder -> master gate / STOP ALL / quarantine -> capability broker -> action policy / approval queue -> lifecycle and diagnostics.

COGNITIVE / LOCAL STATE -> structured memory -> local session state -> Inneres Werden V32 -> resonance / symbolic state -> future neural suggestion layer.

PROVIDER / INTEGRATION PLANE -> local/loopback providers -> multi-AI router -> external providers as adapters -> X Space gateway -> media/image/video adapters.

DEVICE PLANE -> Android app -> optional Binder-ready system-core scaffold -> permissions, foreground services, widgets -> Samsung/One UI controlled capabilities.

RUNTIME / BUILD -> Gradle wrapper -> Termux runtime -> Luna Guardian -> CI, artifacts, hashes, release gates.

## 2. Canonical repository rule

The existing repository remains the source of truth. Do not create a parallel replacement project merely to represent this architecture.

Canonical roots currently observed on main include android/, docs/, integrations/ and luna-guardian/. Development branches contain additional feature layers. Integration into main remains evidence-gated.

## 3. Master data domains

1. identity
2. preferences
3. memory
4. session
5. learning / Inneres Werden
6. values and principles
7. capability state
8. provider configuration
9. device state
10. conversation
11. media/assets
12. diagnostics
13. approvals/audit
14. release/build evidence

Each persistent item should have a clear owner, lifetime, sensitivity, persistence policy and export/delete policy.

## 4. Authority boundaries

- Android runtime permissions remain controlled by Android.
- External side effects retain policy/approval boundaries where applicable.
- STOP ALL and MASTER OFF remain dominant safety controls.
- Local bridges remain loopback-only unless a reviewed requirement changes that boundary.
- Experimental mutation stays outside main until review and evidence gates pass.
- A scaffold does not constitute a privileged/system claim.
- Device E2E success is never inferred from CI.

## 5. AI provider independence

External AI services are providers, not the identity core of Ariana.

Target direction: Ariana Core -> Provider Router -> local provider / OpenAI adapter / Meta adapter / Google adapter / Anthropic adapter / other reviewed adapters.

Provider-specific assumptions belong at adapter boundaries. Availability, credentials, quotas and policy remain external conditions.

Meta-specific integration is therefore an item to audit and isolate, not a reason to rewrite the whole system.

## 6. Inneres Werden placement

Inneres Werden is a local computational learning/reflection layer. It consumes structured evidence and produces bounded state or proposals.

It must not silently rewrite production code, bypass approval gates, grant itself capabilities, or be represented as biological emotion or consciousness.

Its outputs feed the normal control/review pipeline.

## 7. Release path

REQUIREMENT -> ARCHITECTURE -> IMPLEMENTATION BRANCH -> UNIT TEST -> LINT -> ANDROID BUILD -> ARTIFACT + HASH -> REVIEW -> DEVICE E2E when required -> APPROVAL -> main/release.

## 8. Current integration priorities

P0: preserve the existing control/security boundary.
P1: consolidate memory, learning and state contracts.
P1: audit provider dependencies, especially Meta integration.
P2: unify diagnostics and evidence records.
P2: connect Snowworks media capabilities through explicit adapters.
P3: improve device UX only after core contracts remain stable.

## 9. Definition of done

This document is a map, not a claim that every listed future component is implemented. Implementation status must be established from repository evidence and release gates.
