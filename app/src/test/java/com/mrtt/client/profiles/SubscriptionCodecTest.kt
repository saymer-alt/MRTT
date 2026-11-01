package com.mrtt.client.profiles

import com.mrtt.client.ui.utils.formatMrttLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionCodecTest {
    @Test
    fun subscriptionRoundTripKeepsNamesAndLinks() {
        val first = formatMrttLink("203.0.113.7", 46000, "pass1234567890ab")
        val second = formatMrttLink("198.51.100.9", 46001, "pass2234567890ab")
        val encoded = encodeSubscription(Subscription("Семья", listOf("Дом" to first, "Дача" to second)))

        val decoded = decodeSubscription(encoded)!!
        assertEquals("Семья", decoded.name)
        assertEquals(listOf("Дом" to first, "Дача" to second), decoded.links)
    }

    @Test
    fun invalidLinksInsideSubscriptionAreDropped() {
        val good = formatMrttLink("203.0.113.7", 46000, "pass1234567890ab")
        val decoded = decodeSubscription(
            encodeSubscription(Subscription("Смесь", listOf("ok" to good, "bad" to "https://example.com"))),
        )!!
        assertEquals(listOf("ok" to good), decoded.links)
    }

    @Test
    fun plainLinksAndGarbageAreNotSubscriptions() {
        assertNull(decodeSubscription(formatMrttLink("203.0.113.7", 46000, "pass1234567890ab")))
        assertNull(decodeSubscription("mrttsub://not-base64"))
    }
}
