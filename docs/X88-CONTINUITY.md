# X88 Continuity Contract

## Purpose

This document defines the canonical continuity contract for ARIANA/X88. It is a technical recovery mechanism, not a claim of human memory, consciousness, feelings, or identity.

## Canonical state

The canonical state is represented by a versioned Master State object. A state is only promoted to VERIFIED when the relevant implementation or fact has fresh evidence.

### Required fields

- `PROJECT_ID`
- `MASTER_VERSION`
- `STATE_STATUS` — `CURRENT`, `VERIFIED`, or `NEEDS_REVIEW`
- `CURRENT_STATE`
- `VERIFIED_STATE`
- `ACTIVE_TASK`
- `OPEN_BLOCKERS`
- `IMPORTANT_DECISIONS`
- `CONSTRAINTS`
- `LAST_VERIFIED_CHECKPOINT`
- `NEXT_RESUME_ACTION`
- `CHECKPOINTS`

## Evidence rule

No state is marked VERIFIED merely because a conversation, agent, or note says it is complete.

A verification record should identify:

1. what was checked,
2. how it was checked,
3. the relevant revision/commit,
4. the observed result,
5. the timestamp.

## Checkpoints

A checkpoint is immutable in meaning once recorded. New checkpoints append to history instead of overwriting prior checkpoints.

Recommended identifier:

`x88-checkpoint-YYYYMMDD-HHMMSS-vN`

Each checkpoint should record the Master State version and the source revision.

## Recovery flow

1. Load the latest Master State.
2. Validate its schema and version.
3. Load the latest VERIFIED checkpoint.
4. Separate verified facts from current assumptions.
5. Present OPEN_BLOCKERS and NEXT_RESUME_ACTION.
6. Continue work from NEXT_RESUME_ACTION.
7. After implementation, run fresh verification.
8. Create a new checkpoint only after evidence exists.

## Safety

- Never store secrets, access tokens, passwords, or private keys in the Master State.
- Do not silently overwrite an existing state.
- Imports must be explicit and validated.
- Exported recovery context must distinguish VERIFIED facts from NEEDS_REVIEW assumptions.
- Device permissions and platform security must never be bypassed.

## Resume context

The generated resume context should be compact enough to paste into a fresh conversation while preserving:

- project identity,
- current verified revision,
- current task,
- decisions,
- blockers,
- constraints,
- next action,
- verification evidence.

## Initial implementation status

This contract is introduced on the `x88-inneres-werden-v1` development line. The initial Master State is deliberately marked `NEEDS_REVIEW` until the continuity implementation itself has been independently verified.
