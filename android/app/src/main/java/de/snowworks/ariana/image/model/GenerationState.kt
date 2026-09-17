package de.snowworks.ariana.image.model

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Queued : GenerationState
    data class Running(val progress: Float) : GenerationState
    data class Completed(val image: GeneratedImage) : GenerationState
    data class Failed(val code: String, val message: String) : GenerationState
    data object Cancelled : GenerationState
}
