package com.mrtt.client.servers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal data class ClientDevice(
    val id: String,
    val name: String,
    val ip: String,
    val lastSeen: Long,
    val online: Boolean,
)

internal data class ServerClient(
    val password: String,
    val name: String,
    val up: Long,
    val down: Long,
    val expires: Long,
    val active: Boolean,
    val activeSessions: Int,
    val ip: String,
    val vkHashes: String,
    val vkToken: Boolean,
    val peerPort: Int,
    val localPort: Int,
    val groupId: String,
    val routingRuleId: String,
    val link: String,
    val workers: Int,
    val maxDevices: Int,
    val devices: List<ClientDevice>,
) {
    /** Строка владельца (главный пароль) всегда получает фиксированный IP 10.66.67.2. */
    val isOwner: Boolean get() = ip == OWNER_TUNNEL_IP && expires == 0L && groupId.isEmpty() && name == "Владелец"
}

internal data class ServerGroup(
    val id: String,
    val name: String,
    val routingRuleId: String,
    val memberCount: Int,
    val up: Long,
    val down: Long,
    val activeSessions: Int,
)

internal data class RoutingRuleData(
    val id: String = "",
    val name: String = "",
    val appsWhitelist: Boolean = false,
    val packages: List<String> = emptyList(),
    val templateDomains: Boolean = false,
    val domainsWhitelist: Boolean = false,
    val domains: List<String> = emptyList(),
    val ipWhitelist: Boolean = false,
    val ips: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val clients: List<String> = emptyList(),
)

internal data class ProxyProfile(
    val id: String = "",
    val name: String = "",
    val port: Int = 45000,
    val username: String = "",
    val password: String = "",
)

internal data class ProxyState(
    val activeId: String,
    val profiles: List<ProxyProfile>,
    val routeActive: Boolean,
    val portListening: Boolean,
    val healthError: String,
)

internal data class ServerSettings(
    val mainPassword: String = "",
    val dnsProvider: String = "yandex",
    val dnsPrimary: String = "",
    val dnsSecondary: String = "",
    val autoRestartHours: Int = 0,
    val vkToken: String = "",
    val vkHashes: String = "",
    /** Комната Jitsi `host/room`, в которой сидит сервер; пусто — Jitsi выключен. */
    val jitsiRoom: String = "",
    val jitsiSeats: Int = 6,
    val restartRequired: Boolean = false,
)

internal const val OWNER_TUNNEL_IP = "10.66.67.2"

internal val DNS_PROVIDERS = listOf(
    "yandex" to "Yandex DNS",
    "cloudflare" to "Cloudflare",
    "google" to "Google",
    "adguard" to "AdGuard",
    "xbox" to "Xbox DNS",
    "comms" to "Comss DNS",
    "geohide" to "GeoHide DNS",
    "quad9" to "Quad9",
    "opendns" to "OpenDNS",
    "rostelecom" to "Rostelecom DNS",
    "nextdns" to "NextDNS",
    "bizone" to "BI.ZONE DNS",
    "nsdi" to "НСДИ DNS",
    "custom" to "Ручной DNS",
)

/** Вызовы API управления сервером (то, что раньше делала веб-панель). */
internal class ServerControl(context: Context, server: VpsServer) {
    private val api = ServerApi(context, server)

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    suspend fun clients(): List<ServerClient> = api.getArray("/api/clients").objects().map(::parseClient)

    suspend fun createClient(name: String, groupId: String, days: Int, workers: Int, maxDevices: Int) {
        api.post(
            "/api/clients",
            JSONObject()
                .put("name", name)
                .put("group_id", groupId)
                .put("days", days)
                .put("hash", "")
                .put("workers", workers)
                .put("max_devices", maxDevices),
        )
    }

    suspend fun updateClient(
        client: ServerClient,
        name: String,
        groupId: String,
        days: Int,
        workers: Int,
        maxDevices: Int,
        vkHashes: String,
        vkToken: Boolean,
    ) {
        api.post(
            "/api/clients/${enc(client.password)}",
            JSONObject()
                .put("name", name)
                .put("group_id", groupId)
                .put("days", days)
                .put("hash", if (vkToken) "" else vkHashes)
                .put("vk_token", vkToken)
                .put("peer_port", client.peerPort)
                .put("local_port", client.localPort)
                .put("workers", workers)
                .put("max_devices", maxDevices),
        )
    }

