package com.wickwirez.goingdark

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

val STATES = mapOf(
    "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California",
    "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware", "DC" to "District of Columbia", "FL" to "Florida",
    "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana",
    "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine",
    "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi",
    "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada", "NH" to "New Hampshire",
    "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota",
    "OH" to "Ohio", "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island",
    "SC" to "South Carolina", "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah",
    "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington", "WV" to "West Virginia", "WI" to "Wisconsin",
    "WY" to "Wyoming"
)

data class Profile(
    val first: String = "",
    val last: String = "",
    val otherNames: String = "",
    val birthYear: String = "",
    val city: String = "",
    val state: String = "",
    val pastPlaces: String = "",
    val email: String = "",
    val middle: String = "",
    val street: String = "",
    val zip: String = "",
    val phone: String = ""
) {
    val ready: Boolean
        get() = first.isNotBlank() && last.isNotBlank() && city.isNotBlank() &&
            STATES.containsKey(state.trim().uppercase())

    fun toJson(): String = JSONObject()
        .put("first", first).put("last", last).put("otherNames", otherNames)
        .put("birthYear", birthYear).put("city", city).put("state", state)
        .put("pastPlaces", pastPlaces).put("email", email)
        .put("middle", middle).put("street", street).put("zip", zip).put("phone", phone)
        .toString()

    fun toProbeJson(): String {
        val locs = JSONArray()
        fun add(c: String, s: String) {
            val st = s.trim().uppercase()
            val full = STATES[st] ?: return
            if (c.isNotBlank()) {
                locs.put(JSONObject().put("city", c.trim()).put("st", st).put("state", full))
            }
        }
        add(city, state)
        pastPlaces.split(";").forEach { part ->
            val bits = part.split(",")
            if (bits.size == 2) add(bits[0], bits[1])
        }
        val others = JSONArray()
        otherNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { others.put(it) }
        val by = birthYear.trim().toIntOrNull() ?: 0
        val age = if (by in 1900..2100) Calendar.getInstance().get(Calendar.YEAR) - by else 0
        return JSONObject()
            .put("first", first.trim()).put("last", last.trim()).put("otherNames", others)
            .put("age", age).put("birthYear", by).put("locations", locs)
            .toString()
    }

    fun toFillJson(listingUrl: String): String {
        val st = state.trim().uppercase()
        return JSONObject()
            .put("email", email.trim()).put("url", listingUrl)
            .put("first", first.trim()).put("middle", middle.trim()).put("last", last.trim())
            .put("full", first.trim() + " " + last.trim())
            .put("street", street.trim()).put("city", city.trim())
            .put("st", st).put("stateName", STATES[st] ?: "")
            .put("zip", zip.trim()).put("phone", phone.trim())
            .toString()
    }

    companion object {
        fun fromJson(s: String): Profile {
            val o = JSONObject(s)
            return Profile(
                o.optString("first"), o.optString("last"), o.optString("otherNames"),
                o.optString("birthYear"), o.optString("city"), o.optString("state"),
                o.optString("pastPlaces"), o.optString("email"),
                o.optString("middle"), o.optString("street"), o.optString("zip"), o.optString("phone")
            )
        }
    }
}

enum class Status { LISTED, CLEAR, BLOCKED, UNKNOWN }

data class Listing(val url: String, val strong: Boolean, val text: String)

data class BrokerResult(
    val brokerId: String,
    val status: Status,
    val checkedAt: Long,
    val listings: List<Listing>,
    val note: String
)

