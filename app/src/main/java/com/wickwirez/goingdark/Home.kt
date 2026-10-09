package com.wickwirez.goingdark

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    brokers: List<Broker>,
    results: Map<String, BrokerResult>,
    sent: Map<String, Long>,
    profile: Profile,
    listState: LazyListState,
    todoOnly: Boolean,
    onTodoToggle: () -> Unit,
    onScan: () -> Unit,
    onProfile: () -> Unit,
    onOptOut: (Broker, String) -> Unit,
    onExternal: (String) -> Unit,
    onMarkSent: (Broker) -> Unit,
    onSetManual: (Broker, Boolean) -> Unit,
    onSetNone: (Broker, Boolean) -> Unit,
    onTimerToggle: () -> Unit,
    onTimerAdjust: (Long) -> Unit,
    onTimerExpire: () -> Unit
) {
    val ctx = LocalContext.current
    val siteRows = brokers.filter { it.search.isNotEmpty() }
    val scanList = siteRows.filter { !it.hand }
    val formOnly = brokers.filter { it.search.isEmpty() }
    val listed = scanList.count {
        sent[manualKey(it.id)] != null || results[it.id]?.status == Status.LISTED
    }
    val clear = scanList.count {
        sent[manualKey(it.id)] == null && results[it.id]?.status == Status.CLEAR
    }
    val scanned = scanList.count { sent[manualKey(it.id)] != null || results[it.id] != null }
    val now = System.currentTimeMillis()
    val todoCount = brokers.count { needsAction(it, results, sent, now) }
    val shownSites = if (todoOnly) siteRows.filter { needsAction(it, results, sent, now) } else siteRows
    val shownForms = if (todoOnly) formOnly.filter { needsAction(it, results, sent, now) } else formOnly
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
                Stat(scanList.size - scanned, "NOT SCANNED", Dim, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            TimerPanel(sent, onTimerToggle, onTimerAdjust, onTimerExpire)
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTodoToggle, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (todoOnly) "SHOW ALL ROWS" else "SHOW TO-DO ONLY (" + todoCount + ")", fontFamily = Mono)
            }
            if (todoOnly && todoCount == 0) {
                Text("Nothing needs you right now.", color = Green, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
        items(shownSites, key = { it.id }) { b ->
            BrokerRow(
                b, results[b.id], sent[b.id], sent[manualKey(b.id)] != null, sent[noneKey(b.id)],
                onOpen = { url -> onExternal(url) },
                onOptOut = { url -> onOptOut(b, url) },
                onSearchInBrowser = { onExternal(b.buildUrl(profile)) },
                onOptOutInBrowser = { onExternal(b.optOut) },
                onEmail = { url -> onExternal(b.mailto(profile, url)) },
                onMarkSent = { onMarkSent(b) },
                onSetManual = { on -> onSetManual(b, on) },
                onSetNone = { on -> onSetNone(b, on) }
            )
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text("OPT-OUT ONLY", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
            Text(
                "These cannot be scanned. Each button opens the removal page or a filled-in email. " +
                    "Tap NOT LISTED when a site has nothing on you, or once it confirms you are removed.",
                color = Dim, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
        }
        items(shownForms, key = { it.id }) { b ->
            FormRow(
                b, sent[b.id], sent[noneKey(b.id)],
                onOptOut = { onOptOut(b, "") },
                onBrowser = { onExternal(b.optOut) },
                onEmail = { onExternal(b.mailto(profile, "")) },
                onFind = { onExternal(b.findUrl(profile)) },
                onReport = { onExternal(AG_COMPLAINT) },
                onMarkSent = { onMarkSent(b) },
                onSetNone = { on -> onSetNone(b, on) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
