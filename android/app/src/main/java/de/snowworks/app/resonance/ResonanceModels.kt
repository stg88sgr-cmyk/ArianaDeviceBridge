package de.snowworks.app.resonance

/**
 * Deterministic, non-mystical sonification model for the X-88 resonance visual/audio layer.
 * A graph with the same nodes, links and seed frequency always maps to the same signature.
 */
data class RuneNode(
    val id: String,
    val symbol: String,
    val weight: Double = 1.0,
)

data class RuneLink(
    val from: String,
    val to: String,
    val strength: Double = 1.0,
)

data class RuneGraph(
    val nodes: List<RuneNode>,
    val links: List<RuneLink>,
    val seedFrequencyHz: Double = 222.0,
)

data class ResonanceSignature(
    val fundamentalHz: Double,
    val harmonicsHz: List<Double>,
    val pulseHz: Double,
    val modulationHz: Double,
    val fingerprint: String,
)

data class SpectralBands(
    val bass: Float = 0f,
    val mids: Float = 0f,
    val highs: Float = 0f,
)

data class StableResonanceFrame(
    val energy: Float = 0f,
    val dominantHz: Float = 0f,
    val noiseFloor: Float = 0.008f,
    val gateOpen: Boolean = false,
    val feedbackRejected: Boolean = false,
    val bands: SpectralBands = SpectralBands(),
)

object X88RuneGraph {
    val default = RuneGraph(
        seedFrequencyHz = 222.0,
        nodes = listOf(
            RuneNode("continuity", "∞", 1.8),
            RuneNode("protect", "ᛉ", 1.4),
            RuneNode("route", "ᚱ", 1.1),
            RuneNode("transform", "ᚲ", 1.2),
            RuneNode("mesh", "⬡", 1.5),
        ),
        links = listOf(
            RuneLink("continuity", "protect", 1.0),
            RuneLink("continuity", "route", 0.9),
            RuneLink("continuity", "transform", 0.9),
            RuneLink("continuity", "mesh", 1.0),
            RuneLink("protect", "mesh", 0.7),
            RuneLink("route", "mesh", 0.8),
            RuneLink("transform", "mesh", 0.8),
        ),
    )
}
