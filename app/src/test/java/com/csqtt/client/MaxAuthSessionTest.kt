package com.csqtt.client

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class MaxAuthSessionTest {
    @Test
    fun `monitor continues after token until a group is opened and stops after completion`() {
        mockStatic(Looper::class.java).use { loopers ->
            loopers.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock(Looper::class.java))
            mockStatic(SystemClock::class.java).use {
                mockConstruction(Handler::class.java).use {
                    val view = mock(WebView::class.java)
                    `when`(view.url).thenReturn("https://web.max.ru/")
                    var client: WebViewClient? = null
                    doAnswer { client = it.getArgument(0); null }.`when`(view).setWebViewClient(any())
                    val token = "test-token-".repeat(5)
                    var url = "https://web.max.ru/"
                    var ready = false
                    doAnswer {
                        it.getArgument<ValueCallback<String>?>(1)?.onReceiveValue(
                            JSONObject().put("url", url).put("token", token).put("ready", ready).toString(),
                        )
                        null
                    }.`when`(view).evaluateJavascript(anyString(), any())
                    var detected = 0
                    var uiReady = 0
                    val saved = mutableListOf<MaxAuthCredentials>()
                    val session = MaxAuthSession(onUiReady = { uiReady++ }, onTokenDetected = { detected++ }, onCredentials = { saved.add(it) })
                    session.attach(view)
                    client!!.onPageFinished(view, url)
                    assertEquals("HTML loading alone is not a ready login form", 0, uiReady)
                    assertEquals(0, detected)
                    ready = true
                    client!!.onPageFinished(view, url)
                    assertEquals(1, uiReady)
                    assertEquals(1, detected)
                    assertTrue(saved.isEmpty())
                    verify(view, never()).onPause()
                    url = "https://web.max.ru/-123"
                    client!!.onPageFinished(view, url)
                    assertEquals(listOf(MaxAuthCredentials(token, "-123")), saved)
                    client!!.onPageFinished(view, url)
                    assertEquals(1, saved.size)
                    assertEquals(1, detected)
                    verify(view).onPause()
                    session.stop()
                }
            }
        }
    }
}