    suspend fun toggleClient(password: String) = api.post("/api/clients/${enc(password)}/toggle")
    suspend fun deleteClient(password: String) = api.delete("/api/clients/${enc(password)}")
    suspend fun extendClient(password: String) = api.post("/api/clients/${enc(password)}/extend", JSONObject().put("days", 30))
    suspend fun resetTraffic(password: String) = api.post("/api/clients/${enc(password)}/reset-traffic")
    suspend fun unbindDevice(password: String, deviceId: String) =
        api.post("/api/clients/${enc(password)}/unbind?device=${enc(deviceId)}")

    suspend fun batch(action: String, passwords: Collection<String>, extra: JSONObject.() -> Unit = {}) {
        val body = JSONObject().put("passwords", JSONArray(passwords)).apply(extra)
        api.post("/api/clients/batch-$action", body)
    }

    suspend fun groups(): List<ServerGroup> = api.getArray("/api/groups").objects().map {
        ServerGroup(
            id = it.optString("id"),
            name = it.optString("name"),
            routingRuleId = it.optString("routing_rule_id"),
            memberCount = it.optInt("member_count"),
            up = it.optLong("up"),
            down = it.optLong("down"),
            activeSessions = it.optInt("active_sessions"),
        )
    }

    suspend fun createGroup(name: String) = api.post("/api/groups", JSONObject().put("name", name))
    suspend fun updateGroup(id: String, name: String, routingRuleId: String) =
        api.put("/api/groups/${enc(id)}", JSONObject().put("name", name).put("routing_rule_id", routingRuleId))
    suspend fun deleteGroup(id: String) = api.delete("/api/groups/${enc(id)}")

    suspend fun routingRules(): List<RoutingRuleData> = api.getArray("/api/routing-rules").objects().map {
        RoutingRuleData(
            id = it.optString("id"),
            name = it.optString("name"),
            appsWhitelist = it.optBoolean("is_whitelist"),
            packages = it.optJSONArray("packages").strings(),
            templateDomains = it.optBoolean("template_domains"),
            domainsWhitelist = it.optBoolean("domains_is_whitelist"),
            domains = it.optJSONArray("direct_domains").strings(),
            ipWhitelist = it.optBoolean("ip_is_whitelist"),
            ips = it.optJSONArray("ips").strings(),
            groups = it.optJSONArray("assigned_groups").strings(),
            clients = it.optJSONArray("assigned_clients").strings(),
        )
    }

    suspend fun saveRoutingRule(rule: RoutingRuleData) {
        val body = JSONObject()
            .put("name", rule.name)
            .put("is_whitelist", rule.appsWhitelist)
            .put("packages", JSONArray(rule.packages))
            .put("template_domains", rule.templateDomains)
            .put("domains_is_whitelist", rule.domainsWhitelist)
            .put("direct_domains", JSONArray(rule.domains))
            .put("ip_is_whitelist", rule.ipWhitelist)
            .put("ips", JSONArray(rule.ips))
            .put("assigned_groups", JSONArray(rule.groups))
            .put("assigned_clients", JSONArray(rule.clients))
        if (rule.id.isEmpty()) api.post("/api/routing-rules", body) else api.put("/api/routing-rules/${enc(rule.id)}", body)
    }

    suspend fun deleteRoutingRule(id: String) = api.delete("/api/routing-rules/${enc(id)}")

    suspend fun proxy(): ProxyState {
        val json = api.getObject("/api/local-proxy")
        return ProxyState(
            activeId = json.optString("active_profile_id"),
            profiles = json.optJSONArray("profiles").objects().map {
                ProxyProfile(
                    id = it.optString("id"),
                    name = it.optString("name"),
                    port = it.optInt("port", 45000),
                    username = it.optString("username"),
                    password = it.optString("password"),
                )
            },
            routeActive = json.optBoolean("route_active"),
            portListening = json.optBoolean("port_listening", true),
            healthError = json.optString("health_error").takeUnless { it == "null" }.orEmpty(),
        )
    }

