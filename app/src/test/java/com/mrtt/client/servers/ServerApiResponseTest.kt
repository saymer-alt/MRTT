package com.mrtt.client.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerApiResponseTest {
    @Test
    fun okResponseReturnsBody() {
        val raw = "HTTP/1.0 200 OK\r\ncontent-type: application/json\r\n\r\n[{\"name\":\"a\"}]".toByteArray()
        assertEquals("[{\"name\":\"a\"}]", ServerApi.parseResponse(raw))
    }

    @Test
    fun errorStatusCarriesServerMessage() {
        val raw = "HTTP/1.0 400 Bad Request\r\n\r\nmax_devices must be 0 (unlimited) or between 1 and 1000".toByteArray()
        val error = runCatching { ServerApi.parseResponse(raw) }.exceptionOrNull()
        assertTrue(error is ServerApiException)
        assertTrue(error!!.message!!.contains("max_devices"))
    }

    @Test
    fun emptyReplyIsAnError() {
        assertTrue(runCatching { ServerApi.parseResponse(ByteArray(0)) }.exceptionOrNull() is ServerApiException)
    }
}
