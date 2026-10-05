package com.wickwirez.goingdark

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
fun BrokerRow(
    b: Broker,
    r: BrokerResult?,
    sentAt: Long?,
    onOpen: (String) -> Unit,
    onOptOut: (String) -> Unit,
    onSearchInBrowser: () -> Unit,
    onOptOutInBrowser: () -> Unit,
    onMarkSent: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(Panel).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                rowLabel(r, sentAt), color = statusColor(r?.status),
                fontSize = 12.sp, fontFamily = Mono
            )
        }
        if (r != null) {
            if (r.note.isNotEmpty()) Text(r.note, color = Dim, fontSize = 12.sp)
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
            if (sentAt != null && r.status != Status.CLEAR) {
                Text(
                    "Opt-out sent " + dateText(sentAt) + ". Scan again in a few days to confirm.",
                    color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (r.status == Status.LISTED && b.optOut.isNotEmpty()) {
                val best = r.listings.firstOrNull { it.strong } ?: r.listings.firstOrNull()
                val link = best?.url ?: ""
                Button(
                    onClick = { onOptOut(link) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (sentAt != null) "OPEN OPT-OUT AGAIN" else "START OPT-OUT",
                        fontFamily = Mono, fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (link.isNotEmpty()) copyText(ctx, "Listing link", link)
                        onOptOutInBrowser()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("OPT OUT IN BROWSER", fontFamily = Mono)
                }
                if (sentAt == null) {
                    OutlinedButton(
                        onClick = onMarkSent,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("MARK AS SENT", fontFamily = Mono)
                    }
                }
            }
            if (r.status == Status.CLEAR) {
                Text(
                    "Open the search page to double-check",
                    color = Cyan, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp).clickable { onSearchInBrowser() }
                )
            }
            if (r.status == Status.BLOCKED || r.status == Status.UNKNOWN) {
                OutlinedButton(
                    onClick = onSearchInBrowser,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CHECK IN BROWSER", fontFamily = Mono)
                }
                if (b.optOut.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOptOutInBrowser,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("OPT OUT IN BROWSER", fontFamily = Mono)
                    }
                    if (sentAt == null) {
                        OutlinedButton(
                            onClick = onMarkSent,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("MARK AS SENT", fontFamily = Mono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormRow(b: Broker, sentAt: Long?, onOptOut: () -> Unit) {
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
        }
        Button(
            onClick = onOptOut,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (sentAt != null) "OPEN OPT-OUT AGAIN" else "START OPT-OUT",
                fontFamily = Mono, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProfileScreen(initial: Profile, canGoBack: Boolean, onBack: () -> Unit, onSave: (Profile) -> Unit) {
    var first by remember { mutableStateOf(initial.first) }
    var middle by remember { mutableStateOf(initial.middle) }
    var last by remember { mutableStateOf(initial.last) }
    var street by remember { mutableStateOf(initial.street) }
    var city by remember { mutableStateOf(initial.city) }
    var st by remember { mutableStateOf(initial.state) }
    var zip by remember { mutableStateOf(initial.zip) }
    var year by remember { mutableStateOf(initial.birthYear) }
    var email by remember { mutableStateOf(initial.email) }
    var phone by remember { mutableStateOf(initial.phone) }
    var others by remember { mutableStateOf(initial.otherNames) }
    var past by remember { mutableStateOf(initial.pastPlaces) }
    val draft = Profile(
        first.trim(), last.trim(), others.trim(), year.trim(), city.trim(), st.trim(), past.trim(),
        email.trim(), middle.trim(), street.trim(), zip.trim(), phone.trim()
    )
    BackHandler(enabled = canGoBack) { onBack() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("YOUR DETAILS", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
        Text(
            "Encrypted on this phone and never uploaded. Used only to build the searches, recognise your listings and fill in opt-out forms.",
            color = Dim, fontSize = 13.sp
        )
        Field(first, { first = it }, "First name")
        Field(middle, { middle = it }, "Middle name or initial (optional)")
        Field(last, { last = it }, "Last name")
        Field(street, { street = it }, "Street address (optional, for forms)")
        Field(city, { city = it }, "Current city")
        Field(st, { st = it.uppercase().take(2) }, "State, 2 letters (TX)")
        Field(
            zip, { v -> zip = v.filter { c -> c.isDigit() || c == '-' }.take(10) },
            "ZIP code (optional, for forms)", KeyboardType.Number
        )
        Field(
            year, { v -> year = v.filter { c -> c.isDigit() }.take(4) },
            "Birth year (sharpens matching)", KeyboardType.Number
        )
        Field(email, { email = it }, "Email for opt-out confirmations", KeyboardType.Email)
        Field(phone, { phone = it }, "Phone (optional, for forms)", KeyboardType.Phone)
        Field(others, { others = it }, "Other names, comma separated (optional)")
        Field(past, { past = it }, "Past places: City, ST; City, ST (optional)")
        Button(
            onClick = { onSave(draft) }, enabled = draft.ready,
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(10.dp)
        ) {
            Text("SAVE", fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(hint) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth()
    )
}
