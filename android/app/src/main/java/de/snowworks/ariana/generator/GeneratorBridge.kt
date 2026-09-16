package de.snowworks.ariana.generator

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors

object GeneratorBridge {
    data class Result(val status: Int, val body: JSONObject)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ArianaGenerator").apply { isDaemon = true } }

    fun handle(context: Context, method: String, path: String, body: String): Result {
        val app = context.applicationContext
        val store = GeneratorJobStore(app)
        return when {
            path == "/generator/status" && method == "GET" -> {
                val config = SecureGeneratorProviderStore(app).load()
                val health = config?.let { HttpsImageGeneratorProvider(app, it).health() }
                    ?: JSONObject().put("state", "UNCONFIGURED")
                Result(200, JSONObject().put("ok", true).put("provider", health).put("jobs", store.list().length()))
            }
            path == "/generator/jobs" && method == "GET" -> Result(200, JSONObject().put("ok", true).put("jobs", store.list()))
            path == "/generator/jobs" && method == "POST" -> createJob(app, store, body)
            path.startsWith("/generator/jobs/") && method == "GET" -> {
                val id = path.removePrefix("/generator/jobs/")
                val job = store.get(id) ?: return Result(404, error("JOB_NOT_FOUND"))
                Result(200, JSONObject().put("ok", true).put("job", job))
            }
            path == "/generator/provider/probe" && method == "GET" -> {
                val config = SecureGeneratorProviderStore(app).load() ?: return Result(503, error("PROVIDER_UNCONFIGURED"))
                Result(200, JSONObject().put("ok", true).put("provider", HttpsImageGeneratorProvider(app, config).health()))
            }
            path.startsWith("/generator/") -> Result(405, error("METHOD_NOT_ALLOWED"))
            else -> Result(404, error("NOT_FOUND"))
        }
    }

    private fun createJob(context: Context, store: GeneratorJobStore, body: String): Result {
        val config = SecureGeneratorProviderStore(context).load() ?: return Result(503, error("PROVIDER_UNCONFIGURED"))
        val request = runCatching { JSONObject(body) }.getOrElse { return Result(400, error("INVALID_JSON")) }
        val prompt = request.optString("prompt").trim()
        if (prompt.isBlank() || prompt.length > 4000) return Result(400, error("INVALID_PROMPT"))
        val target8k = request.optBoolean("target8k", false)
        val job = store.create(prompt, target8k)
        executor.execute {
            val id = job.getString("id")
            try {
                job.put("status", "RUNNING"); store.save(job)
                val asset = HttpsImageGeneratorProvider(context, config).generate(prompt, target8k, id)
                job.put("status", "COMPLETE").put("assetPath", asset.path).put("sha256", asset.sha256).put("width", asset.width).put("height", asset.height)
                store.save(job)
            } catch (t: Throwable) {
                job.put("status", "FAILED").put("error", t.message?.take(200) ?: t.javaClass.simpleName)
                store.save(job)
            }
        }
        return Result(202, JSONObject().put("ok", true).put("job", job))
    }

    private fun error(code: String) = JSONObject().put("ok", false).put("error", code)
}
