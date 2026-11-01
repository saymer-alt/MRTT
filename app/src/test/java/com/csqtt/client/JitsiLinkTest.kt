package com.csqtt.client

import com.csqtt.client.ui.utils.formatMrttLink
import com.csqtt.client.ui.utils.isValidJitsiRoom
import com.csqtt.client.ui.utils.parseJitsiRoomInput
import org.junit.Assert.assertNull
import com.csqtt.client.ui.utils.parseMrttLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JitsiLinkTest {
    @Test
    fun linkCarriesTheServersJitsiRoom() {
        val link = formatMrttLink("203.0.113.7", 46000, "pass1234567890ab", jitsiRoom = "meet.egovm.ru/mrtt0a1b2c3d")
        assertEquals("meet.egovm.ru/mrtt0a1b2c3d", parseMrttLink(link)!!.jitsiRoom)
    }

    @Test
    fun linkWithoutRoomHasNone() {
        assertEquals("", parseMrttLink(formatMrttLink("203.0.113.7", 46000, "pass1234567890ab"))!!.jitsiRoom)
    }

    @Test
    fun roomFormatIsStrict() {
        assertTrue(isValidJitsiRoom("meet.mamba.group/abc123"))
        assertFalse(isValidJitsiRoom("meet.mamba.group"))
        assertFalse(isValidJitsiRoom("meet.mamba.group/room with space"))
        assertFalse(isValidJitsiRoom("evil.example/../x"))
    }

    @Test
    fun conferenceLinkBecomesRoom() {
        assertEquals("meet.riddlerx.org/myroom", parseJitsiRoomInput(" https://Meet.Riddlerx.org/MyRoom?lang=ru#config "))
        assertEquals("meet.riddlerx.org/myroom", parseJitsiRoomInput("meet.riddlerx.org/myroom/"))
        assertNull(parseJitsiRoomInput("https://meet.riddlerx.org/"))
        assertNull(parseJitsiRoomInput("https://meet.riddlerx.org/моя комната"))
    }
}
