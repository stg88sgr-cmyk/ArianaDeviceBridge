package de.snowworks.ariana.generator

import android.content.Context
import de.snowworks.ariana.ArianaGate
import de.snowworks.ariana.bridge.BridgeTokenStore
import org.json.JSONObject
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Authenticated local-only HTTP surface for the image generator runtime.
 * Uses the same encrypted x-ariana-token as the primary Ariana bridge.
 */
object GeneratorLoopbackServer {
    const val PORT = 8766
    private const val MAX_BODY_BYTES = 8 * 1024
    private const val MAX_HTTP_LINE_BYTES = 8 * 1024
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var worker: Thread? = null

    fun isRunning(): Boolean = running.get()

    @Synchronized
    fun start(context: Context) {
        if (running.get()) return
        val app = context.applicationContext
        val gate = ArianaGate(app)
        if (!gate.isMasterEnabled || gate.isBlocked) return
        val token = BridgeTokenStore(app).getOrCreate()

        // A process death can leave a persisted job in RUNNING although no worker
        // survived. Mark those jobs explicitly instead of pretending they still run.
        GeneratorJobStore(app).recoverInterruptedJobs()

        running.set(true)
        worker = Thread({
            try {
                ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1")).use { server ->
                    server.soTimeout = 1000
                    serverSocket = server
                    while (running.get()) {
                        try {
                            server.accept().use { socket -> handle(app, socket, token) }
                        } catch (_: SocketTimeoutException) {
                            // Re-check stop flag.
                        }
                    }
                }
            } catch (_: Exception) {
                running.set(false)
            } finally {
                serverSocket = null
                running.set(false)
            }
        }, "ArianaGeneratorLoopback").also {
            it.isDaemon = true
            it.start()
        }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker?.interrupt()
        worker = null
    }

    private fun handle(context: Context, socket: Socket, token: String) {
        socket.soTimeout = 5000
        val input = socket.getInputStream()
        val requestLine = runCatching { readHttpLine(input) }.getOrNull()
            ?: return respond(socket, 400, error("INVALID_REQUEST"))
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(socket, 400, error("INVALID_REQUEST"))
        val method = parts[0].uppercase()
        val path = parts[1]

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = runCatching { readHttpLine(input) }.getOrNull()
                ?: return respond(socket, 400, error("INVALID_HEADERS"))
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx <= 0) return respond(socket, 400, error("INVALID_HEADERS"))
            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }

        if (headers["x-ariana-token"] != token) {
            return respond(socket, 401, error("UNAUTHORIZED"))
        }

        val gate = ArianaGate(context)
        if (!gate.isMasterEnabled || gate.isBlocked) {
            return respond(socket, 403, error("MASTER_DISABLED"))
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) {
            return respond(socket, 413, error("REQUEST_TOO_LARGE"))
        }
        val bodyBytes = if (length > 0) readExactBytes(input, length) else ByteArray(0)
        if (bodyBytes == null) return respond(socket, 400, error("INCOMPLETE_BODY"))

        val result = GeneratorBridge.handle(
            context = context,
            method = method,
            path = path,
            body = bodyBytes.toString(Charsets.UTF_8),
        )
        respond(socket, result.status, result.body.put("requestId", UUID.randomUUID().toString()))
    }

    private fun readHttpLine(input: InputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size <= MAX_HTTP_LINE_BYTES) {
            val value = input.read()
            if (value == -1) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), Charsets.US_ASCII)
            if (value == '\n'.code) return String(bytes.toByteArray(), Charsets.US_ASCII)
            if (value != '\r'.code) bytes.add(value.toByte())
        }
        throw IllegalArgumentException("HTTP line too long")
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray? {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(data, offset, length - offset)
            if (count <= 0) return null
            offset += count
        }
        return data
    }

    private fun error(code: String) = JSONObject()
        .put("ok", false)
        .put("requestId", UUID.randomUUID().toString())
        .put("error", code)

    private fun respond(socket: Socket, status: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            422 -> "Unprocessable Entity"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Error"
        }
        socket.getOutputStream().use { out ->
            out.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(body)
            out.flush()
        }
    }
}