    suspend fun saveProxy(profile: ProxyProfile) {
        val body = JSONObject()
            .put("name", profile.name)
            .put("port", profile.port)
            .put("username", profile.username)
            .put("password", profile.password)
        if (profile.id.isEmpty()) api.post("/api/local-proxy", body) else api.put("/api/local-proxy/profiles/${enc(profile.id)}", body)
    }

    suspend fun deleteProxy(id: String) = api.delete("/api/local-proxy/profiles/${enc(id)}")
    suspend fun activateProxy(id: String) = api.post("/api/local-proxy/activate/${enc(id)}")
    suspend fun deactivateProxy() = api.post("/api/local-proxy/deactivate")

    suspend fun settings(): ServerSettings {
        val json = api.getObject("/api/settings")
        return ServerSettings(
            mainPassword = json.optString("main_password"),
            dnsProvider = json.optString("dns_provider", "yandex"),
            dnsPrimary = json.optString("dns_primary"),
            dnsSecondary = json.optString("dns_secondary"),
            autoRestartHours = json.optInt("auto_restart_interval_hours"),
            vkToken = json.optString("vk_token"),
            vkHashes = json.optString("vk_hashes"),
            jitsiRoom = json.optString("jitsi_room"),
            jitsiSeats = json.optInt("jitsi_seats", 6),
            restartRequired = json.optBoolean("restart_required"),
        )
    }

    suspend fun saveSettings(settings: ServerSettings) {
        val body = JSONObject()
            .put("main_password", settings.mainPassword)
            .put("dns_provider", settings.dnsProvider)
            .put("auto_restart_interval_hours", settings.autoRestartHours)
            .put("vk_token", settings.vkToken)
            .put("vk_hashes", settings.vkHashes)
            .put("jitsi_room", settings.jitsiRoom)
            .put("jitsi_seats", settings.jitsiSeats)
        if (settings.dnsProvider == "custom") {
            body.put("dns_primary", settings.dnsPrimary).put("dns_secondary", settings.dnsSecondary)
        }
        api.post("/api/settings", body)
    }

    suspend fun applyVkToAllClients(vkToken: String, vkHashes: String) = api.post(
        "/api/links/batch-vk",
        JSONObject().put("vk_token", vkToken).put("vk_hashes", vkHashes).put("apply_to_all_clients", true),
    )

    suspend fun links(): List<Pair<String, String>> = api.getArray("/api/links").objects()
        .map { it.optString("name") to it.optString("link") }
        .filter { it.second.isNotBlank() }

    suspend fun stats(): JSONObject = api.getObject("/api/stats")
    suspend fun logs(): JSONObject = api.getObject("/api/logs")
    suspend fun setLogging(active: Boolean) = api.post("/api/logs/toggle", JSONObject().put("active", active))
    suspend fun clearLogs() = api.post("/api/logs/clear")
    suspend fun exportConfig(): String = api.getText("/api/export")
    suspend fun importConfig(json: String) = api.request("POST", "/api/import", json)
    suspend fun reboot() = api.post("/api/reboot")
    suspend fun trimMemory() = api.post("/api/memory/trim")

    private fun parseClient(it: JSONObject) = ServerClient(
        password = it.optString("password"),
        name = it.optString("name"),
        up = it.optLong("up"),
        down = it.optLong("down"),
        expires = it.optLong("expires"),
        active = it.optBoolean("active"),
        activeSessions = it.optInt("active_sessions"),
        ip = it.optString("ip"),
        vkHashes = it.optString("vk_hashes"),
        vkToken = it.optBoolean("vk_token"),
        peerPort = it.optInt("peer_port"),
        localPort = it.optInt("local_port"),
        groupId = it.optString("group_id"),
        routingRuleId = it.optString("routing_rule_id"),
        link = it.optString("link"),
        workers = if (it.isNull("workers")) 0 else it.optInt("workers"),
        maxDevices = it.optInt("max_devices", 1),
        devices = it.optJSONArray("devices").objects().map { device ->
            ClientDevice(
                id = device.optString("id"),
                name = device.optString("name"),
                ip = device.optString("ip"),
                lastSeen = device.optLong("last_seen"),
                online = device.optBoolean("online"),
            )
        },
    )
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
