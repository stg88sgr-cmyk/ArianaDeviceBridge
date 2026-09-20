# ARIANA X-88 Dependency Map v1

Status: AUDIT SNAPSHOT
Date: 2026-09-20

## Observed layers

- Android application: android/; canonical.
- Phone Core: release-gate documentation; canonical baseline.
- Presence v2: release gate and Code-37 candidate; device gate still required.
- Memory: multiple x88-memory-* branches; fragmented across development history.
- Inneres Werden: x88-inneres-werden-v1, PR #57; development branch, not assumed merged.
- Multi-AI routing: feature/meta-multi-ai-router; development branch.
- Meta integration: feature/presence-v2-meta-integration and Meta-related branches; requires targeted audit.
- X Space: integrations/xspace-gateway plus related PRs; adapter/integration layer.
- Termux runtime: termux-runtime branches and merged work; runtime support layer.
- Luna Guardian: luna-guardian/; safety/runtime support.
- Media generation: generator-related branches/PRs; integration layer.

## Provider independence audit

1. Which classes import or directly reference Meta-specific SDKs/APIs?
2. Which data structures contain Meta-specific response assumptions?
3. Does any core control path require Meta availability?
4. Can a local provider satisfy the same interface for offline operation?
5. Can provider failure degrade to a safe local state without disabling core functions?
6. Are credentials isolated from normal logs and persisted state?
7. Are provider adapters replaceable without changing ArianaStateHolder, policy or memory contracts?

## Decision rule

Do not remove Meta integration yet. First identify the exact dependency graph. Then isolate it behind an adapter/interface, add a local/mock provider for tests, and verify that the core remains functional when Meta is unavailable.

## Evidence boundary

This snapshot is based on repository structure, branch names and release documentation inspected on 2026-09-20. It is not a claim that every historical branch is merged or active.