data class Broker(
    val id: String,
    val name: String,
    val search: String,
    val optOut: String = "",
    val ua: String = "",
    val tip: String = "",
    val email: String = "",
    val hand: Boolean = false,
    val browser: Boolean = false,
    val find: String = "",
    // Phone lookup sites need your number to find you, so their emails include it.
    val sendPhone: Boolean = false,
    // Not a company that owes you an answer (Do Not Call, Google tools), so no 45-day countdown.
    val tool: Boolean = false
) {
    fun buildUrl(p: Profile): String = fill(search, p)

    // The site's own page for your name, when it has one.
    fun findUrl(p: Profile): String = fill(find, p)

    private fun fill(template: String, p: Profile): String {
        val st = p.state.trim().uppercase()
        val full = STATES[st] ?: st
        fun words(s: String) = s.replace(Regex("[^\\p{L}0-9 \\-]"), "").trim()
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun lower(s: String) = words(s).joinToString("-") { it.lowercase() }
        fun title(s: String) = words(s).joinToString("-") { w ->
            w.lowercase().replaceFirstChar { c -> c.uppercase() }
        }
        fun query(s: String) = URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")
        return template
            .replace("{first}", lower(p.first)).replace("{First}", title(p.first))
            .replace("{First_q}", query(p.first))
            .replace("{last}", lower(p.last)).replace("{Last}", title(p.last))
            .replace("{Last_q}", query(p.last)).replace("{l}", lower(p.last).take(1))
            .replace("{city}", lower(p.city)).replace("{City}", title(p.city))
            .replace("{City_q}", query(p.city))
            .replace("{st}", st.lowercase()).replace("{ST}", st)
            .replace("{state}", lower(full)).replace("{State}", title(full))
    }

    // listingUrl may hold several links, one per line.
    fun mailto(p: Profile, listingUrl: String): String = "mailto:" + email +
        "?subject=" + Uri.encode(mailSubject(p)) + "&body=" + Uri.encode(mailBody(p, listingUrl))

    fun mailSubject(p: Profile): String {
        val who = p.first.trim() + " " + p.last.trim()
        return if (p.state.trim().uppercase() == "TX") "Data deletion request (Texas resident) - " + who
        else "Opt-out request - " + who
    }

    fun mailBody(p: Profile, listingUrl: String): String {
        val who = p.first.trim() + " " + p.last.trim()
        val company = company()
        val texas = p.state.trim().uppercase() == "TX"
        val sb = StringBuilder()
        sb.append("Hello,\n\n")
        if (texas) {
            sb.append("I am a Texas resident. Under the Texas Data Privacy and Security Act ")
            sb.append("(Texas Business and Commerce Code, Section 541.051), I ask you to:\n\n")
            sb.append("1. Delete all personal data you hold about me, including data you obtained from other sources.\n")
            sb.append("2. Opt me out of the sale of my personal data, targeted advertising and profiling.\n")
            sb.append("3. Remove every listing about me from ").append(company)
                .append(" and any related sites you operate.\n\n")
        } else {
            sb.append("Please opt me out and delete or suppress every record about me on ")
            sb.append(company).append(" and any related sites you operate.\n\n")
        }
        details(sb, p, listingUrl)
        sb.append("\n")
        if (texas) sb.append("Section 541.052 requires a response within 45 days of receiving this request. ")
        sb.append("Please confirm by email once this is done.\n\nThank you,\n").append(who)
        return sb.toString()
    }

    // For a company that let the 45 days run out.
    fun finalMailto(p: Profile, sentAt: Long, now: Long, listingUrl: String): String = "mailto:" + email +
        "?subject=" + Uri.encode(finalSubject(p)) + "&body=" + Uri.encode(finalBody(p, sentAt, now, listingUrl))

    fun finalSubject(p: Profile): String {
        val who = p.first.trim() + " " + p.last.trim()
        return if (p.state.trim().uppercase() == "TX") "Final notice: data deletion request (Texas resident) - " + who
        else "Second request: opt-out request - " + who
    }

    fun finalBody(p: Profile, sentAt: Long, now: Long, listingUrl: String): String {
        val who = p.first.trim() + " " + p.last.trim()
        val texas = p.state.trim().uppercase() == "TX"
        val sb = StringBuilder()
        sb.append("Hello,\n\n")
        sb.append("On ").append(dateText(sentAt)).append(" I asked ").append(company())
        if (texas) {
            sb.append(" to delete my personal data and to opt me out of its sale, targeted advertising and profiling, ")
            sb.append("under the Texas Data Privacy and Security Act (Texas Business and Commerce Code, Section 541.051).\n\n")
            sb.append("Section 541.052 required you to respond within 45 days. That deadline passed on ")
            sb.append(dateText(dueAt(sentAt))).append(", and I have not received a response.\n\n")
            sb.append("Please complete my request now and confirm by email. If I do not hear from you, I will file ")
            sb.append("a complaint with the Office of the Texas Attorney General, which enforces the Act.\n\n")
        } else {
            sb.append(" to opt me out and delete or suppress every record about me. I have not received a response.\n\n")
            sb.append("Please complete my request now and confirm by email.\n\n")
        }
        details(sb, p, listingUrl)
        sb.append("\nThank you,\n").append(who)
        return sb.toString()
    }

    // Ready to paste into the description box of the Texas Attorney General's complaint form.
    fun complaintText(p: Profile, sentAt: Long, now: Long, listingUrl: String): String {
        val links = splitLinks(listingUrl)
        val late = -daysLeft(sentAt, now)
        val site = site()
        val sb = StringBuilder()
        if (p.state.trim().uppercase() == "TX") sb.append("I am a Texas resident. ")
        sb.append("On ").append(dateText(sentAt)).append(" I sent ").append(company())
        if (site.isNotEmpty()) sb.append(" (").append(site).append(")")
        sb.append(" a request under the Texas Data Privacy and Security Act to delete my personal data and to opt me ")
        sb.append("out of its sale, targeted advertising and profiling. Section 541.052 required a response within ")
        sb.append("45 days, by ").append(dateText(dueAt(sentAt))).append(". As of ").append(dateText(now)).append(", ")
        sb.append(if (late == 1) "1 day" else "$late days")
        sb.append(" after that deadline, I have not received a response and my request has not been completed.")
        if (links.size == 1) sb.append(" My information is still listed at ").append(links[0])
        if (links.size > 1) {
            sb.append(" My information is still listed at these pages:")
            for (l in links) sb.append("\n").append(l)
        }
        return sb.toString()
    }

    private fun details(sb: StringBuilder, p: Profile, listingUrl: String) {
        val full = listOf(p.first.trim(), p.middle.trim(), p.last.trim())
            .filter { it.isNotEmpty() }.joinToString(" ")
        val links = splitLinks(listingUrl)
        sb.append("Name: ").append(full).append("\n")
        sb.append("Location: ").append(p.city.trim()).append(", ")
            .append(p.state.trim().uppercase()).append("\n")
        if (p.email.isNotBlank()) sb.append("Email: ").append(p.email.trim()).append("\n")
        if (sendPhone && p.phone.isNotBlank()) sb.append("Phone: ").append(p.phone.trim()).append("\n")
        if (links.size == 1) sb.append("Listing: ").append(links[0]).append("\n")
        if (links.size > 1) {
            sb.append("Listings:\n")
            for (l in links) sb.append(l).append("\n")
        }
    }

    // The company's name without the note in brackets.
    fun company(): String = name.replace(Regex("\\s*\\(.*\\)\\s*$"), "")

    // The websites this company runs, taken from its links and email address.
    fun sites(): Set<String> {
        val out = LinkedHashSet<String>()
        val at = email.substringAfter('@', "")
        for (h in listOf(hostOf(search), hostOf(find), at, hostOf(optOut))) {
            val d = baseDomain(h)
            if (d.isNotEmpty() && d !in FORM_HOSTS) out.add(d)
        }
        return out
    }

    fun site(): String = sites().firstOrNull() ?: ""

    // True for a single link to one of this company's own sites, like a copied listing.
    fun isOwnLink(text: String): Boolean {
        val t = text.trim()
        if (!(t.startsWith("https://") || t.startsWith("http://"))) return false
        if (t.any { it.isWhitespace() }) return false
        val d = baseDomain(hostOf(t))
        return d.isNotEmpty() && d in sites()
    }
}

