package de.snowworks.ariana.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class HttpsImageGeneratorProvider(private val context: Context, private val config: SecureGeneratorProviderStore.Config) {
    data class Asset(val path: String, val sha256: String, val width: Int, val height: Int)
    fun health(): JSONObject {
        val connection = (validatedUri().toURL().openConnection() as HttpsURLConnection).apply { requestMethod = "GET"; connectTimeout = 5_000; readTimeout = 5_000; instanceFollowRedirects = false; setRequestProperty("Accept", "application/json"); if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}") }
        return try { val code = connection.responseCode; JSONObject().put("state", when { code == 401 || code == 403 -> "UNAUTHORIZED"; code in 500..599 -> "DEGRADED"; else -> "READY" }).put("httpStatus", code) } catch (t: Throwable) { JSONObject().put("state", "UNREACHABLE").put("error", t.javaClass.simpleName) } finally { connection.disconnect() }
    }
    fun generate(prompt: String, target8k: Boolean, jobId: String): Asset {
        val payload = JSONObject().put("model", config.model.trim()).put("prompt", prompt.trim()).put("size", "1024x1024").put("n", 1).put("response_format", "b64_json").toString().toByteArray(Charsets.UTF_8)
        val connection = (validatedUri().toURL().openConnection() as HttpsURLConnection).apply { requestMethod = "POST"; connectTimeout = 7_000; readTimeout = 60_000; instanceFollowRedirects = false; doOutput = true; useCaches = false; setRequestProperty("Accept", "application/json"); setRequestProperty("Content-Type", "application/json; charset=utf-8"); if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}") }
        try {
            connection.setFixedLengthStreamingMode(payload.size); connection.outputStream.use { it.write(payload) }; val code = connection.responseCode; if (code !in 200..299) error("PROVIDER_HTTP_$code")
            val root = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }); val item = root.optJSONArray("data")?.optJSONObject(0) ?: error("PROVIDER_RESPONSE_INVALID"); var bytes = Base64.decode(item.getString("b64_json"), Base64.DEFAULT)
            var width = 0; var height = 0
            if (target8k) { val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("IMAGE_DECODE_FAILED"); val scaled = Bitmap.createScaledBitmap(source, 7680, 4320, true); val out = ByteArrayOutputStream(); scaled.compress(Bitmap.CompressFormat.JPEG, 95, out); bytes = out.toByteArray(); width = scaled.width; height = scaled.height; if (scaled !== source) scaled.recycle(); source.recycle() } else { val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts); width = opts.outWidth; height = opts.outHeight }
            val file = File(File(context.filesDir, "generator_assets").apply { mkdirs() }, "$jobId.jpg"); file.writeBytes(bytes); return Asset(file.absolutePath, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, width, height)
        } finally { connection.disconnect() }
    }
    private fun validatedUri(): URI { val uri = URI(config.endpoint.trim()); require(uri.scheme.equals("https", true)); val host = uri.host ?: error("host required"); require(uri.userInfo == null && uri.fragment == null); require(!host.equals("localhost", true) && !host.endsWith(".local", true) && !IP_LITERAL.matches(host)); val addresses = InetAddress.getAllByName(host); require(addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }); return uri }
    companion object { private val IP_LITERAL = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+$") }
}
