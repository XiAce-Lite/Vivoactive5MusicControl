package com.xiace.musicctrl.companion

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Tiny HTTP server on 127.0.0.1:17865 — watch → phone commands only.
 */
class LocalCommandServer(
    private val onCommand: (cmd: String, vol: Int?) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "MusicCtrlHttp", isDaemon = true) {
            try {
                val ss = ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))
                server = ss
                StatusBus.log("HTTP待ち受け 127.0.0.1:$PORT (時計→携帯用)")
                while (running.get()) {
                    val socket = ss.accept()
                    thread(isDaemon = true) { handleClient(socket) }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    StatusBus.log("HTTPサーバエラー: ${e.message}")
                    Log.e(TAG, "server failed", e)
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val input = BufferedInputStream(socket.getInputStream())
            val reader = BufferedReader(InputStreamReader(input))
            val line = reader.readLine() ?: return
            val parts = line.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
            }

            val params = parseQuery(path)
            val cmd = params["c"]
            if (cmd != null) {
                val vol = params["v"]?.toIntOrNull()
                onCommand(cmd, vol)
            }
            val body = "ok".toByteArray(Charsets.UTF_8)
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\n".toByteArray())
            out.write("Content-Type: text/plain\r\n".toByteArray())
            out.write("Content-Length: ${body.size}\r\n".toByteArray())
            out.write("Connection: close\r\n\r\n".toByteArray())
            out.write(body)
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "client error", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseQuery(pathAndQuery: String): Map<String, String> {
        val qIndex = pathAndQuery.indexOf('?')
        if (qIndex < 0) return emptyMap()
        val query = pathAndQuery.substring(qIndex + 1)
        val map = HashMap<String, String>()
        query.split("&").forEach { pair ->
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) {
                map[URLDecoder.decode(kv[0], "UTF-8")] = URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return map
    }

    companion object {
        private const val TAG = "LocalCommandServer"
        const val PORT = 17865
    }
}
