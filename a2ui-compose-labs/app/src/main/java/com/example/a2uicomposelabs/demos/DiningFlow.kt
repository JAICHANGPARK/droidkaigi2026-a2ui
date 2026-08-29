package com.example.a2uicomposelabs.demos

import com.example.a2uicomposelabs.a2ui.A2uiAction
import com.example.a2uicomposelabs.a2ui.SurfaceState
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The restaurant flow, separated from the screen that shows it.
 *
 * Two demos run it: the dedicated Dining demo, and the Assistant, where booking a table is just
 * one more thing the single catalog can answer. Everything here is the *app's* half of the
 * conversation — the menu the agent is not allowed to invent, the arithmetic the protocol
 * cannot do, and the replies the app writes into its own surface when an action comes back.
 */

/** The dishes the app is willing to sell. The agent never names one itself. */
internal val HouseMenu: JsonArray = JsonArray(
    listOf(
        diningMenuItem("Margherita", "Tomato · basil · mozzarella", 14000),
        diningMenuItem("Truffle pasta", "Cream · mushroom · truffle oil", 17000),
        diningMenuItem("Caesar salad", "Romaine · parmigiano", 9000),
        diningMenuItem("Garlic bread", "Two slices", 5000),
    )
)

private fun diningMenuItem(name: String, note: String, price: Int): JsonObject = buildJsonObject {
    put("name", name)
    put("note", note)
    put("price", price)
    put("quantity", 0)
}

/** True when the user is asking to order food rather than book a table. */
internal fun isDeliveryPrompt(prompt: String): Boolean {
    val p = prompt.lowercase()
    return listOf("배달", "주문", "시킬", "시켜", "메뉴", "delivery", "order", "deliver", "menu")
        .any(p::contains)
}

/**
 * True when the user wants to queue for a table now, rather than book one for later.
 *
 * Checked before [isDeliveryPrompt] wherever both are asked, because "웨이팅" and "queue" say
 * nothing about food but everything about which screen to draw.
 */
internal fun isWaitlistPrompt(prompt: String): Boolean {
    val p = prompt.lowercase()
    return listOf("웨이팅", "대기", "줄서", "줄 서", "기다", "waitlist", "wait list", "queue", "line up")
        .any(p::contains)
}

/** True when the prompt is about the restaurant at all — booking, queueing or ordering. */
internal fun isDiningPrompt(prompt: String): Boolean {
    val p = prompt.lowercase()
    return isDeliveryPrompt(prompt) || isWaitlistPrompt(prompt) ||
        listOf("예약", "테이블", "자리", "book", "reserv", "table").any(p::contains)
}

/** The recording that answers this prompt when there is no live model. */
internal fun diningAssetFor(prompt: String): String = when {
    isWaitlistPrompt(prompt) -> "dining_waitlist.jsonl"
    isDeliveryPrompt(prompt) -> "dining_delivery.jsonl"
    else -> "dining_reservation.jsonl"
}

private fun numberField(item: JsonObject, key: String): Double {
    val primitive = item[key] as? JsonPrimitive ?: return 0.0
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull() ?: 0.0
}

