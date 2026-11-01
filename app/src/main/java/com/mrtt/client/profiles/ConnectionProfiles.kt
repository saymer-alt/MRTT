package com.mrtt.client.profiles

import android.content.Context
import com.mrtt.client.SettingsStore
import com.mrtt.client.ui.utils.decryptMrttPayload
import com.mrtt.client.ui.utils.encryptMrttPayload
import com.mrtt.client.ui.utils.parseMrttLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Добавленная ссылка подключения; [group] — имя сабки, из которой она пришла. */
internal data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val link: String,
    val group: String = "",
)

/** Сабка: одна ссылка, внутри которой несколько mrtt-ссылок (можно с разных серверов). */
internal data class Subscription(val name: String, val links: List<Pair<String, String>>)

internal const val SUBSCRIPTION_SCHEME = "mrttsub://"

internal fun encodeSubscription(subscription: Subscription): String {
    val links = JSONArray()
    subscription.links.forEach { (name, link) -> links.put(JSONObject().put("name", name).put("link", link)) }
    val payload = JSONObject().put("v", 1).put("name", subscription.name).put("links", links)
    return SUBSCRIPTION_SCHEME + encryptMrttPayload(payload.toString())
}

internal fun decodeSubscription(raw: String): Subscription? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith(SUBSCRIPTION_SCHEME, ignoreCase = true)) return null
    val json = decryptMrttPayload(trimmed.substring(SUBSCRIPTION_SCHEME.length)) ?: return null
    val payload = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (payload.optInt("v") != 1) return null
    val array = payload.optJSONArray("links") ?: return null
    val links = (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val link = item.optString("link").trim()
        // В сабку пускаем только валидные mrtt-ссылки: это граница доверия для чужих сабок.
        if (parseMrttLink(link) == null) return@mapNotNull null
        item.optString("name").ifBlank { "Ссылка ${index + 1}" } to link
    }
    if (links.isEmpty()) return null
    return Subscription(payload.optString("name").ifBlank { "Подписка" }, links)
}

internal class ProfileRepository(context: Context) {
    private val store = SettingsStore(context)

    val profiles: Flow<List<ConnectionProfile>> = store.connectionProfilesJson.map(::decode)
    val activeId: Flow<String> = store.activeConnectionProfileId

    suspend fun current(): List<ConnectionProfile> = profiles.first()

    /** Добавляет ссылку или сабку. Возвращает число новых профилей или null, если текст не распознан. */
    suspend fun import(raw: String, name: String = ""): Int? {
        val text = raw.trim()
        val subscription = decodeSubscription(text)
        val incoming = when {
            subscription != null -> subscription.links.map { (linkName, link) ->
                ConnectionProfile(name = linkName, link = link, group = subscription.name)
            }
            parseMrttLink(text) != null -> listOf(
                ConnectionProfile(name = name.ifBlank { defaultName(text) }, link = text),
            )
            else -> return null
        }
        val existing = current()
        val fresh = incoming.filter { profile -> existing.none { it.link == profile.link } }
        save(existing + fresh)
        if (activeId.first().isEmpty()) fresh.firstOrNull()?.let { activate(it) }
        return fresh.size
    }

    suspend fun rename(id: String, name: String) {
        save(current().map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    suspend fun delete(id: String) {
        save(current().filterNot { it.id == id })
    }

    suspend fun activate(profile: ConnectionProfile) {
        store.activateConnectionProfile(profile.id, profile.link)
        // Профиль не навязывает транспорт: по умолчанию VK, остальное пользователь выберет в «Транспорт».
        store.saveTransportMode(com.mrtt.client.MrttConstants.Transport.MODE_VK)
    }

    /** Меняет ссылку профиля (свои хеши и т. п.); активный профиль сразу получает новую ссылку. */
    suspend fun updateLink(id: String, link: String) {
        val updated = current().map { if (it.id == id) it.copy(link = link) else it }
        save(updated)
        if (activeId.first() == id) updated.firstOrNull { it.id == id }?.let { store.activateConnectionProfile(it.id, it.link) }
    }

    /** Один раз переносит ссылки старых слотов (в том числе настройки «владельца»). */
    suspend fun migrateLegacy() {
        if (store.connectionProfilesMigrated()) return
        val existing = current()
        val legacy = store.legacyConnectionLinks()
            .filter { (_, link) -> parseMrttLink(link) != null && existing.none { it.link == link } }
            .map { (name, link) -> ConnectionProfile(name = name, link = link) }
        save(existing + legacy)
        if (activeId.first().isEmpty()) {
            val currentLink = store.mrttLink.first()
            (existing + legacy).firstOrNull { it.link == currentLink }?.let { activate(it) }
                ?: legacy.firstOrNull()?.let { activate(it) }
        }
        store.markConnectionProfilesMigrated()
    }

    private fun defaultName(link: String): String = parseMrttLink(link)?.host ?: "Профиль"

    private suspend fun save(list: List<ConnectionProfile>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("link", it.link).put("group", it.group))
        }
        store.saveConnectionProfilesJson(array.toString())
    }

    private fun decode(json: String): List<ConnectionProfile> {
        val array = runCatching { JSONArray(json.ifBlank { "[]" }) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val link = item.optString("link")
            if (link.isBlank()) return@mapNotNull null
            ConnectionProfile(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = item.optString("name").ifBlank { defaultName(link) },
                link = link,
                group = item.optString("group"),
            )
        }
    }
}
