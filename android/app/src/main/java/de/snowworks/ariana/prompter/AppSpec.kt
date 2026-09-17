package de.snowworks.ariana.prompter

data class AppSpec(
    val schemaVersion: Int = 1,
    val name: String,
    val packageName: String,
    val description: String,
    val screens: List<ScreenSpec>,
    val localData: Boolean = true,
)

data class ScreenSpec(
    val id: String,
    val title: String,
    val components: List<ComponentSpec>,
)

data class ComponentSpec(
    val id: String,
    val type: ComponentType,
    val text: String? = null,
    val stateKey: String? = null,
    val action: ActionSpec? = null,
)

data class ActionSpec(
    val type: ActionType,
    val target: String? = null,
    val value: String? = null,
)

enum class ComponentType {
    TEXT,
    BUTTON,
    TEXT_FIELD,
    LIST,
    SWITCH,
    NUMBER_INPUT,
}

enum class ActionType {
    NAVIGATE,
    SET_STATE,
    CALCULATE,
    ADD_ITEM,
    REMOVE_ITEM,
    TOGGLE,
    RESET,
    SHARE,
}
