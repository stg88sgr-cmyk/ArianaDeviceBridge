# X88 Meta Dependency Audit v1

Date: 2026-09-20
Status: AUDIT RESULT

## Finding

The repository evidence reviewed here does **not** show a native Meta SDK dependency in the inspected Android Gradle dependency list. The Meta path is implemented as a provider/integration path using the existing OpenAI-compatible remote-provider architecture.

The documented Meta route is:
- encrypted provider configuration;
- remote HTTPS request;
- `CloudAiPolicy` sanitization before egress;
- explicit multi-AI commands such as `/meta`, `/review`, `/consensus` and `/repair`;
- Ariana's local provider remains authoritative for normal dialogue.

The repository documentation identifies the Meta preset as `https://api.meta.ai/v1/chat/completions` with model `muse-spark-1.3`.

## Evidence

PR #33 describes the multi-AI router as modular and says normal dialogue is unchanged unless explicit multi-AI commands are used.

PR #35 documents real-device validation of the Meta path and states that the local core remained online during those runs.

The inspected `android/app/build.gradle.kts` on the Meta branches contains AndroidX, Material, MediaPipe GenAI and JUnit dependencies, but no dependency whose coordinates identify a Meta/Facebook SDK.

## Architectural conclusion

Meta should be treated as an **optional remote provider adapter**, not as Ariana's core identity or local runtime foundation.

Therefore the next safe implementation step is not "remove Meta". It is:

1. identify the concrete Meta request/response adapter classes;
2. define a provider-neutral interface around them;
3. add a deterministic local/mock provider for tests;
4. prove that core dialogue, memory, policy and safety controls work with Meta unavailable;
5. keep credentials and provider configuration isolated;
6. only then consider changing or removing the Meta adapter.

## Remaining unknown

The current GitHub code-search surface did not return source-file matches for generic Meta search terms, so this document deliberately does not claim a complete class-level dependency graph. The exact source locations still require inspection of the feature branch tree/files.

## Safety

No production source was changed by this audit. The only current change is documentation on the isolated audit branch.
