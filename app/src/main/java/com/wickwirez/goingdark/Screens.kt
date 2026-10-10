package com.wickwirez.goingdark

import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import org.json.JSONObject

// Types a value into the box you tapped on the page, looking inside frames and page parts it can reach. When focus
// has left the page, it uses the box you tapped last (autofill.js remembers it). Returns 'ok', or why it could not:
// then the chip copies the value for you to paste.
fun insertJs(value: String): String =
    "(function(v){" +
        "var NOT=['hidden','checkbox','radio','submit','button','file','image','reset','range','color'];" +
        "function box(e){if(!e||!e.tagName)return false;" +
        "if(e.tagName==='TEXTAREA')return true;" +
        "if(e.tagName==='INPUT')return NOT.indexOf((e.type||'text').toLowerCase())<0;" +
        "return !!e.isContentEditable;}" +
        "var el=document.activeElement;" +
        "try{for(var i=0;i<20&&el;i++){" +
        "if(el.shadowRoot&&el.shadowRoot.activeElement){el=el.shadowRoot.activeElement;continue;}" +
        "if((el.tagName==='IFRAME'||el.tagName==='FRAME')&&el.contentDocument){" +
        "var inner=el.contentDocument.activeElement;" +
        "if(inner&&inner!==el.contentDocument.body){el=inner;continue;}}" +
        "break;}}catch(x){}" +
        "if(el&&(el.tagName==='IFRAME'||el.tagName==='FRAME'))return 'frame';" +
        "if(!box(el)){" +
        "if(el&&el!==document.body&&el!==document.documentElement)return 'nofocus';" +
        "var last=window.__gdLast;" +
        "if(!(last&&last.isConnected&&box(last)))return 'nofocus';" +
        "el=last;}" +
        "if(el.disabled||el.readOnly)return 'locked';" +
        "try{el.focus();}catch(x){}" +
        "if(el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA'){" +
        "var r=false;try{r=el.ownerDocument.execCommand('insertText',false,v);}catch(x){}" +
        "if(!r)el.textContent=v;" +
        "el.dispatchEvent(new Event('input',{bubbles:true}));return 'ok';}" +
        "var w=(el.ownerDocument&&el.ownerDocument.defaultView)||window;" +
        "var p=el.tagName==='TEXTAREA'?w.HTMLTextAreaElement.prototype:w.HTMLInputElement.prototype;" +
        "var d=Object.getOwnPropertyDescriptor(p,'value');" +
        "if(d&&d.set)d.set.call(el,v);else el.value=v;" +
        "['keydown','keypress','input','keyup','change'].forEach(function(n){" +
        "el.dispatchEvent(new w.Event(n,{bubbles:true}));});" +
        "return el.value?'ok':'refused';})(" + JSONObject.quote(value) + ")"

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
    onNone: () -> Unit,
    onExit: () -> Unit
) {
    val ctx = LocalContext.current
    val fillJs = remember { ctx.assets.open("autofill.js").bufferedReader().use { it.readText() } }
    val webView = remember {
        WebView(ctx).apply {
            prepareWebView(this, broker.ua == "stock")
            webViewClient = QuietClient()
        }
    }
    var filled by remember { mutableStateOf("") }
    val tip = if (broker.tip.isNotEmpty()) {
        broker.tip
    } else {
        "Follow the site's removal steps. Solve any CAPTCHA and submit. If they email a confirmation link, tap it to finish."
    }
    val chips = remember(profile, listingUrl) {
        listOf(
            "Email" to profile.email.trim(),
            "Request" to requestText(profile),
            "Subject" to subjectText(profile),
            "First" to profile.first.trim(),
            "Middle" to profile.middle.trim(),
            "Last" to profile.last.trim(),
            "Full name" to (profile.first.trim() + " " + profile.last.trim()),
            "Street" to profile.street.trim(),
            "City" to profile.city.trim(),
            "State" to profile.state.trim().uppercase(),
            "ZIP" to profile.zip.trim(),
            "Phone" to profile.phone.trim(),
            "Listing link" to listingUrl
        ).filter { it.second.isNotBlank() }
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
        Spacer(Modifier.height(4.dp))
        Text(tip, color = Ink, fontSize = 12.sp)
        if (profile.email.isBlank()) {
            Text(
                "Add your email under EDIT MY DETAILS so the app can fill it in.",
                color = Amber, fontSize = 12.sp
            )
        }
        if (filled.isNotEmpty()) Text(filled, color = Green, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Dim)
        )
        Spacer(Modifier.height(6.dp))
        Text("Tap a box on the page, then tap a chip to type it in.", color = Dim, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (chip in chips) {
                OutlinedButton(
                    onClick = {
                        webView.evaluateJavascript(insertJs(chip.second)) { r ->
                            if (r != "\"ok\"") {
                                copyText(ctx, chip.first, chip.second)
                                Toast.makeText(ctx, chip.first + " copied. Press and hold the box, then tap Paste.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(chip.first, fontSize = 12.sp)
                }
            }
        }
        val pad = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (saveProof(ctx, webView, broker)) {
                        Toast.makeText(ctx, "Proof saved to your Gallery, album " + PROOF_ALBUM, Toast.LENGTH_SHORT).show()
                    }
                    onSent()
                },
                modifier = Modifier.weight(1f), contentPadding = pad
            ) {
                Text(if (broker.tool) "MARK AS DONE" else "MARK AS SENT")
            }
            if (broker.search.isEmpty()) {
                OutlinedButton(onClick = onNone, modifier = Modifier.weight(1f), contentPadding = pad) {
                    Text("NOT LISTED")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { openUrl(ctx, broker.optOut) }, modifier = Modifier.weight(1f), contentPadding = pad
            ) {
                Text("OPEN IN BROWSER")
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f), contentPadding = pad) {
                Text("CLOSE")
            }
        }
    }
}
