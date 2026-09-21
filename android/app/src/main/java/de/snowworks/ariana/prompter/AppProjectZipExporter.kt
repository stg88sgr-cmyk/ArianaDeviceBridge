package de.snowworks.ariana.prompter

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AppProjectZipExporter {
    fun export(project: GeneratedAppProject, destination: File): File {
        destination.parentFile?.mkdirs()
        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            project.files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return destination
    }
}
