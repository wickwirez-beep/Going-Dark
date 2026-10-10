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
import kotlinx.coroutines.launch

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
    onAppeal: (Broker) -> Unit,
    onUndoAppeal: (Broker) -> Unit,
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
    // Where each row that still needs you sits in the list below, for the NEXT TO-DO button.
    val todoAt = ArrayList<Int>()
    shownSites.forEachIndexed { i, b -> if (needsAction(b, results, sent, now)) todoAt.add(1 + i) }
    shownForms.forEachIndexed { i, b -> if (needsAction(b, results, sent, now)) todoAt.add(2 + shownSites.size + i) }
    val scope = rememberCoroutineScope()
    val complaintCopied = "Complaint copied. Paste it into the description box on the AG's form."
    val next = nextDeadline(brokers, results, sent, now)
    // Copies a ready-made complaint, then opens the Texas Attorney General's form to paste it into.
    val report: (Broker, String) -> Unit = { b, links ->
        val t = System.currentTimeMillis()
        copyText(ctx, "Complaint", b.complaintText(profile, firstSent(sent, b.id) ?: t, t, links))
        Toast.makeText(ctx, complaintCopied, Toast.LENGTH_LONG).show()
        onExternal(AG_COMPLAINT)
    }
    // Copies the appeal, opens it as an email when the company has an address, and starts the 60 days.
    val appeal: (Broker, String) -> Unit = { b, links ->
        val t = System.currentTimeMillis()
        val first = firstSent(sent, b.id) ?: t
        copyText(ctx, "Appeal", b.appealBody(profile, first, links))
        if (b.email.isNotEmpty()) {
            Toast.makeText(ctx, "Appeal copied too. If they refused by email, you can paste it into a reply instead.", Toast.LENGTH_LONG).show()
            onExternal(b.appealMailto(profile, first, links))
        } else {
            Toast.makeText(ctx, "Appeal copied. Paste it into a reply to their refusal, or into their appeal form.", Toast.LENGTH_LONG).show()
        }
        onAppeal(b)
    }
    // Copies a reply that refuses to send ID, and opens it as an email when the company has an address.
    val noId: (Broker, String) -> Unit = { b, links ->
        val first = firstSent(sent, b.id) ?: System.currentTimeMillis()
        copyText(ctx, "Reply", b.noIdBody(profile, first, links))
        if (b.email.isNotEmpty()) {
            Toast.makeText(ctx, "Reply copied too. If they asked by email, you can paste it into a reply instead.", Toast.LENGTH_LONG).show()
            onExternal(b.noIdMailto(profile, first, links))
        } else {
            Toast.makeText(ctx, "Reply copied. Paste it into a reply to their email, or into their form.", Toast.LENGTH_LONG).show()
        }
    }
    val appealReport: (Broker, String, Boolean) -> Unit = { b, links, denied ->
        val t = System.currentTimeMillis()
        copyText(
            ctx, "Complaint",
            b.appealComplaintText(profile, firstSent(sent, b.id) ?: t, sent[appealKey(b.id)] ?: t, t, denied, links)
        )
        Toast.makeText(ctx, complaintCopied, Toast.LENGTH_LONG).show()
        onExternal(AG_COMPLAINT)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = listState) {
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
            if (next != null) {
                Spacer(Modifier.height(8.dp))
                Text(nextDeadlineText(next), color = Ink, fontSize = 13.sp)
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
            Spacer(Modifier.height(10.dp))
            Text("Find sites this app does not know about:", color = Dim, fontSize = 12.sp)
            val phoneSearch = googlePhoneUrl(profile)
            if (phoneSearch.isNotEmpty()) {
                RowButtonPair(
                    "GOOGLE NAME", { onExternal(googleNameUrl(profile)) },
                    "GOOGLE PHONE", { onExternal(phoneSearch) }
                )
            } else {
                RowButton("GOOGLE MY NAME") { onExternal(googleNameUrl(profile)) }
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
            val r = results[b.id]
            val first = firstSent(sent, b.id)
            val goneAt = sent[goneKey(b.id)]
            BrokerRow(
                b, r, first, sent[b.id], sent[manualKey(b.id)] != null, sent[noneKey(b.id)],
                sent[appealKey(b.id)], goneAt, isBack(b, r, sent), backNeedsYou(b, r, sent),
                onOpen = { url -> onExternal(url) },
                onOptOut = { url -> onOptOut(b, url) },
                onSearchInBrowser = { onExternal(b.buildUrl(profile)) },
                onOptOutInBrowser = { onExternal(b.optOut) },
                onEmail = { url -> onExternal(b.mailto(profile, url.ifEmpty { clipLink(ctx, b) })) },
                onMarkSent = { onMarkSent(b) },
                onSetManual = { on -> onSetManual(b, on) },
                onSetNone = { on -> onSetNone(b, on) },
                onReport = { url -> report(b, url.ifEmpty { clipLink(ctx, b) }) },
                onFinal = { url ->
                    val t = System.currentTimeMillis()
                    onExternal(b.finalMailto(profile, first ?: t, t, url.ifEmpty { clipLink(ctx, b) }))
                },
                onNoId = { url -> noId(b, url.ifEmpty { clipLink(ctx, b) }) },
                onAppeal = { url -> appeal(b, url.ifEmpty { clipLink(ctx, b) }) },
                onUndoAppeal = { onUndoAppeal(b) },
                onAppealReport = { url, denied -> appealReport(b, url.ifEmpty { clipLink(ctx, b) }, denied) },
                onRepeat = { url ->
                    val t = System.currentTimeMillis()
                    onExternal(b.repeatMailto(profile, first ?: t, goneAt ?: t, r?.checkedAt ?: t, url))
                },
                onRelistReport = { url ->
                    val t = System.currentTimeMillis()
                    copyText(ctx, "Complaint", b.relistComplaintText(profile, first ?: t, goneAt ?: t, r?.checkedAt ?: t, url))
                    Toast.makeText(ctx, complaintCopied, Toast.LENGTH_LONG).show()
                    onExternal(AG_COMPLAINT)
                }
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
                b, firstSent(sent, b.id), sent[b.id], sent[noneKey(b.id)], sent[appealKey(b.id)],
                onOptOut = { onOptOut(b, "") },
                onBrowser = { onExternal(b.optOut) },
                onEmail = {
                    val link = clipLink(ctx, b)
                    if (link.isNotEmpty()) Toast.makeText(ctx, "Your copied link is in the email", Toast.LENGTH_SHORT).show()
                    onExternal(b.mailto(profile, link))
                },
                onFind = { onExternal(b.findUrl(profile)) },
                onReport = { report(b, clipLink(ctx, b)) },
                onFinal = {
                    val t = System.currentTimeMillis()
                    onExternal(b.finalMailto(profile, firstSent(sent, b.id) ?: t, t, clipLink(ctx, b)))
                },
                onMarkSent = { onMarkSent(b) },
                onSetNone = { on -> onSetNone(b, on) },
                onNoId = { noId(b, clipLink(ctx, b)) },
                onAppeal = { appeal(b, clipLink(ctx, b)) },
                onUndoAppeal = { onUndoAppeal(b) },
                onAppealReport = { denied -> appealReport(b, clipLink(ctx, b), denied) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    // Always on screen: hops to the next row that still needs you, and wraps round at the end.
    if (todoAt.isNotEmpty()) {
        Button(
            onClick = {
                val here = listState.firstVisibleItemIndex
                // At the bottom of the list every row below is already on screen, so go back round to the first.
                val next = if (listState.canScrollForward) todoAt.firstOrNull { it > here } else null
                val target = next ?: todoAt[0]
                scope.launch { listState.animateScrollToItem(target) }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(10.dp)
        ) {
            Text("NEXT TO-DO (" + todoAt.size + " LEFT)", fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
    }
}
