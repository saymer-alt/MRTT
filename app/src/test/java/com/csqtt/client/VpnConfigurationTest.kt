package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConfigurationTest {
    @Test
    fun defaultMtuIs1280() {
        assertEquals(1280, MrttConstants.Vpn.DEFAULT_MTU)
    }

    @Test
    fun defaultMtuWithinConfiguredBounds() {
        assertTrue(MrttConstants.Vpn.DEFAULT_MTU >= MrttConstants.Vpn.MTU_MIN)
        assertTrue(MrttConstants.Vpn.DEFAULT_MTU <= MrttConstants.Vpn.MTU_MAX)
    }
}
