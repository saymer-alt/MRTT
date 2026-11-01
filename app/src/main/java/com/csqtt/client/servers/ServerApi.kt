package com.csqtt.client.servers

import android.content.Context
import com.csqtt.client.ui.createSSHClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.SSHClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal class ServerApiException(message: String) : IOException(message)

/**
 * API управления сервером слушает только 127.0.0.1, поэтому запросы идут через SSH
 * (direct-tcpip) теми же доступами, что и деплой. Наружу никаких веб-портов.
 */
internal class ServerApi(private val context: Context, private val server: VpsServer) {

    suspend fun getObject(path: String): JSONObject = JSONObject(request("GET", path))
    suspend fun getArray(path: String): JSONArray = JSONArray(request("GET", path))
    suspend fun getText(path: String): String = request("GET", path)
    suspend fun post(path: String, body: JSONObject? = null): String = request("POST", path, body?.toString() ?: "{}")
    suspend fun put(path: String, body: JSONObject): String = request("PUT", path, body.toString())
    suspend fun delete(path: String): String = request("DELETE", path)

    suspend fun request(method: String, path: String, body: String? = null): String {
        // Блокирующее чтение SSH не отменяется само: ждём не дольше таймаута и рвём соединение,
        // иначе экран сервера висит на «Загрузка...», пока не перезайти во вкладку.
        val call = DETACHED.async {
            try {
                exchange(connection(), method, path, body)
            } catch (error: ServerApiException) {
                throw error
            } catch (error: IOException) {
                // Закэшированное SSH-соединение могло умереть: один повтор на свежем.
                drop()
                exchange(connection(), method, path, body)
            }
        }
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { call.await() } ?: run {
            call.cancel()
            drop()
            throw ServerApiException("Сервер не ответил за ${REQUEST_TIMEOUT_MS / 1000} с")
        }
    }

    private fun connection(): SSHClient = synchronized(clients) {
        clients[server.id]?.takeIf { it.isConnected && it.isAuthenticated }?.let { return it }
        if (!server.hasSshAuth) throw ServerApiException("Укажите SSH-пароль или ключ сервера")
        createSSHClient(
            context,
            server.host,
            server.sshUser,
            server.sshPassword,
            server.sshPort,
            server.sshPrivateKey,
            server.sshKeyPassphrase,
        ).also { clients[server.id] = it }
    }

    private fun drop() {
        clients.remove(server.id)?.let { runCatching { it.disconnect() } }
    }

    private fun exchange(ssh: SSHClient, method: String, path: String, body: String?): String {
        val payload = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val head = buildString {
            append("$method $path HTTP/1.0\r\n")
            // Сервер подставляет Host в клиентские ссылки, поэтому передаём адрес VPS, а не 127.0.0.1.
            append("Host: ${server.host}\r\n")
            append("Accept: application/json\r\n")
            append("Connection: close\r\n")
            if (body != null) append("Content-Type: application/json\r\n")
            append("Content-Length: ${payload.size}\r\n\r\n")
        }
        val raw = ssh.newDirectConnection("127.0.0.1", server.apiPort).use { channel ->
            channel.outputStream.apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(payload)
                flush()
            }
            channel.inputStream.readBytes()
        }
        return parseResponse(raw)
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 20_000L
        private val DETACHED = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val clients = ConcurrentHashMap<String, SSHClient>()

        fun forget(serverId: String) {
            clients.remove(serverId)?.let { runCatching { it.disconnect() } }
        }

        internal fun parseResponse(raw: ByteArray): String {
            val text = String(raw, Charsets.UTF_8)
            val split = text.indexOf("\r\n\r\n")
            if (split < 0) throw ServerApiException("Сервер не ответил: API управления недоступно")
            val status = text.substring(0, text.indexOf("\r\n").coerceAtLeast(0))
                .split(' ')
                .getOrNull(1)
                ?.toIntOrNull()
                ?: throw ServerApiException("Некорректный ответ API")
            val body = text.substring(split + 4)
            if (status !in 200..299) {
                throw ServerApiException(body.trim().ifBlank { "HTTP $status" }.take(300))
            }
            return body
        }
    }
}
