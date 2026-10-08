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

fun buildReport(brokers: List<Broker>, results: Map<String, BrokerResult>, sent: Map<String, Long>): String {
    val sb = StringBuilder("Going Dark report\n")
    sb.append("Time spent: ").append(clockText(timerMillis(sent, System.currentTimeMillis()))).append("\n")
    for (b in brokers) {
        val isSent = sent[b.id] != null
        val mark = if (isSent) " [opt-out sent]" else ""
        sb.append(b.name).append(": ")
        if (b.search.isEmpty()) {
            sb.append(if (isSent) "opt-out sent" else "opt-out not sent")
        } else if (b.hand) {
            sb.append("CHECK BY HAND").append(mark)
        } else if (sent[manualKey(b.id)] != null) {
            sb.append("LISTED (marked by you)").append(mark)
        } else {
            val r = results[b.id]
            sb.append(rowLabel(r, sent[b.id]))
            if (r != null) {
                if (r.listings.isNotEmpty()) sb.append(" (").append(r.listings.size).append(if (r.listings.size == 1) " listing)" else " listings)")
                if (r.note.isNotEmpty()) sb.append(" - ").append(r.note)
            }
            sb.append(mark)
        }
        sb.append("\n")
    }
    return sb.toString()
}
