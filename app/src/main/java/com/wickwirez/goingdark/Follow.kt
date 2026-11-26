package com.wickwirez.goingdark

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Set when you tap SEND FINAL NOTICE.
fun finalKey(id: String): String = "final:" + id

const val FINAL_WAIT_DAYS = 14
const val RESEND_WAIT_DAYS = 7

// What you last did after their 45 days ran out: a final notice (wait 14 days) or sending the request again (wait 7).
// Returns the day you did it and how many days to wait, or null if you have done neither.
fun followUp(b: Broker, sent: Map<String, Long>): Pair<Long, Int>? {
    val first = firstSent(sent, b.id) ?: return null
    val fin = sent[finalKey(b.id)]
    val again = sent[b.id]
    val a = if (fin != null && daysBetween(first, fin) >= REPLY_DAYS) Pair(fin, FINAL_WAIT_DAYS) else null
    val c = if (again != null && daysBetween(first, again) >= REPLY_DAYS) Pair(again, RESEND_WAIT_DAYS) else null
    if (a == null) return c
    if (c == null) return a
    val endA = addDays(a.first, a.second)
    val endC = addDays(c.first, c.second)
    return if (endA >= endC) a else c
}

// The day their extra waiting time ends, or null when you are not waiting on a follow-up.
fun waitingUntil(b: Broker, sent: Map<String, Long>, now: Long): Long? {
    if (b.tool || sent[appealKey(b.id)] != null) return null
    val f = followUp(b, sent) ?: return null
    val end = addDays(f.first, f.second)
    return if (now < end) end else null
}

// One line above a row you already followed up on: what you did and when to act again.
@Composable
fun FollowUpNote(b: Broker, sent: Map<String, Long>, now: Long) {
    val end = waitingUntil(b, sent, now) ?: return
    val f = followUp(b, sent) ?: return
    val what = if (f.second == FINAL_WAIT_DAYS) "Final notice sent " else "Request sent again "
    Text(
        what + dateText(f.first) + ". Waiting until " + dateText(end) + ". If they stay silent, tap REPORT TO TEXAS AG.",
        color = Cyan, fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
    )
}
