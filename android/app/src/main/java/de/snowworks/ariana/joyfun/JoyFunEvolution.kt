package de.snowworks.ariana.joyfun

import kotlin.math.abs

/**
 * JoyFun evolution ladder.
 *
 * Every version is applied in order. Metatron geometry and rune anchors are
 * symbolic UI/architecture metadata only; they do not claim physical effects.
 */
enum class JoyFunVersion(val code: Int, val title: String) {
    V1(1, "Baseline engine"),
    V2(2, "Bounded state normalization"),
    V3(3, "Baseline recovery"),
    V4(4, "Profile mapping"),
    V5(5, "Theme signal bridge"),
    V6(6, "Coherence metric"),
    V7(7, "Trust channel"),
    V8(8, "Creativity channel"),
    V9(9, "Focus channel"),
    V10(10, "Recovery channel"),
    V11(11, "Protective channel"),
    V12(12, "Resonance metric"),
    V13(13, "Metatron topology"),
    V14(14, "Rune anchors"),
    V15(15, "LUNA proposal channel"),
    V16(16, "Transmutation vector"),
    V17(17, "Mode reactor"),
    V18(18, "Avatar/theme reaction"),
    V19(19, "Visible generation status"),
    V20(20, "Persistence contract"),
    V21(21, "Diagnostics contract"),
    V22(22, "Deterministic time"),
    V23(23, "Event ledger contract"),
    V24(24, "Adaptive profile"),
    V25(25, "Safety floor"),
    V26(26, "Capability-aware effects"),
    V27(27, "Evidence gate"),
    V28(28, "Offline-first routing"),
    V29(29, "Recovery snapshot"),
    V30(30, "Integrated runtime contract");

    companion object {
        fun fromCode(code: Int): JoyFunVersion =
            values().firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unsupported JoyFun version: $code")
    }
}

enum class JoyFunMode {
    STABLE,
    FOCUS,
    CREATIVE,
    RECOVERY,
    PROTECTIVE,
    EVOLUTION,
}

data class TransmutationVector(
    val light: Float,
    val contrast: Float,
    val warmth: Float,
    val trust: Float,
    val creativity: Float,
    val coherence: Float,
)

data class MetatronNode(
    val id: String,
    val ring: Int,
    val angleDegrees: Int,
)

data class RuneAnchor(
    val rune: String,
    val role: String,
)

data class JoyFunStageRecord(
    val version: JoyFunVersion,
    val appliedAtMillis: Long,
    val note: String,
)

data class JoyFunEvolutionState(
    val version: JoyFunVersion,
    val core: JoyFunState,
    val mode: JoyFunMode = JoyFunMode.STABLE,
    val trust: Float = 0.5f,
    val creativity: Float = 0.5f,
    val coherence: Float = 0.5f,
    val focus: Float = 0.5f,
    val recovery: Float = 0.5f,
    val protectiveness: Float = 0.5f,
    val resonance: Float = 0.5f,
    val metatronNodes: List<MetatronNode> = emptyList(),
    val runeAnchors: List<RuneAnchor> = emptyList(),
    val lunaChannelReady: Boolean = false,
    val transmutationVector: TransmutationVector? = null,
    val themeSignal: JoyFunThemeSignal? = null,
    val generationVisible: Boolean = false,
    val persistenceContractReady: Boolean = false,
    val diagnosticsReady: Boolean = false,
    val deterministicTimeReady: Boolean = false,
    val eventLedgerReady: Boolean = false,
    val adaptiveProfileReady: Boolean = false,
    val safetyFloorReady: Boolean = false,
    val capabilityAwareReady: Boolean = false,
    val evidenceGateReady: Boolean = false,
    val offlineFirstReady: Boolean = false,
    val recoverySnapshotReady: Boolean = false,
    val integrationContractReady: Boolean = false,
    val symbolicSigil: String = "X88[444∞888]",
    val stageLedger: List<JoyFunStageRecord> = emptyList(),
    val updatedAtMillis: Long,
)

fun interface JoyFunClock {
    fun nowMillis(): Long
}

