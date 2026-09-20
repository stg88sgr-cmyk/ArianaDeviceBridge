# X88 Memory Bridge v1

The memory bridge is the neutral boundary between external AI/provider data and the
local X88 memory layer.

## Boundary

Provider-specific code implements MemorySourceAdapter and returns MemoryItem.
The X88 side never needs to know whether the source is Meta AI, another AI provider,
or a future local source.

MemoryBridge currently performs only two boundary operations:

1. trims the source/content/key representation;
2. removes repeated items by stable id.

No provider credentials, Android permissions, network transport, or private provider
API is required by this module.

## Meta AI position

Meta AI is a possible adapter, not a dependency of the X88 memory core. A future
Meta adapter must use an officially supported user-authorized export/import path.
The adapter should remain outside this package's core data model.

## Continuity

This module is additive and reversible. It does not replace the existing V32 runtime
and does not change device-action permissions or the X88 SecurityChain.
