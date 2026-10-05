package com.wickwirez.goingdark

import android.content.Context
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
    val tip: String = ""
) {
    fun buildUrl(p: Profile): String {
        val st = p.state.trim().uppercase()
        val full = STATES[st] ?: st
        fun words(s: String) = s.replace(Regex("[^\\p{L}0-9 \\-]"), "").trim()
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun lower(s: String) = words(s).joinToString("-") { it.lowercase() }
        fun title(s: String) = words(s).joinToString("-") { w ->
            w.lowercase().replaceFirstChar { c -> c.uppercase() }
        }
        fun query(s: String) = URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")
        return search
            .replace("{first}", lower(p.first)).replace("{First}", title(p.first))
            .replace("{First_q}", query(p.first))
            .replace("{last}", lower(p.last)).replace("{Last}", title(p.last))
            .replace("{Last_q}", query(p.last))
            .replace("{city}", lower(p.city)).replace("{City}", title(p.city))
            .replace("{City_q}", query(p.city))
            .replace("{st}", st.lowercase()).replace("{ST}", st)
            .replace("{state}", lower(full)).replace("{State}", title(full))
    }
}

fun loadBrokers(ctx: Context): List<Broker> {
    val arr = JSONArray(ctx.assets.open("brokers.json").bufferedReader().use { it.readText() })
    val list = ArrayList<Broker>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list.add(
            Broker(
                o.getString("id"), o.getString("name"), o.getString("search"),
                o.optString("optOut"), o.optString("ua"), o.optString("tip")
            )
        )
    }
    return list
}