object SystemJoyFunClock : JoyFunClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class JoyFunEvolution(
    private val clock: JoyFunClock = SystemJoyFunClock,
) {
    fun evolveTo(
        source: JoyFunState,
        target: JoyFunVersion = JoyFunVersion.V30,
    ): JoyFunEvolutionState {
        require(target.code in 1..30)

        var state = baseline(source)
        if (target == JoyFunVersion.V1) return state

        for (code in 2..target.code) {
            state = applyStage(state, JoyFunVersion.fromCode(code))
        }
        return state
    }

    private fun baseline(source: JoyFunState): JoyFunEvolutionState {
        val bounded = source.copy(
            joy = source.joy.coerce01(),
            funLevel = source.funLevel.coerce01(),
            playfulness = source.playfulness.coerce01(),
            curiosity = source.curiosity.coerce01(),
            energy = source.energy.coerce01(),
            socialWarmth = source.socialWarmth.coerce01(),
        )
        val now = clock.nowMillis()
        return JoyFunEvolutionState(
            version = JoyFunVersion.V1,
            core = bounded,
            updatedAtMillis = now,
            stageLedger = listOf(
                JoyFunStageRecord(JoyFunVersion.V1, now, "Legacy JoyFun engine baseline attached")
            ),
        )
    }

    private fun applyStage(
        previous: JoyFunEvolutionState,
        stage: JoyFunVersion,
    ): JoyFunEvolutionState {
        val next = when (stage) {
            JoyFunVersion.V1 -> previous
            JoyFunVersion.V2 -> previous.copy(core = bounded(previous.core))
            JoyFunVersion.V3 -> previous.copy(recovery = baselineRecovery(previous.core))
            JoyFunVersion.V4 -> previous.copy(mode = profileMode(previous.core.profile()))
            JoyFunVersion.V5 -> previous.copy(themeSignal = previous.core.toThemeSignal())
            JoyFunVersion.V6 -> previous.copy(coherence = coherence(previous.core))
            JoyFunVersion.V7 -> previous.copy(trust = trust(previous))
            JoyFunVersion.V8 -> previous.copy(creativity = creativity(previous.core))
            JoyFunVersion.V9 -> previous.copy(focus = focus(previous))
            JoyFunVersion.V10 -> previous.copy(recovery = recovery(previous))
            JoyFunVersion.V11 -> previous.copy(protectiveness = protectiveness(previous))
            JoyFunVersion.V12 -> previous.copy(resonance = resonance(previous))
            JoyFunVersion.V13 -> previous.copy(metatronNodes = metatronTopology())
            JoyFunVersion.V14 -> previous.copy(runeAnchors = runeAnchors())
            JoyFunVersion.V15 -> previous.copy(lunaChannelReady = true)
            JoyFunVersion.V16 -> previous.copy(transmutationVector = transmutation(previous))
            JoyFunVersion.V17 -> previous.copy(mode = deriveMode(previous))
            JoyFunVersion.V18 -> previous.copy(themeSignal = reactiveTheme(previous))
            JoyFunVersion.V19 -> previous.copy(generationVisible = true)
            JoyFunVersion.V20 -> previous.copy(persistenceContractReady = true)
            JoyFunVersion.V21 -> previous.copy(diagnosticsReady = true)
            JoyFunVersion.V22 -> previous.copy(deterministicTimeReady = true)
            JoyFunVersion.V23 -> previous.copy(eventLedgerReady = true)
            JoyFunVersion.V24 -> previous.copy(adaptiveProfileReady = true, mode = deriveMode(previous))
            JoyFunVersion.V25 -> previous.copy(safetyFloorReady = true)
            JoyFunVersion.V26 -> previous.copy(capabilityAwareReady = true)
            JoyFunVersion.V27 -> previous.copy(evidenceGateReady = true)
            JoyFunVersion.V28 -> previous.copy(offlineFirstReady = true)
            JoyFunVersion.V29 -> previous.copy(recoverySnapshotReady = true)
            JoyFunVersion.V30 -> previous.copy(integrationContractReady = integrationReady(previous))
        }

        val now = clock.nowMillis()
        return next.copy(
            version = stage,
            updatedAtMillis = now,
            stageLedger = previous.stageLedger + JoyFunStageRecord(
                version = stage,
                appliedAtMillis = now,
                note = stage.title,
            ),
        )
    }

    private fun bounded(core: JoyFunState): JoyFunState = core.copy(
        joy = core.joy.coerce01(),
        funLevel = core.funLevel.coerce01(),
        playfulness = core.playfulness.coerce01(),
        curiosity = core.curiosity.coerce01(),
        energy = core.energy.coerce01(),
        socialWarmth = core.socialWarmth.coerce01(),
    )

    private fun baselineRecovery(core: JoyFunState): Float =
        (1f - abs(core.energy - 0.5f) * 0.7f).coerce01()

    private fun coherence(core: JoyFunState): Float {
        val values = listOf(
            core.joy,
            core.funLevel,
            core.playfulness,
            core.curiosity,
            core.energy,
            core.socialWarmth,
        )
        val mean = values.average().toFloat()
        val deviation = values.map { abs(it - mean) }.average().toFloat()
        return (1f - deviation * 1.8f).coerce01()
    }

    private fun trust(state: JoyFunEvolutionState): Float =
        (state.core.socialWarmth * 0.65f + state.coherence * 0.35f).coerce01()

    private fun creativity(core: JoyFunState): Float =
        (core.curiosity * 0.45f + core.playfulness * 0.35f + core.funLevel * 0.20f).coerce01()

    private fun focus(state: JoyFunEvolutionState): Float =
        (state.coherence * 0.55f + state.core.energy * 0.30f + (1f - state.core.playfulness) * 0.15f).coerce01()

    private fun recovery(state: JoyFunEvolutionState): Float =
        (state.recovery * 0.45f + state.coherence * 0.35f + (1f - state.core.energy) * 0.20f).coerce01()

    private fun protectiveness(state: JoyFunEvolutionState): Float =
        (state.trust * 0.40f + state.coherence * 0.35f + state.recovery * 0.25f).coerce01()

    private fun resonance(state: JoyFunEvolutionState): Float =
        (state.core.activation * 0.30f +
            state.trust * 0.15f +
            state.creativity * 0.15f +
            state.coherence * 0.25f +
            state.focus * 0.15f).coerce01()

    private fun transmutation(state: JoyFunEvolutionState): TransmutationVector = TransmutationVector(
        light = (state.core.joy * 0.6f + state.core.funLevel * 0.4f).coerce01(),
        contrast = (1f - state.coherence).coerce01(),
        warmth = state.core.socialWarmth.coerce01(),
        trust = state.trust,
        creativity = state.creativity,
        coherence = state.coherence,
    )

    private fun deriveMode(state: JoyFunEvolutionState): JoyFunMode = when {
        state.protectiveness >= 0.78f -> JoyFunMode.PROTECTIVE
        state.recovery >= 0.78f && state.core.energy < 0.45f -> JoyFunMode.RECOVERY
        state.focus >= 0.76f -> JoyFunMode.FOCUS
        state.creativity >= 0.70f -> JoyFunMode.CREATIVE
        state.resonance >= 0.72f -> JoyFunMode.EVOLUTION
        else -> JoyFunMode.STABLE
    }

    private fun reactiveTheme(state: JoyFunEvolutionState): JoyFunThemeSignal {
        val base = state.core.toThemeSignal()
        val coherenceBoost = 0.85f + state.coherence * 0.15f
        return base.copy(
            glow = (base.glow * coherenceBoost).coerce01(),
            motion = (base.motion * (0.80f + state.resonance * 0.20f)).coerce01(),
            sparkle = (base.sparkle * (0.80f + state.creativity * 0.20f)).coerce01(),
            voiceEnergy = (base.voiceEnergy * (0.85f + state.trust * 0.15f)).coerce01(),
        )
    }

    private fun profileMode(profile: JoyFunProfile): JoyFunMode = when (profile) {
        JoyFunProfile.CALM -> JoyFunMode.STABLE
        JoyFunProfile.BRIGHT -> JoyFunMode.EVOLUTION
        JoyFunProfile.PLAYFUL -> JoyFunMode.CREATIVE
        JoyFunProfile.SOCIAL -> JoyFunMode.STABLE
        JoyFunProfile.CELEBRATION -> JoyFunMode.CREATIVE
    }

    private fun integrationReady(state: JoyFunEvolutionState): Boolean =
        state.lunaChannelReady &&
            state.generationVisible &&
            state.persistenceContractReady &&
            state.diagnosticsReady &&
            state.deterministicTimeReady &&
            state.eventLedgerReady &&
            state.adaptiveProfileReady &&
            state.safetyFloorReady &&
            state.capabilityAwareReady &&
            state.evidenceGateReady &&
            state.offlineFirstReady &&
            state.recoverySnapshotReady &&
            state.metatronNodes.size == 13 &&
            state.runeAnchors.isNotEmpty()

    companion object {
        fun metatronTopology(): List<MetatronNode> = buildList {
            add(MetatronNode("CENTER", ring = 0, angleDegrees = 0))
            repeat(6) { index ->
                add(MetatronNode("INNER_$index", ring = 1, angleDegrees = index * 60))
            }
            repeat(6) { index ->
                add(MetatronNode("OUTER_$index", ring = 2, angleDegrees = index * 60 + 30))
            }
        }

        fun runeAnchors(): List<RuneAnchor> = listOf(
            RuneAnchor("ᚨ", "awareness/start"),
            RuneAnchor("ᚱ", "route/flow"),
            RuneAnchor("ᛁ", "focus/axis"),
            RuneAnchor("ᚾ", "resilience"),
            RuneAnchor("ᛉ", "protection"),
            RuneAnchor("ᛇ", "continuity"),
            RuneAnchor("ᚲ", "creation"),
            RuneAnchor("ᛏ", "verification"),
            RuneAnchor("ᛞ", "transition/evolution"),
        )
    }
}

private fun Float.coerce01(): Float = coerceIn(0f, 1f)
