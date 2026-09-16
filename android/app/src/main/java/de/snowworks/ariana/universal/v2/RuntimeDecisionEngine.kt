package de.snowworks.ariana.universal.v2

import android.os.PowerManager

class ArianaRuntimeDecisionEngine {
    fun budgetFor(t: ArianaTelemetry): ArianaExecutionBudget = when {
        t.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE ->
            ArianaExecutionBudget(1, false, false, false, "Thermal protection")
        t.batteryPercent <= 15 && !t.charging ->
            ArianaExecutionBudget(1, false, false, false, "Low battery")
        t.powerSaveMode ->
            ArianaExecutionBudget(1, false, true, false, "Power saver")
        t.availableRamMb < 1500 ->
            ArianaExecutionBudget(1, true, false, true, "Low memory")
        t.charging && t.availableRamMb > 3000 ->
            ArianaExecutionBudget(3, true, true, true, "High capacity")
        else ->
            ArianaExecutionBudget(2, true, true, true, "Normal capacity")
    }

    fun evaluate(task: ArianaProjectTask, t: ArianaTelemetry, runningCount: Int): TaskAdmissionResult {
        val budget = budgetFor(t)
        val reasons = mutableListOf<String>()

        if (runningCount >= budget.maxParallelTasks) reasons += "Parallelism limit reached"
        if (task.weight == ArianaTaskWeight.HEAVY && !budget.allowHeavyBuilds) {
            reasons += "Heavy work blocked: ${budget.reason}"
        }
        if (t.availableRamMb < 700) reasons += "Critical memory pressure"
        if (t.batteryPercent <= 8 && !t.charging) reasons += "Critical battery level"

        return TaskAdmissionResult(
            allowed = reasons.isEmpty(),
            reasons = reasons.ifEmpty { listOf("Allowed: ${budget.reason}") },
        )
    }
}
