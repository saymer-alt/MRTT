package com.csqtt.client

import com.csqtt.client.ui.utils.parseMrttLink
import com.csqtt.client.ui.utils.peerAddress
import kotlinx.coroutines.flow.first

data class ConnectionSource(
    val peer: String,
    val password: String,
    val hashes: String,
    val hashesFromLink: Boolean,
    val vkAccessToken: String,
    val tokenFromLink: Boolean,
    val maxGroupId: String = "",
    /** Комната Jitsi из ссылки, когда выбран транспорт Jitsi. */
    val jitsiRoom: String = "",
    /** Места сервера в комнате из ссылки; 0, если неизвестно (например, своя комната). */
    val jitsiSeats: Int = 0,
)


suspend fun resolveConnectionSource(store: SettingsStore): ConnectionSource? {
    val transportMode = store.transportMode.first()
    val maxTransportSelected = transportMode == MrttConstants.Transport.MODE_MAX
    val jitsiSelected = transportMode == MrttConstants.Transport.MODE_JITSI
    val invalidHashes = VkHashValidationCodec.decode(store.vkHashCheckResults.first())
    fun activeHashes(raw: String): String {
        val rawList = raw.split(Regex("[,\\s\\n]+")).map(String::trim).filter { it.length >= 16 }.distinct()
        val filtered = VkHashValidationCodec.active(rawList, invalidHashes)
        return (if (filtered.isNotEmpty()) filtered else rawList).joinToString(",")
    }

    if (store.mrttLinkMode.first()) {
        val link = parseMrttLink(store.mrttLink.first()) ?: return null
        val linkHashes = activeHashes(link.hashes.joinToString(","))
        val tokenFromLink = link.vkAccessToken.isNotEmpty()
        return ConnectionSource(
            peer = link.peerAddress(),
            password = link.password,
            hashes = if (tokenFromLink) "" else linkHashes.ifEmpty { activeHashes(store.vkHashes.first()) },
            hashesFromLink = linkHashes.isNotEmpty(),
            vkAccessToken = link.vkAccessToken,
            tokenFromLink = tokenFromLink,
            maxGroupId = if (maxTransportSelected) link.maxGroupId.ifBlank { store.maxGroupId.first() } else "",
            jitsiRoom = if (jitsiSelected) store.jitsiRoomOverride.first().ifBlank { link.jitsiRoom } else "",
            jitsiSeats = if (jitsiSelected && store.jitsiRoomOverride.first().ifBlank { link.jitsiRoom } == link.jitsiRoom) {
                link.jitsiSeats
            } else {
                0
            },
        )
    }

    val basePeer = store.peer.first()
    val passwordState = store.connectionPasswordState.first()
    val password = passwordState.value
    if (basePeer.isBlank()) return null
    val serverPeerPort = if (store.manualPortsEnabled.first()) {
        store.serverPeerPort.first()
    } else {
        MrttConstants.Network.DEFAULT_SERVER_PEER_PORT
    }
    val peer = if (basePeer.contains(':')) basePeer else "$basePeer:$serverPeerPort"
    return ConnectionSource(
        peer = peer,
        password = password,
        hashes = activeHashes(store.vkHashes.first()),
        hashesFromLink = false,
        vkAccessToken = "",
        tokenFromLink = false,
        maxGroupId = if (maxTransportSelected) store.maxGroupId.first() else "",
    )
}
