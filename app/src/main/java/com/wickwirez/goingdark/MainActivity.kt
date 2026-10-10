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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
        try {
            Alerts.schedule(this)
            Alerts.askOnce(this)
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Alerts.welcome(this)
        } catch (e: Exception) {
        }
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

fun manualKey(id: String): String = "manual:" + id

// Set when you checked a site and it has nothing on you.
fun noneKey(id: String): String = "none:" + id

// Texas law gives a business 45 days to answer a privacy request.
const val REPLY_DAYS = 45
const val DAY_MS = 24L * 60L * 60L * 1000L
const val AG_COMPLAINT = "https://consumerprotection.texasattorneygeneral.gov/"

// Days they have left to answer, counted in calendar days from the day you sent it. Below zero once they are late.
fun daysLeft(sentAt: Long, now: Long): Int = REPLY_DAYS - daysBetween(sentAt, now)

fun dueText(sentAt: Long, now: Long): String {
    val left = daysLeft(sentAt, now)
    return when {
        left > 1 -> "$left days left"
        left == 1 -> "1 day left"
        left == 0 -> "due today"
        left == -1 -> "1 day overdue"
        else -> "${-left} days overdue"
    }
}

fun dueSentence(sentAt: Long, now: Long): String {
    val left = daysLeft(sentAt, now)
    return when {
        left > 1 -> "$left days left for them to respond."
        left == 1 -> "1 day left for them to respond."
        left == 0 -> "Their 45 days are up today."
        left == -1 -> "Their 45 days ran out 1 day ago."
        else -> "Their 45 days ran out ${-left} days ago."
    }
}

// True while a row still needs something from you: not sent yet, back on a site that had dropped you,
// or sent and their time to answer ran out.
fun needsAction(b: Broker, results: Map<String, BrokerResult>, sent: Map<String, Long>, now: Long): Boolean {
    if (sent[noneKey(b.id)] != null) return false
    val scanRow = b.search.isNotEmpty() && !b.hand
    if (scanRow && sent[manualKey(b.id)] == null && results[b.id]?.status == Status.CLEAR) return false
    val sentAt = firstSent(sent, b.id) ?: return true
    if (b.tool) return false
    val appealAt = sent[appealKey(b.id)]
    if (appealAt != null) return appealDaysLeft(appealAt, now) < 0
    if (backNeedsYou(b, results[b.id], sent)) return true
    return daysLeft(sentAt, now) < 0
}

// A link you copied from this company's own site, so the email can include your listing.
fun clipLink(ctx: Context, b: Broker): String {
    val text = try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
    } catch (e: Exception) {
        ""
    }
    return if (b.isOwnLink(text)) text.trim() else ""
}

fun openUrl(ctx: Context, url: String) {
    try {
        val action = if (url.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW
        ctx.startActivity(Intent(action, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app found to open this", Toast.LENGTH_SHORT).show()
    }
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun dateText(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun dateTimeText(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun buildReport(brokers: List<Broker>, results: Map<String, BrokerResult>, sent: Map<String, Long>): String {
    val now = System.currentTimeMillis()
    val sb = StringBuilder("Going Dark report\n")
    sb.append("Time spent: ").append(clockText(timerMillis(sent, now))).append("\n")
    val last = results.values.maxOfOrNull { it.checkedAt }
    sb.append("Last scan: ").append(if (last == null) "never" else dateTimeText(last)).append("\n")
    sb.append("Still to do: ").append(brokers.count { needsAction(it, results, sent, now) }).append("\n")
    for (b in brokers) {
        val sentAt = sent[b.id]
        val none = sent[noneKey(b.id)] != null
        // The first request date, the latest one if you sent it again, then their 45 days or the appeal's 60.
        val sentOn = sentText(sent, b.id)
        val mark = if (sentAt != null) " [$sentOn]" else ""
        val markDue = if (sentAt != null) " [$sentOn, " + clockNote(sent, b.id, now) + "]" else ""
        sb.append(b.name).append(": ")
        if (b.search.isEmpty()) {
            sb.append(
                if (none) "not listed"
                else if (b.tool) (if (sentAt != null) "done " + dateText(sentAt) else "not done")
                else if (sentAt != null) sentOn + ", " + clockNote(sent, b.id, now)
                else "opt-out not sent"
            )
        } else if (b.hand) {
            if (none) sb.append("NOT LISTED (checked by you)").append(mark)
            else sb.append("CHECK BY HAND").append(markDue)
        } else if (sent[manualKey(b.id)] != null) {
            sb.append("LISTED (marked by you)").append(markDue)
        } else {
            val r = results[b.id]
            val back = isBack(b, r, sent)
            sb.append(if (back) "BACK AGAIN" else rowLabel(r, sentAt))
            if (r != null) {
                if (r.listings.isNotEmpty()) sb.append(" (").append(r.listings.size).append(if (r.listings.size == 1) " listing)" else " listings)")
                if (r.note.isNotEmpty()) sb.append(" - ").append(r.note)
                if (back) sb.append(" - not found in the scan of ").append(dateText(sent[goneKey(b.id)] ?: now))
            }
            sb.append(if (r != null && r.status == Status.CLEAR) mark else markDue)
        }
        sb.append("\n")
    }
    return sb.toString()
}
