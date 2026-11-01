package com.mrtt.client

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnTransportPolicyTest {
    @Test
    fun tcpWarningAppearsOnlyWhenEnteringTcpWithoutAcknowledgement() {
        assertEquals(
            true,
            shouldConfirmTcpTransport(
                currentTransport = MrttConstants.Tunnel.DEFAULT_TURN_TRANSPORT,
                requestedTransport = MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmTcpTransport(
                currentTransport = MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                requestedTransport = MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                warningAcknowledged = false,
            ),
        )
        assertEquals(
            false,
            shouldConfirmTcpTransport(
                currentTransport = MrttConstants.Tunnel.DEFAULT_TURN_TRANSPORT,
                requestedTransport = MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS,
                warningAcknowledged = true,
            ),
        )
    }
}
