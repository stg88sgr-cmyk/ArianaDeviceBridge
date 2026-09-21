# X88 Memory Capability Design

## Goal

Connect the existing X88 Control Plane to the provider-neutral X88 Memory Bridge through a dedicated capability adapter. The control plane selects the memory capability; the adapter executes memory operations without making the control plane depend on concrete storage or AI providers.

## Architecture

X88ControlPlane -> X88Capability.LOCAL_MEMORY -> X88MemoryCapabilityAdapter -> X88MemoryBridge

The X88 backend registry remains descriptor-only. Executable memory behavior stays behind the adapter.

## Interfaces

Proposed file: `android/app/src/main/java/de/snowworks/ariana/orchestration/X88MemoryCapabilityAdapter.kt`.

The adapter consumes `X88MemoryBridge` and exposes focused operations for importing supplied memory and normalizing/deduplicating through the bridge. It produces `X88MemoryItem?` for import, preserving the bridge's existing null-on-duplicate behavior.

The adapter must not add network access, Android permissions, provider SDKs, encryption policy, or persistence semantics that are not already provided by the bridge.

## Control-plane integration

`X88Capability.LOCAL_MEMORY` remains the routing target for `X88TaskKind.MEMORY`. The capability adapter is registered as a local X88 backend descriptor. Routing selects the capability; execution resolves the adapter separately.

## Data flow

1. A memory task reaches the control plane.
2. The control plane selects LOCAL_MEMORY.
3. The memory capability adapter receives the supplied ImportedMemory payload.
4. The adapter delegates to X88MemoryBridge.
5. DefaultX88MemoryBridge normalizes content, derives hashes, and suppresses duplicate content.
6. The resulting X88MemoryItem is returned to the caller.

## Failure behavior

Blank task text continues to be rejected by the existing control plane. A duplicate import returns null, matching the existing bridge contract. No new fallback from memory into dialogue or external providers is introduced by this change.

## Tests

Focused tests will verify:
- memory capability adapter delegates import to the bridge;
- normalized content and duplicate behavior remain controlled by the bridge;
- the memory backend is marked local;
- the control plane routes MEMORY tasks to LOCAL_MEMORY;
- no external provider is required for memory execution.

## Scope boundary

This change does not implement durable storage, memory search/recall, encryption, Android permission handling, Meta AI import, or automatic memory capture. Those remain separate, reversible capabilities.
