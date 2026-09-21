package de.snowworks.ariana.prompter

import android.content.Context
import java.io.File

class AppProjectStore(context: Context) {
    private val root = File(context.filesDir, "AppPrompter/projects")

    init { root.mkdirs() }

    fun save(spec: AppProjectSpec): File {
        val dir = File(root, safeName(spec.appName))
        dir.mkdirs()
        File(dir, "prompt.txt").writeText(spec.originalPrompt, Charsets.UTF_8)
        File(dir, "app-preview.html").writeText(spec.html, Charsets.UTF_8)
        File(dir, "features.txt").writeText(spec.features.joinToString("
"), Charsets.UTF_8)
        return dir
    }

    fun listProjects(): List<String> =
        root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    private fun safeName(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "Snowworks_App" }
}
