package com.wickwirez.goingdark

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

class ScanEngine(context: Context) {
    val webView = WebView(context)
    private val extractJs = context.assets.open("extract.js").bufferedReader().use { it.readText() }

    var current by mutableStateOf("")
        private set
    var step by mutableStateOf("")
        private set
    var needsHuman by mutableStateOf(false)
        private set
    var done by mutableStateOf(0)
        private set

    private var pageDone: CompletableDeferred<Unit>? = null
    private var human: CompletableDeferred<Boolean>? = null
    private var httpStatus = 200

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        // Present as the regular Chrome on this phone instead of an embedded WebView.
        s.userAgentString = WebSettings.getDefaultUserAgent(webView.context)
            .replace("; wv", "")
            .replace(Regex("Version/\\S+\\s"), "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                httpStatus = 200
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageDone?.complete(Unit)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) httpStatus = errorResponse?.statusCode ?: 0
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) httpStatus = -1
            }
        }
    }

    suspend fun run(brokers: List<Broker>, profile: Profile, onResult: (BrokerResult) -> Unit) {
        val pj = profile.toProbeJson()
        done = 0
        for (b in brokers) {
            current = b.name
            step = "Loading the search"
            load(b.buildUrl(profile))
            var result = settle(b, pj)
            while (result.status == Status.BLOCKED) {
                val gate = CompletableDeferred<Boolean>()
                human = gate
                needsHuman = true
                step = "This site wants a human check. Solve it in the window below."
                var go: Boolean? = null
                while (go == null) {
                    go = withTimeoutOrNull(2500) { gate.await() }
                    if (go == null) {
                        val peek = probe(pj)
                        if (peek != null && !peek.optBoolean("blocked")) go = true
                    }
                }
                human = null
                needsHuman = false
                if (go != true) break
                step = "Check passed. Reading the page"
                result = settle(b, pj)
            }
            onResult(result)
            done += 1
        }
        current = ""
        step = "All sites checked"
    }

    fun humanDone(carryOn: Boolean) {
        human?.complete(carryOn)
    }

    fun destroy() {
        human?.cancel()
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun load(url: String) {
        val gate = CompletableDeferred<Unit>()
        pageDone = gate
        webView.loadUrl(url)
        withTimeoutOrNull(25_000) { gate.await() }
        pageDone = null
    }

    private suspend fun probe(pj: String): JSONObject? {
        val script = "(" + extractJs + ")(" + pj + ")"
        val raw = suspendCancellableCoroutine<String?> { cont ->
            webView.evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(value)
            }
        }
        if (raw == null || raw == "null") return null
        return try {
            val inner = JSONTokener(raw).nextValue() as? String
            if (inner == null) null else JSONObject(inner)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun settle(b: Broker, pj: String): BrokerResult {
        var last: JSONObject? = null
        var lastChars = -1
        for (i in 0 until 10) {
            delay(1500)
            val r = probe(pj) ?: continue
            last = r
            val chars = r.optInt("chars")
            val stable = chars == lastChars
            lastChars = chars
            if (r.optBoolean("blocked")) {
                step = "Waiting on the site's security check"
                continue
            }
            step = "Reading the page"
            if (stable && i >= 3) break
        }
        val now = System.currentTimeMillis()
        val r = last ?: return BrokerResult(b.id, Status.UNKNOWN, now, emptyList(), "Page did not respond")
        if (r.optBoolean("blocked")) {
            return BrokerResult(b.id, Status.BLOCKED, now, emptyList(), "Stopped by the site's human check")
        }
        val found = ArrayList<Listing>()
        val arr = r.optJSONArray("listings")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                found.add(Listing(o.optString("url"), o.optBoolean("strong"), o.optString("text")))
            }
        }
        val none = r.optBoolean("none")
        val okHttp = httpStatus in 200..299 || httpStatus == 404
        return when {
            found.isNotEmpty() && (!none || found.any { it.strong }) ->
                BrokerResult(b.id, Status.LISTED, now, found, "")
            okHttp && (none || r.optBoolean("echo")) ->
                BrokerResult(b.id, Status.CLEAR, now, emptyList(), "")
            httpStatus == -1 ->
                BrokerResult(b.id, Status.UNKNOWN, now, emptyList(), "Page failed to load")
            !okHttp ->
                BrokerResult(b.id, Status.UNKNOWN, now, emptyList(), "Site answered HTTP $httpStatus")
            else ->
                BrokerResult(b.id, Status.UNKNOWN, now, emptyList(), "Page never mentioned your name")
        }
    }
}
