package de.snowworks.ariana.system

/**
 * Release channels for X88 itself. These are separate from Samsung/Android OTA
 * channels so X88 can evolve without pretending to replace One UI.
 */
enum class X88UpdateChannel(val label: String) {
    STABLE("Stable"),
    BETA("Beta"),
    DEV("Dev"),
}