private fun textField(item: JsonObject, key: String): String =
    (item[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

/**
 * Turns the live menu into the receipt's own array — only the dishes actually ordered, each
 * with its line total worked out. The app does this arithmetic because nothing else can.
 */
internal fun orderedLines(menu: JsonArray): JsonArray = JsonArray(
    menu.mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val quantity = numberField(item, "quantity")
        if (quantity <= 0) return@mapNotNull null
        buildJsonObject {
            put("name", textField(item, "name"))
            put("quantity", quantity.roundToInt())
            put("price", numberField(item, "price"))
            put("lineTotal", numberField(item, "price") * quantity)
        }
    }
)

/** Adds half an hour to an ISO `yyyy-MM-ddTHH:mm`, leaving anything else alone. */
/**
 * Copies values out of an action's context into the paths this app reads.
 *
 * Only what the user actually saw travels: `context` holds values the renderer already
 * resolved, so nothing here trusts the agent for content. Unknown keys are ignored, which is
 * why several spellings can be offered for the same destination.
 */
private fun SurfaceState.adopt(action: A2uiAction, vararg mapping: Pair<String, String>) {
    for ((key, path) in mapping) {
        val value = action.context[key] ?: continue
        if (value is JsonPrimitive && value.contentOrNull.isNullOrBlank()) continue
        updateData(path, value)
    }
}

internal fun halfHourLater(iso: String): String {
    val parts = iso.split("T")
    if (parts.size != 2) return iso
    val time = parts[1].split(":")
    val hour = time.getOrNull(0)?.toIntOrNull() ?: return iso
    val minute = time.getOrNull(1)?.toIntOrNull() ?: return iso
    val total = (hour * 60 + minute + 30) % (24 * 60)
    return parts[0] + "T%02d:%02d".format(total / 60, total % 60)
}

/**
 * What a delivery goes through, in order. The renderer is sent this list once; from then on the
 * app moves an index and everything on screen follows.
 */
internal val DeliveryStages = listOf(
    "Order received",
    "Rider matched",
    "Cooking",
    "Picked up from the restaurant",
    "On the way to you",
    "Delivered",
)

/** How long each stage lasts on stage. Short enough to watch, long enough to read. */
private const val STAGE_MILLIS = 3_500L

private val Riders = listOf("Minsu", "Haruka", "Elena", "Tomás", "Priya")

/** A one-line description of what is happening during each stage. */
private fun stageNote(index: Int, rider: String): String = when (index) {
    0 -> "The restaurant is confirming your order."
    1 -> "$rider is heading to the restaurant."
    2 -> "Your food is on the stove."
    3 -> "$rider has your order and is setting off."
    4 -> "$rider is a few minutes away."
    else -> "$rider handed it over. Enjoy."
}

private fun stageEta(index: Int): String = when {
    index >= DeliveryStages.lastIndex -> "Delivered"
    else -> "about ${(DeliveryStages.lastIndex - index) * 5} min away"
}

/**
 * The queue, as four stages rather than a bare number.
 *
 * Reuses `DeliveryProgress` deliberately: a waitlist is the same shape as a delivery — a fixed
 * list of stages and an index the app moves on its own — so it needs no new component, only new
 * data. The agent sends the component once; every tick after that is `updateDataModel`.
 */
internal val WaitlistStages = listOf(
    "In the queue",
    "Getting close",
    "You're next",
    "Table ready",
)

/**
 * One restaurant minute, on stage.
 *
 * The queue has always talked in minutes — "about 15 min", and now "held for 10 more min" —
 * while a team was actually seated every three seconds regardless. One constant relates the
 * two, so the clock on screen and the clock in the room differ by this factor and nothing else.
 *
 * Two seconds is also the answer to what prompted it: at three seconds a whole queue was over
 * in ten, so stepping away and coming back always landed on "table ready" and read as the demo
 * having reset. Raise it for a calmer stage; lower it to rehearse.
 */
private const val MINUTE_MILLIS = 2_000L

/** Five restaurant minutes to seat one team — which is exactly what [queueEta] promises. */
private const val QUEUE_MILLIS = 5 * MINUTE_MILLIS

/** How long the house holds a ready table before it goes to the next party. */
internal const val HOLD_MINUTES = 10

/** Which stage a count of teams-ahead lands on. */
private fun queueStage(ahead: Int): Int = when {
    ahead <= 0 -> 3
    ahead == 1 -> 2
    ahead <= 3 -> 1
    else -> 0
}

private fun queueNote(ahead: Int, name: String): String = when {
    ahead <= 0 -> "Your table is ready${if (name.isBlank()) "" else ", $name"}. Come to the host stand."
    ahead == 1 -> "One team ahead of you. Stay close."
    else -> "$ahead teams ahead of you."
}

private fun queueEta(ahead: Int): String =
    if (ahead <= 0) "Ready now" else "about ${ahead * 5} min"

/** What the note under the stages says once the table is free and the clock is running. */
private fun holdNote(minutesLeft: Int, name: String): String = when {
    minutesLeft <= 2 -> "$minutesLeft min left before the table goes to the next party."
    else -> "Your table is ready${if (name.isBlank()) "" else ", $name"}. Come to the host stand."
}

/**
 * Everything that happens once a party has a ticket, however they got one.
 *
 * The interesting half is what happens *after* this returns: the ticket crosses the queue, sits
 * through a hold, and expires — and not one component is sent for any of it.
 */
private suspend fun beginWaitlist(
    surface: SurfaceState,
    session: ChatSession,
    surfaceId: String,
    ticket: String,
    ahead: Int,
    play: suspend (asset: String, values: Map<String, String>) -> Unit,
    say: (String) -> Unit,
) {
    val name = (surface.read("/waitlist/name") as? JsonPrimitive)?.contentOrNull.orEmpty()
    surface.updateData("/waitlist/ticket", JsonPrimitive(ticket))
    surface.updateData("/waitlist/steps", JsonArray(WaitlistStages.map(::JsonPrimitive)))
    surface.showQueueing(ahead, name)
    play("dining_waitlist_done.jsonl", emptyMap())
    say("You're in the queue. Ticket $ticket, $ahead teams ahead.")

    // Keyed to the surface so two parties can queue at once, and owned by the session so the
    // line keeps moving while the user is on another screen entirely. The queue and the hold
    // share one job deliberately: a ticket can be given up at any point in its life, and one
    // key cancels one job.
    session.launchBackground("waitlist-$surfaceId") {
        for (remaining in ahead - 1 downTo 1) {
            delay(QUEUE_MILLIS)
            surface.showQueueing(remaining, name)
        }
        delay(QUEUE_MILLIS)
        surface.showHeld(HOLD_MINUTES, name)
        session.say("Ticket $ticket — your table is ready, and it is held for $HOLD_MINUTES minutes.")

        for (minutesLeft in HOLD_MINUTES - 1 downTo 1) {
            delay(MINUTE_MILLIS)
            surface.showHeld(minutesLeft, name)
        }
        delay(MINUTE_MILLIS)
        // Nobody came. A held table nobody claims is a table the restaurant is losing, so the
        // house takes it back — and says so, rather than leaving a screen that goes on
        // promising a table that is already someone else's.
        surface.showClosed(
            "hold expired",
            "Nobody arrived within $HOLD_MINUTES minutes, so the table went to the next party. " +
                "Ask again to rejoin.",
        )
        play("dining_waitlist_closed.jsonl", emptyMap())
        session.say("Ticket $ticket expired. The table went to the next party.")
    }
}

/**
 * The agent, for the queue. Not a model — a service that knows how long the line is.
 *
 * Worth being loud about on stage: the spec says an agent "does not have to be LLM-backed; a
 * deterministic service returning prebuilt UI is an agent too". This is that. It answers
 * `callAgentFunction` in the agent's own JSON, over the same wire a hosted model would use, and
 * the renderer cannot tell the difference — which is the entire claim.
 *
 * It also owns the ticket numbers. That used to be the app reaching into itself; now the value
 * crosses a wire and arrives as an `agentFunctionResponse`, which is the part you can point at.
 */
internal object DiningHouse {

    /** What the house hands back when a party joins. */
    data class Ticket(val ticket: String, val ahead: Int)

    /** Issues one. The house is busy tonight; how busy is the one thing the screen cannot know. */
    fun join(): Ticket = Ticket("W-${Random.nextInt(10, 99)}", Random.nextInt(3, 6))

    /** The wards this kitchen's couriers actually cover. */
    private val ServiceArea = listOf("shibuya", "shinjuku", "meguro", "setagaya", "minato")

    /** What the house says about an address, and how long it would take. */
    data class DeliveryArea(val deliverable: Boolean, val etaMinutes: Int, val note: String)

    /**
     * Whether the couriers reach it.
     *
     * A restaurant's range is a fact about the restaurant, not about the screen. The renderer
     * has the characters the customer typed; only this side has the map.
     */
    fun serviceable(address: String): DeliveryArea {
        val ward = ServiceArea.firstOrNull { address.lowercase().contains(it) }
        return if (ward == null) {
            DeliveryArea(
                deliverable = false,
                etaMinutes = 0,
                note =
                    "We deliver to Shibuya, Shinjuku, Meguro, Setagaya and Minato. That address " +
                        "is outside the kitchen's range tonight.",
            )
        } else {
            val eta = Random.nextInt(25, 46)
            DeliveryArea(true, eta, "${ward.replaceFirstChar(Char::uppercase)} — about $eta min.")
        }
    }

    /**
     * Answers one renderer-initiated call, given the raw line the renderer sent.
     *
     * Returns null for anything that is not a question — a `rendererFunctionResponse` going the
     * other way is not the house's business. Everything that *is* a question gets an answer,
     * including one it does not recognise: the renderer is holding a `functionCallId` and will
     * hold it until the timeout otherwise.
     */
    fun answer(line: String): String? {
        val message = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
            ?: return null
        val body = message["callAgentFunction"] as? JsonObject ?: return null
        val callId = (body["functionCallId"] as? JsonPrimitive)?.contentOrNull ?: return null
        val call = body["callFunction"] as? JsonObject
        val name = (call?.get("call") as? JsonPrimitive)?.contentOrNull

        return when (name) {
            "join_queue" -> {
                val issued = join()
                respond(callId) {
                    put("ticket", issued.ticket)
                    put("ahead", issued.ahead)
                    put("holdMinutes", HOLD_MINUTES)
                }
            }
            "check_delivery_area" -> {
                val address =
                    ((call["args"] as? JsonObject)?.get("address") as? JsonPrimitive)
                        ?.contentOrNull.orEmpty()
                val area = serviceable(address)
                respond(callId) {
                    put("deliverable", area.deliverable)
                    put("etaMinutes", area.etaMinutes)
                    put("note", area.note)
                }
            }
            else -> refuse(callId, "this restaurant has no function called '$name'")
        }
    }

    private fun respond(callId: String, value: JsonObjectBuilder.() -> Unit): String = buildJsonObject {
        put("version", "v1.0")
        putJsonObject("agentFunctionResponse") {
            put("functionCallId", callId)
            put("value", buildJsonObject(value))
        }
    }.toString()

    private fun refuse(callId: String, why: String): String = buildJsonObject {
        put("version", "v1.0")
        putJsonObject("agentFunctionResponse") {
            put("functionCallId", callId)
            putJsonObject("error") {
                put("code", "INVALID_FUNCTION_CALL")
                put("message", why)
            }
        }
    }.toString()
}

/**
 * Still waiting: writes every value the queue screen reads, and nothing else.
 *
 * There is no message here, and that is the point. The components went out once when the ticket
 * was issued; everything after — the line shortening, the table coming free, the hold running
 * down, the ticket expiring — is this function and its two neighbours writing into paths the
 * screen is already bound to.
 */
internal fun SurfaceState.showQueueing(ahead: Int, name: String) {
    updateData("/waitlist/ahead", JsonPrimitive(ahead))
    updateData("/waitlist/line", JsonPrimitive("$ahead ahead of you"))
    updateData("/waitlist/eta", JsonPrimitive(queueEta(ahead)))
    updateData("/waitlist/current", JsonPrimitive(queueStage(ahead)))
    updateData("/waitlist/note", JsonPrimitive(queueNote(ahead, name)))
}

/** The table is free and the hold is counting down. Still only data. */
internal fun SurfaceState.showHeld(minutesLeft: Int, name: String) {
    updateData("/waitlist/ahead", JsonPrimitive(0))
    updateData("/waitlist/line", JsonPrimitive("Table ready"))
    updateData("/waitlist/eta", JsonPrimitive("held for $minutesLeft more min"))
    updateData("/waitlist/current", JsonPrimitive(WaitlistStages.lastIndex))
    updateData("/waitlist/note", JsonPrimitive(holdNote(minutesLeft, name)))
}

/**
 * How a ticket ended, for the one screen that reports all three endings.
 *
 * Seated, left, expired — same components, different words. Which is what a data model is for.
 */
internal fun SurfaceState.showClosed(outcome: String, note: String) {
    updateData("/waitlist/outcome", JsonPrimitive(outcome))
    updateData("/waitlist/outcomeNote", JsonPrimitive(note))
}

/** The picker stores an array of selected values; this is the readable form of it. */
private fun paymentLabel(surface: SurfaceState): String {
    val chosen = (surface.read("/payment/method") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    return when (chosen.firstOrNull()) {
        "card" -> "Paid by card"
        "cash" -> "Cash on delivery"
        "mobile" -> "Mobile pay"
        else -> "Paid"
    }
}


/**
 * What an action looks like in the transcript.
 *
 * The payload is the interesting artefact — it is what the agent would receive — but a basket
 * of six dishes turns into forty lines that push the actual UI off the screen. So the bubble
 * shows a summary and the copy button still yields the whole thing.
 */
internal fun actionSummary(action: A2uiAction): String {
    val fields = action.context.entries.joinToString(" · ") { (key, value) ->
        val rendered = when (value) {
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            // A single-choice picker still stores an array; show the choice, not the count.
            is JsonArray ->
                (value.singleOrNull() as? JsonPrimitive)?.contentOrNull
                    ?: "${value.size} items"
            else -> "…"
        }
        "$key ${rendered.take(28)}"
    }
    return buildString {
        // Name the direction. "action" and "callAgentFunction" are different events with
        // different consequences, and a transcript that calls both "action" hides the one
        // moment in the demo where the renderer asks instead of tells.
        append(if (action.wantsAnswer) "callAgentFunction → " else "action → ").append(action.name)
        if (fields.isNotEmpty()) append('\n').append(fields)
    }
}

/** Fills `/menu` when the agent asked for it. Returns true when it filled something. */
internal fun fulfilMenuRequest(surface: SurfaceState, kind: String): Boolean {
    if (kind != "menu") return false
    surface.updateData("/menu", HouseMenu)
    return true
}

/**
 * Makes sure the dishes on screen are this restaurant's, whether or not the agent asked.
 *
 * Asking with `{"kind":"menu"}` is the polite path and the one worth teaching. But a model that
 * skips it and writes four plausible dishes of its own is not a hypothetical — it happened on
 * the second live run — and a made-up price on a real order screen is the worst failure in this
 * whole talk. So the app asserts ownership: structure is the agent's, the menu is never.
 *
 * Returns true when it had to step in, so the demo can say out loud that it did.
 */
internal fun enforceHouseMenu(surface: SurfaceState): Boolean {
    val onScreen = surface.read("/menu") as? JsonArray ?: return false
    if (onScreen == HouseMenu) return false
    // Keep whatever the customer has already put in the basket, matched by dish name.
    val counts = onScreen.mapNotNull { it as? JsonObject }.associate { row ->
        (row["name"] as? JsonPrimitive)?.contentOrNull.orEmpty() to
            ((row["quantity"] as? JsonPrimitive)?.doubleOrNull ?: 0.0)
    }
    val restored = JsonArray(
        HouseMenu.map { item ->
            val row = item.jsonObject
            val name = (row["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val kept = counts[name] ?: 0.0
            if (kept <= 0.0) item else JsonObject(row + ("quantity" to JsonPrimitive(kept.toInt())))
        }
    )
    surface.updateData("/menu", restored)
    return true
}

/**
 * The app answering its own action: no agent, no network, just writes into the surface it
 * already holds — exactly as the agent would have.
 *
 * Returns false when the action is none of the restaurant's business, so a caller that also
 * handles other actions can carry on.
 *
 * @param play replays a recorded stream into this surface, with the given tokens substituted.
 * @param say  appends a line of assistant prose to the transcript.
 */
internal suspend fun runDiningAction(
    action: A2uiAction,
    session: ChatSession,
    play: suspend (asset: String, values: Map<String, String>) -> Unit,
    say: (String) -> Unit,
): Boolean {
    val surface = session.client.surfaces[action.surfaceId] ?: return false
    when (action.name) {
        "confirm_reservation" -> {
            val code = "R-${Random.nextInt(10000, 99999)}"
            // The model names its own paths — /booking/name, /reservation/guests, whatever it
            // felt like. The action carries the RESOLVED values, so copy them once into the
            // places this app reads, and every screen after this can bind one known shape.
            surface.adopt(action, "when" to "/resv/when", "datetime" to "/resv/when")
            surface.adopt(action, "guests" to "/resv/people", "party_size" to "/resv/people")
            surface.adopt(action, "name" to "/resv/name", "phone" to "/resv/phone")
            play("dining_reservation_done.jsonl", mapOf("__code__" to code))
            say("Booked. Your reference is $code.")
        }

        // Queueing for a table, the v1.0 way: the button did not announce anything, it asked.
        //
        // This is the one place in the app where the renderer opens its mouth first. The tap
        // becomes `callAgentFunction`, the house answers `agentFunctionResponse` with a ticket,
        // and only then is there a queue screen to draw. Turn on "Show the wire" and both lines
        // are there, in order, with the same functionCallId on each.
        "join_queue" -> {
            val call = action.functionCall ?: return false
            surface.adopt(action, "party" to "/waitlist/party", "guests" to "/waitlist/party")
            surface.adopt(action, "name" to "/waitlist/name", "phone" to "/waitlist/phone")

            val answer = session.client.callAgentFunction(action.surfaceId, call)
                .getOrElse { failure ->
                    // A question that goes unanswered is the failure mode an event never had,
                    // and it deserves better than a screen that just does not change.
                    say("The restaurant did not answer: ${failure.message}")
                    return true
                } as? JsonObject
                ?: run {
                    say("The restaurant answered with something that is not a ticket.")
                    return true
                }

            val ticket = (answer["ticket"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val ahead = (answer["ahead"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
            beginWaitlist(surface, session, action.surfaceId, ticket, ahead, play, say)
        }

        // The same thing as a plain event, for a model that drew the older form of the button.
        // The house issues the ticket either way; only the wire is different.
        "join_waitlist" -> {
            surface.adopt(action, "party" to "/waitlist/party", "guests" to "/waitlist/party")
            surface.adopt(action, "people" to "/waitlist/party", "party_size" to "/waitlist/party")
            surface.adopt(action, "name" to "/waitlist/name", "phone" to "/waitlist/phone")
            val issued = DiningHouse.join()
            beginWaitlist(surface, session, action.surfaceId, issued.ticket, issued.ahead, play, say)
        }

        // Arriving. Turning up early is not an error: the app knows where the ticket actually
        // is and says so, which is knowledge the agent never had in the first place.
        "check_in_waitlist" -> {
            val ticket = (surface.read("/waitlist/ticket") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val ahead = (surface.read("/waitlist/ahead") as? JsonPrimitive)
                ?.contentOrNull?.toIntOrNull() ?: 0
            if (ahead > 0) {
                say("Not yet — $ahead ${if (ahead == 1) "team is" else "teams are"} still ahead of you.")
                return true
            }
            session.stopBackground("waitlist-${action.surfaceId}")
            surface.showClosed("seated", "The table is yours. Enjoy the meal.")
            play("dining_waitlist_closed.jsonl", emptyMap())
            say("Checked in. Ticket $ticket is seated.")
        }

        // Leaving the queue has to stop the ticker too, or the line keeps advancing behind a
        // screen that says the customer already left.
        "cancel_waitlist" -> {
            session.stopBackground("waitlist-${action.surfaceId}")
            surface.showClosed("left the queue", "No table is being held. Ask again to rejoin.")
            play("dining_waitlist_closed.jsonl", emptyMap())
            say("Left the queue. No table is being held.")
        }

        // The basket is priced and the order becomes something to pay for.
        "place_order", "submit_order" -> {
            val menu = surface.read("/menu") as? JsonArray ?: JsonArray(emptyList())
            // Price the basket before swapping the surface: everything after reads /ordered.
            surface.updateData("/ordered", orderedLines(menu))
            if ((surface.read("/ordered") as? JsonArray).isNullOrEmpty()) {
                say("Nothing is in the basket yet.")
                return true
            }
            // The model picks its own path for the address — /address, /delivery/address,
            // whatever. The action carries the resolved value, so normalise it once here and
            // every screen after this can bind one known place.
            (action.context["address"] as? JsonPrimitive)?.let {
                surface.updateData("/delivery/address", it)
            }

            // Ask the house whether it reaches this address, before anyone pays for it.
            //
            // This is the one thing on the order screen no amount of local evaluation can
            // settle: a kitchen's delivery range is a fact about the kitchen. Before v1.0 the
            // screen had no way to ask — it could send the order and be told afterwards, which
            // means taking payment for a delivery you cannot make.
            val address = (surface.read("/delivery/address") as? JsonPrimitive)
                ?.contentOrNull.orEmpty()
            val area = session.client.callAgentFunction(
                surfaceId = action.surfaceId,
                call = buildJsonObject {
                    put("call", "check_delivery_area")
                    putJsonObject("args") { put("address", address) }
                },
            ).getOrNull() as? JsonObject

            val deliverable =
                (area?.get("deliverable") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            val note = (area?.get("note") as? JsonPrimitive)?.contentOrNull

            if (deliverable == false) {
                // The order screen stays up, so the address can simply be changed. Replacing it
                // with an error would throw away a basket the customer already filled.
                say(note ?: "We do not deliver to that address.")
                return true
            }
            if (area == null) {
                say("Could not reach the restaurant to check that address. Try again.")
                return true
            }
            (area["etaMinutes"] as? JsonPrimitive)?.let { surface.updateData("/delivery/eta", it) }
            note?.let { say(it) }

            play("dining_payment.jsonl", emptyMap())
            say("Almost there — choose how you'd like to pay.")
        }

        // Paid. The surface becomes a live document: one component, then only data.
        "confirm_payment" -> {
            val code = "D-${Random.nextInt(1000, 9999)}"
            val rider = Riders.random()
            surface.updateData("/order", buildJsonObject { put("code", code) })
            surface.updateData("/payment/label", JsonPrimitive(paymentLabel(surface)))
            surface.updateData("/tracking/rider", JsonPrimitive(rider))
            surface.updateData("/tracking/steps", JsonArray(DeliveryStages.map(::JsonPrimitive)))
            surface.updateData("/tracking/current", JsonPrimitive(0))
            surface.updateData("/tracking/note", JsonPrimitive(stageNote(0, rider)))
            surface.updateData("/tracking/eta", JsonPrimitive(stageEta(0)))
            play("dining_tracking.jsonl", emptyMap())
            say("Paid. Order $code is in — you can watch it from here.")

            // Keyed to the surface so two orders can run at once, and owned by the session so
            // it keeps moving while the user is on another screen entirely.
            session.launchBackground("delivery-${action.surfaceId}") {
                for (stage in 1..DeliveryStages.lastIndex) {
                    delay(STAGE_MILLIS)
                    surface.updateData("/tracking/current", JsonPrimitive(stage))
                    surface.updateData("/tracking/note", JsonPrimitive(stageNote(stage, rider)))
                    surface.updateData("/tracking/eta", JsonPrimitive(stageEta(stage)))
                }
                // Delivered. Ask how it went — still the same surface, a new set of components.
                delay(1_200L)
                // Seed the answers. A check over a path that does not exist yet cannot be
                // evaluated, and an unevaluable check counts as passing — which would leave
                // "Submit review" open before a single star was tapped.
                surface.updateData("/review/food", JsonPrimitive(0))
                surface.updateData("/review/rider", JsonPrimitive(0))
                surface.updateData("/review/comment", JsonPrimitive(""))
                play("dining_review.jsonl", emptyMap())
                session.say("Delivered by $rider. Mind rating it?")
            }
        }

        "submit_review" -> {
            play("dining_review_done.jsonl", emptyMap())
            val food = (surface.read("/review/food") as? JsonPrimitive)?.contentOrNull ?: "—"
            say("Thanks — $food stars for the food, noted.")
        }

        // The whole point of separating structure from content: one value changes and the Text
        // bound to it redraws. No components are sent.
        "request_change" -> {
            val current = (surface.read("/resv/when") as? JsonPrimitive)?.contentOrNull
                ?: return true
            surface.updateData("/resv/when", JsonPrimitive(halfHourLater(current)))
            say("Moved 30 minutes later — one value changed, no components re-sent.")
        }

        else -> return false
    }
    return true
}

/** The catalog rules a model needs before it can run either restaurant flow. */
internal val DINING_RULES = """
This app handles exactly these action names, and nothing else. An action it does not know is
dropped, so the button does nothing — always use one of these, spelled exactly:

  confirm_reservation   the Button that books the table
  place_order           the Button that sends the food order
  cancel_waitlist       the Button that leaves the queue again
  check_in_waitlist     the "I'm here" Button — on the queue screen the app draws, not yours

The queue is joined a different way. Its Button carries no action name at all; it carries a
function call, which is the v1.0 form for a button that expects an answer back.
  request_change        an optional Button offering a later time

- booking a table -> a Card holding a Column of DateTimeInput (date+time), Slider (party size,
  1..8), TextField for the name, TextField for the phone number, and one Button whose action
  name is `confirm_reservation`. Put a `required` check on the name and a `regex` check on the
  phone. Carry `when`, `guests`, `name` and `phone` in the action's `context`.

- queueing for a table NOW ("웨이팅", "대기", "waitlist", "queue") is a different job from
  booking one for later: there is no date and no time, only who is waiting. Draw a Card holding
  a Column of Slider (party size, 1..8), TextField for the name, TextField for the phone, and
  one Button that ASKS for a ticket rather than announcing anything. Same `required` and `regex`
  checks as the booking form. The Button's action is a function call, exactly like this:

  {"id":"join","component":"Button","label":"Join the queue",
   "action":{"functionCall":{"call":"join_queue","args":{
      "party":{"path":"/waitlist/party"},
      "name":{"path":"/waitlist/name"},
      "phone":{"path":"/waitlist/phone"}}}}}

  `join_queue` is declared `allowedCallers: agentOnly`, which means the restaurant runs it and
  you do not. Never put it in a binding — it answers over the wire, not in a frame.

  Then stop. You do not know how long the queue is and you never will: `join_queue` returns
  {ticket, ahead, holdMinutes}, the app draws the queue screen itself, and from there moves the
  position, the ten-minute hold on a ready table, and the outcome with data alone. Never write
  a position, a wait time, or a progress list of your own.

- ordering food -> a Card holding a Column of: a Text heading, a List repeating MenuItemRow
  over the menu array, one OrderTotalRow, a TextField for the address, and one Button whose
  action name is `place_order`, carrying `address` in its `context`.

MenuItemRow carries its own −/+ stepper, so use it instead of CheckBox whenever the customer
could want two of something. Inside the List template the paths are RELATIVE to the current
item: bind {"path":"quantity"}, never {"path":"/menu/0/quantity"}.

You cannot add numbers. There is no + operator anywhere in A2UI, so a total you write by hand
will be wrong the first time someone taps the stepper. Call the functions instead:

  calcOrderTotal(items: {"path":"/menu"})    -> the basket's price
  countOrderItems(items: {"path":"/menu"})   -> how many dishes are in it

Money on this menu is Korean won. Every amount you show goes through
formatCurrency(currency: "KRW", decimals: 0) — the default is dollars with cents, and a
lunch menu priced in dollars is the first thing anyone in the room will notice.

Write the row and the total exactly like this:

  {"id":"row","component":"MenuItemRow","name":{"path":"name"},"note":{"path":"note"},
   "priceLabel":{"call":"formatCurrency","args":{"value":{"path":"price"},"currency":"KRW","decimals":0}},
   "quantity":{"path":"quantity"}}

  {"id":"total","component":"OrderTotalRow","label":"Total",
   "value":{"call":"formatCurrency","args":{
      "value":{"call":"calcOrderTotal","args":{"items":{"path":"/menu"}}},
      "currency":"KRW","decimals":0}}}

`priceLabel` is optional in the schema and essential on screen: a menu with no prices renders
without complaint and is useless. Always send it.

Gate the submit Button with numeric(value: countOrderItems(...), min: 1) so an empty basket
cannot be ordered, and keep the button's label a plain string — a count interpolated into it
reads as "Place Order ()" for as long as the basket is empty.

You do NOT know this restaurant's menu. Ask for it with {"kind":"menu"} and bind to
/menu = [{"name","note","price","quantity"}, ...], which the app fills with quantity 0.
""".trimIndent()
