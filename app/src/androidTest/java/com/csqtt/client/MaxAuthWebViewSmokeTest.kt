package com.csqtt.client

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.csqtt.client.ui.dialogs.MaxAuthDialog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MaxAuthWebViewSmokeTest {
    @Test
    fun loginFormRendersInAndroidWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val diagnostic = AtomicReference("")
        val console = mutableListOf<String>()
        var webView: WebView? = null
        var rendered = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { activity ->
                    activity.setContent {
                        MaterialTheme(colorScheme = lightColorScheme()) {
                            MaxAuthDialog(requiredGroupId = "", onBind = {}, onDismiss = {})
                        }
                    }
                }
                repeat(100) {
                    if (webView == null) {
                        instrumentation.runOnMainSync {
                            webView = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView)
                        }
                        if (webView == null) Thread.sleep(100)
                    }
                }
                assertTrue("MAX dialog must attach a WebView", webView != null)
                instrumentation.runOnMainSync {
                    val view = webView!!
                    view.webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                synchronized(console) { console.add(message.message().take(500)) }
                            }
                            return true
                        }
                    }
                    println("MAX smoke WebView: ${WebView.getCurrentWebViewPackage()?.versionName}; UA: ${view.settings.userAgentString}")
                    view.reload()
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75)
                while (!rendered && System.nanoTime() < deadline) {
                    val latch = CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        webView!!.evaluateJavascript(
                            "({ready: !!document.querySelector('button, input') && document.body.innerText.trim().length > 20, text: document.body.innerText.slice(0, 300)})",
                        ) { value ->
                            val snapshot = runCatching { JSONObject(value) }.getOrNull()
                            rendered = snapshot?.optBoolean("ready") == true
                            diagnostic.set(snapshot?.toString().orEmpty())
                            latch.countDown()
                        }
                    }
                    latch.await(5, TimeUnit.SECONDS)
                    if (!rendered) Thread.sleep(1000)
                }
            } finally {
                val file = File(instrumentation.targetContext.getExternalFilesDir(null), "max-webview.png")
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                println("MAX smoke page: ${diagnostic.get()}; JS errors: ${synchronized(console) { console.joinToString(" | ") }}")
            }
        }
        assertTrue("MAX login did not render: ${diagnostic.get()}; JS errors: $console", rendered)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        return (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
    }
}
