package com.wickwirez.goingdark

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

// Deadline alerts. Once a day the phone checks your requests and tells you when a company's time to answer has run
// out, so you can send the final notice or report it. It also reminds you to scan once the last scan is a week old.

const val ALERT_ACTION = "com.wickwirez.goingdark.CHECK_DEADLINES"
const val ALERT_CHANNEL = "deadlines"
const val SCAN_NUDGE_DAYS = 7

// True once a sent request is past its 45 days, or past the 60 days of its appeal.
fun timeUp(b: Broker, sent: Map<String, Long>, at: Long): Boolean {
    if (b.tool) return false
    val first = firstSent(sent, b.id) ?: return false
    val appealAt = sent[appealKey(b.id)]
    return if (appealAt != null) appealDaysLeft(appealAt, at) < 0 else daysLeft(first, at) < 0
}

// Rows whose time ran out since the last check and that still need you.
fun newlyLate(
    brokers: List<Broker>,
    results: Map<String, BrokerResult>,
    sent: Map<String, Long>,
    since: Long,
    now: Long
): List<Broker> = brokers.filter { timeUp(it, sent, now) && !timeUp(it, sent, since) && needsAction(it, results, sent, now) }

// The last day a company has to answer: 45 days from your first request, or 60 days from your appeal.
fun deadlineAt(b: Broker, sent: Map<String, Long>): Long? {
    if (b.tool) return null
    val first = firstSent(sent, b.id) ?: return null
    val appealAt = sent[appealKey(b.id)]
    return if (appealAt != null) addDays(appealAt, APPEAL_DAYS) else dueAt(first)
}

// The next day on which companies run out of time, and which ones. Rows already late, and rows that will not need
// you when their time is up (a site that removed you, one marked not listed), are left out.
fun nextDeadline(
    brokers: List<Broker>,
    results: Map<String, BrokerResult>,
    sent: Map<String, Long>,
    now: Long
): Pair<Long, List<Broker>>? {
    val ahead = brokers.mapNotNull { b ->
        val due = deadlineAt(b, sent)
        if (due == null || timeUp(b, sent, now) || !needsAction(b, results, sent, addDays(due, 1))) null else b to due
    }
    val soonest = ahead.minByOrNull { it.second } ?: return null
    return soonest.second to ahead.filter { daysBetween(soonest.second, it.second) == 0 }.map { it.first }
}

// "Spokeo", "Spokeo and Nuwber", "Spokeo, Nuwber and MyLife", "Spokeo, Nuwber and 5 more"
fun namesText(list: List<Broker>): String {
    val names = list.map { it.company() }
    return when (names.size) {
        0 -> ""
        1 -> names[0]
        2 -> names[0] + " and " + names[1]
        3 -> names[0] + ", " + names[1] + " and " + names[2]
        else -> names[0] + ", " + names[1] + " and " + (names.size - 2) + " more"
    }
}

fun nextDeadlineText(next: Pair<Long, List<Broker>>): String =
    "Next deadline: " + dateText(next.first) + ", for " + namesText(next.second) + "."

fun lateTitle(late: List<Broker>): String =
    if (late.size == 1) "Time is up: " + late[0].company() else "Time is up for " + late.size + " companies"

fun lateText(late: List<Broker>): String =
    "Out of time to answer you: " + namesText(late) + ". Open Going Dark to send a final notice or report " +
        (if (late.size == 1) "it" else "them") + " to the Texas Attorney General."

// True when the last scan and the last reminder are both a week old, and the scan checks a site you opted out of.
fun scanDue(brokers: List<Broker>, sent: Map<String, Long>, lastScan: Long?, nudgedAt: Long, now: Long): Boolean {
    if (lastScan == null || daysBetween(lastScan, now) < SCAN_NUDGE_DAYS) return false
    if (nudgedAt > 0L && daysBetween(nudgedAt, now) < SCAN_NUDGE_DAYS) return false
    return brokers.any { it.search.isNotEmpty() && !it.hand && firstSent(sent, it.id) != null && sent[noneKey(it.id)] == null }
}

fun scanText(lastScan: Long, now: Long): String =
    "Your last scan was " + daysBetween(lastScan, now) + " days ago. A scan shows which sites removed you and " +
        "catches any that list you again."

fun welcomeText(next: Pair<Long, List<Broker>>?): String =
    "You will get a note here when a company runs out of time to answer you." +
        (if (next != null) " " + nextDeadlineText(next) else "")

// 9 in the morning: today if that is still ahead, otherwise tomorrow.
fun nextAlarmAt(now: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = now
    c.set(Calendar.HOUR_OF_DAY, 9)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

object Alerts {
    private fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    // Sets the daily check. Safe to call again: it replaces the one already set.
    fun schedule(ctx: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        if (p.getLong("checkedAt", 0L) == 0L) p.edit().putLong("checkedAt", now).apply()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextAlarmAt(now), AlarmManager.INTERVAL_DAY, checkIntent(ctx))
    }

    private fun checkIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlertReceiver::class.java).setAction(ALERT_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Android 13 and later need your OK before an app can show alerts. This asks once.
    fun askOnce(a: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val p = prefs(a)
        if (p.getBoolean("asked", false)) return
        p.edit().putBoolean("asked", true).apply()
        a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
    }

    // The daily check.
    fun check(ctx: Context) {
        val now = System.currentTimeMillis()
        val p = prefs(ctx)
        val sent = Store.loadSent(ctx)
        // Nothing sent yet, or the file could not be read just now: the next check starts from the same point.
        if (sent.isEmpty()) return
        val since = p.getLong("checkedAt", 0L).let { if (it == 0L) now else it }
        val brokers = loadBrokers(ctx)
        val results = Store.loadResults(ctx)
        val late = newlyLate(brokers, results, sent, since, now)
        if (late.isNotEmpty()) post(ctx, 1, lateTitle(late), lateText(late))
        val last = results.values.maxOfOrNull { it.checkedAt }
        if (last != null && scanDue(brokers, sent, last, p.getLong("nudgedAt", 0L), now)) {
            post(ctx, 2, "Time for a scan", scanText(last, now))
            p.edit().putLong("nudgedAt", now).apply()
        }
        p.edit().putLong("checkedAt", now).apply()
    }

    // Once alerts are allowed, one note says so and gives the next deadline, so you can see that they work.
    fun welcome(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean("welcomed", false) || !allowed(ctx)) return
        p.edit().putBoolean("welcomed", true).apply()
        val next = nextDeadline(loadBrokers(ctx), Store.loadResults(ctx), Store.loadSent(ctx), System.currentTimeMillis())
        post(ctx, 3, "Deadline alerts are on", welcomeText(next))
    }

    private fun allowed(ctx: Context): Boolean =
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Deadlines and scans", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}

// Runs the daily check, and sets it again after the phone restarts or the app is updated.
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action == ALERT_ACTION) Alerts.check(context) else Alerts.schedule(context)
        } catch (e: Exception) {
        }
    }
}
