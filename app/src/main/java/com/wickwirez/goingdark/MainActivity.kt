package com.wickwirez.goingdark

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

val Bg = Color(0xFF05070A)
val Panel = Color(0xFF0E141B)
val Cyan = Color(0xFF00E5FF)
val Pink = Color(0xFFFF2D95)
val Green = Color(0xFF39FF14)
val Amber = Color(0xFFFFB000)
val Ink = Color(0xFFD7E3EA)
val Dim = Color(0xFF6B7C88)
val Mono = FontFamily.Monospace

enum class Screen { HOME, PROFILE, SCAN }

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

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
    }
}

@Composable
fun GoingDarkApp() {
    val ctx = LocalContext.current
    val brokers = remember { loadBrokers(ctx) }
    val results = remember {
        mutableStateMapOf<String, BrokerResult>().apply { putAll(Store.loadResults(ctx)) }
    }
    var profile by remember { mutableStateOf(Store.loadProfile(ctx)) }
    var screen by remember { mutableStateOf(if (profile.ready) Screen.HOME else Screen.PROFILE) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Bg, secondary = Pink, background = Bg, onBackground = Ink,
            surface = Panel, onSurface = Ink, onSurfaceVariant = Dim, outline = Dim
        )
    ) {
        Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    brokers, results, profile.ready,
                    onScan = { screen = Screen.SCAN },
                    onProfile = { screen = Screen.PROFILE }
                )
                Screen.PROFILE -> ProfileScreen(
                    profile,
                    canGoBack = profile.ready,
                    onBack = { screen = Screen.HOME },
                    onSave = { p ->
                        profile = p
                        Store.saveProfile(ctx, p)
                        screen = Screen.HOME
                    }
                )
                Screen.SCAN -> ScanScreen(
                    brokers, profile,
                    onResult = { r ->
                        results[r.brokerId] = r
                        Store.saveResults(ctx, results.values.toList())
                    },
                    onExit = { screen = Screen.HOME }
                )
            }
        }
    }
}

@Composable
fun Stat(n: Int, title: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Panel).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(n.toString(), color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Text(title, color = Dim, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
fun HomeScreen(
    brokers: List<Broker>,
    results: Map<String, BrokerResult>,
    canScan: Boolean,
    onScan: () -> Unit,
    onProfile: () -> Unit
) {
    val ctx = LocalContext.current
    val listed = brokers.count { results[it.id]?.status == Status.LISTED }
    val clear = brokers.count { results[it.id]?.status == Status.CLEAR }
    val scanned = brokers.count { results[it.id] != null }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
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
                Stat(brokers.size - scanned, "NOT SCANNED", Dim, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onScan, enabled = canScan,
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
            Spacer(Modifier.height(14.dp))
        }
        items(brokers, key = { it.id }) { b ->
            BrokerRow(b, results[b.id]) { url -> openUrl(ctx, url) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun BrokerRow(b: Broker, r: BrokerResult?, onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(Panel).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(statusText(r?.status), color = statusColor(r?.status), fontSize = 12.sp, fontFamily = Mono)
        }
        if (r != null) {
            if (r.note.isNotEmpty()) Text(r.note, color = Dim, fontSize = 12.sp)
            for (l in r.listings) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onOpen(l.url) }) {
                    Text(
                        if (l.strong) "STRONG MATCH" else "POSSIBLE MATCH",
                        color = if (l.strong) Pink else Amber, fontSize = 11.sp, fontFamily = Mono
                    )
                    Text(l.text, color = Ink, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("Tap to open the listing", color = Cyan, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(initial: Profile, canGoBack: Boolean, onBack: () -> Unit, onSave: (Profile) -> Unit) {
    var first by remember { mutableStateOf(initial.first) }
    var last by remember { mutableStateOf(initial.last) }
    var city by remember { mutableStateOf(initial.city) }
    var st by remember { mutableStateOf(initial.state) }
    var year by remember { mutableStateOf(initial.birthYear) }
    var others by remember { mutableStateOf(initial.otherNames) }
    var past by remember { mutableStateOf(initial.pastPlaces) }
    val draft = Profile(
        first.trim(), last.trim(), others.trim(), year.trim(), city.trim(), st.trim(), past.trim()
    )
    BackHandler(enabled = canGoBack) { onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("YOUR DETAILS", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
        Text(
            "Encrypted on this phone and never uploaded. Used only to build the searches and recognise your listings.",
            color = Dim, fontSize = 13.sp
        )
        Field(first, { first = it }, "First name")
        Field(last, { last = it }, "Last name")
        Field(city, { city = it }, "Current city")
        Field(st, { st = it.uppercase().take(2) }, "State, 2 letters (TX)")
        Field(
            year, { v -> year = v.filter { c -> c.isDigit() }.take(4) },
            "Birth year (sharpens matching)", KeyboardType.Number
        )
        Field(others, { others = it }, "Other names, comma separated (optional)")
        Field(past, { past = it }, "Past places: City, ST; City, ST (optional)")
        Button(
            onClick = { onSave(draft) }, enabled = draft.ready,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(10.dp)
        ) {
            Text("SAVE", fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(hint) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth()
    )
}

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
