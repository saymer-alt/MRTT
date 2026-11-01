package com.csqtt.client.ui

import com.csqtt.client.ui.utils.decryptMrttPayload
import com.csqtt.client.ui.utils.encryptMrttPayload
import com.csqtt.client.ui.utils.formatMrttLink
import com.csqtt.client.ui.utils.generate16CharPassword
import com.csqtt.client.ui.utils.isValidTunnelPassword
import com.csqtt.client.ui.utils.parseMrttLink
import com.csqtt.client.ui.utils.peerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MrttLinkTest {
    private fun enc(plain: String): String = "csqttr://" + encryptMrttPayload(plain)

    @Test
    fun parsesV2WithoutHashes() {
        val link = parseMrttLink(enc("connect?v=2&host=203.0.113.7&peer=46000&password=p%40ss"))
        requireNotNull(link)
        assertEquals("203.0.113.7", link.host)
        assertEquals(46000, link.port)
        assertEquals("p@ss", link.password)
        assertEquals(emptyList<String>(), link.hashes)
    }

    @Test
    fun parsesV2ConcatenatedParameters() {
        val link = parseMrttLink(enc("connect?v=2host=203.0.113.88peer=46000password=dummy_secret_pass"))
        requireNotNull(link)
        assertEquals("203.0.113.88", link.host)
        assertEquals(46000, link.port)
        assertEquals("dummy_secret_pass", link.password)
        assertEquals(emptyList<String>(), link.hashes)
    }

    @Test
    fun parsesV2WithSixPlusSeparatedHashes() {
        val hashes = (1..6).map { "abcdefghijklmnop$it" }
        val link = parseMrttLink(
            enc("connect?v=2&host=vps.example&peer=46000&password=secret&hashes=${hashes.joinToString("+")}"),
        )
        requireNotNull(link)
        assertEquals(hashes, link.hashes)
    }

    @Test
    fun parsesV2WithVkToken() {
        val link = parseMrttLink(
            enc("connect?v=2&host=vps.example&peer=46000&password=secret&token=vk1.aBc-_%2B9"),
        )
        requireNotNull(link)
        assertEquals("vk1.aBc-_+9", link.vkAccessToken)
        assertEquals(emptyList<String>(), link.hashes)
    }

    @Test
    fun preservesEncodedPlusInsideHash() {
        val link = parseMrttLink(
            enc("connect?v=2&host=vps.example&peer=46000&password=secret&hashes=abcdefghijklmno%2Bp"),
        )
        requireNotNull(link)
        assertEquals(listOf("abcdefghijklmno+p"), link.hashes)
    }

    @Test
    fun formatsAndParsesCleanV2LinkWithPassword() {
        val formatted = formatMrttLink("203.0.113.7", 46000, "SecretPass123456", listOf("abcdefghijklmnop1"))
        val link = parseMrttLink(formatted)
        requireNotNull(link)
        assertEquals("203.0.113.7", link.host)
        assertEquals(46000, link.port)
        assertEquals("SecretPass123456", link.password)
        assertEquals(listOf("abcdefghijklmnop1"), link.hashes)
    }

    @Test
    fun formatsAnyValidPasswordSoItParsesBack() {
        for (pass in listOf("Пароль-туннеля", "p#ss&w=rd?x", "100%+plus;semi/slash:colon!", "a".repeat(128), "ж".repeat(64))) {
            val link = parseMrttLink(formatMrttLink("203.0.113.7", 46000, pass))
            requireNotNull(link) { pass }
            assertEquals(pass, link.password)
        }
    }

    @Test
    fun aLettersAndDigitsPasswordIsWrittenAsBefore() {
        val plain = decryptMrttPayload(formatMrttLink("203.0.113.7", 46000, "SecretPass123456").removePrefix("csqttr://"))
        assertEquals("connect?v=2&host=203.0.113.7&peer=46000&password=SecretPass123456", plain)
    }

    @Test
    fun tunnelPasswordRuleIsTheServers() {
        assertTrue(isValidTunnelPassword("Пароль18символовЁё"))
        assertTrue(isValidTunnelPassword("a".repeat(128)))
        assertTrue(isValidTunnelPassword("ж".repeat(64)))
        assertFalse(isValidTunnelPassword("a".repeat(129)))
        assertFalse(isValidTunnelPassword("ж".repeat(65)))
        assertFalse(isValidTunnelPassword(""))
        assertFalse(isValidTunnelPassword("a|b"))
        assertFalse(isValidTunnelPassword("a b"))
        assertFalse(isValidTunnelPassword("a\tb"))
        assertFalse(isValidTunnelPassword("a\u0000b"))
    }

    @Test
    fun generate16CharPasswordProducesValidPassword() {
        val pass = generate16CharPassword()
        assertEquals(16, pass.length)
        assertTrue(pass.matches(Regex("^[0-9A-Za-z]{16}$")))
    }

    @Test
    fun rejectsOldSchemeEvenForEncryptedLinks() {
        val encrypted = encryptMrttPayload("connect?v=2&host=203.0.113.7&peer=46000")
        assertNull(parseMrttLink("csqtt://$encrypted"))
        assertNull(parseMrttLink(enc("csqtt://connect?v=2&host=203.0.113.7&peer=46000")))
    }

    @Test
    fun rejectsLegacyUnencryptedLinks() {
        assertNull(parseMrttLink("csqtt://secret@203.0.113.7:46000"))
        assertNull(parseMrttLink("csqtt://connect?v=2&host=203.0.113.7&peer=46000&password=secret"))
        val rawPayload = "connect?v=2&host=203.0.113.7&peer=46000&password=secret"
        val base64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawPayload.toByteArray(Charsets.UTF_8))
        assertNull(parseMrttLink("csqtt://$base64"))
        assertNull(parseMrttLink("csqttr://$base64"))
    }

    @Test
    fun formatsIpv6PeerWithBrackets() {
        val link = parseMrttLink(enc("connect?v=2&host=2001%3Adb8%3A%3A1&peer=46000&password=secret"))
        requireNotNull(link)
        assertEquals("[2001:db8::1]:46000", link.peerAddress())
    }

    @Test
    fun rejectsMalformedV2Links() {
        assertNull(parseMrttLink(enc("connect?v=3&host=1.2.3.4&peer=46000&password=secret")))
        assertNull(parseMrttLink(enc("connect?v=2&host=1.2.3.4&peer=0&password=secret")))
        assertNull(parseMrttLink(enc("connect?v=2&host=1.2.3.4&peer=46000&password=secret&hashes=")))
        assertNull(parseMrttLink(enc("connect?v=2&host=1.2.3.4&peer=46000&password=secret&hashes=short")))
        assertNull(parseMrttLink(enc("connect?v=2&host=1.2.3.4&peer=46000&password=secret&token=bad%20token")))
        assertNull(parseMrttLink(enc("connect?v=2&host=1.2.3.4&peer=46000&password=secret&token=one&token=two")))
        assertNull(
            parseMrttLink(
                enc("connect?v=2&host=1.2.3.4&peer=46000&password=secret&hashes=abcdefghijklmnop&token=token"),
            ),
        )
        assertNull(
            parseMrttLink(
                enc("connect?v=2&host=1.2.3.4&peer=46000&password=secret&hashes=" +
                    (1..7).joinToString("+") { "abcdefghijklmnop$it" }),
            ),
        )
    }

    @Test
    fun ignoresWorkersInOldLinks() {
        val link = parseMrttLink(enc("connect?v=2&host=203.0.113.7&peer=46000&workers=36"))
        requireNotNull(link)
        assertEquals("203.0.113.7", link.host)

        val malformed = parseMrttLink(enc("connect?v=2&host=203.0.113.7&peer=46000&workers=not-a-number"))
        requireNotNull(malformed)
        assertEquals("203.0.113.7", malformed.host)
    }

    @Test
    fun parsesEncryptedLink() {
        val rawPayload = "connect?v=2&host=203.0.113.7&peer=46000&workers=36"
        val encryptedBase64 = encryptMrttPayload(rawPayload)
        val encryptedLink = "csqttr://$encryptedBase64"
        val link = parseMrttLink(encryptedLink)
        requireNotNull(link)
        assertEquals("203.0.113.7", link.host)
        assertEquals(46000, link.port)
    }

    @Test
    fun parsesRustEncryptedLink() {
        val rustLink = "csqttr://GNANVH0fhD6MisDFiWP_P0Bm_FHr-frawgfwur3Zx4ipjJx8gk-M6ZZbnDr_2clwPmzO-QS8AkiHLaMXyYO1RHyPeehXPqTc6jJi_iw72fRQJ9AR918ne5XsBRg"
        val link = parseMrttLink(rustLink)
        requireNotNull(link)
        assertEquals("1.2.3.4", link.host)
        assertEquals(46000, link.port)
    }

    @Test
    fun deterministicLinkEncryption() {
        val rawPayload = "connect?v=2&host=203.0.113.7&peer=46000"
        val enc1 = encryptMrttPayload(rawPayload)
        val enc2 = encryptMrttPayload(rawPayload)
        assertEquals(enc1, enc2)
    }

    @Test
    fun parsesV2OwnerLinkWithoutPassword() {
        val link = parseMrttLink(enc("connect?v=2&host=203.0.113.7&peer=46000"))
        requireNotNull(link)
        assertEquals("203.0.113.7", link.host)
        assertEquals(46000, link.port)
        assertEquals("", link.password)
    }

    @Test
    fun parsesV2WithHashesWithoutPassword() {
        val link = parseMrttLink(enc("connect?v=2&host=203.0.113.7&peer=46000&hashes=abcdefghijklmnop1"))
        requireNotNull(link)
        assertEquals("203.0.113.7", link.host)
        assertEquals(46000, link.port)
        assertEquals("", link.password)
        assertEquals(listOf("abcdefghijklmnop1"), link.hashes)
    }

    @Test
    fun tamperedEncryptedLinkFailsToDecrypt() {
        val validEncrypted = encryptMrttPayload("connect?v=2&host=203.0.113.7&peer=46000")
        val decoded = java.util.Base64.getUrlDecoder().decode(validEncrypted)
        decoded[decoded.size - 1] = (decoded[decoded.size - 1].toInt() xor 1).toByte()
        val tamperedBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
        assertNull(parseMrttLink("csqttr://$tamperedBase64"))
    }

    @Test
    fun truncatedEncryptedLinkFailsToDecrypt() {
        assertNull(parseMrttLink("csqttr://" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(10))))
    }
    @Test
    fun maxGroupRejectsMalformedIdentifiers() {
        for (bad in listOf("--123", "12-3", "-", "1 2", "abc", "")) {
            assertNull(parseMrttLink(enc("connect?v=2&host=example.org&peer=46000&max=$bad")))
        }
    }

    @org.junit.Test
    fun maxGroupLinkRoundTrip() {
        val encrypted = com.csqtt.client.ui.utils.formatMrttLink(
            host = "example.org", peer = 46000, password = "secret", maxGroupId = "-12345678",
        )
        org.junit.Assert.assertTrue(encrypted.startsWith("csqttr://"))
        val parsed = parseMrttLink(encrypted)
        org.junit.Assert.assertNotNull(parsed)
        org.junit.Assert.assertEquals("-12345678", parsed!!.maxGroupId)
        org.junit.Assert.assertTrue(parsed.hashes.isEmpty())
    }

}
