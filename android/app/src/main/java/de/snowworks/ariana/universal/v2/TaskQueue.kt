package de.snowworks.ariana.universal.v2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ArianaTaskQueue {
    private val _tasks = MutableStateFlow<List<ArianaProjectTask>>(emptyList())
    val tasks: StateFlow<List<ArianaProjectTask>> = _tasks.asStateFlow()

    fun enqueue(task: ArianaProjectTask) {
        _tasks.update { current ->
            if (current.any { it.taskId == task.taskId }) current else current + task
        }
    }

    fun update(taskId: String, transform: (ArianaProjectTask) -> ArianaProjectTask) {
        _tasks.update { list -> list.map { if (it.taskId == taskId) transform(it) else it } }
    }

    fun nextRunnable(): ArianaProjectTask? {
        val byId = _tasks.value.associateBy { it.taskId }
        return _tasks.value
            .filter { it.status == ArianaTaskStatus.QUEUED || it.status == ArianaTaskStatus.WAITING }
            .filter { task -> task.dependsOn.all { byId[it]?.status == ArianaTaskStatus.COMPLETED } }
            .sortedWith(compareByDescending<ArianaProjectTask> { it.priority.weight }.thenBy { it.createdAt })
            .firstOrNull()
    }
}
