package com.wickwirez.goingdark

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

val Bg = Color(0xFF05070A)
val Panel = Color(0xFF0E141B)
val Cyan = Color(0xFF00E5FF)
val Pink = Color(0xFFFF2D95)
val Green = Color(0xFF39FF14)
val Amber = Color(0xFFFFB000)
val Ink = Color(0xFFD7E3EA)
val Dim = Color(0xFF6B7C88)
val Mono = FontFamily.Monospace

enum class Screen { HOME, PROFILE, SCAN, OPTOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent { GoingDarkApp() }
    }
}

fun statusText(s: Status?): String = when (s) {
    Status.LISTED -> "LISTED"
    Status.CLEAR -> "NOT FOUND"
    Status.BLOCKED -> "BLOCKED"
    Status.UNKNOWN -> "COULDN'T CHECK"
    null -> "NOT SCANNED"
}

fun statusColor(s: Status?): Color = when (s) {
    Status.LISTED -> Pink
    Status.CLEAR -> Green
    Status.BLOCKED -> Amber
    Status.UNKNOWN -> Amber
    null -> Dim
}

fun rowLabel(r: BrokerResult?, sentAt: Long?): String {
    if (r == null) return "NOT SCANNED"
    if (r.status == Status.CLEAR && sentAt != null) return "REMOVED"
    if (r.status == Status.UNKNOWN && r.note.startsWith("Page failed to load")) return "UNREACHABLE"
    return statusText(r.status)
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
    }
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun dateText(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun buildReport(brokers: List<Broker>, results: Map<String, BrokerResult>, sent: Map<String, Long>): String {
    val sb = StringBuilder("Going Dark report\n")
    for (b in brokers) {
        sb.append(b.name).append(": ")
        if (b.search.isEmpty()) {
            sb.append(if (sent[b.id] != null) "opt-out sent" else "opt-out not sent").append("\n")
            continue
        }
        val r = results[b.id]
        sb.append(rowLabel(r, sent[b.id]))
        if (r != null) {
            if (r.listings.isNotEmpty()) sb.append(" (").append(r.listings.size).append(" listing)")
            if (r.note.isNotEmpty()) sb.append(" - ").append(r.note)
        }
        if (sent[b.id] != null) sb.append(" [opt-out sent]")
        sb.append("\n")
    }
    return sb.toString()
}

@Composable
fun GoingDarkApp() {
    val ctx = LocalContext.current
    val brokers = remember { loadBrokers(ctx) }
    val scannable = remember { brokers.filter { it.search.isNotEmpty() } }
    val results = remember {
        mutableStateMapOf<String, BrokerResult>().apply { putAll(Store.loadResults(ctx)) }
    }
    val sent = remember {
        mutableStateMapOf<String, Long>().apply { putAll(Store.loadSent(ctx)) }
    }
    var profile by remember { mutableStateOf(Store.loadProfile(ctx)) }
    var screen by remember { mutableStateOf(if (profile.ready) Screen.HOME else Screen.PROFILE) }
    var target by remember { mutableStateOf<Broker?>(null) }
    var targetUrl by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Bg, secondary = Pink, background = Bg, onBackground = Ink,
            surface = Panel, onSurface = Ink, onSurfaceVariant = Dim, outline = Dim
        )
    ) {
        Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
            val t = target
            when {
                screen == Screen.OPTOUT && t != null -> OptOutScreen(
                    t, profile, targetUrl,
                    onSent = {
                        sent[t.id] = System.currentTimeMillis()
                        Store.saveSent(ctx, sent.toMap())
                        screen = Screen.HOME
                    },
                    onExit = { screen = Screen.HOME }
                )
                screen == Screen.PROFILE -> ProfileScreen(
                    profile,
                    canGoBack = profile.ready,
                    onBack = { screen = Screen.HOME },
                    onSave = { p ->
                        profile = p
                        Store.saveProfile(ctx, p)
                        screen = Screen.HOME
                    }
                )
                screen == Screen.SCAN -> ScanScreen(
                    scannable, profile,
                    onResult = { r ->
                        results[r.brokerId] = r
                        Store.saveResults(ctx, results.values.toList())
                    },
                    onExit = { screen = Screen.HOME }
                )
                else -> HomeScreen(
                    brokers, results, sent, profile, listState,
                    onScan = { screen = Screen.SCAN },
                    onProfile = { screen = Screen.PROFILE },
                    onOptOut = { b, url ->
                        target = b
                        targetUrl = url
                        screen = Screen.OPTOUT
                    },
                    onMarkSent = { b ->
                        sent[b.id] = System.currentTimeMillis()
                        Store.saveSent(ctx, sent.toMap())
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    brokers: List<Broker>,
    results: Map<String, BrokerResult>,
    sent: Map<String, Long>,
    profile: Profile,
    listState: LazyListState,
    onScan: () -> Unit,
    onProfile: () -> Unit,
    onOptOut: (Broker, String) -> Unit,
    onMarkSent: (Broker) -> Unit
) {
    val ctx = LocalContext.current
    val scannable = brokers.filter { it.search.isNotEmpty() }
    val formOnly = brokers.filter { it.search.isEmpty() }
    val listed = scannable.count { results[it.id]?.status == Status.LISTED }
    val clear = scannable.count { results[it.id]?.status == Status.CLEAR }
    val scanned = scannable.count { results[it.id] != null }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), state = listState) {
        item {
            Spacer(Modifier.height(18.dp))
            Text(
                "GOING DARK", color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Black,
                fontFamily = Mono, letterSpacing = 4.sp
            )
            Text("data broker hunter  //  by Wick", color = Dim, fontSize = 12.sp, fontFamily = Mono)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(listed, "LISTED", Pink, Modifier.weight(1f))
                Stat(clear, "NOT FOUND", Green, Modifier.weight(1f))
                Stat(scanned - listed - clear, "UNVERIFIED", Amber, Modifier.weight(1f))
                Stat(scannable.size - scanned, "NOT SCANNED", Dim, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onScan, enabled = profile.ready,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(10.dp)
            ) {
                Text("RUN SCAN", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onProfile, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
            ) {
                Text("EDIT MY DETAILS", fontFamily = Mono)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    copyText(ctx, "Going Dark report", buildReport(brokers, results, sent))
                    Toast.makeText(ctx, "Report copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
            ) {
                Text("COPY REPORT", fontFamily = Mono)
            }
            Spacer(Modifier.height(14.dp))
        }
        items(scannable, key = { it.id }) { b ->
            BrokerRow(
                b, results[b.id], sent[b.id],
                onOpen = { url -> openUrl(ctx, url) },
                onOptOut = { url -> onOptOut(b, url) },
                onSearchInBrowser = { openUrl(ctx, b.buildUrl(profile)) },
                onOptOutInBrowser = { openUrl(ctx, b.optOut) },
                onMarkSent = { onMarkSent(b) }
            )
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text("OPT-OUT ONLY", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
            Text(
                "These sites cannot be scanned, but one removal form covers each group.",
                color = Dim, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
        }
        items(formOnly, key = { it.id }) { b ->
            FormRow(b, sent[b.id], onOptOut = { onOptOut(b, "") })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
