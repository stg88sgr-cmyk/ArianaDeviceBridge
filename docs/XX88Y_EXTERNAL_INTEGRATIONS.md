# XX88Y External Integration Ring

This document records external tools requested for the ARIANA X88 -> XX88Y transformation. It distinguishes ChatGPT/Codex-side tooling from runtime providers that the Android app can actually call.

## Rule

No external tool is marked CONNECTED in the Android runtime unless a concrete authenticated adapter/endpoint exists and a reachability check succeeds.

## Verified available in the current ChatGPT environment

### Replit
- Role: rapid web/backend prototype builder and hosted test harness.
- X88 category: DEVELOPMENT_TOOL / OPTIONAL_BACKEND.
- Android runtime status: NOT_CONNECTED until an explicit API/endpoint adapter is configured.

### Vercel
- Role: web/agent deployment target and test environment.
- X88 category: BUILD_DEPLOY_TOOL.
- Android runtime status: NOT_CONNECTED until an explicit deployment/runtime API adapter is configured.

### Linear
- Role: issue/project/task tracking for OBSERVE -> PLAN -> MUTATE -> VERIFY work.
- X88 category: PROJECT_CONTROL.
- Android runtime status: NOT_CONNECTED by default; this is a development/project integration, not a device capability.

### Higgsfield
- Role: creative image/video/web generation provider on the ChatGPT side.
- X88 category: CREATIVE_PROVIDER.
- Android runtime status: NOT_CONNECTED until a supported direct endpoint/auth adapter exists.

### OpenAI Ads Conversions
- Role: Ads measurement instrumentation / conversion setup during development.
- X88 category: DEVELOPMENT_SKILL / TELEMETRY_INTEGRATION.
- Android runtime status: NOT_CONNECTED unless measurement endpoints are deliberately integrated.

### Life Sciences database tooling
- Role: structured public life-science research and evidence retrieval.
- X88 category: SPECIALIST_RESEARCH_WORKER.
- Android runtime status: NOT_CONNECTED unless a dedicated worker service/endpoint is created.

## Requested but not currently resolved as a direct connector

### Dossaro
- Status: UNRESOLVED.
- Action: do not assume capabilities or connectivity until the provider/app is identified and an adapter contract is known.

### Life Sciences NGS Analysis
- Status: UNRESOLVED as a named connector.
- Note: general life-science database tooling is available, but a dedicated NGS analysis runtime/plugin was not confirmed by current discovery.

### NVIDIA BioNeMo Agent Toolkit
- Status: UNRESOLVED as a directly connected plugin/runtime.
- Action: later adapter should expose explicit endpoint, auth mode, health check, capabilities and error reporting.

## XX88Y adapter contract for external integrations

Every external integration must expose, at minimum:

```text
id
name
category
endpoint/auth reference
connection status
health check
capabilities
last success
last error
```

Permitted connection states:

```text
UNRESOLVED
NOT_CONNECTED
CONNECTING
CONNECTED
ERROR
```

No UI button press may promote an integration to CONNECTED. Only a successful adapter health check can do that.
