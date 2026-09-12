package de.snowworks.ariana.bridge

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Owns the one local Ariana model file copied into app-private storage. */
class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelDir = File(appContext.filesDir, "ariana_local")
    private val modelFile = File(modelDir, "model.task")

    data class Status(
        val installed: Boolean,
        val enabled: Boolean,
        val file: File?,
        val sizeBytes: Long,
        val displayName: String?,
    )

    fun status(): Status {
        val installed = modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES
        return Status(
            installed = installed,
            enabled = installed && prefs.getBoolean(KEY_ENABLED, false),
            file = modelFile.takeIf { installed },
            sizeBytes = if (installed) modelFile.length() else 0L,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
        )
    }

    fun importModel(uri: Uri): Status {
        modelDir.mkdirs()
        val temp = File(modelDir, "model.task.partial")
        runCatching { temp.delete() }

        var total = 0L
        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("Modelldatei konnte nicht geöffnet werden.")
        try {
            input.use { source ->
                FileOutputStream(temp).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count <= 0) break
                        total += count
                        if (total > MAX_MODEL_BYTES) {
                            error("Modelldatei ist größer als 2 GB.")
                        }
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            require(total > MIN_MODEL_BYTES) { "Modelldatei ist zu klein oder ungültig." }
            if (modelFile.exists() && !modelFile.delete()) {
                error("Altes lokales Modell konnte nicht ersetzt werden.")
            }
            if (!temp.renameTo(modelFile)) {
                error("Lokales Modell konnte nicht übernommen werden.")
            }
            prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_DISPLAY_NAME, uri.lastPathSegment?.takeLast(120) ?: "model.task")
                .apply()
            return status()
        } catch (error: Throwable) {
            runCatching { temp.delete() }
            throw error
        }
    }

    fun setEnabled(enabled: Boolean) {
        val installed = modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES
        prefs.edit().putBoolean(KEY_ENABLED, enabled && installed).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
        runCatching { File(modelDir, "model.task.partial").delete() }
        runCatching { modelFile.delete() }
    }

    companion object {
        private const val PREFS_NAME = "ariana_local_model_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val MIN_MODEL_BYTES = 1L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
