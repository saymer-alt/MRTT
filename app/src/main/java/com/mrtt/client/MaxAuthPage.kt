package com.mrtt.client

import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject

internal data class MaxAuthCredentials(val token: String, val groupId: String)

internal data class MaxAuthPage(val token: String, val groupId: String?) {
    fun credentials(requiredGroupId: String): MaxAuthCredentials? {
        val group = groupId ?: return null
        if (requiredGroupId.isNotEmpty() && group != requiredGroupId) return null
        return MaxAuthCredentials(token, group)
    }

    companion object {
        fun isMaxUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                (host == "max.ru" || host.endsWith(".max.ru"))
        }

        fun parse(result: String): MaxAuthPage? = runCatching {
            val json = JSONObject(result)
            val url = json.optString("url")
            if (!isMaxUrl(url)) return null
            val token = json.opt("token") as? String ?: return null
            if (token.length < 32 || token.any(Char::isWhitespace)) return null
            MaxAuthPage(token, groupIdFromUrl(url))
        }.getOrNull()

        fun groupIdFromUrl(url: String): String? {
            if (!isMaxUrl(url)) return null
            val uri = URI(url)
            val segments = uri.path.orEmpty().trim('/').split('/')
            val candidate = when (segments.firstOrNull()) {
                "chat", "chats", "c" -> segments.getOrNull(1)
                else -> segments.firstOrNull()
            }
            if (candidate != null && validGroupId(candidate)) return candidate
            return uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { parameter ->
                val pair = parameter.split('=', limit = 2)
                if (pair.size != 2 || pair[0] !in GROUP_PARAMS) return@firstNotNullOfOrNull null
                runCatching { URLDecoder.decode(pair[1], "UTF-8") }.getOrNull()
                    ?.takeIf(::validGroupId)
            }
        }

        private val GROUP_PARAMS = setOf("chat_id", "chatId", "chat", "chat_id_str")
        private val GROUP_ID = Regex("-?[0-9]{1,64}")
        private fun validGroupId(value: String): Boolean = value.length <= 64 && GROUP_ID.matches(value)
    }
}
