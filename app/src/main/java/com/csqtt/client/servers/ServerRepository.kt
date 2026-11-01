package com.csqtt.client.servers

import android.content.Context
import com.csqtt.client.MrttConstants
import com.csqtt.client.SecureStringStore
import com.csqtt.client.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** VPS, которым управляет приложение: SSH-доступ для деплоя и для API управления. */
internal data class VpsServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val sshPort: Int = MrttConstants.Network.DEFAULT_SSH_PORT,
    val sshUser: String = "root",
    val sshPassword: String = "",
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val peerPort: Int = MrttConstants.Network.DEFAULT_SERVER_PEER_PORT,
    val apiPort: Int = MrttConstants.Network.DEFAULT_SERVER_WEB_PORT,
    val docker: Boolean = false,
    val mainPassword: String = "",
) {
    val title: String get() = name.ifBlank { host }
    val hasSshAuth: Boolean get() = sshPassword.isNotEmpty() || sshPrivateKey.isNotBlank()
}

internal class ServerRepository(context: Context) {
    private val store = SettingsStore(context)
    private val secure = SecureStringStore(context)

    val servers: Flow<List<VpsServer>> = store.vpsServersJson.map(::decode)

    suspend fun current(): List<VpsServer> = servers.first()

    suspend fun save(server: VpsServer) {
        val list = current().toMutableList()
        val index = list.indexOfFirst { it.id == server.id }
        if (index >= 0) list[index] = server else list += server
        store.saveVpsServersJson(encode(list))
    }

    suspend fun delete(id: String) {
        store.saveVpsServersJson(encode(current().filterNot { it.id == id }))
    }

    /** Один раз переносит старые настройки деплоя (слоты профилей) в список серверов. */
    suspend fun migrateLegacy() {
        if (store.vpsServersMigrated()) return
        val legacy = store.legacyDeployServers().map { snapshot ->
            VpsServer(
                name = snapshot.ip,
                host = snapshot.ip,
                sshPort = snapshot.sshPort.toIntOrNull() ?: MrttConstants.Network.DEFAULT_SSH_PORT,
                sshUser = snapshot.sshLogin.ifBlank { "root" },
                sshPassword = if (snapshot.sshKeysMode) "" else snapshot.sshPassword,
                sshPrivateKey = if (snapshot.sshKeysMode) snapshot.sshPrivateKey else "",
                sshKeyPassphrase = if (snapshot.sshKeysMode) snapshot.sshKeyPassphrase else "",
                peerPort = snapshot.serverPeerPort,
                apiPort = snapshot.serverWebPort,
                docker = snapshot.dockerInstall,
                mainPassword = snapshot.mainPassword,
            )
        }
        val existing = current()
        store.saveVpsServersJson(encode(existing + legacy.filter { l -> existing.none { it.host == l.host } }))
        store.markVpsServersMigrated()
    }

    private fun encode(list: List<VpsServer>): String = JSONArray().apply {
        list.forEach { server ->
            put(
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("host", server.host)
                    .put("ssh_port", server.sshPort)
                    .put("ssh_user", server.sshUser)
                    .put("ssh_password", secret(server.sshPassword))
                    .put("ssh_private_key", secret(server.sshPrivateKey))
                    .put("ssh_key_passphrase", secret(server.sshKeyPassphrase))
                    .put("peer_port", server.peerPort)
                    .put("api_port", server.apiPort)
                    .put("docker", server.docker)
                    .put("main_password", secret(server.mainPassword)),
            )
        }
    }.toString()

    private fun decode(json: String): List<VpsServer> {
        val array = runCatching { JSONArray(json.ifBlank { "[]" }) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            VpsServer(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = item.optString("name"),
                host = item.optString("host"),
                sshPort = item.optInt("ssh_port", MrttConstants.Network.DEFAULT_SSH_PORT),
                sshUser = item.optString("ssh_user", "root"),
                sshPassword = reveal(item.optString("ssh_password")),
                sshPrivateKey = reveal(item.optString("ssh_private_key")),
                sshKeyPassphrase = reveal(item.optString("ssh_key_passphrase")),
                peerPort = item.optInt("peer_port", MrttConstants.Network.DEFAULT_SERVER_PEER_PORT),
                apiPort = item.optInt("api_port", MrttConstants.Network.DEFAULT_SERVER_WEB_PORT),
                docker = item.optBoolean("docker", false),
                mainPassword = reveal(item.optString("main_password")),
                // Серверы из старых версий: имя комнаты выводится из id, чтобы не меняться при каждом чтении.
            )
        }
    }

    private fun secret(value: String): String = if (value.isEmpty()) "" else secure.encrypt(value)

    private fun reveal(value: String): String = secure.decrypt(value).orEmpty()
}
