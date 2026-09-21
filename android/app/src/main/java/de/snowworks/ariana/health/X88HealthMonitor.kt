package de.snowworks.ariana.health

enum class X88HealthLevel { HEALTHY, DEGRADED, UNAVAILABLE }

data class X88HealthCheck(
    val component: String,
    val level: X88HealthLevel,
    val detail: String,
)

data class X88HealthReport(
    val stage: Int,
    val checks: List<X88HealthCheck>,
) {
    val level: X88HealthLevel
        get() = when {
            checks.any { it.level == X88HealthLevel.UNAVAILABLE } -> X88HealthLevel.UNAVAILABLE
            checks.any { it.level == X88HealthLevel.DEGRADED } -> X88HealthLevel.DEGRADED
            else -> X88HealthLevel.HEALTHY
        }

    val healthy: Boolean get() = checks.isNotEmpty() && level == X88HealthLevel.HEALTHY
}

class X88HealthMonitor(private val stage: Int = 38) {
    private val checks = linkedMapOf<String, X88HealthCheck>()

    @Synchronized
    fun report(check: X88HealthCheck): X88HealthReport {
        require(check.component.isNotBlank())
        checks[check.component] = check
        return snapshot()
    }

    @Synchronized
    fun snapshot(): X88HealthReport = X88HealthReport(stage, checks.values.toList())

    @Synchronized
    fun clear(component: String): X88HealthReport {
        checks.remove(component)
        return snapshot()
    }
}