// Opt-out forms hosted by these services say nothing about whose site it is.
val FORM_HOSTS = setOf("onetrust.com", "trustarc.eu", "trustarc.com", "zendesk.com", "consumerprivacyinfo.com")

fun hostOf(url: String): String {
    val u = url.trim()
    val i = u.indexOf("://")
    if (i < 0) return ""
    val rest = u.substring(i + 3)
    val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' || it == ':' }
    return (if (end < 0) rest else rest.substring(0, end)).lowercase()
}

// tx.veripages.com and www.veripages.com both become veripages.com.
fun baseDomain(host: String): String {
    val parts = host.lowercase().trim().split('.').filter { it.isNotEmpty() }
    return if (parts.size < 2) "" else parts[parts.size - 2] + "." + parts[parts.size - 1]
}

fun splitLinks(listingUrl: String): List<String> =
    listingUrl.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

fun dueAt(sentAt: Long): Long = sentAt + REPLY_DAYS * DAY_MS

// Chip texts for the opt-out screen, so subject and message boxes need no typing.
fun subjectText(p: Profile): String =
    if (p.state.trim().uppercase() == "TX") "Data deletion request (Texas resident)" else "Data deletion request"

fun requestText(p: Profile): String {
    val ask = "please delete all personal data you hold about me and stop selling or sharing it. " +
        "Please confirm by email when this is done."
    return if (p.state.trim().uppercase() == "TX") "I am a Texas resident. Under the Texas Data Privacy and Security Act, $ask"
    else ask.replaceFirstChar { it.uppercase() }
}

fun loadBrokers(ctx: Context): List<Broker> {
    val list = ArrayList<Broker>()
    val seen = HashSet<String>()
    val files = (ctx.assets.list("") ?: emptyArray())
        .filter { it.startsWith("brokers") && it.endsWith(".json") }
        .sorted()
    for (f in files) {
        try {
            val arr = JSONArray(ctx.assets.open(f).bufferedReader().use { it.readText() })
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                if (!seen.add(id)) continue
                list.add(
                    Broker(
                        id, o.getString("name"), o.optString("search"),
                        o.optString("optOut"), o.optString("ua"), o.optString("tip"),
                        o.optString("email"), o.optBoolean("hand"), o.optBoolean("browser"),
                        o.optString("find"), o.optBoolean("sendPhone"), o.optBoolean("tool")
                    )
                )
            }
        } catch (e: Exception) {
        }
    }
    return list
}
