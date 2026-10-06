package com.wickwirez.goingdark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun RowButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val m = Modifier.fillMaxWidth().padding(top = 4.dp)
    if (primary) {
        Button(onClick = onClick, modifier = m, shape = RoundedCornerShape(8.dp)) {
            Text(text, fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = m, shape = RoundedCornerShape(8.dp)) {
            Text(text, fontFamily = Mono)
        }
    }
}

@Composable
fun BrokerRow(
    b: Broker,
    r: BrokerResult?,
    sentAt: Long?,
    manual: Boolean,
    onOpen: (String) -> Unit,
    onOptOut: (String) -> Unit,
    onSearchInBrowser: () -> Unit,
    onOptOutInBrowser: () -> Unit,
    onEmail: (String) -> Unit,
    onMarkSent: () -> Unit,
    onSetManual: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val listed = manual || (r != null && r.status == Status.LISTED)
    val best = r?.listings?.firstOrNull { it.strong } ?: r?.listings?.firstOrNull()
    val link = best?.url ?: ""
    val label = when {
        b.hand -> if (sentAt != null) "SENT" else "CHECK BY HAND"
        manual -> "LISTED"
        else -> rowLabel(r, sentAt)
    }
    val labelColor = when {
        b.hand -> if (sentAt != null) Green else Amber
        manual -> Pink
        else -> statusColor(r?.status)
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(Panel).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(label, color = labelColor, fontSize = 12.sp, fontFamily = Mono)
        }
        if (b.hand) {
            Text(
                "This site blocks the app's browser, so scans skip it. Check it in your own browser.",
                color = Dim, fontSize = 12.sp
            )
            if (sentAt != null) {
                Text("Opt-out sent " + dateText(sentAt), color = Green, fontSize = 12.sp)
            }
            RowButton("CHECK IN BROWSER") { onSearchInBrowser() }
            if (b.optOut.isNotEmpty()) RowButton("OPT OUT IN BROWSER") { onOptOutInBrowser() }
            if (sentAt == null) RowButton("MARK AS SENT") { onMarkSent() }
        } else {
            if (manual) {
                Text(
                    "Marked as listed by you. The scan cannot see this record, so check the site by hand.",
                    color = Amber, fontSize = 12.sp
                )
            } else if (r != null && r.note.isNotEmpty()) {
                Text(r.note, color = Dim, fontSize = 12.sp)
            }
            if (r != null) {
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
            if (sentAt != null && listed) {
                Text(
                    "Opt-out sent " + dateText(sentAt) + ". Check again in a few days to confirm.",
                    color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (listed) {
                if (b.optOut.isNotEmpty()) {
                    RowButton(if (sentAt != null) "OPEN OPT-OUT AGAIN" else "START OPT-OUT", primary = true) {
                        onOptOut(link)
                    }
                    RowButton("OPT OUT IN BROWSER") {
                        if (link.isNotEmpty()) copyText(ctx, "Listing link", link)
                        onOptOutInBrowser()
                    }
                }
                if (b.email.isNotEmpty()) RowButton("SEND EMAIL REQUEST") { onEmail(link) }
                if (sentAt == null) RowButton("MARK AS SENT") { onMarkSent() }
            }
            if (manual) RowButton("NO LONGER LISTED") { onSetManual(false) }
            if (!manual && r != null && r.status == Status.CLEAR) {
                Text(
                    "Open the search page to double-check",
                    color = Cyan, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable { onSearchInBrowser() }
                )
                RowButton("I'M LISTED HERE") { onSetManual(true) }
            }
            if (!manual && r != null && (r.status == Status.BLOCKED || r.status == Status.UNKNOWN)) {
                RowButton("CHECK IN BROWSER") { onSearchInBrowser() }
                if (b.optOut.isNotEmpty()) RowButton("OPT OUT IN BROWSER") { onOptOutInBrowser() }
                RowButton("I'M LISTED HERE") { onSetManual(true) }
            }
        }
    }
}

@Composable
fun FormRow(
    b: Broker,
    sentAt: Long?,
    onOptOut: () -> Unit,
    onBrowser: () -> Unit,
    onEmail: () -> Unit,
    onMarkSent: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(Panel).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (sentAt != null) "SENT" else "NOT SENT",
                color = if (sentAt != null) Green else Dim, fontSize = 12.sp, fontFamily = Mono
            )
        }
        if (b.tip.isNotEmpty()) Text(b.tip, color = Dim, fontSize = 12.sp)
        if (sentAt != null) {
            Text(
                "Opt-out sent " + dateText(sentAt), color = Green, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (b.optOut.isNotEmpty()) {
                RowButton("OPEN OPT-OUT AGAIN") { if (b.browser) onBrowser() else onOptOut() }
            }
        } else {
            if (b.optOut.isNotEmpty()) {
                if (!b.browser) RowButton("START OPT-OUT", primary = true) { onOptOut() }
                RowButton(
                    if (b.browser) "OPEN OPT-OUT IN BROWSER" else "OPT OUT IN BROWSER",
                    primary = b.browser
                ) { onBrowser() }
            }
            if (b.email.isNotEmpty()) {
                RowButton("SEND EMAIL REQUEST", primary = b.optOut.isEmpty()) { onEmail() }
            }
            RowButton("MARK AS SENT") { onMarkSent() }
        }
    }
}
