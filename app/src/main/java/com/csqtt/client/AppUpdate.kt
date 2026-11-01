package com.csqtt.client

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val UPDATE_CHECK_NEVER = MrttConstants.Update.CHECK_NEVER
const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = MrttConstants.Update.DEFAULT_CHECK_INTERVAL_HOURS
const val UPDATE_DIALOG_ACTION_POSTPONED = MrttConstants.Update.ACTION_POSTPONED
const val UPDATE_DIALOG_ACTION_UPDATE = MrttConstants.Update.ACTION_UPDATE

private const val UPDATE_LOG_TAG = MrttConstants.General.APP_NAME
private const val GITHUB_RELEASES_URL = MrttConstants.Update.GITHUB_RELEASES_URL
private const val GITHUB_API_RATE_LIMIT_FALLBACK_MS = MrttConstants.Timeouts.UPDATE_RATE_LIMIT_COOLDOWN_MS
private val VERSION_NUMBER_REGEX = MrttConstants.Patterns.VERSION_NUMBER
private val PRE_RELEASE_REGEX = Regex("""^v?\d+(?:\.\d+)*-[A-Za-z]+(\d*)""", RegexOption.IGNORE_CASE)

@Volatile
private var githubApiCooldownUntilMs = 0L

fun updateIntervalHoursToMillis(hours: Int): Long? = when {
    hours <= 0 -> null
    else -> hours * 60L * 60L * 1000L
}

data class AppReleaseInfo(
    val versionTag: String,
    val releaseUrl: String,
    val source: RemoteVersionSource
)

enum class RemoteVersionSource {
    Release,
    Tag
}

suspend fun fetchLatestReleaseInfo(localVersion: String? = null): AppReleaseInfo? = withContext(Dispatchers.IO) {
    // Pre-releases are offered only to builds that are pre-releases themselves.
    val includePrereleases = localVersion != null && preReleaseNumber(localVersion) != null
    val response = fetchGitHubApi(GITHUB_RELEASES_URL) ?: return@withContext null
    val releases = try {
        JSONArray(response)
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: failed to parse releases list", e)
        return@withContext null
    }

    var bestRelease: AppReleaseInfo? = null
    for (i in 0 until releases.length()) {
        val json = releases.optJSONObject(i) ?: continue
        if (json.optBoolean("draft")) continue
        if (json.optBoolean("prerelease") && !includePrereleases) continue
        val release = json.toAppReleaseInfo() ?: continue
        if (isLegacyRelease(release.versionTag)) continue
        if (bestRelease == null || isNewerVersion(bestRelease.versionTag, release.versionTag)) {
            bestRelease = release
        }
    }
    bestRelease
}

// CSQTT legacy releases live in the same repository but are a separate product, never an update.
internal fun isLegacyRelease(versionTag: String): Boolean =
    versionTag.contains("legacy", ignoreCase = true)

fun isNewerVersion(local: String, remote: String): Boolean {
    val localParts = versionParts(local)
    val remoteParts = versionParts(remote)
    if (remoteParts.isEmpty()) return false

    val maxLen = maxOf(localParts.size, remoteParts.size)
    for (i in 0 until maxLen) {
        val localPart = localParts.getOrElse(i) { 0 }
        val remotePart = remoteParts.getOrElse(i) { 0 }
        if (remotePart > localPart) return true
        if (remotePart < localPart) return false
    }

    // Same numbers: 1.0-beta1 < 1.0-beta2 < 1.0.
    val localPre = preReleaseNumber(local)
    val remotePre = preReleaseNumber(remote)
    return when {
        localPre == null -> false
        remotePre == null -> true
        else -> remotePre > localPre
    }
}

private fun preReleaseNumber(version: String): Int? {
    val match = PRE_RELEASE_REGEX.find(version.trim()) ?: return null
    return match.groupValues[1].toIntOrNull() ?: 0
}

private fun fetchGitHubApi(url: String): String? {
    val now = System.currentTimeMillis()
    if (now < githubApiCooldownUntilMs) return null
    return fetchHttpText(
        url = url,
        sourceLabel = "GitHub API",
        accept = "application/vnd.github+json",
        isGitHubApi = true
    )
}

private fun fetchHttpText(
    url: String,
    sourceLabel: String,
    accept: String,
    isGitHubApi: Boolean = false
): String? {
    var conn: HttpURLConnection? = null
    return try {
        conn = URL(url).openConnection() as HttpURLConnection
        applyNoCacheHeaders(conn)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", accept)
        if (isGitHubApi) {
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        conn.setRequestProperty("User-Agent", "MRTTAndroid/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (responseCode in 200..299) {
            if (isGitHubApi) githubApiCooldownUntilMs = 0L
            response
        } else {
            if (isGitHubApi) noteGitHubApiCooldown(conn, responseCode, response)
            Log.w(
                UPDATE_LOG_TAG,
                "[WARN] Update check: $sourceLabel returned $responseCode ${response.take(300)}"
            )
            null
        }
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: $sourceLabel request failed", e)
        null
    } finally {
        conn?.disconnect()
    }
}

private fun applyNoCacheHeaders(conn: HttpURLConnection) {
    conn.useCaches = false
    conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
    conn.setRequestProperty("Pragma", "no-cache")
    conn.setRequestProperty("Expires", "0")
}

private fun noteGitHubApiCooldown(conn: HttpURLConnection, responseCode: Int, response: String) {
    if (responseCode != HttpURLConnection.HTTP_FORBIDDEN && responseCode != 429) return
    val now = System.currentTimeMillis()
    val retryAfterUntil = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { now + it * 1000L }
    val rateLimitResetUntil = conn.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { it * 1000L }
    val fallbackUntil = now + if (response.contains("rate limit", ignoreCase = true)) GITHUB_API_RATE_LIMIT_FALLBACK_MS else 5L * 60L * 1000L
    val cooldownUntil = listOfNotNull(retryAfterUntil, rateLimitResetUntil).filter { it > now }.minOrNull() ?: fallbackUntil
    if (cooldownUntil > githubApiCooldownUntilMs) {
        githubApiCooldownUntilMs = cooldownUntil
        Log.w(
            UPDATE_LOG_TAG,
            "[WARN] Update check: GitHub API cooldown ${(cooldownUntil - now) / 1000}s after HTTP $responseCode"
        )
    }
}

private fun JSONObject.toAppReleaseInfo(): AppReleaseInfo? {
    val versionTag = normalizeVersionTag(optString("tag_name"))
    val releaseUrl = optString("html_url").trim()
    if (versionTag.isBlank() || releaseUrl.isBlank()) return null
    return AppReleaseInfo(versionTag, releaseUrl, RemoteVersionSource.Release)
}

private fun versionParts(version: String): List<Int> {
    val normalized = VERSION_NUMBER_REGEX.find(version.trim())?.value ?: return emptyList()
    return normalized.split(".").mapNotNull { it.toIntOrNull() }
}

internal fun normalizeVersionTag(version: String): String {
    val trimmed = version.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.startsWith("v", ignoreCase = true)) trimmed else "v$trimmed"
}
