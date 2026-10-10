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

// Two buttons side by side, so the row does not grow taller.
@Composable
fun RowButtonPair(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onLeft, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(left, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onRight, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(right, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BrokerRow(
    b: Broker,
    r: BrokerResult?,
    sentAt: Long?,
    manual: Boolean,
    noneAt: Long?,
    onOpen: (String) -> Unit,
    onOptOut: (String) -> Unit,
    onSearchInBrowser: () -> Unit,
    onOptOutInBrowser: () -> Unit,
    onEmail: (String) -> Unit,
    onMarkSent: () -> Unit,
    onSetManual: (Boolean) -> Unit,
    onSetNone: (Boolean) -> Unit,
    onReport: (String) -> Unit,
    onFinal: (String) -> Unit
) {
    val ctx = LocalContext.current
    val listed = manual || (r != null && r.status == Status.LISTED)
    val best = r?.listings?.firstOrNull { it.strong } ?: r?.listings?.firstOrNull()
    val link = best?.url ?: ""
    // Every listing that clearly matches you goes into an email request.
    val mine = r?.listings?.filter { it.strong }?.map { it.url }.orEmpty()
        .ifEmpty { if (link.isEmpty()) emptyList() else listOf(link) }
    val now = System.currentTimeMillis()
    val late = sentAt != null && daysLeft(sentAt, now) < 0
    val label = when {
        b.hand -> if (noneAt != null) "NOT LISTED" else if (sentAt != null) dueText(sentAt, now).uppercase() else "CHECK BY HAND"
        manual -> "LISTED"
        else -> rowLabel(r, sentAt)
    }
    val labelColor = when {
        b.hand -> if (noneAt != null || (sentAt != null && !late)) Green else Amber
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
            if (noneAt != null) {
                Text("Marked not listed " + dateText(noneAt), color = Green, fontSize = 12.sp)
            } else if (sentAt != null) {
                Text(
                    "Opt-out sent " + dateText(sentAt) + ". " + dueSentence(sentAt, now),
                    color = if (late) Amber else Green, fontSize = 12.sp
                )
                if (late) {
                    RowButton("REPORT TO TEXAS AG") { onReport("") }
                    if (b.email.isNotEmpty()) RowButton("SEND FINAL NOTICE") { onFinal("") }
                }
            }
            RowButton("CHECK IN BROWSER") { onSearchInBrowser() }
            if (noneAt != null) {
                RowButton("UNDO NOT LISTED") { onSetNone(false) }
            } else {
                if (b.optOut.isNotEmpty()) RowButton("OPT OUT IN BROWSER") { onOptOutInBrowser() }
                if (sentAt == null) {
                    RowButtonPair("MARK AS SENT", onMarkSent, "NOT LISTED", { onSetNone(true) })
                } else {
                    RowButton("NOT LISTED") { onSetNone(true) }
                }
            }
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
                    "Opt-out sent " + dateText(sentAt) + ". " + dueSentence(sentAt, now),
                    color = if (late) Amber else Green, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
                if (late) {
                    RowButton("REPORT TO TEXAS AG") { onReport(mine.joinToString("\n")) }
                    if (b.email.isNotEmpty()) RowButton("SEND FINAL NOTICE") { onFinal(mine.joinToString("\n")) }
                }
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
                if (b.email.isNotEmpty()) RowButton("SEND EMAIL REQUEST") { onEmail(mine.joinToString("\n")) }
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
    noneAt: Long?,
    onOptOut: () -> Unit,
    onBrowser: () -> Unit,
    onEmail: () -> Unit,
    onFind: () -> Unit,
    onReport: () -> Unit,
    onFinal: () -> Unit,
    onMarkSent: () -> Unit,
    onSetNone: (Boolean) -> Unit
) {
    val now = System.currentTimeMillis()
    val late = !b.tool && sentAt != null && daysLeft(sentAt, now) < 0
    val markText = if (b.tool) "MARK AS DONE" else "MARK AS SENT"
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(Panel).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (noneAt != null) "NOT LISTED"
                else if (sentAt != null) (if (b.tool) "DONE" else dueText(sentAt, now).uppercase())
                else if (b.tool) "NOT DONE" else "NOT SENT",
                color = if (noneAt != null) Green else if (sentAt != null) (if (late) Amber else Green) else Dim,
                fontSize = 12.sp, fontFamily = Mono
            )
        }
        if (b.tip.isNotEmpty()) Text(b.tip, color = Dim, fontSize = 12.sp)
        if (noneAt != null) {
            Text(
                "Marked not listed " + dateText(noneAt), color = Green, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (b.find.isNotEmpty()) RowButton("FIND ME ON THIS SITE") { onFind() }
            RowButton("UNDO NOT LISTED") { onSetNone(false) }
        } else if (sentAt != null) {
            Text(
                if (b.tool) "Done " + dateText(sentAt) + "."
                else "Opt-out sent " + dateText(sentAt) + ". " + dueSentence(sentAt, now),
                color = if (late) Amber else Green, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (late) {
                RowButton("REPORT TO TEXAS AG") { onReport() }
                if (b.email.isNotEmpty()) RowButton("SEND FINAL NOTICE") { onFinal() }
            }
            if (b.find.isNotEmpty()) RowButton("FIND ME ON THIS SITE") { onFind() }
            if (b.optOut.isNotEmpty()) {
                RowButtonPair(
                    if (b.tool) "OPEN AGAIN" else "OPT-OUT AGAIN", { if (b.browser) onBrowser() else onOptOut() },
                    "NOT LISTED", { onSetNone(true) }
                )
            } else if (b.email.isNotEmpty()) {
                RowButtonPair("EMAIL AGAIN", onEmail, "NOT LISTED", { onSetNone(true) })
            } else {
                RowButton("NOT LISTED") { onSetNone(true) }
            }
        } else {
            if (b.find.isNotEmpty()) RowButton("FIND ME ON THIS SITE", primary = true) { onFind() }
            if (b.optOut.isNotEmpty()) {
                if (!b.browser) RowButton("START OPT-OUT", primary = b.find.isEmpty()) { onOptOut() }
                RowButton(
                    if (b.browser) "OPEN OPT-OUT IN BROWSER" else "OPT OUT IN BROWSER",
                    primary = b.browser && b.find.isEmpty()
                ) { onBrowser() }
            }
            if (b.email.isNotEmpty()) {
                RowButton("SEND EMAIL REQUEST", primary = b.optOut.isEmpty()) { onEmail() }
            }
            RowButtonPair(markText, onMarkSent, "NOT LISTED", { onSetNone(true) })
        }
    }
}
