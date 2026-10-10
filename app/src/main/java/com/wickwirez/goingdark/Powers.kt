package com.wickwirez.goingdark

import java.util.Calendar
import java.util.TimeZone

// The day you first sent a request. Sending it again does not move this, so their 45 days keep running.
fun firstKey(id: String): String = "first:" + id

// The day you appealed a refusal.
fun appealKey(id: String): String = "appeal:" + id

// The first scan in the current run of scans that did not find you, on a site you had opted out of.
fun clearKey(id: String): String = "clear:" + id

// The latest scan that did not find you, kept once two separate scans in a row had not found you.
fun goneKey(id: String): String = "gone:" + id

// Texas law gives a business 60 days to answer an appeal in writing.
const val APPEAL_DAYS = 60

// Whole calendar days from one moment to another, in the phone's time zone.
fun daysBetween(from: Long, to: Long): Int {
    val tz = TimeZone.getDefault()
    val a = Math.floorDiv(from + tz.getOffset(from), DAY_MS)
    val b = Math.floorDiv(to + tz.getOffset(to), DAY_MS)
    return (b - a).toInt()
}

fun addDays(ms: Long, days: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

fun firstSent(sent: Map<String, Long>, id: String): Long? = sent[firstKey(id)] ?: sent[id]

// Records a request as sent now and keeps the date of the first one.
fun markSent(sent: MutableMap<String, Long>, id: String, now: Long) {
    if (sent[firstKey(id)] == null) sent[firstKey(id)] = sent[id] ?: now
    sent[id] = now
}

// Two scans count as separate readings when they are on different days and at least 12 hours apart, so one late
// sitting that runs past midnight is a single reading.
const val APART_MS = 12L * 60L * 60L * 1000L

fun apart(from: Long, to: Long): Boolean = to - from >= APART_MS && daysBetween(from, to) >= 1

// Keeps track of whether a site you opted out of has dropped you. One scan that does not find you can be a
// misread, so a site counts as having dropped you only once two separate scans in a row did not find you. A scan
// that finds you soon after one that did not means the earlier reading was wrong, so the record is thrown away.
// True when it changed something.
fun applyScan(sent: MutableMap<String, Long>, id: String, status: Status, now: Long): Boolean {
    // Nothing to track until you have opted out, or on a site you marked by hand because the scan cannot read it.
    if (sent[id] == null || sent[manualKey(id)] != null) return false
    if (status == Status.CLEAR) {
        val start = sent[clearKey(id)]
        if (start == null) {
            sent[clearKey(id)] = now
            return true
        }
        if (apart(start, now)) {
            sent[goneKey(id)] = now
            return true
        }
        return false
    }
    if (status == Status.LISTED) {
        var changed = sent.remove(clearKey(id)) != null
        val gone = sent[goneKey(id)]
        if (gone != null && !apart(gone, now)) {
            sent.remove(goneKey(id))
            changed = true
        }
        return changed
    }
    return false
}

// You marked a site as listing you, so the scans that did not find you there were wrong. Forget them.
fun forgetDrop(sent: MutableMap<String, Long>, id: String) {
    sent.remove(clearKey(id))
    sent.remove(goneKey(id))
}

// A scanned site that dropped you after your opt-out and, in a later scan, lists you again.
fun isBack(b: Broker, r: BrokerResult?, sent: Map<String, Long>): Boolean {
    if (b.search.isEmpty() || b.hand || r == null) return false
    if (sent[manualKey(b.id)] != null || sent[noneKey(b.id)] != null) return false
    val gone = sent[goneKey(b.id)] ?: return false
    return r.status == Status.LISTED && apart(gone, r.checkedAt)
}

// Back on a site, and you have not sent anything since it dropped you. Sending once settles it: later scans
// that still find you do not ask again, unless the site drops you and lists you a second time.
fun backNeedsYou(b: Broker, r: BrokerResult?, sent: Map<String, Long>): Boolean {
    val gone = sent[goneKey(b.id)] ?: return false
    return isBack(b, r, sent) && (sent[b.id] ?: 0L) < gone
}

fun appealDaysLeft(appealAt: Long, now: Long): Int = APPEAL_DAYS - daysBetween(appealAt, now)

fun appealDueText(appealAt: Long, now: Long): String {
    val left = appealDaysLeft(appealAt, now)
    return when {
        left > 1 -> "$left days left on appeal"
        left == 1 -> "1 day left on appeal"
        left == 0 -> "appeal answer due today"
        left == -1 -> "appeal 1 day overdue"
        else -> "appeal ${-left} days overdue"
    }
}

fun appealSentence(appealAt: Long, now: Long): String {
    val left = appealDaysLeft(appealAt, now)
    return when {
        left > 1 -> "$left days left for them to answer in writing."
        left == 1 -> "1 day left for them to answer in writing."
        left == 0 -> "Their 60 days are up today."
        left == -1 -> "Their 60 days ran out 1 day ago."
        else -> "Their 60 days ran out ${-left} days ago."
    }
}

// "opt-out sent Oct 4, 2026, again Oct 9, 2026"
fun sentText(sent: Map<String, Long>, id: String): String {
    val first = firstSent(sent, id) ?: return ""
    val last = sent[id] ?: first
    val again = if (daysBetween(first, last) > 0) ", again " + dateText(last) else ""
    return "opt-out sent " + dateText(first) + again
}

// What the clock says for a sent row: their 45 days, or the 60 days of an appeal.
fun clockNote(sent: Map<String, Long>, id: String, now: Long): String {
    val appealAt = sent[appealKey(id)]
    if (appealAt != null) return "appeal sent " + dateText(appealAt) + ", " + appealDueText(appealAt, now)
    val first = firstSent(sent, id) ?: return ""
    return dueText(first, now)
}

// "Opt-out sent Oct 4, 2026, again Oct 9, 2026. 40 days left for them to respond."
fun sentLine(firstAt: Long, lastAt: Long?, now: Long): String {
    val again = if (lastAt != null && daysBetween(firstAt, lastAt) > 0) ", again " + dateText(lastAt) else ""
    return "Opt-out sent " + dateText(firstAt) + again + ". " + dueSentence(firstAt, now)
}
