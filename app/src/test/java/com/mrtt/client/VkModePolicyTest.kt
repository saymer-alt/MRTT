package com.mrtt.client

import org.junit.Assert.assertEquals
import org.junit.Test

class VkModePolicyTest {
    @Test
    fun autoVkHashSelectsAutoVkWorkMode() {
        assertEquals(
            MrttConstants.VkAuth.MODE_AUTO_JS,
            vkAuthModeForHashMode(
                MrttConstants.VkAutoHash.MODE_AUTO_JS,
                MrttConstants.VkAuth.MODE_CALLS,
            ),
        )
    }

    @Test
    fun autoVkWorkModeSelectsAutoVkHashes() {
        assertEquals(
            MrttConstants.VkAutoHash.MODE_AUTO_JS,
            vkHashModeForAuthMode(
                MrttConstants.VkAuth.MODE_AUTO_JS,
                MrttConstants.VkAutoHash.MODE_AUTO_API,
                hasVkToken = true,
            ),
        )
    }

    @Test
    fun autoApiHashLeavesAutoVkWorkMode() {
        assertEquals(
            MrttConstants.VkAuth.MODE_CALLS,
            vkAuthModeForHashMode(
                MrttConstants.VkAutoHash.MODE_AUTO_API,
                MrttConstants.VkAuth.MODE_AUTO_JS,
            ),
        )
    }

    @Test
    fun autoWorkModeMovesAutoVkHashesToAvailableNonVkMode() {
        assertEquals(
            MrttConstants.VkAutoHash.MODE_AUTO_API,
            vkHashModeForAuthMode(
                MrttConstants.VkAuth.MODE_CALLS,
                MrttConstants.VkAutoHash.MODE_AUTO_JS,
                hasVkToken = true,
            ),
        )
        assertEquals(
            MrttConstants.VkAutoHash.MODE_MANUAL,
            vkHashModeForAuthMode(
                MrttConstants.VkAuth.MODE_CALLS,
                MrttConstants.VkAutoHash.MODE_AUTO_JS,
                hasVkToken = false,
            ),
        )
    }

    @Test
    fun manualHashLeavesAutoVkWorkMode() {
        assertEquals(
            MrttConstants.VkAuth.MODE_CALLS,
            vkAuthModeForHashMode(
                MrttConstants.VkAutoHash.MODE_MANUAL,
                MrttConstants.VkAuth.MODE_AUTO_JS,
            ),
        )
    }

    @Test
    fun unavailableManualHashStaysInCurrentProcessWhileAutoHashIsReplaced() {
        assertEquals(
            false,
            shouldReplaceUnavailableVkHash(MrttConstants.VkAutoHash.MODE_MANUAL),
        )
        assertEquals(
            true,
            shouldReplaceUnavailableVkHash(MrttConstants.VkAutoHash.MODE_AUTO_API),
        )
        assertEquals(
            true,
            shouldReplaceUnavailableVkHash(MrttConstants.VkAutoHash.MODE_AUTO_JS),
        )
    }

    @Test
    fun autoVkWarningAppearsOnlyWhenEnteringTheModeWithoutAcknowledgement() {
        assertEquals(
            true,
            shouldConfirmAutoJsMode(
                currentMode = MrttConstants.VkAuth.MODE_CALLS,
                requestedMode = MrttConstants.VkAuth.MODE_AUTO_JS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmAutoJsMode(
                currentMode = MrttConstants.VkAuth.MODE_AUTO_JS,
                requestedMode = MrttConstants.VkAuth.MODE_AUTO_JS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmAutoJsMode(
                currentMode = MrttConstants.VkAuth.MODE_CALLS,
                requestedMode = MrttConstants.VkAuth.MODE_AUTO_JS,
                warningAcknowledged = true,
            ),
        )
    }
}
