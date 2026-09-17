package de.snowworks.ariana.prompter

data class GeneratedProject(
    val plan: ProjectPlan,
    val files: Map<String, String>,
) {
    init {
        require(files.keys == plan.files.map { it.path }.toSet()) {
            "Generated files must exactly match the project plan."
        }
    }
}

class ComposeProjectGenerator(
    private val planner: ProjectPlanner = ProjectPlanner(),
) {
    fun generate(spec: AppSpec): GeneratedProject {
        val plan = planner.plan(spec)
        val files = plan.files.associate { file ->
            file.path to when (file.role) {
                ProjectFileRole.SETTINGS_GRADLE -> settings(plan)
                ProjectFileRole.ROOT_BUILD_GRADLE -> rootBuild()
                ProjectFileRole.GRADLE_PROPERTIES -> gradleProperties()
                ProjectFileRole.APP_BUILD_GRADLE -> appBuild(plan)
                ProjectFileRole.ANDROID_MANIFEST -> manifest(plan)
                ProjectFileRole.MAIN_ACTIVITY -> mainActivity(spec, plan)
            }
        }
        return GeneratedProject(plan, files)
    }

    private fun settings(plan: ProjectPlan) = """
        pluginManagement {
            repositories { google(); mavenCentral(); gradlePluginPortal() }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories { google(); mavenCentral() }
        }
        rootProject.name = ${kotlinString(plan.projectName)}
        include(":app")
    """.trimIndent() + "\n"

    private fun rootBuild() = """
        plugins {
            id("com.android.application") version "8.9.1" apply false
            id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
        }
    """.trimIndent() + "\n"

    private fun gradleProperties() = """
        org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
        android.useAndroidX=true
        kotlin.code.style=official
    """.trimIndent() + "\n"

    private fun appBuild(plan: ProjectPlan) = """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
            id("org.jetbrains.kotlin.plugin.compose")
        }

        android {
            namespace = ${kotlinString(plan.packageName)}
            compileSdk = ${plan.compileSdk}

            defaultConfig {
                applicationId = ${kotlinString(plan.packageName)}
                minSdk = ${plan.minSdk}
                targetSdk = ${plan.targetSdk}
                versionCode = 1
                versionName = "1.0"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions { jvmTarget = "17" }
            buildFeatures { compose = true }
        }

        dependencies {
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("androidx.compose.ui:ui:1.7.8")
            implementation("androidx.compose.foundation:foundation:1.7.8")
            implementation("androidx.compose.material3:material3:1.3.1")
            implementation("androidx.compose.runtime:runtime-saveable:1.7.8")
        }
    """.trimIndent() + "\n"

    private fun manifest(plan: ProjectPlan) = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application android:allowBackup="true" android:label=${xmlAttribute(plan.projectName)}>
                <activity android:name=".MainActivity" android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent() + "\n"

    private fun mainActivity(spec: AppSpec, plan: ProjectPlan): String = buildString {
        appendLine("package ${plan.packageName}")
        appendLine()
        appendLine("import android.content.Intent")
        appendLine("import android.os.Bundle")
        appendLine("import androidx.activity.ComponentActivity")
        appendLine("import androidx.activity.compose.setContent")
        appendLine("import androidx.compose.foundation.layout.*")
        appendLine("import androidx.compose.foundation.lazy.LazyColumn")
        appendLine("import androidx.compose.foundation.lazy.items")
        appendLine("import androidx.compose.foundation.text.KeyboardOptions")
        appendLine("import androidx.compose.material3.*")
        appendLine("import androidx.compose.runtime.*")
        appendLine("import androidx.compose.runtime.saveable.rememberSaveable")
        appendLine("import androidx.compose.ui.Modifier")
        appendLine("import androidx.compose.ui.platform.LocalContext")
        appendLine("import androidx.compose.ui.text.input.KeyboardType")
        appendLine("import androidx.compose.ui.unit.dp")
        appendLine()
        appendLine("class MainActivity : ComponentActivity() {")
        appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
        appendLine("        super.onCreate(savedInstanceState)")
        appendLine("        setContent { MaterialTheme { GeneratedApp() } }")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("@Composable")
        appendLine("private fun GeneratedApp() {")
        appendLine("    var currentScreen by rememberSaveable { mutableStateOf(${kotlinString(plan.entryScreenId)}) }")
        appendLine("    val textState = remember { mutableStateMapOf<String, String>() }")
        appendLine("    val listState = remember { mutableStateMapOf<String, List<String>>() }")
        appendLine("    val context = LocalContext.current")
        appendLine("    when (currentScreen) {")
        spec.screens.forEach { screen -> renderScreen(this, screen, spec, plan) }
        appendLine("        else -> Text(\"Unbekannter Screen\")")
        appendLine("    }")
        appendLine("}")
    }

    private fun renderScreen(
        out: StringBuilder,
        screen: ScreenSpec,
        spec: AppSpec,
        plan: ProjectPlan,
    ) {
        out.appendLine("        ${kotlinString(screen.id)} -> Column(")
        out.appendLine("            modifier = Modifier.fillMaxSize().padding(16.dp),")
        out.appendLine("            verticalArrangement = Arrangement.spacedBy(12.dp),")
        out.appendLine("        ) {")
        out.appendLine("            Text(${kotlinString(screen.title)}, style = MaterialTheme.typography.headlineSmall)")
        if (screen.id != plan.entryScreenId) {
            out.appendLine("            TextButton(onClick = { currentScreen = ${kotlinString(plan.entryScreenId)} }) { Text(\"Zurück\") }")
        }
        screen.components.forEach { renderComponent(out, it, spec) }
        out.appendLine("        }")
    }

    private fun renderComponent(out: StringBuilder, component: ComponentSpec, spec: AppSpec) {
        val i = "            "
        when (component.type) {
            ComponentType.TEXT -> {
                if (component.stateKey != null) {
                    out.appendLine("${i}Text(textState[${kotlinString(component.stateKey)}].orEmpty())")
                } else {
                    out.appendLine("${i}Text(${kotlinString(component.text.orEmpty())})")
                }
            }
            ComponentType.TEXT_FIELD -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${i}OutlinedTextField(")
                out.appendLine("${i}    value = textState[${kotlinString(key)}].orEmpty(),")
                out.appendLine("${i}    onValueChange = { textState[${kotlinString(key)}] = it },")
                out.appendLine("${i}    label = { Text(${kotlinString(component.text ?: key)}) },")
                out.appendLine("${i}    modifier = Modifier.fillMaxWidth(),")
                out.appendLine("$i)")
            }
            ComponentType.NUMBER_INPUT -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${i}OutlinedTextField(")
                out.appendLine("${i}    value = textState[${kotlinString(key)}].orEmpty(),")
                out.appendLine("${i}    onValueChange = { textState[${kotlinString(key)}] = it },")
                out.appendLine("${i}    label = { Text(${kotlinString(component.text ?: key)}) },")
                out.appendLine("${i}    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),")
                out.appendLine("${i}    modifier = Modifier.fillMaxWidth(),")
                out.appendLine("$i)")
            }
            ComponentType.SWITCH -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${i}Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {")
                out.appendLine("${i}    Text(${kotlinString(component.text ?: key)}, modifier = Modifier.weight(1f))")
                out.appendLine("${i}    Switch(")
                out.appendLine("${i}        checked = textState[${kotlinString(key)}]?.toBoolean() ?: false,")
                out.appendLine("${i}        onCheckedChange = { textState[${kotlinString(key)}] = it.toString() },")
                out.appendLine("${i}    )")
                out.appendLine("$i}")
            }
            ComponentType.LIST -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${i}LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {")
                out.appendLine("${i}    items(listState[${kotlinString(key)}].orEmpty()) { item -> Text(item) }")
                out.appendLine("$i}")
            }
            ComponentType.BUTTON -> renderButton(out, component, spec, i)
        }
    }

    private fun renderButton(out: StringBuilder, component: ComponentSpec, spec: AppSpec, i: String) {
        val action = requireNotNull(component.action)
        out.appendLine("${i}Button(onClick = {")
        when (action.type) {
            ActionType.NAVIGATE ->
                out.appendLine("${i}    currentScreen = ${kotlinString(requireNotNull(action.target))}")
            ActionType.SET_STATE -> {
                val key = component.stateKey ?: component.id
                val value = action.value
                if (value == null) {
                    out.appendLine("${i}    textState[${kotlinString(key)}] = textState[${kotlinString(key)}].orEmpty()")
                } else {
                    out.appendLine("${i}    textState[${kotlinString(key)}] = ${kotlinString(value)}")
                }
            }
            ActionType.CALCULATE -> {
                val resultKey = component.stateKey ?: "result"
                val parts = action.value.orEmpty().split('+', limit = 2).map(String::trim)
                if (parts.size == 2 && parts.all(String::isNotBlank)) {
                    out.appendLine("${i}    val left = textState[${kotlinString(parts[0])}]?.toDoubleOrNull() ?: 0.0")
                    out.appendLine("${i}    val right = textState[${kotlinString(parts[1])}]?.toDoubleOrNull() ?: 0.0")
                    out.appendLine("${i}    textState[${kotlinString(resultKey)}] = (left + right).toString()")
                } else {
                    out.appendLine("${i}    textState[${kotlinString(resultKey)}] = \"0.0\"")
                }
            }
            ActionType.ADD_ITEM -> {
                val key = component.stateKey ?: "items"
                out.appendLine("${i}    val current = listState[${kotlinString(key)}].orEmpty()")
                out.appendLine("${i}    listState[${kotlinString(key)}] = current + (\"Eintrag \" + (current.size + 1))")
            }
            ActionType.REMOVE_ITEM -> {
                val key = component.stateKey ?: "items"
                out.appendLine("${i}    val current = listState[${kotlinString(key)}].orEmpty()")
                out.appendLine("${i}    if (current.isNotEmpty()) listState[${kotlinString(key)}] = current.dropLast(1)")
            }
            ActionType.TOGGLE -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${i}    textState[${kotlinString(key)}] = (!(textState[${kotlinString(key)}]?.toBoolean() ?: false)).toString()")
            }
            ActionType.RESET -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${i}    textState.remove(${kotlinString(key)})")
                out.appendLine("${i}    listState.remove(${kotlinString(key)})")
            }
            ActionType.SHARE -> {
                val shareText = action.value ?: spec.description
                out.appendLine("${i}    val send = Intent(Intent.ACTION_SEND).apply {")
                out.appendLine("${i}        type = \"text/plain\"")
                out.appendLine("${i}        putExtra(Intent.EXTRA_TEXT, ${kotlinString(shareText)})")
                out.appendLine("${i}    }")
                out.appendLine("${i}    context.startActivity(Intent.createChooser(send, null))")
            }
        }
        out.appendLine("$i}) { Text(${kotlinString(component.text ?: component.id)}) }")
    }

    private fun kotlinString(value: String): String = buildString {
        append('"')
        value.forEach {
            when (it) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(it)
            }
        }
        append('"')
    }

    private fun xmlAttribute(value: String): String =
        "\"" + value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;") + "\""
}
