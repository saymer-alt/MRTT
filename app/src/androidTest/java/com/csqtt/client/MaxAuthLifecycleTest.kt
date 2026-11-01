package com.csqtt.client

import android.Manifest
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class MaxAuthLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun pendingBindingSurvivesBackgroundWithoutReloadingTheWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { SettingsStore(context).markBatteryOptimizationPromptHandled() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText(context.getString(R.string.nav_tunnel)).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Привязать MAX").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Привязать MAX").performScrollTo().performClick()
            compose.waitForIdle()
            val original = currentWebView()
            // A local form checks input retention without logging into MAX or relying on its network.
            instrumentation.runOnMainSync {
                original.stopLoading()
                original.loadDataWithBaseURL(
                    "https://web.max.ru/",
                    "<html><body><p>MAX binding lifecycle fixture</p><input id='phone' value='+79001234567'></body></html>",
                    "text/html", "UTF-8", null,
                )
            }
            compose.waitUntil(10_000) { inputValue(original) == "\"+79001234567\"" }

            repeat(2) {
                scenario.moveToState(Lifecycle.State.CREATED)
                instrumentation.waitForIdleSync()
                scenario.moveToState(Lifecycle.State.RESUMED)
                compose.waitForIdle()
                compose.onNodeWithText("Привязка MAX").assertExists()
                assertSame("Returning to the app must keep the same MAX page", original, currentWebView())
                assertEquals("\"+79001234567\"", inputValue(original))
            }
        }
    }

    private fun currentWebView(): WebView {
        var view: WebView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView)
        }
        return requireNotNull(view) { "MAX binding dialog must remain open" }
    }

    private fun inputValue(view: WebView): String? {
        val result = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript("document.getElementById('phone')?.value") {
                result.set(it)
                latch.countDown()
            }
        }
        assertTrue("The MAX page must answer after returning to the app", latch.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        return (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
    }
}
