package com.wickwirez.goingdark

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val GUIDE_URL = "https://wickwirez-beep.github.io/Going-Dark/guide.html"

class HelpTopic(val title: String, val body: String)

// The same text is on the web guide, so the Play listing can link to it.
val HELP_TOPICS = listOf(
    HelpTopic(
        "Start here: 5 steps",
        """
1. Tap EDIT MY DETAILS and enter your name, city, state and birth year. Add an email address so companies can confirm with you. Everything stays on this phone.
2. Tap RUN SCAN. The app checks about 15 well-known people-search sites and marks each one LISTED, NOT FOUND or UNVERIFIED.
3. Tap SHOW TO-DO ONLY to see just the rows that need you.
4. On each row, tap the opt-out button. The app opens the company's form, or an email, with your details filled in. Send it, then tap MARK AS SENT.
5. Come back in a few days. Replies, deadlines and late companies are all handled on this same screen. If you get stuck, open the question below that matches.
"""
    ),
    HelpTopic(
        "What is this app doing for me?",
        """
Data brokers are companies that collect and sell personal information such as your name, address, phone number and relatives. Many US states give you the right to ask them to delete it. Going Dark helps you find who lists you, sends each company a removal request, and keeps a 45-day clock on every request so you know who has not answered.

Some companies remove you within days. Most take a few weeks. A few never answer, and some will list you again later. The app keeps track of all of that for you.
"""
    ),
    HelpTopic(
        "What does RUN SCAN do? Why only about 15 sites?",
        """
Only some sites have a public search page. RUN SCAN visits those, searches for your name and city, and reads the results the way you would. The other 400 or so companies have nothing to search, so they only need a removal request. They are in the list below with their own buttons.

If a site shows a human check, solve it on the page. If a site blocks the scan, the row says UNVERIFIED. Tap CHECK IN BROWSER, look yourself up, and tap NOT LISTED if nothing is there.
"""
    ),
    HelpTopic(
        "What do the labels on each row mean?",
        """
LISTED: the scan found a listing that looks like you.
NOT FOUND: the scan searched and found nothing.
UNVERIFIED or UNREACHABLE: the scan could not check this site. Check it yourself with CHECK IN BROWSER.
NOT SCANNED: no scan has run for this site yet.
NOT LISTED: you checked, or they told you, that nothing is there.
X DAYS LEFT or DUE TODAY: the company's 45 days to answer are still running.
X DAYS OVERDUE (amber): their 45 days are up. See "The 45 days ran out".
REMOVED: the company confirmed it deleted or suppressed your data.
REPORTED: you filed a complaint with the attorney general and are waiting.
DONE: a checklist step you finished.
"""
    ),
    HelpTopic(
        "What do the buttons do?",
        """
MARK AS SENT: you sent the request. Starts the 45-day clock.
NOT LISTED / NO LONGER LISTED: nothing is there, or the listing is gone.
REMOVED: THEY CONFIRMED: the company said it deleted your data. UNDO REMOVED reverses a mistake.
EMAIL AGAIN / OPT-OUT AGAIN: send the request a second time.
THEY WANT ID: copies a reply that politely declines to send ID.
THEY SAID NO: copies an appeal and starts a 60-day clock.
SEND FINAL NOTICE: a firmer second email, once their time is up.
REPORT TO TEXAS AG: copies a ready-made complaint and opens the Texas Attorney General's form.
MARK AS REPORTED: records that you filed it. UNDO REPORTED reverses it.
FIND ME ON THIS SITE: opens that site's page for your name.
CHECK IN BROWSER: opens the site so you can look yourself up.
SHOW TO-DO ONLY / SHOW OVERDUE ONLY: filters the list to what needs you.
COPY REPORT: copies a text summary of every row to share or save.
"""
    ),
    HelpTopic(
        "A company replied. What do I tap?",
        """
They say it is done or removed: tap REMOVED: THEY CONFIRMED.
They say they have no data on you: tap NOT LISTED.
They send a link or form to finish: open it and complete it. The request is not done until you do.
They ask you to confirm by clicking a link: click it, and do it soon, because many links expire in 24 hours.
They ask for a copy of your ID: do not send it. Tap THEY WANT ID.
They refuse: tap THEY SAID NO to send an appeal.
They send an automatic ticket or reply: nothing to tap. Wait for the real answer.
They ask which site or which record: reply with the website name and a link to your listing. FIND ME ON THIS SITE helps.
"""
    ),
    HelpTopic(
        "Should I send my ID?",
        """
No. A removal request only needs enough to find your record, such as your name, city and email. Do not send a driver's license, passport, Social Security number or full birth date. Tap THEY WANT ID and the app copies a polite reply that declines and offers what you already gave them. If a company still insists, tap THEY SAID NO to appeal, and report it if they do not answer.
"""
    ),
    HelpTopic(
        "The 45 days ran out. What now?",
        """
Tap SHOW OVERDUE ONLY. Each late company shows one line that tells you what to do next.
If the company has an email address: tap SEND FINAL NOTICE. If they still say nothing for two more weeks, tap REPORT TO TEXAS AG.
If the company only has a web form: tap OPT-OUT AGAIN and submit the form again. If they stay silent for a week, tap REPORT TO TEXAS AG.
After you file the complaint, tap MARK AS REPORTED so the row stops asking. Keep the confirmation page or email the attorney general gives you.
First check your inbox and spam for an answer. A reply you missed means you can simply tap what they said.
"""
    ),
    HelpTopic(
        "I do not live in Texas",
        """
Going Dark works anywhere in the United States. If your state is TX, the letters cite the Texas Data Privacy and Security Act. In any other state, the letters are a plain, polite request to delete or suppress your information.

The REPORT TO TEXAS AG button is for Texas residents. If you live elsewhere, file your complaint with your own state attorney general instead, and use MARK AS REPORTED after. Privacy rights and deadlines differ by state, and this app is not legal advice.
"""
    ),
    HelpTopic(
        "Forms, emails and confirmation links",
        """
Removal is always free. Never pay a company to remove you.
Many sites email a confirmation link. Check your spam folder, and click it within a day, because they often expire.
Solve any CAPTCHA yourself. Save the confirmation page or email.
Use an email address you do not mind sharing with these companies. A separate one for this is ideal.
"""
    ),
    HelpTopic(
        "My listing came back",
        """
It can happen. Companies buy new data and sometimes list you again. Run RUN SCAN about once a month. A row that shows BACK AGAIN means a site dropped you and then listed you again: tap OPT-OUT AGAIN. A company may keep a small record of your request on purpose, so it can block you from being added again. That is normal.
"""
    ),
    HelpTopic(
        "Other steps that protect you more",
        """
The list ends with steps that are not brokers. The ones that matter most are free credit freezes at Equifax, Experian, TransUnion and Innovis. A freeze stops new credit being opened in your name. Also on the list: the Do Not Call registry, opting out of pre-approved credit offers, Google's Results about you, and deleting your phone's advertising ID. Open each row, follow its tip, and tap MARK AS DONE.
"""
    ),
    HelpTopic(
        "Is my information safe in the app?",
        """
Your details are encrypted on this phone with a key held by Android. There is no account, no server and no tracking. The app has no ads, no analytics and no way to upload your information to the developer.

Some information does leave the phone when you tell the app to use it. A scan sends your name and city to each site's own search page. Opt-out forms receive what you fill in. Emails go through your own email app. Those sites get this information the same way they would if you typed it yourself.

Uninstalling the app deletes everything. Android backup is turned off on purpose.
"""
    ),
    HelpTopic(
        "New phone or reinstalling?",
        """
Your progress is not backed up, on purpose, because it holds personal details. Before you uninstall or switch phones, tap COPY REPORT and email it to yourself. On the new phone, enter your details again, run a scan, and mark each row from your report.
"""
    ),
    HelpTopic(
        "Can you guarantee removal?",
        """
No. Companies decide what to do with a request, and some do not comply. Going Dark helps you send clear requests, keeps the deadlines, and writes the follow-ups and complaints. It is a tool, not a lawyer. Results vary by company and by state.
"""
    ),
    HelpTopic(
        "Price and support",
        """
Going Dark is a one-time purchase. There is no subscription and no ads. Questions or bugs: email wickwirez@yahoo.com or open an issue at github.com/wickwirez-beep/Going-Dark/issues. The full guide is also at wickwirez-beep.github.io/Going-Dark/guide.html.
"""
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    BackHandler { onBack() }
    var open by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    val words = q.lowercase().split(" ").filter { it.isNotBlank() }
    val shown = if (words.isEmpty()) HELP_TOPICS else HELP_TOPICS.filter { t ->
        val hay = (t.title + " " + t.body).lowercase()
        words.all { hay.contains(it) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("HELP AND GUIDE", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Mono)
        Text("Search, or tap a question to open it.", color = Dim, fontSize = 13.sp)
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("Search the guide") }, modifier = Modifier.fillMaxWidth()
        )
        if (words.isNotEmpty() && shown.isEmpty()) {
            Text("No match. Try one word, like ID, overdue, removed or scan.", color = Amber, fontSize = 13.sp)
        }
        shown.forEach { t ->
            val idx = HELP_TOPICS.indexOf(t)
            val expanded = words.isNotEmpty() || open == idx
            Column(
                Modifier.fillMaxWidth()
                    .background(Panel, RoundedCornerShape(10.dp))
                    .clickable { open = if (open == idx) -1 else idx }
                    .padding(14.dp)
            ) {
                Text(
                    (if (expanded) "- " else "+ ") + t.title,
                    color = if (expanded) Cyan else Ink, fontWeight = FontWeight.SemiBold,
                    fontFamily = Mono, fontSize = 14.sp
                )
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(t.body.trim(), color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        OutlinedButton(
            onClick = { onOpen(GUIDE_URL) }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("OPEN THE GUIDE ON THE WEB", fontFamily = Mono)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("BACK", fontFamily = Mono)
        }
        Spacer(Modifier.height(16.dp))
    }
}
