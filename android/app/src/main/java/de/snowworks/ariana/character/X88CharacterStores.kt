package de.snowworks.ariana.character

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class X88GalleryItem(
    val id: String,
    val uri: String,
    val sceneId: String,
    val prompt: String,
    val createdAt: Long,
    val favorite: Boolean = false,
)

class X88CharacterSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("x88_character_settings", Context.MODE_PRIVATE)

    var sceneId: String
        get() = prefs.getString("scene_id", X88Scenes.master.id) ?: X88Scenes.master.id
        set(value) { prefs.edit().putString("scene_id", value).apply() }

    var aspectRatio: String
        get() = prefs.getString("aspect_ratio", "9:16") ?: "9:16"
        set(value) { prefs.edit().putString("aspect_ratio", value).apply() }
}

class X88CharacterReferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("x88_character_references", Context.MODE_PRIVATE)

    fun list(): List<String> = prefs.getStringSet("uris", emptySet())
        ?.toList()
        ?.sorted()
        .orEmpty()

    fun add(uri: Uri) {
        val next = list().toMutableSet().apply { add(uri.toString()) }
        prefs.edit().putStringSet("uris", next).apply()
    }

    fun clear() {
        prefs.edit().remove("uris").apply()
    }
}

class X88CharacterGalleryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("x88_character_gallery", Context.MODE_PRIVATE)
    private val lock = Any()

    fun list(): List<X88GalleryItem> = synchronized(lock) {
        decode(prefs.getString("items", "[]") ?: "[]").sortedByDescending { it.createdAt }
    }

    fun add(
        uri: String,
        sceneId: String,
        prompt: String,
        favorite: Boolean = false,
    ): X88GalleryItem = synchronized(lock) {
        val item = X88GalleryItem(
            id = UUID.randomUUID().toString(),
            uri = uri,
            sceneId = sceneId,
            prompt = prompt,
            createdAt = System.currentTimeMillis(),
            favorite = favorite,
        )
        val next = listOf(item) + list().filterNot { it.uri == uri }
        write(next)
        item
    }

    fun setFavorite(id: String, favorite: Boolean) = synchronized(lock) {
        write(list().map { if (it.id == id) it.copy(favorite = favorite) else it })
    }

    fun remove(id: String) = synchronized(lock) {
        write(list().filterNot { it.id == id })
    }

    private fun write(items: List<X88GalleryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("uri", item.uri)
                put("sceneId", item.sceneId)
                put("prompt", item.prompt)
                put("createdAt", item.createdAt)
                put("favorite", item.favorite)
            })
        }
        prefs.edit().putString("items", array.toString()).commit()
    }

    private fun decode(raw: String): List<X88GalleryItem> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    X88GalleryItem(
                        id = obj.optString("id"),
                        uri = obj.optString("uri"),
                        sceneId = obj.optString("sceneId", X88Scenes.master.id),
                        prompt = obj.optString("prompt"),
                        createdAt = obj.optLong("createdAt"),
                        favorite = obj.optBoolean("favorite"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
