# LUNA MUTATIONS

LUNA experiments are proposals, never production evidence by themselves.

## Rules

- Experiments belong in `luna/experimental/*` or another explicitly designated sandbox branch.
- No direct merge to `main`.
- Every successful mutation returns through requirements, build, review and approval gates.
- Android/system permission boundaries and security gates cannot be bypassed by mutation.

## 2026-09-17 - Finalization report evaluation

Luna proposed several Android 16 and CI changes. Repository verification showed that parts of the report were hypothetical or incorrect for the actual branch:
- the real branch already used compileSdk/targetSdk 36;
- Gradle wrapper was already 8.11.1;
- the actual CI used JDK 17 and completed successfully;
- the reported private-repository CI blocker did not apply to the connected GitHub workflow;
- generic extra foreground-service types are not to be added without a concrete service requirement.

Useful Luna proposals remain candidates only where they correspond to a real, verified gap.