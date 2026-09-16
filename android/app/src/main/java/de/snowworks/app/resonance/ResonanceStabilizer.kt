package de.snowworks.app.resonance

import kotlin.math.abs

class ResonanceStabilizer(
    private val energySmoothing: Float = 0.18f,
    private val frequencySmoothing: Float = 0.12f,
    private val minimumNoiseFloor: Float = 0.008f,
    private val gateMultiplier: Float = 2.2f,
    private val holdFrames: Int = 5,
) {
    private var smoothedEnergy = 0f
    private var smoothedFrequency = 0f
    private var noiseFloor = minimumNoiseFloor
    private var holdCounter = 0

    fun process(
        inputEnergy: Float,
        dominantHz: Float,
        bands: SpectralBands = SpectralBands(),
        generatedFrequencyHz: Float? = null,
    ): StableResonanceFrame {
        val rawEnergy = inputEnergy.coerceIn(0f, 1f)
        updateNoiseFloor(rawEnergy)

        val threshold = (noiseFloor * gateMultiplier).coerceAtLeast(minimumNoiseFloor)
        val aboveGate = rawEnergy >= threshold
        if (aboveGate) holdCounter = holdFrames else if (holdCounter > 0) holdCounter--
        val gateOpen = aboveGate || holdCounter > 0

        val feedback = isLikelyFeedback(dominantHz, generatedFrequencyHz, rawEnergy)
        val accepted = gateOpen && !feedback
        val targetEnergy = if (accepted) rawEnergy else 0f
        smoothedEnergy += (targetEnergy - smoothedEnergy) * energySmoothing

        if (accepted && dominantHz > 0f) {
            if (smoothedFrequency <= 0f) smoothedFrequency = dominantHz
            else smoothedFrequency += (dominantHz - smoothedFrequency) * frequencySmoothing
        }

        return StableResonanceFrame(
            energy = smoothedEnergy.coerceIn(0f, 1f),
            dominantHz = smoothedFrequency.coerceAtLeast(0f),
            noiseFloor = noiseFloor,
            gateOpen = gateOpen,
            feedbackRejected = feedback,
            bands = if (accepted) bands.clamped() else SpectralBands(),
        )
    }

    fun reset() {
        smoothedEnergy = 0f
        smoothedFrequency = 0f
        noiseFloor = minimumNoiseFloor
        holdCounter = 0
    }

    private fun updateNoiseFloor(energy: Float) {
        val alpha = if (energy > noiseFloor) 0.015f else 0.004f
        noiseFloor += (energy - noiseFloor) * alpha
        noiseFloor = noiseFloor.coerceIn(minimumNoiseFloor, 0.35f)
    }

    private fun isLikelyFeedback(measuredHz: Float, generatedHz: Float?, energy: Float): Boolean {
        val generated = generatedHz ?: return false
        if (generated <= 0f || measuredHz <= 0f || energy <= 0.03f) return false
        return matches(measuredHz, generated, 0.025f, 8f) ||
            matches(measuredHz, generated * 2f, 0.04f, 12f) ||
            matches(measuredHz, generated * 3f, 0.04f, 12f)
    }

    private fun matches(measured: Float, target: Float, ratio: Float, floorHz: Float): Boolean =
        abs(measured - target) <= maxOf(floorHz, target * ratio)

    private fun SpectralBands.clamped() = SpectralBands(
        bass = bass.coerceIn(0f, 1f),
        mids = mids.coerceIn(0f, 1f),
        highs = highs.coerceIn(0f, 1f),
    )
}
