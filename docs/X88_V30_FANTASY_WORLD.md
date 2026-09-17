# X88 V30 Fantasy World Layer

Branch: `x88-evolution-g2`

## Position in the runtime

The existing neuro runtime keeps the full stage chain V16 through V30. The fantasy-world layer attaches after `V30NeuroRuntime.initialize()` to the same local `V16NeuralFabric`.

It is a companion module, not a replacement version. The numbered V21-V30 stages remain unchanged and continuous:

- V21 attention routing
- V22 context fusion
- V23 explicit-intent stabilization
- V24 proposal-only planning
- V25 action-proposal bridge
- V26 outcome integration
- V27 lifecycle regulation
- V28 metadata-only telemetry
- V29 integrity monitoring
- V30 composed runtime health gate

## Fantasy world model

`X88FantasyWorldEngine` derives a bounded fictional scene from emotion/expression/action-result metadata.

Realms:

- `NEXUS_CORE`
- `CYAN_GARDEN`
- `MAGENTA_ARCHIVE`
- `GOLDEN_SANCTUM`
- `VOID_OBSERVATORY`

Symbolic geometry:

- `VESICA_PISCIS`
- `FLOWER_OF_LIFE`
- `METATRON_GRID`
- `GOLDEN_SPIRAL`
- `TORUS_FIELD`

The engine emits only `CONTEXT` and `AVATAR` signals. It has no `ACTION_REQUEST` output and therefore cannot directly execute Android/device actions.

Every world payload explicitly carries:

- `world.kind = fictional_symbolic`
- `world.fictional = true`
- `world.physicalEffect = false`

This keeps storytelling, Hermetic/resonance-inspired design and sacred-geometry visuals available as software/UI semantics while preserving the X88 reality and consent boundary.

## Boot path

`SnowworksApp.onCreate()` now performs:

1. `V30NeuroRuntime.initialize()`
2. `X88FantasyWorldBootstrap.attach(neuroRuntime)`

The module is therefore available automatically during normal app startup.

## Verification

The V16-V30 GitHub Actions gate now watches the world source, world tests and the application bootstrap. It runs the existing V16-V30 tests plus `X88FantasyWorldEngineTest`.

A release is not considered verified until that workflow completes successfully.
