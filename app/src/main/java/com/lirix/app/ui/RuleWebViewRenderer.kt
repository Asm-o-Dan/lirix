package com.lirix.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lirix.app.feature.lyrics.RenderResult
import com.lirix.app.feature.lyrics.RuleHttpClient
import com.lirix.app.feature.lyrics.RuleRenderBudget
import com.lirix.app.feature.lyrics.RuleRenderer
import com.lirix.app.feature.lyrics.RuleRendererRegistry
import com.lirix.app.feature.lyrics.RuleUrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URI
import kotlin.coroutines.resume

/**
 * Controlled off-screen WebView for JavaScript-rendered rule pages (SPEC-TEACH-06 R6, §5.2 item 8).
 *
 * Used in two places: the interactive rule check in TeachMode, and the automatic cascade for
 * rules the check marked `render.engine = WEBVIEW` (decision 2026-11-13). Every other rule
 * stays on plain HTTP and never creates a WebView.
 *
 * Waiting is adaptive, not a fixed delay: the page is polled until the rule's selector holds
 * text whose length stopped changing between two polls, so a fast page returns at once and a
 * slow one gets up to the deadline stored in the rule (measured during the check, with a margin).
 *
 * Cost control for the cascade ([background] = true): one session at a time for the whole app,
 * [RuleRenderBudget] (a few sessions per ten minutes, a pause for a failing site), and no
 * rendering in battery saver mode.
 *
 * Guarantees: main thread only, a hard deadline, no JavaScript bridge, no file/content access,
 * images not loaded, navigation limited to the rule's domain over HTTPS, and destruction in
 * `finally` (also on cancellation).
 */
class RuleWebViewRenderer(
    context: Context,
    private val ruleDomain: String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_MS,
    private val background: Boolean = false
) : RuleRenderer {

    private val appContext = context.applicationContext

    override suspend fun render(url: String, readySelector: String): RenderResult {
        if (RuleUrlPolicy.check(url, ruleDomain) != null) return RenderResult.Failed
        if (!background) return sessionLock.withLock { withContext(Dispatchers.Main) { renderOnMain(url, readySelector) } }

        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isPowerSaveMode == true) return RenderResult.Unavailable
        if (!RuleRenderBudget.tryAcquire(ruleDomain, SystemClock.elapsedRealtime())) return RenderResult.Unavailable
        val result = sessionLock.withLock { withContext(Dispatchers.Main) { renderOnMain(url, readySelector) } }
        if (result != RenderResult.Cancelled) {
            RuleRenderBudget.report(ruleDomain, result is RenderResult.Html, SystemClock.elapsedRealtime())
        }
        return result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun renderOnMain(url: String, readySelector: String): RenderResult {
        val webView = try {
            WebView(appContext)
        } catch (t: Throwable) {
            // Missing or updating WebView provider.
            Timber.w(t, "Rule renderer: WebView unavailable")
            return RenderResult.Unavailable
        }
        var pageFinished = false
        var mainFrameFailed = false
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkImage = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setGeolocationEnabled(false)
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString() ?: return true
                    return RuleUrlPolicy.check(target, ruleDomain) != null
                }

                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    pageFinished = true
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) mainFrameFailed = true
                }
            }
            webView.loadUrl(url)

            val quotedSelector = JSONObject.quote(readySelector)
            // Total length of the text under the selector: 0 = not there yet, -1 = bad selector.
            val readyProbe = "(function(){try{var e=document.querySelectorAll($quotedSelector),n=0;" +
                "for(var i=0;i<e.length;i++){n+=(e[i].innerText||'').trim().length;}return n;}catch(x){return -1;}})()"

            val startedAt = SystemClock.elapsedRealtime()
            var previousLength = 0
            val outcome = withTimeoutOrNull(timeoutMs) {
                while (true) {
                    delay(pollIntervalMs)
                    if (mainFrameFailed) return@withTimeoutOrNull RenderResult.Failed
                    val length = evaluate(webView, readyProbe)?.toIntOrNull() ?: 0
                    if (length < 0) return@withTimeoutOrNull RenderResult.NotReady(null)
                    // Text that is still growing (loaded in parts) is not ready yet.
                    if (isSettled(previousLength, length)) {
                        val html = readHtml(webView) ?: return@withTimeoutOrNull RenderResult.Failed
                        return@withTimeoutOrNull RenderResult.Html(html, webView.url ?: url, SystemClock.elapsedRealtime() - startedAt)
                    }
                    previousLength = length
                }
                @Suppress("UNREACHABLE_CODE")
                RenderResult.Failed
            }
            // The deadline has passed: a page that never answers must not hold the session open.
            return outcome ?: if (pageFinished) {
                RenderResult.NotReady(withTimeoutOrNull(READ_HTML_TIMEOUT_MS) { readHtml(webView) })
            } else {
                RenderResult.Timeout
            }
        } finally {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private suspend fun evaluate(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value) }
        }

    private suspend fun readHtml(webView: WebView): String? {
        val raw = evaluate(webView, "document.documentElement.outerHTML") ?: return null
        if (raw == "null" || raw.length > RuleHttpClient.MAX_BODY_BYTES * 2) return null
        return runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val DEFAULT_POLL_MS = 300L
        const val READ_HTML_TIMEOUT_MS = 2_000L

        /** Content is ready when it is present and its length did not change since the last poll. */
        fun isSettled(previousLength: Int, length: Int): Boolean = length > 0 && length == previousLength

        /** Lets the automatic cascade run rules that need a rendered page. Call once at app start. */
        fun installForBackgroundRules(context: Context) {
            val appContext = context.applicationContext
            RuleRendererRegistry.factory = { domain, timeoutMs ->
                RuleWebViewRenderer(appContext, domain, timeoutMs, background = true)
            }
        }
        private val sessionLock = Mutex()

        fun domainOf(url: String): String =
            runCatching { URI(url).host.orEmpty() }.getOrDefault("").let(RuleUrlPolicy::normalizeDomain)
    }
}
