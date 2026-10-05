package com.wickwirez.goingdark

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

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
