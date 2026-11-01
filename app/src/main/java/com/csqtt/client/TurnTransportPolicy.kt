package com.csqtt.client

internal fun shouldConfirmTcpTransport(
    currentTransport: String,
    requestedTransport: String,
    warningAcknowledged: Boolean,
): Boolean =
    !warningAcknowledged &&
        currentTransport != MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS &&
        requestedTransport == MrttConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
