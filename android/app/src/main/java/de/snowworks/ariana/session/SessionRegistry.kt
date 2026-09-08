package de.snowworks.ariana.session

import de.snowworks.ariana.Feature
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe source of truth for feature sessions. */
object SessionRegistry {
    private val active = ConcurrentHashMap.newKeySet<Feature>()

    fun isActive(feature: Feature): Boolean = feature in active

    fun markActive(feature: Feature, value: Boolean) {
        if (value) active.add(feature) else active.remove(feature)
    }

    fun snapshot(): Set<Feature> = active.toSet()

    fun activeCaptureFeatures(): Set<Feature> =
        active.filterTo(mutableSetOf()) { it.needsForegroundService }

    fun clearCaptureFeatures() {
        active.removeAll { it.needsForegroundService }
    }

    fun clear() {
        active.clear()
    }
}
