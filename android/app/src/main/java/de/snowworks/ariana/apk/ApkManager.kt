package de.snowworks.ariana.apk

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import de.snowworks.ariana.bridge.UniversalBridgeStateStore
import java.io.File

class ApkManager(
    context: Context,
    private val stateStore: UniversalBridgeStateStore = UniversalBridgeStateStore(context),
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    data class DownloadCandidate(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val dateModifiedSeconds: Long,
    )

    fun listDownloadedApks(limit: Int = 25): List<DownloadCandidate> {
        if (!stateStore.apkManagerEnabled || !stateStore.watchDownloads) return emptyList()

        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%.apk")
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

        val result = mutableListOf<DownloadCandidate>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

            while (cursor.moveToNext() && result.size < limit.coerceIn(1, 100)) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: continue
                result += DownloadCandidate(
                    uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    displayName = name,
                    sizeBytes = cursor.getLong(sizeIndex),
                    dateModifiedSeconds = cursor.getLong(modifiedIndex),
                )
            }
        }
        return result
    }

    fun stage(candidate: DownloadCandidate): File {
        require(candidate.displayName.endsWith(".apk", ignoreCase = true)) { "Not an APK" }
        val dir = File(appContext.filesDir, "apk-staging").apply { mkdirs() }
        val safeName = candidate.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, safeName)

        resolver.openInputStream(candidate.uri)?.use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        } ?: error("Unable to open downloaded APK")

        return target
    }

    fun stageLatest(): File? = listDownloadedApks(limit = 1).firstOrNull()?.let(::stage)

    fun clearStaging(keepFileName: String? = null) {
        val dir = File(appContext.filesDir, "apk-staging")
        dir.listFiles()?.forEach { file ->
            if (file.name != keepFileName) file.delete()
        }
    }
}
