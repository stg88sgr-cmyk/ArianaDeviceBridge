package de.snowworks.ariana.joyfun

enum class JoyFunProfile { CALM, BRIGHT, PLAYFUL, SOCIAL, CELEBRATION }

data class JoyFunThemeSignal(
    val profile: JoyFunProfile,
    val glow: Float,
    val motion: Float,
    val sparkle: Float,
    val voiceEnergy: Float,
)

fun JoyFunState.profile(): JoyFunProfile = when {
    joy > 0.82f && funLevel > 0.72f && energy > 0.65f -> JoyFunProfile.CELEBRATION
    playfulness > 0.68f && funLevel > 0.62f -> JoyFunProfile.PLAYFUL
    socialWarmth > 0.72f -> JoyFunProfile.SOCIAL
    activation > 0.62f -> JoyFunProfile.BRIGHT
    else -> JoyFunProfile.CALM
}

fun JoyFunState.toThemeSignal(): JoyFunThemeSignal = JoyFunThemeSignal(
    profile = profile(),
    glow = (joy * 0.65f + funLevel * 0.35f).coerceIn(0f, 1f),
    motion = (energy * 0.55f + playfulness * 0.45f).coerceIn(0f, 1f),
    sparkle = (funLevel * 0.60f + curiosity * 0.40f).coerceIn(0f, 1f),
    voiceEnergy = (energy * 0.50f + joy * 0.50f).coerceIn(0f, 1f),
)
