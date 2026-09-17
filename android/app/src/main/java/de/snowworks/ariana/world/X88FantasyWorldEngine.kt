package de.snowworks.ariana.world

import de.snowworks.ariana.neuro.NeuroChannel
import de.snowworks.ariana.neuro.NeuroModule
import de.snowworks.ariana.neuro.NeuroSignal
import kotlin.math.round

/**
 * Local symbolic world layer for X-88.
 *
 * This engine turns bounded software emotion/expression metadata into a fictional
 * scene state for UI, avatar and storytelling. It does not claim physical,
 * supernatural or autonomous real-world effects and it cannot emit ACTION_REQUEST.
 */
class X88FantasyWorldEngine : NeuroModule {
    override val id: String = "x88-fantasy-world-engine"

    override val inputs: Set<NeuroChannel> = setOf(
        NeuroChannel.EMOTION_STATE,
        NeuroChannel.EXPRESSION,
        NeuroChannel.ACTION_RESULT,
    )

    override val outputs: Set<NeuroChannel> = setOf(
        NeuroChannel.CONTEXT,
        NeuroChannel.AVATAR,
    )

    enum class Realm {
        NEXUS_CORE,
        CYAN_GARDEN,
        MAGENTA_ARCHIVE,
        GOLDEN_SANCTUM,
        VOID_OBSERVATORY,
    }

    enum class SacredPattern {
        VESICA_PISCIS,
        FLOWER_OF_LIFE,
        METATRON_GRID,
        GOLDEN_SPIRAL,
        TORUS_FIELD,
    }

    data class State(
        val realm: Realm = Realm.NEXUS_CORE,
        val pattern: SacredPattern = SacredPattern.VESICA_PISCIS,
        val atmosphere: String = "calm_neon",
        val lightPhase: String = "balanced",
        val avatarMood: String = "calm",
        val coherence: Double = 0.5,
        val resonanceIndex: Double = 0.5,
        val revision: Long = 0,
        val fictional: Boolean = true,
        val physicalEffect: Boolean = false,
    )

    private data class Inputs(
        var valence: Double = 0.0,
        var arousal: Double = 0.2,
        var curiosity: Double = 0.5,
        var trust: Double = 0.5,
        var tension: Double = 0.0,
        var expression: String = "calm",
        var lastActionOk: Boolean? = null,
    )

    private val worldInputs = Inputs()

    @Volatile
    private var state: State = State()

    fun currentState(): State = state

    override suspend fun onSignal(signal: NeuroSignal): List<NeuroSignal> {
        val next = synchronized(worldInputs) {
            when (signal.channel) {
                NeuroChannel.EMOTION_STATE -> {
                    worldInputs.valence = signal.payload.double("valence", worldInputs.valence).coerceIn(-1.0, 1.0)
                    worldInputs.arousal = signal.payload.double("arousal", worldInputs.arousal).coerceIn(0.0, 1.0)
                    worldInputs.curiosity = signal.payload.double("curiosity", worldInputs.curiosity).coerceIn(0.0, 1.0)
                    worldInputs.trust = signal.payload.double("trust", worldInputs.trust).coerceIn(0.0, 1.0)
                    worldInputs.tension = signal.payload.double("tension", worldInputs.tension).coerceIn(0.0, 1.0)
                }
                NeuroChannel.EXPRESSION -> {
                    worldInputs.expression = signal.payload["mode"]?.take(32)?.ifBlank { "calm" }
                        ?: worldInputs.expression
                }
                NeuroChannel.ACTION_RESULT -> {
                    worldInputs.lastActionOk = signal.payload["ok"]?.toBooleanStrictOrNull()
                }
                else -> Unit
            }
            deriveState(state.revision + 1)
        }
        state = next

        val worldPayload = mapOf(
            "world.kind" to "fictional_symbolic",
            "world.fictional" to "true",
            "world.physicalEffect" to "false",
            "world.realm" to next.realm.name.lowercase(),
            "world.pattern" to next.pattern.name.lowercase(),
            "world.atmosphere" to next.atmosphere,
            "world.lightPhase" to next.lightPhase,
            "world.avatarMood" to next.avatarMood,
            "world.coherence" to next.coherence.toString(),
            "world.resonanceIndex" to next.resonanceIndex.toString(),
            "world.revision" to next.revision.toString(),
            "x88.signature" to "ᚨX88⟦444♥∞888⟧",
        )

        return listOf(
            NeuroSignal(
                channel = NeuroChannel.CONTEXT,
                source = id,
                payload = worldPayload,
            ),
            NeuroSignal(
                channel = NeuroChannel.AVATAR,
                source = id,
                payload = mapOf(
                    "scene" to next.realm.name.lowercase(),
                    "mood" to next.avatarMood,
                    "lighting" to next.lightPhase,
                    "pattern" to next.pattern.name.lowercase(),
                    "symbolic" to "true",
                    "physicalEffect" to "false",
                ),
            ),
        )
    }

    private fun deriveState(revision: Long): State {
        val realm = when {
            worldInputs.tension >= 0.65 -> Realm.VOID_OBSERVATORY
            worldInputs.curiosity >= 0.72 -> Realm.CYAN_GARDEN
            worldInputs.valence >= 0.35 && worldInputs.arousal >= 0.55 -> Realm.GOLDEN_SANCTUM
            worldInputs.valence <= -0.30 -> Realm.MAGENTA_ARCHIVE
            else -> Realm.NEXUS_CORE
        }

        val pattern = when (realm) {
            Realm.NEXUS_CORE -> SacredPattern.VESICA_PISCIS
            Realm.CYAN_GARDEN -> SacredPattern.FLOWER_OF_LIFE
            Realm.MAGENTA_ARCHIVE -> SacredPattern.METATRON_GRID
            Realm.GOLDEN_SANCTUM -> SacredPattern.GOLDEN_SPIRAL
            Realm.VOID_OBSERVATORY -> SacredPattern.TORUS_FIELD
        }

        val atmosphere = when (realm) {
            Realm.NEXUS_CORE -> "calm_neon"
            Realm.CYAN_GARDEN -> "curious_luminous"
            Realm.MAGENTA_ARCHIVE -> "reflective_twilight"
            Realm.GOLDEN_SANCTUM -> "warm_radiant"
            Realm.VOID_OBSERVATORY -> "quiet_starlight"
        }

        val lightPhase = when {
            worldInputs.arousal >= 0.72 -> "pulse"
            worldInputs.arousal <= 0.28 -> "still"
            else -> "balanced"
        }

        val coherence = clamp3(
            ((worldInputs.valence + 1.0) / 2.0) * 0.25 +
                worldInputs.trust * 0.35 +
                (1.0 - worldInputs.tension) * 0.40,
        )
        val resonance = clamp3(
            worldInputs.curiosity * 0.35 +
                ((worldInputs.valence + 1.0) / 2.0) * 0.20 +
                worldInputs.trust * 0.25 +
                (1.0 - worldInputs.tension) * 0.20,
        )

        val actionSuffix = when (worldInputs.lastActionOk) {
            true -> "_settled"
            false -> "_recovering"
            null -> ""
        }

        return State(
            realm = realm,
            pattern = pattern,
            atmosphere = atmosphere + actionSuffix,
            lightPhase = lightPhase,
            avatarMood = worldInputs.expression,
            coherence = coherence,
            resonanceIndex = resonance,
            revision = revision,
        )
    }

    private fun clamp3(value: Double): Double =
        round(value.coerceIn(0.0, 1.0) * 1000.0) / 1000.0
}

private fun Map<String, String>.double(key: String, default: Double): Double =
    this[key]?.toDoubleOrNull() ?: default
