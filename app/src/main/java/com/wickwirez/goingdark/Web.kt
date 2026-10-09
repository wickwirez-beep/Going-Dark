package com.wickwirez.goingdark

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

// Ad servers. When an ad blocker or private DNS stops one, its box on the page shows
// "Webpage not available". The opt-out screen gives those boxes an empty page instead.
val AD_HOSTS = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
    "googletagservices.com", "2mdn.net", "amazon-adsystem.com", "adnxs.com", "taboola.com",
    "outbrain.com", "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net",
    "adsrvr.org", "casalemedia.com", "media.net", "moatads.com", "smartadserver.com", "yieldmo.com",
    "sharethrough.com", "teads.tv", "33across.com", "lijit.com", "bidswitch.net", "adform.net",
    "revcontent.com", "mgid.com"
)

fun isAdHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.')
    return AD_HOSTS.any { h == it || h.endsWith(".$it") }
}

private val adChecks = ConcurrentHashMap<String, Pair<Boolean, Long>>()

// True when an ad server cannot be reached from this phone right now.
fun adHostBlocked(host: String): Boolean {
    if (!isAdHost(host)) return false
    val now = System.currentTimeMillis()
    val seen = adChecks[host]
    if (seen != null && now - seen.second < 60_000L) return seen.first
    val blocked = try {
        val a = InetAddress.getByName(host)
        a.isAnyLocalAddress || a.isLoopbackAddress
    } catch (e: Exception) {
        true
    }
    adChecks[host] = Pair(blocked, now)
    return blocked
}

class QuietClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        return try {
            val r = request ?: return null
            val host = r.url.host ?: return null
            if (r.isForMainFrame || !adHostBlocked(host)) return null
            WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(ByteArray(0)))
        } catch (e: Exception) {
            null
        }
    }
}

// stock = the phone's plain WebView identity; otherwise present as regular Chrome.
@SuppressLint("SetJavaScriptEnabled")
fun prepareWebView(webView: WebView, stock: Boolean) {
    val s = webView.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.useWideViewPort = true
    s.loadWithOverviewMode = true
    s.javaScriptCanOpenWindowsAutomatically = true
    s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    val base = WebSettings.getDefaultUserAgent(webView.context)
    s.userAgentString = if (stock) {
        base
    } else {
        base.replace("; wv", "").replace(Regex("Version/\\S+\\s"), "")
    }
    webView.webChromeClient = WebChromeClient()
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
}

suspend fun evalJson(webView: WebView, script: String): JSONObject? {
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
