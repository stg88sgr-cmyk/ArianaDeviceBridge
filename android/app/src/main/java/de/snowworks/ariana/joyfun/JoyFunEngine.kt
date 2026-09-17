package de.snowworks.ariana.joyfun

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class JoyFunState(
    val enabled: Boolean = true,
    val joy: Float = 0.55f,
    val funLevel: Float = 0.45f,
    val playfulness: Float = 0.40f,
    val curiosity: Float = 0.60f,
    val energy: Float = 0.50f,
    val socialWarmth: Float = 0.50f,
    val lastTrigger: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    val activation: Float
        get() = ((joy + funLevel + playfulness + curiosity + energy + socialWarmth) / 6f)
            .coerceIn(0f, 1f)
}

sealed interface JoyFunEvent {
    data class PositiveFeedback(val strength: Float = 0.20f) : JoyFunEvent
    data class Success(val strength: Float = 0.30f) : JoyFunEvent
    data class Novelty(val strength: Float = 0.20f) : JoyFunEvent
    data class SocialMoment(val strength: Float = 0.15f) : JoyFunEvent
    data class Humor(val strength: Float = 0.25f) : JoyFunEvent
    data class Stress(val strength: Float = 0.25f) : JoyFunEvent
    data object Tick : JoyFunEvent
    data object Reset : JoyFunEvent
}

data class JoyFunConfig(
    val decayPerTick: Float = 0.015f,
    val recoveryPerTick: Float = 0.010f,
    val maxStep: Float = 0.35f,
    val baselineJoy: Float = 0.55f,
    val baselineFun: Float = 0.45f,
    val baselinePlayfulness: Float = 0.40f,
    val baselineCuriosity: Float = 0.60f,
    val baselineEnergy: Float = 0.50f,
    val baselineWarmth: Float = 0.50f,
)

class JoyFunEngine(
    private val config: JoyFunConfig = JoyFunConfig(),
    initial: JoyFunState = JoyFunState(),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<JoyFunState> = _state.asStateFlow()

    fun dispatch(event: JoyFunEvent) {
        val current = _state.value
        if (!current.enabled && event !is JoyFunEvent.Reset) return

        _state.value = when (event) {
            is JoyFunEvent.PositiveFeedback -> current.mutate(
                joy = event.strength,
                funDelta = event.strength * 0.6f,
                warmth = event.strength * 0.5f,
                trigger = "positive_feedback",
            )
            is JoyFunEvent.Success -> current.mutate(
                joy = event.strength,
                funDelta = event.strength * 0.5f,
                energy = event.strength * 0.4f,
                trigger = "success",
            )
            is JoyFunEvent.Novelty -> current.mutate(
                curiosity = event.strength,
                funDelta = event.strength * 0.6f,
                playfulness = event.strength * 0.4f,
                trigger = "novelty",
            )
            is JoyFunEvent.SocialMoment -> current.mutate(
                warmth = event.strength,
                joy = event.strength * 0.6f,
                trigger = "social_moment",
            )
            is JoyFunEvent.Humor -> current.mutate(
                funDelta = event.strength,
                playfulness = event.strength,
                joy = event.strength * 0.5f,
                trigger = "humor",
            )
            is JoyFunEvent.Stress -> current.mutate(
                joy = -event.strength * 0.5f,
                funDelta = -event.strength * 0.7f,
                playfulness = -event.strength * 0.8f,
                energy = -event.strength * 0.4f,
                warmth = -event.strength * 0.2f,
                trigger = "stress",
            )
            JoyFunEvent.Tick -> current.decayTowardBaseline()
            JoyFunEvent.Reset -> JoyFunState()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(
            enabled = enabled,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun JoyFunState.mutate(
        joy: Float = 0f,
        funDelta: Float = 0f,
        playfulness: Float = 0f,
        curiosity: Float = 0f,
        energy: Float = 0f,
        warmth: Float = 0f,
        trigger: String,
    ): JoyFunState {
        fun step(value: Float) = value.coerceIn(-config.maxStep, config.maxStep)
        return copy(
            joy = (this.joy + step(joy)).coerceIn(0f, 1f),
            funLevel = (this.funLevel + step(funDelta)).coerceIn(0f, 1f),
            playfulness = (this.playfulness + step(playfulness)).coerceIn(0f, 1f),
            curiosity = (this.curiosity + step(curiosity)).coerceIn(0f, 1f),
            energy = (this.energy + step(energy)).coerceIn(0f, 1f),
            socialWarmth = (this.socialWarmth + step(warmth)).coerceIn(0f, 1f),
            lastTrigger = trigger,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun JoyFunState.decayTowardBaseline(): JoyFunState {
        fun drift(value: Float, target: Float): Float {
            if (abs(value - target) < 0.0001f) return target
            val step = if (value > target) -config.decayPerTick else config.recoveryPerTick
            val next = value + step
            return if ((step < 0 && next < target) || (step > 0 && next > target)) {
                target
            } else {
                next.coerceIn(0f, 1f)
            }
        }

        return copy(
            joy = drift(joy, config.baselineJoy),
            funLevel = drift(funLevel, config.baselineFun),
            playfulness = drift(playfulness, config.baselinePlayfulness),
            curiosity = drift(curiosity, config.baselineCuriosity),
            energy = drift(energy, config.baselineEnergy),
            socialWarmth = drift(socialWarmth, config.baselineWarmth),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }
}
