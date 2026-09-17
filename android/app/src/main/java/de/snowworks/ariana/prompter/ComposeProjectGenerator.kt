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
        val files = plan.files.associate { planned ->
            planned.path to when (planned.role) {
                ProjectFileRole.SETTINGS_GRADLE -> renderSettings(plan)
                ProjectFileRole.ROOT_BUILD_GRADLE -> renderRootBuild()
                ProjectFileRole.GRADLE_PROPERTIES -> renderGradleProperties()
                ProjectFileRole.APP_BUILD_GRADLE -> renderAppBuild(plan)
                ProjectFileRole.ANDROID_MANIFEST -> renderManifest(plan)
                ProjectFileRole.MAIN_ACTIVITY -> renderMainActivity(spec, plan)
            }
        }
        return GeneratedProject(plan = plan, files = files)
    }

    private fun renderSettings(plan: ProjectPlan): String = """
        pluginManagement {
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }
        rootProject.name = ${kotlinString(plan.projectName)}
        include(":app")
    """.trimIndent() + "\n"

    private fun renderRootBuild(): String = """
        plugins {
            id("com.android.application") version "8.9.1" apply false
            id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
        }
    """.trimIndent() + "\n"

    private fun renderGradleProperties(): String = """
        org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
        android.useAndroidX=true
        kotlin.code.style=official
    """.trimIndent() + "\n"

    private fun renderAppBuild(plan: ProjectPlan): String = """
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

            buildTypes {
                release {
                    isMinifyEnabled = false
                }
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

    private fun renderManifest(plan: ProjectPlan): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:allowBackup="true"
                android:label=${xmlAttribute(plan.projectName)}
                android:supportsRtl="true"
                android:theme="@style/Theme.Material3.DayNight.NoActionBar">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent() + "\n"

    private fun renderMainActivity(spec: AppSpec, plan: ProjectPlan): String {
        val out = StringBuilder()
        out.appendLine("package ${plan.packageName}")
        out.appendLine()
        out.appendLine("import android.content.Intent")
        out.appendLine("import android.os.Bundle")
        out.appendLine("import androidx.activity.ComponentActivity")
        out.appendLine("import androidx.activity.compose.setContent")
        out.appendLine("import androidx.compose.foundation.layout.*")
        out.appendLine("import androidx.compose.foundation.lazy.LazyColumn")
        out.appendLine("import androidx.compose.foundation.lazy.items")
        out.appendLine("import androidx.compose.foundation.text.KeyboardOptions")
        out.appendLine("import androidx.compose.material3.*")
        out.appendLine("import androidx.compose.runtime.*")
        out.appendLine("import androidx.compose.runtime.saveable.rememberSaveable")
        out.appendLine("import androidx.compose.ui.Modifier")
        out.appendLine("import androidx.compose.ui.platform.LocalContext")
        out.appendLine("import androidx.compose.ui.text.input.KeyboardType")
        out.appendLine("import androidx.compose.ui.unit.dp")
        out.appendLine()
        out.appendLine("class MainActivity : ComponentActivity() {")
        out.appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
        out.appendLine("        super.onCreate(savedInstanceState)")
        out.appendLine("        setContent { MaterialTheme { GeneratedApp() } }")
        out.appendLine("    }")
        out.appendLine("}")
        out.appendLine()
        out.appendLine("@Composable")
        out.appendLine("private fun GeneratedApp() {")
        out.appendLine("    var currentScreen by rememberSaveable { mutableStateOf(${kotlinString(plan.entryScreenId)}) }")
        out.appendLine("    val textState = remember { mutableStateMapOf<String, String>() }")
        out.appendLine("    val listState = remember { mutableStateMapOf<String, List<String>>() }")
        out.appendLine("    val context = LocalContext.current")
        out.appendLine()
        out.appendLine("    when (currentScreen) {")
        spec.screens.forEach { screen ->
            out.appendLine("        ${kotlinString(screen.id)} -> {")
            out.appendLine("            Scaffold(")
            out.appendLine("                topBar = { TopAppBar(title = { Text(${kotlinString(screen.title)}) }) },")
            out.appendLine("            ) { padding ->")
            out.appendLine("                Column(")
            out.appendLine("                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),")
            out.appendLine("                    verticalArrangement = Arrangement.spacedBy(12.dp),")
            out.appendLine("                ) {")
            if (screen.id != plan.entryScreenId) {
                out.appendLine("                    TextButton(onClick = { currentScreen = ${kotlinString(plan.entryScreenId)} }) { Text(\"Zurück\") }")
            }
            screen.components.forEach { component ->
                renderComponent(out, component, spec)
            }
            out.appendLine("                }")
            out.appendLine("            }")
            out.appendLine("        }")
        }
        out.appendLine("        else -> Text(\"Unbekannter Screen\")")
        out.appendLine("    }")
        out.appendLine("}")
        return out.toString()
    }

    private fun renderComponent(out: StringBuilder, component: ComponentSpec, spec: AppSpec) {
        val indent = "                    "
        when (component.type) {
            ComponentType.TEXT -> {
                val stateKey = component.stateKey
                if (!stateKey.isNullOrBlank()) {
                    out.appendLine("${indent}Text(textState[${kotlinString(stateKey)}].orEmpty())")
                } else {
                    out.appendLine("${indent}Text(${kotlinString(component.text.orEmpty())})")
                }
            }

            ComponentType.TEXT_FIELD -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${indent}OutlinedTextField(")
                out.appendLine("${indent}    value = textState[${kotlinString(key)}].orEmpty(),")
                out.appendLine("${indent}    onValueChange = { textState[${kotlinString(key)}] = it },")
                out.appendLine("${indent}    label = { Text(${kotlinString(component.text ?: key)}) },")
                out.appendLine("${indent}    modifier = Modifier.fillMaxWidth(),")
                out.appendLine("$indent)")
            }

            ComponentType.NUMBER_INPUT -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${indent}OutlinedTextField(")
                out.appendLine("${indent}    value = textState[${kotlinString(key)}].orEmpty(),")
                out.appendLine("${indent}    onValueChange = { textState[${kotlinString(key)}] = it },")
                out.appendLine("${indent}    label = { Text(${kotlinString(component.text ?: key)}) },")
                out.appendLine("${indent}    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),")
                out.appendLine("${indent}    modifier = Modifier.fillMaxWidth(),")
                out.appendLine("$indent)")
            }

            ComponentType.SWITCH -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${indent}Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {")
                out.appendLine("${indent}    Text(${kotlinString(component.text ?: key)}, modifier = Modifier.weight(1f))")
                out.appendLine("${indent}    Switch(")
                out.appendLine("${indent}        checked = textState[${kotlinString(key)}]?.toBoolean() ?: false,")
                out.appendLine("${indent}        onCheckedChange = { checked -> textState[${kotlinString(key)}] = checked.toString() },")
                out.appendLine("${indent}    )")
                out.appendLine("$indent}")
            }

            ComponentType.LIST -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${indent}LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {")
                out.appendLine("${indent}    items(listState[${kotlinString(key)}].orEmpty()) { item ->")
                out.appendLine("${indent}        Text(item, modifier = Modifier.padding(vertical = 4.dp))")
                out.appendLine("${indent}    }")
                out.appendLine("$indent}")
            }

            ComponentType.BUTTON -> renderButton(out, component, spec, indent)
        }
    }

    private fun renderButton(
        out: StringBuilder,
        component: ComponentSpec,
        spec: AppSpec,
        indent: String,
    ) {
        val action = requireNotNull(component.action) { "Validated buttons must have an action." }
        out.appendLine("${indent}Button(onClick = {")
        when (action.type) {
            ActionType.NAVIGATE ->
                out.appendLine("${indent}    currentScreen = ${kotlinString(requireNotNull(action.target))}")

            ActionType.SET_STATE -> {
                val key = component.stateKey ?: component.id
                if (action.value != null) {
                    out.appendLine("${indent}    textState[${kotlinString(key)}] = ${kotlinString(action.value)}")
                } else {
                    out.appendLine("${indent}    textState[${kotlinString(key)}] = textState[${kotlinString(key)}].orEmpty()")
                }
            }

            ActionType.CALCULATE -> {
                val targetKey = component.stateKey ?: "result"
                val expression = action.value.orEmpty()
                val parts = expression.split('+', limit = 2)
                if (parts.size == 2 && parts.all { it.isNotBlank() }) {
                    out.appendLine("${indent}    val left = textState[${kotlinString(parts[0].trim())}]?.toDoubleOrNull() ?: 0.0")
                    out.appendLine("${indent}    val right = textState[${kotlinString(parts[1].trim())}]?.toDoubleOrNull() ?: 0.0")
                    out.appendLine("${indent}    textState[${kotlinString(targetKey)}] = (left + right).toString()")
                } else {
                    out.appendLine("${indent}    textState[${kotlinString(targetKey)}] = \"0.0\"")
                }
            }

            ActionType.ADD_ITEM -> {
                val key = component.stateKey ?: "items"
                out.appendLine("${indent}    val current = listState[${kotlinString(key)}].orEmpty()")
                out.appendLine("${indent}    listState[${kotlinString(key)}] = current + \"Eintrag \\${current.size + 1}\"")
            }

            ActionType.REMOVE_ITEM -> {
                val key = component.stateKey ?: "items"
                out.appendLine("${indent}    val current = listState[${kotlinString(key)}].orEmpty()")
                out.appendLine("${indent}    if (current.isNotEmpty()) listState[${kotlinString(key)}] = current.dropLast(1)")
            }

            ActionType.TOGGLE -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${indent}    val next = !(textState[${kotlinString(key)}]?.toBoolean() ?: false)")
                out.appendLine("${indent}    textState[${kotlinString(key)}] = next.toString()")
            }

            ActionType.RESET -> {
                val key = component.stateKey ?: component.id
                out.appendLine("${indent}    textState.remove(${kotlinString(key)})")
                out.appendLine("${indent}    listState.remove(${kotlinString(key)})")
            }

            ActionType.SHARE -> {
                val shareText = action.value ?: spec.description
                out.appendLine("${indent}    val send = Intent(Intent.ACTION_SEND).apply {")
                out.appendLine("${indent}        type = \"text/plain\"")
                out.appendLine("${indent}        putExtra(Intent.EXTRA_TEXT, ${kotlinString(shareText)})")
                out.appendLine("${indent}    }")
                out.appendLine("${indent}    context.startActivity(Intent.createChooser(send, null))")
            }
        }
        out.appendLine("$indent}) { Text(${kotlinString(component.text ?: component.id)}) }")
    }

    private fun kotlinString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '$' -> append("\\$")
                else -> append(ch)
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
