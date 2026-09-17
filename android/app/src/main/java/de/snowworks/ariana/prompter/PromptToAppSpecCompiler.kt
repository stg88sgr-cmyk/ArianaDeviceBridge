package de.snowworks.ariana.prompter

import java.util.Locale

class PromptToAppSpecCompiler {
    fun compile(prompt: String): AppSpec {
        val normalizedPrompt = prompt.trim()
        require(normalizedPrompt.isNotBlank()) { "Prompt must not be blank." }

        val lower = normalizedPrompt.lowercase(Locale.ROOT)
        val appName = deriveName(normalizedPrompt)
        val requestedKinds = buildList {
            if (containsAny(lower, "liste", "list", "inventar", "inventory", "todo", "aufgaben", "katalog")) add(ScreenKind.LIST)
            if (containsAny(lower, "formular", "form", "eingabe", "kontakt", "profile", "profil")) add(ScreenKind.FORM)
            if (containsAny(lower, "einstellung", "settings", "optionen", "preferences")) add(ScreenKind.SETTINGS)
            if (containsAny(lower, "rechner", "calculator", "berechnung", "calculate", "kalkulation")) add(ScreenKind.CALCULATOR)
        }.distinct().take(AppSpecValidator.MAX_SCREENS - 1)

        val secondaryScreens = requestedKinds.map(::screenFor)
        val mainComponents = mutableListOf<ComponentSpec>(
            ComponentSpec(
                id = "introText",
                type = ComponentType.TEXT,
                text = normalizedPrompt.take(180),
            ),
        )

        if (secondaryScreens.isEmpty()) {
            mainComponents += ComponentSpec(
                id = "startButton",
                type = ComponentType.BUTTON,
                text = "Start",
                stateKey = "started",
                action = ActionSpec(type = ActionType.SET_STATE, value = "true"),
            )
        } else {
            secondaryScreens.forEach { screen ->
                mainComponents += ComponentSpec(
                    id = "open${screen.id.replaceFirstChar { it.uppercaseChar() }}",
                    type = ComponentType.BUTTON,
                    text = screen.title,
                    action = ActionSpec(type = ActionType.NAVIGATE, target = screen.id),
                )
            }
        }

        val mainScreen = ScreenSpec(
            id = "main",
            title = appName,
            components = mainComponents,
        )

        return AppSpec(
            name = appName,
            packageName = "de.snowworks.generated.${packageSuffix(appName)}",
            description = normalizedPrompt.take(240),
            screens = listOf(mainScreen) + secondaryScreens,
            localData = true,
        )
    }

    private fun screenFor(kind: ScreenKind): ScreenSpec = when (kind) {
        ScreenKind.LIST -> ScreenSpec(
            id = "list",
            title = "Liste",
            components = listOf(
                ComponentSpec(id = "listTitle", type = ComponentType.TEXT, text = "Einträge"),
                ComponentSpec(id = "items", type = ComponentType.LIST, stateKey = "items"),
                ComponentSpec(
                    id = "addItem",
                    type = ComponentType.BUTTON,
                    text = "Hinzufügen",
                    stateKey = "items",
                    action = ActionSpec(type = ActionType.ADD_ITEM),
                ),
            ),
        )

        ScreenKind.FORM -> ScreenSpec(
            id = "form",
            title = "Eingabe",
            components = listOf(
                ComponentSpec(id = "formTitle", type = ComponentType.TEXT, text = "Daten eingeben"),
                ComponentSpec(id = "input", type = ComponentType.TEXT_FIELD, stateKey = "input"),
                ComponentSpec(
                    id = "saveInput",
                    type = ComponentType.BUTTON,
                    text = "Speichern",
                    stateKey = "input",
                    action = ActionSpec(type = ActionType.SET_STATE),
                ),
            ),
        )

        ScreenKind.SETTINGS -> ScreenSpec(
            id = "settings",
            title = "Einstellungen",
            components = listOf(
                ComponentSpec(id = "settingsTitle", type = ComponentType.TEXT, text = "Einstellungen"),
                ComponentSpec(
                    id = "featureToggle",
                    type = ComponentType.SWITCH,
                    text = "Aktiv",
                    stateKey = "featureEnabled",
                    action = ActionSpec(type = ActionType.TOGGLE),
                ),
            ),
        )

        ScreenKind.CALCULATOR -> ScreenSpec(
            id = "calculator",
            title = "Rechner",
            components = listOf(
                ComponentSpec(id = "numberA", type = ComponentType.NUMBER_INPUT, stateKey = "numberA"),
                ComponentSpec(id = "numberB", type = ComponentType.NUMBER_INPUT, stateKey = "numberB"),
                ComponentSpec(
                    id = "calculate",
                    type = ComponentType.BUTTON,
                    text = "Berechnen",
                    stateKey = "result",
                    action = ActionSpec(type = ActionType.CALCULATE, value = "numberA+numberB"),
                ),
                ComponentSpec(id = "result", type = ComponentType.TEXT, stateKey = "result"),
            ),
        )
    }

    private fun deriveName(prompt: String): String {
        val quoted = Regex("[\\\"“”']([^\\\"“”']{2,40})[\\\"“”']").find(prompt)?.groupValues?.get(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted.take(40)

        val compact = prompt
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercaseChar() } }

        return compact.ifBlank { "Snowworks App" }.take(40)
    }

    private fun packageSuffix(name: String): String {
        val raw = name
            .lowercase(Locale.ROOT)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9_]"), "")
            .take(24)

        val nonEmpty = raw.ifBlank { "app" }
        return if (nonEmpty.first().isLetter()) nonEmpty else "app$nonEmpty"
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any(text::contains)

    private enum class ScreenKind {
        LIST,
        FORM,
        SETTINGS,
        CALCULATOR,
    }
}
