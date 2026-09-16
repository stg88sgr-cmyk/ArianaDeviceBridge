package de.snowworks.ariana.generator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class GeneratorJobStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "generator_jobs").apply { mkdirs() }

    fun create(prompt: String, target8k: Boolean): JSONObject {
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("prompt", prompt)
            .put("target8k", target8k)
            .put("status", "QUEUED")
            .put("createdAt", now)
            .put("updatedAt", now)
            .also(::save)
    }

    @Synchronized
    fun save(job: JSONObject) {
        job.put("updatedAt", System.currentTimeMillis())
        val id = job.getString("id")
        require(ID.matches(id)) { "invalid job id" }
        val target = File(dir, "$id.json")
        val tmp = File(dir, "$id.json.tmp")
        tmp.writeText(job.toString(), Charsets.UTF_8)
        runCatching {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
    }

    fun get(id: String): JSONObject? {
        if (!ID.matches(id)) return null
        val file = File(dir, "$id.json")
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun list(): JSONArray = JSONArray(
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() }
            .sortedByDescending { it.optLong("createdAt") },
    )

    @Synchronized
    fun recoverInterruptedJobs(): Int {
        var recovered = 0
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .forEach { file ->
                val job = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return@forEach
                if (job.optString("status") == "RUNNING") {
                    job.put("status", "INTERRUPTED")
                        .put("error", "PROCESS_RESTARTED")
                    save(job)
                    recovered += 1
                }
            }
        return recovered
    }

    companion object {
        private val ID = Regex("^[0-9a-fA-F-]{36}$")
    }
}
