package com.wickwirez.goingdark

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(
    brokers: List<Broker>,
    profile: Profile,
    onResult: (BrokerResult) -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val engine = remember { ScanEngine(ctx) }
    val log = remember { mutableStateListOf<String>() }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        engine.run(brokers, profile) { r ->
            onResult(r)
            val name = brokers.firstOrNull { it.id == r.brokerId }?.name ?: r.brokerId
            log.add(0, name + "  >  " + statusText(r.status))
        }
        finished = true
    }
    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }
    BackHandler { onExit() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            if (finished) "SCAN COMPLETE" else "SCANNING  " + engine.done + "/" + brokers.size,
            color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = Mono
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { engine.done / brokers.size.toFloat() },
            modifier = Modifier.fillMaxWidth(), color = Cyan, trackColor = Panel
        )
        Spacer(Modifier.height(6.dp))
        Text(engine.current, color = Ink, fontWeight = FontWeight.SemiBold)
        Text(engine.step, color = if (engine.needsHuman) Amber else Dim, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        AndroidView(
            factory = { engine.webView },
            modifier = Modifier.fillMaxWidth().weight(1f)
                .border(1.dp, if (engine.needsHuman) Amber else Dim)
        )
        if (engine.needsHuman) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { engine.humanDone(true) }, modifier = Modifier.weight(1f)) {
                    Text("I SOLVED IT")
                }
                OutlinedButton(onClick = { engine.humanDone(false) }, modifier = Modifier.weight(1f)) {
                    Text("SKIP SITE")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        for (line in log.take(3)) {
            Text(
                line, color = Dim, fontSize = 12.sp, fontFamily = Mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(if (finished) "BACK TO RESULTS" else "STOP SCAN")
        }
    }
}

@Composable
fun OptOutScreen(
    broker: Broker,
    profile: Profile,
    listingUrl: String,
    onSent: () -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val fillJs = remember { ctx.assets.open("autofill.js").bufferedReader().use { it.readText() } }
    val webView = remember {
        WebView(ctx).apply {
            prepareWebView(this, broker.ua == "stock")
            webViewClient = WebViewClient()
        }
    }
    var filled by remember { mutableStateOf("") }
    val tip = if (broker.tip.isNotEmpty()) {
        broker.tip
    } else {
        "Follow the site's removal steps. Solve any CAPTCHA and submit. If they email a confirmation link, tap it to finish."
    }

    LaunchedEffect(Unit) {
        if (listingUrl.isNotEmpty()) copyText(ctx, "Listing link", listingUrl)
        webView.loadUrl(broker.optOut)
        val pj = profile.toFillJson(listingUrl)
        while (true) {
            delay(2500)
            val r = evalJson(webView, "(" + fillJs + ")(" + pj + ")")
            val arr = r?.optJSONArray("filled")
            if (arr != null && arr.length() > 0) {
                val names = ArrayList<String>()
                for (i in 0 until arr.length()) names.add(arr.optString(i))
                filled = "Filled in: " + names.distinct().joinToString(", ")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onExit()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            "OPT OUT  //  " + broker.name,
            color = Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = Mono
        )
        Spacer(Modifier.height(6.dp))
        Text(tip, color = Ink, fontSize = 13.sp)
        if (profile.email.isBlank()) {
            Text(
                "Add your email under EDIT MY DETAILS so the app can fill it in.",
                color = Amber, fontSize = 12.sp
            )
        }
        if (listingUrl.isNotEmpty()) {
            Text(
                "Your listing link is on the clipboard. Long-press a box and tap Paste if it is not filled in.",
                color = Dim, fontSize = 12.sp
            )
        }
        if (filled.isNotEmpty()) Text(filled, color = Green, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Dim)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { openUrl(ctx, broker.optOut) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("PAGE BLANK OR STUCK? OPEN IN BROWSER")
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSent, modifier = Modifier.weight(1f)) { Text("MARK AS SENT") }
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("CLOSE") }
        }
    }
}
