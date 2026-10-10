package com.wickwirez.goingdark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun GoingDarkApp() {
    val ctx = LocalContext.current
    val brokers = remember { loadBrokers(ctx) }
    val scanList = remember { brokers.filter { it.search.isNotEmpty() && !it.hand } }
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
    var todoOnly by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val save: () -> Unit = { Store.saveSent(ctx, sent.toMap()) }
    val touch: () -> Unit = {
        timerTouch(sent, System.currentTimeMillis())
        save()
    }

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
                        markSent(sent, t.id, System.currentTimeMillis())
                        touch()
                        screen = Screen.HOME
                    },
                    onNone = {
                        sent[noneKey(t.id)] = System.currentTimeMillis()
                        touch()
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
                    scanList, profile,
                    onResult = { r ->
                        results[r.brokerId] = r
                        Store.saveResults(ctx, results.values.toList())
                        if (applyScan(sent, r.brokerId, r.status, r.checkedAt)) save()
                    },
                    onExit = { screen = Screen.HOME }
                )
                else -> HomeScreen(
                    brokers, results, sent, profile, listState,
                    todoOnly = todoOnly,
                    onTodoToggle = { todoOnly = !todoOnly },
                    onScan = { screen = Screen.SCAN },
                    onProfile = { screen = Screen.PROFILE },
                    onOptOut = { b, url ->
                        touch()
                        target = b
                        targetUrl = url
                        screen = Screen.OPTOUT
                    },
                    onExternal = { url ->
                        touch()
                        openUrl(ctx, url)
                    },
                    onMarkSent = { b ->
                        markSent(sent, b.id, System.currentTimeMillis())
                        touch()
                    },
                    onSetManual = { b, on ->
                        if (on) {
                            sent[manualKey(b.id)] = System.currentTimeMillis()
                            forgetDrop(sent, b.id)
                        } else {
                            sent.remove(manualKey(b.id))
                        }
                        save()
                    },
                    onSetNone = { b, on ->
                        if (on) sent[noneKey(b.id)] = System.currentTimeMillis() else sent.remove(noneKey(b.id))
                        save()
                    },
                    onAppeal = { b ->
                        sent[appealKey(b.id)] = System.currentTimeMillis()
                        save()
                    },
                    onUndoAppeal = { b ->
                        sent.remove(appealKey(b.id))
                        save()
                    },
                    onTimerToggle = {
                        val now = System.currentTimeMillis()
                        if ((sent[TIMER_SINCE] ?: 0L) > 0L) timerPause(sent, now) else timerTouch(sent, now)
                        save()
                    },
                    onTimerAdjust = { d ->
                        timerAdjust(sent, d, System.currentTimeMillis())
                        save()
                    },
                    onTimerExpire = {
                        timerPause(sent, System.currentTimeMillis())
                        save()
                    }
                )
            }
        }
    }
}
