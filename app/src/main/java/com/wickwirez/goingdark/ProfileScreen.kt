package com.wickwirez.goingdark

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
