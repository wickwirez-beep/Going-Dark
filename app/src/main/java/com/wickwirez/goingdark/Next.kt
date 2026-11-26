package com.wickwirez.goingdark

// An opt-out you have not sent yet, that opens in the app's own form screen. Used by START NEXT UNSENT.
fun isUnsent(b: Broker, sent: Map<String, Long>): Boolean =
    b.search.isEmpty() && !b.tool && !b.browser && b.optOut.isNotEmpty() &&
        firstSent(sent, b.id) == null &&
        sent[noneKey(b.id)] == null && sent[removedKey(b.id)] == null && sent[reportedKey(b.id)] == null

// skip holds ids you tapped SKIP FOR NOW on, so they are passed over until the app is restarted.
fun nextUnsent(brokers: List<Broker>, sent: Map<String, Long>, skip: List<String> = emptyList()): Broker? =
    brokers.firstOrNull { isUnsent(it, sent) && it.id !in skip }
