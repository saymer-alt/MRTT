package com.csqtt.client

internal data class VkModeSelection(
    val authMode: String,
    val hashMode: String,
)

internal fun normalizeVkModeSelection(
    requestedAuthMode: String?,
    requestedHashMode: String?,
    hasVkToken: Boolean,
): VkModeSelection {
    val authMode = when (requestedAuthMode) {
        MrttConstants.VkAuth.MODE_CAPTCHA -> MrttConstants.VkAuth.MODE_CAPTCHA
        MrttConstants.VkAuth.MODE_AUTO_JS -> MrttConstants.VkAuth.MODE_AUTO_JS
        else -> MrttConstants.VkAuth.MODE_CALLS
    }
    val hashMode = when (requestedHashMode) {
        "auto", MrttConstants.VkAutoHash.MODE_AUTO_API -> MrttConstants.VkAutoHash.MODE_AUTO_API
        MrttConstants.VkAutoHash.MODE_AUTO_JS -> MrttConstants.VkAutoHash.MODE_AUTO_JS
        else -> MrttConstants.VkAutoHash.MODE_MANUAL
    }
    return when {
        authMode == MrttConstants.VkAuth.MODE_AUTO_JS ||
            hashMode == MrttConstants.VkAutoHash.MODE_AUTO_JS ->
            VkModeSelection(
                authMode = MrttConstants.VkAuth.MODE_AUTO_JS,
                hashMode = MrttConstants.VkAutoHash.MODE_AUTO_JS,
            )
        else -> VkModeSelection(authMode = authMode, hashMode = hashMode)
    }
}

internal fun vkHashModeForAuthMode(
    requestedAuthMode: String,
    currentHashMode: String?,
    hasVkToken: Boolean,
): String = when (requestedAuthMode) {
    MrttConstants.VkAuth.MODE_AUTO_JS -> MrttConstants.VkAutoHash.MODE_AUTO_JS
    MrttConstants.VkAuth.MODE_CALLS -> if (currentHashMode == MrttConstants.VkAutoHash.MODE_AUTO_JS) {
        if (hasVkToken) MrttConstants.VkAutoHash.MODE_AUTO_API else MrttConstants.VkAutoHash.MODE_MANUAL
    } else {
        normalizeVkModeSelection(requestedAuthMode, currentHashMode, hasVkToken).hashMode
    }
    else -> normalizeVkModeSelection(requestedAuthMode, currentHashMode, hasVkToken).hashMode
}

internal fun vkAuthModeForHashMode(requestedHashMode: String, currentAuthMode: String?): String =
    when (requestedHashMode) {
        MrttConstants.VkAutoHash.MODE_AUTO_JS -> MrttConstants.VkAuth.MODE_AUTO_JS
        MrttConstants.VkAutoHash.MODE_AUTO_API -> MrttConstants.VkAuth.MODE_CALLS
        else -> if (currentAuthMode == MrttConstants.VkAuth.MODE_AUTO_JS) {
            MrttConstants.VkAuth.MODE_CALLS
        } else {
            normalizeVkModeSelection(currentAuthMode, requestedHashMode, false).authMode
        }
    }

internal fun shouldReplaceUnavailableVkHash(hashMode: String): Boolean =
    hashMode != MrttConstants.VkAutoHash.MODE_MANUAL

internal fun shouldConfirmAutoJsMode(
    currentMode: String,
    requestedMode: String,
    warningAcknowledged: Boolean,
): Boolean =
    !warningAcknowledged &&
        currentMode != MrttConstants.VkAuth.MODE_AUTO_JS &&
        requestedMode == MrttConstants.VkAuth.MODE_AUTO_JS
