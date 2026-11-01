package com.csqtt.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(isNewerVersion("1.9.9", "2.0.5"))
        assertTrue(isNewerVersion("2.0", "2.0.1"))
        assertFalse(isNewerVersion("2.0.5", "2.0"))
        assertFalse(isNewerVersion("2.1.0", "2.0.9"))
        assertFalse(isNewerVersion("2.0.5", "invalid"))
    }

    @Test
    fun ordersBetasBeforeTheRelease() {
        assertTrue(isNewerVersion("v1.0-beta1", "v1.0-beta2"))
        assertTrue(isNewerVersion("v1.0-beta1", "v1.0"))
        assertTrue(isNewerVersion("v1.0-beta2", "v1.1-beta1"))
        assertFalse(isNewerVersion("v1.0-beta2", "v1.0-beta1"))
        assertFalse(isNewerVersion("v1.0", "v1.0-beta2"))
        assertFalse(isNewerVersion("v1.0-beta1", "v1.0-beta1"))
    }

    @Test
    fun legacyReleasesAreNotUpdates() {
        assertTrue(isLegacyRelease("v2.5.0-legacy"))
        assertTrue(isLegacyRelease("v2.1.9-legacy"))
        assertFalse(isLegacyRelease("v1.0-beta1"))
    }
}
