package de.snowworks.app.resonance

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

object ResonanceMapper {

    fun map(graph: RuneGraph): ResonanceSignature {
        validate(graph)

        val nodeWeight = graph.nodes.sumOf { it.weight }
        val linkStrength = graph.links.sumOf { it.strength }
        val maxDirectedLinks = graph.nodes.size * (graph.nodes.size - 1)
        val density = if (maxDirectedLinks == 0) 0.0 else graph.links.size.toDouble() / maxDirectedLinks

        val fundamental = graph.seedFrequencyHz *
            (1.0 + nodeWeight * 0.015) *
            (1.0 + density * 0.05)

        val harmonics = listOf(
            fundamental * 2.0,
            fundamental * 3.0,
            fundamental * 4.0,
        )

        val pulse = min(12.0, max(0.25, 0.5 + linkStrength * 0.125))
        val modulation = 0.75 + graph.nodes.size * 0.13 + density

        return ResonanceSignature(
            fundamentalHz = fundamental,
            harmonicsHz = harmonics,
            pulseHz = pulse,
            modulationHz = modulation,
            fingerprint = fingerprint(graph),
        )
    }

    fun validate(graph: RuneGraph) {
        require(graph.nodes.isNotEmpty()) { "Rune graph must contain nodes" }
        require(graph.seedFrequencyHz in 20.0..20_000.0) { "Seed frequency out of range" }

        val ids = graph.nodes.map { it.id }
        require(ids.toSet().size == ids.size) { "Duplicate rune node ids" }
        require(graph.nodes.all { it.weight > 0.0 && it.weight.isFinite() }) { "Invalid node weight" }
        require(graph.links.all { it.strength > 0.0 && it.strength.isFinite() }) { "Invalid link strength" }

        val idSet = ids.toSet()
        graph.links.forEach { link ->
            require(link.from in idSet && link.to in idSet) {
                "Broken rune link: ${link.from} -> ${link.to}"
            }
        }
    }

    private fun fingerprint(graph: RuneGraph): String {
        val canonical = buildString {
            append(graph.seedFrequencyHz)
            graph.nodes.sortedBy { it.id }.forEach {
                append("|N:").append(it.id).append(':').append(it.symbol).append(':').append(it.weight)
            }
            graph.links.sortedWith(compareBy<RuneLink>({ it.from }, { it.to }, { it.strength })).forEach {
                append("|L:").append(it.from).append('>').append(it.to).append(':').append(it.strength)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
