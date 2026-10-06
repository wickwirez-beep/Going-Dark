package com.wickwirez.goingdark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

const val TIMER_TOTAL = "timer:total"
const val TIMER_SINCE = "timer:since"
const val TIMER_TOUCH = "timer:touch"
const val HALF_HOUR = 30L * 60L * 1000L

// A running timer stops counting 30 minutes after the last opt-out activity.
fun timerMillis(sent: Map<String, Long>, now: Long): Long {
    val total = sent[TIMER_TOTAL] ?: 0L
    val since = sent[TIMER_SINCE] ?: 0L
    if (since <= 0L) return total.coerceAtLeast(0L)
    val touch = sent[TIMER_TOUCH] ?: since
    val end = minOf(now, touch + HALF_HOUR)
    return (total + (end - since).coerceAtLeast(0L)).coerceAtLeast(0L)
}

fun timerExpired(sent: Map<String, Long>, now: Long): Boolean {
    val since = sent[TIMER_SINCE] ?: 0L
    if (since <= 0L) return false
    val touch = sent[TIMER_TOUCH] ?: since
    return now > touch + HALF_HOUR
}

fun timerPause(sent: MutableMap<String, Long>, now: Long) {
    if ((sent[TIMER_SINCE] ?: 0L) <= 0L) return
    sent[TIMER_TOTAL] = timerMillis(sent, now)
    sent.remove(TIMER_SINCE)
}

// Call on any opt-out activity: starts the timer if needed and resets the 30 minute window.
fun timerTouch(sent: MutableMap<String, Long>, now: Long) {
    if (timerExpired(sent, now)) timerPause(sent, now)
    if ((sent[TIMER_SINCE] ?: 0L) <= 0L) sent[TIMER_SINCE] = now
    sent[TIMER_TOUCH] = now
}

fun timerAdjust(sent: MutableMap<String, Long>, delta: Long, now: Long) {
    val shown = timerMillis(sent, now)
    val target = (shown + delta).coerceAtLeast(0L)
    sent[TIMER_TOTAL] = (sent[TIMER_TOTAL] ?: 0L) + (target - shown)
}

fun clockText(ms: Long): String {
    val s = ms / 1000L
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600L, (s % 3600L) / 60L, s % 60L)
}

@Composable
fun TimerPanel(
    sent: Map<String, Long>,
    onToggle: () -> Unit,
    onAdjust: (Long) -> Unit,
    onExpire: () -> Unit
) {
    val running = (sent[TIMER_SINCE] ?: 0L) > 0L
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        now = System.currentTimeMillis()
        while (running) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val expired = running && timerExpired(sent, now)
    LaunchedEffect(expired) {
        if (expired) onExpire()
    }
    val ms = timerMillis(sent, now)
    val done = sent.keys.count { !it.contains(":") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Panel).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TIME SPENT OPTING OUT", color = Dim, fontSize = 11.sp, fontFamily = Mono,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (running) "RUNNING" else "PAUSED",
                color = if (running) Green else Dim, fontSize = 11.sp, fontFamily = Mono
            )
        }
        Text(clockText(ms), color = Cyan, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        if (done > 0 && ms >= 60000L) {
            val per = ms / done
            val perMin = (per / 60000L).coerceAtLeast(1L)
            val hours = (per * 400L) / 3600000L
            Text(
                "" + done + " opt-outs sent, about " + perMin + " min each. At this pace, 400 brokers would take about " + hours + " hours.",
                color = Dim, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onToggle, modifier = Modifier.weight(1f)) {
                Text(if (running) "PAUSE" else "START", fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { onAdjust(HALF_HOUR) }, modifier = Modifier.weight(1f)) {
                Text("+30 MIN", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onAdjust(-HALF_HOUR) }, modifier = Modifier.weight(1f)) {
                Text("-30 MIN", fontSize = 12.sp)
            }
        }
        Text(
            "Starts itself when you open an opt-out. Pauses itself after 30 minutes without one.",
            color = Dim, fontSize = 11.sp
        )
    }
}
