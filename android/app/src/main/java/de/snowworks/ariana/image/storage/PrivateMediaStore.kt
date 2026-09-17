package de.snowworks.ariana.image.storage

import android.content.Context
import de.snowworks.ariana.image.model.GeneratedImage
import de.snowworks.ariana.image.model.GeneratorBackend
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PrivateMediaStore(private val baseDir: File) {
    constructor(context: Context) : this(File(context.filesDir, "private_images"))

    init { require(baseDir.mkdirs() || baseDir.isDirectory) { "Cannot create private image directory" } }

    fun save(
        requestId: String,
        imageBytes: ByteArray,
        width: Int,
        height: Int,
        backend: GeneratorBackend,
        modelId: String?,
    ): GeneratedImage {
        val id = UUID.randomUUID().toString()
        val finalFile = File(baseDir, "$id.png")
        val tempFile = File(baseDir, "$id.tmp.${UUID.randomUUID()}")
        try {
            FileOutputStream(tempFile).use { stream ->
                stream.write(imageBytes)
                stream.fd.sync()
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.inputStream().use { input ->
                    FileOutputStream(finalFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                check(tempFile.delete()) { "temporary image cleanup failed" }
            }
            return GeneratedImage(id, requestId, finalFile.absolutePath, width, height, modelId, backend)
        } catch (t: Throwable) {
            tempFile.delete()
            finalFile.delete()
            throw t
        }
    }

    fun get(id: String): GeneratedImage? {
        if (!isUuid(id)) return null
        val file = File(baseDir, "$id.png")
        if (!file.isFile) return null
        return GeneratedImage(id, "unknown", file.absolutePath, 0, 0, null, GeneratorBackend.LOCAL_DIFFUSION)
    }

    fun list(): List<GeneratedImage> = baseDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) && isUuid(it.nameWithoutExtension) }
        ?.map { GeneratedImage(it.nameWithoutExtension, "unknown", it.absolutePath, 0, 0, null, GeneratorBackend.LOCAL_DIFFUSION) }
        ?.toList()
        ?: emptyList()

    fun delete(id: String): Boolean {
        if (!isUuid(id)) return false
        val file = File(baseDir, "$id.png")
        return file.isFile && file.delete()
    }

    fun deleteAll(): Int {
        var count = 0
        baseDir.listFiles()?.forEach { file ->
            val generatedPng = file.isFile && file.extension.equals("png", ignoreCase = true) && isUuid(file.nameWithoutExtension)
            val temporary = file.isFile && file.name.contains(".tmp.")
            if ((generatedPng || temporary) && file.delete()) count++
        }
        return count
    }

    private fun isUuid(value: String): Boolean = try { UUID.fromString(value); true } catch (_: IllegalArgumentException) { false }
}
