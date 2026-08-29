package com.example.a2uicomposelabs.androidxa2ui

// Four recorded generations, one per ticket, on the wire androidx.a2ui accepts.
//
// Read them side by side and the demo's whole argument is visible in the JSON: the five
// questions are different five questions. Nothing here is a template with the nouns swapped —
// the delivery form asks about a courier and a tracking page, the refund form asks about money
// and how long it took, and neither question would mean anything on the other ticket.
//
// Each script is what one turn of a model produced, kept verbatim, so the screen is identical
// every time it is replayed. The live path in SupportCsatDemo writes the same shapes; these are
// what runs when there is no API key, which is most of the time on a conference stage.
//
// The `more` container at the end of every form is deliberately empty. It is the seat the app
// keeps for itself: a low rating is answered by writing components into that id, and because
// the submit Button binds the whole `/answers` subtree rather than one path per question, the
// button that was already on screen carries the follow-up answers too, unchanged.

/** One recorded generation: the form, and the follow-up the app holds in reserve. */
data class CsatScript(val form: List<String>, val followUp: List<String>)

/** Every message in this demo names this surface. */
const val CSAT_SURFACE_ID: String = "csat"

/** The event a finished form sends back. */
const val CSAT_EVENT: String = "submitCsat"

/**
 * The reply's surface. A second one, not an edit of the first.
 *
 * v0.9.1 forbids createSurface on an id that already exists, and the form has done its job — so
 * the reply arrives on its own surface and the app deletes the form once it is on screen. That
 * deleteSurface is the only one in this app, and it belongs here: this is the one place where a
 * surface is genuinely finished with.
 */
const val CSAT_DONE_SURFACE_ID: String = "csat_done"

/**
 * The reply, when there is no model to write one.
 *
 * Deliberately flat and a little cold. A recorded reply cannot say anything about answers it
 * never read, and pretending otherwise on stage would be the demo lying about the very thing it
 * is trying to show. The live one names what the customer actually said; this one names the
 * ticket and stops.
 */
fun recordedClosing(ticketId: String, lowRating: Boolean): List<String> {
    val heading = if (lowRating) "We have read this" else "Thank you"
    val body =
        if (lowRating) {
            "Your answers are with the team that handled ticket #$ticketId, not in a dashboard."
        } else {
            "Your answers are with the team that handled ticket #$ticketId."
        }
    return listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"$CSAT_DONE_SURFACE_ID","catalogId":"$SUPPORT_CATALOG_ID","sendDataModel":false}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"$CSAT_DONE_SURFACE_ID","components":[{"id":"root","component":"Card","child":"reply"},{"id":"reply","component":"Column","children":["done_title","done_body"]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"$CSAT_DONE_SURFACE_ID","components":[{"id":"done_title","component":"Text","text":"$heading"},{"id":"done_body","component":"Text","text":"$body"}]}}""",
    )
}

/** The form is finished with. This is the app's one use of deleteSurface. */
fun deleteFormSurface(): String =
    """{"version":"v0.9.1","deleteSurface":{"surfaceId":"$CSAT_SURFACE_ID"}}"""

/** The answers, as the closing turn's user message. */
fun closingBriefing(ticket: SupportTicket, answers: List<Pair<String, String>>): String =
    """
    The customer has just sent back the satisfaction survey for this ticket.

    - Ticket: #${ticket.id}
    - Category: ${ticket.kind.label}
    - What happened: ${ticket.history}

    Their answers, by the path each control was bound to:

    ${answers.joinToString("\n    ") { (path, value) -> "- $path: $value" }}

    Write the reply they see now. Name what they told you.
    """
        .trimIndent()

// ---------------------------------------------------------------- delivery · ticket #4417

private val DeliveryForm =
    listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"csat","catalogId":"$SUPPORT_CATALOG_ID","sendDataModel":true}}""",
        // The data model is its own message on this wire, and every answer starts empty: "" for
        // a sentence, 0 for a rating, [] for a multiple choice. An unanswered question has to
        // have a value, because the control binds to it before anyone touches the screen.
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"csat","path":"/","value":{"answers":{"overall":0,"estimate":0,"condition":"","wanted":[],"comment":""}}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"root","component":"Card","child":"form"},{"id":"form","component":"Column","children":["header","q1","q2","q3","q4","q5","more","submit"]},{"id":"header","component":"Text","text":"Ticket #4417 — your order arrived three days after the window we promised. Five questions about that delivery."}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q1","component":"Question","text":"Overall, how satisfied are you with the way we handled this delay?","required":true,"children":["a1"]},{"id":"a1","component":"StarRating","value":{"path":"/answers/overall"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q2","component":"Question","text":"How accurate was the delivery date we gave you at checkout?","required":true,"children":["a2"]},{"id":"a2","component":"StarRating","value":{"path":"/answers/estimate"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q3","component":"Question","text":"How did the parcel look when it finally arrived?","required":true,"children":["a3"]},{"id":"a3","component":"ChoicePicker","value":{"path":"/answers/condition"},"options":[{"label":"In good condition","value":"good"},{"label":"Scuffed, but the contents were fine","value":"scuffed"},{"label":"Damaged","value":"damaged"}]}]}}""",
        // The one multiple-choice question on this form: `values`, not `value`, so the control
        // draws checkboxes and writes an array.
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q4","component":"Question","text":"While it was late, what would have helped most? Pick as many as apply.","required":false,"children":["a4"]},{"id":"a4","component":"ChoicePicker","values":{"path":"/answers/wanted"},"options":[{"label":"Tracking that actually moved","value":"tracking"},{"label":"A message the moment it slipped","value":"told_sooner"},{"label":"A way to reach the courier","value":"courier"},{"label":"A new delivery window I could plan around","value":"new_window"}]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q5","component":"Question","text":"Anything else about the delivery?","required":false,"children":["a5"]},{"id":"a5","component":"TextField","label":"Optional","text":{"path":"/answers/comment"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"more","component":"Column","children":[]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"submit","component":"Button","child":"submit_label","variant":"primary","action":{"event":{"name":"$CSAT_EVENT","context":{"ticketId":"4417","answers":{"path":"/answers"}}}}},{"id":"submit_label","component":"Text","text":"Send feedback"}]}}""",
    )

// ---------------------------------------------------------------- refund · ticket #4392

private val RefundForm =
    listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"csat","catalogId":"$SUPPORT_CATALOG_ID","sendDataModel":true}}""",
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"csat","path":"/","value":{"answers":{"overall":0,"clarity":0,"speed":"","chased":false,"comment":""}}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"root","component":"Card","child":"form"},{"id":"form","component":"Column","children":["header","q1","q2","q3","q4","q5","more","submit"]},{"id":"header","component":"Text","text":"Ticket #4392 — your refund for order A-88117 took nine working days to land. Five questions about that."}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q1","component":"Question","text":"Overall, how satisfied are you with the way we handled this refund?","required":true,"children":["a1"]},{"id":"a1","component":"StarRating","value":{"path":"/answers/overall"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q2","component":"Question","text":"How clearly did we explain what you would get back, and when?","required":true,"children":["a2"]},{"id":"a2","component":"StarRating","value":{"path":"/answers/clarity"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q3","component":"Question","text":"Compared with what you were expecting, how long did the money take?","required":true,"children":["a3"]},{"id":"a3","component":"ChoicePicker","value":{"path":"/answers/speed"},"options":[{"label":"Faster than I expected","value":"faster"},{"label":"About what I expected","value":"expected"},{"label":"Slower","value":"slower"},{"label":"Much slower","value":"much_slower"}]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q4","component":"Question","text":"Did you have to chase us for it?","required":false,"children":["a4"]},{"id":"a4","component":"CheckBox","label":"Yes, I wrote in more than once","value":{"path":"/answers/chased"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q5","component":"Question","text":"Anything else about the refund?","required":false,"children":["a5"]},{"id":"a5","component":"TextField","label":"Optional","text":{"path":"/answers/comment"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"more","component":"Column","children":[]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"submit","component":"Button","child":"submit_label","variant":"primary","action":{"event":{"name":"$CSAT_EVENT","context":{"ticketId":"4392","answers":{"path":"/answers"}}}}},{"id":"submit_label","component":"Text","text":"Send feedback"}]}}""",
    )

// ---------------------------------------------------------------- technical · ticket #4405

private val TechnicalForm =
    listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"csat","catalogId":"$SUPPORT_CATALOG_ID","sendDataModel":true}}""",
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"csat","path":"/","value":{"answers":{"overall":0,"fixed":"","steps":0,"retold":1,"comment":""}}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"root","component":"Card","child":"form"},{"id":"form","component":"Column","children":["header","q1","q2","q3","q4","q5","more","submit"]},{"id":"header","component":"Text","text":"Ticket #4405 — the crash on photo upload was fixed in 8.2.1. Five questions about getting there."}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q1","component":"Question","text":"Overall, how satisfied are you with the support you got on this?","required":true,"children":["a1"]},{"id":"a1","component":"StarRating","value":{"path":"/answers/overall"},"max":5}]}}""",
        // The question a satisfaction form usually forgets to ask, and the only one that matters
        // on a technical ticket: is the thing actually fixed?
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q2","component":"Question","text":"Is the problem actually gone on your phone?","required":true,"children":["a2"]},{"id":"a2","component":"ChoicePicker","value":{"path":"/answers/fixed"},"options":[{"label":"Yes, it works now","value":"fixed"},{"label":"Better, but it still happens sometimes","value":"partly"},{"label":"No, it still crashes","value":"not_fixed"}]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q3","component":"Question","text":"How easy were the steps we asked you to follow?","required":true,"children":["a3"]},{"id":"a3","component":"StarRating","value":{"path":"/answers/steps"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q4","component":"Question","text":"How many times did you have to explain the problem from the start?","required":false,"children":["a4"]},{"id":"a4","component":"Slider","label":"Times","value":{"path":"/answers/retold"},"min":1,"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q5","component":"Question","text":"Anything else we should know about the crash or the fix?","required":false,"children":["a5"]},{"id":"a5","component":"TextField","label":"Optional","text":{"path":"/answers/comment"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"more","component":"Column","children":[]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"submit","component":"Button","child":"submit_label","variant":"primary","action":{"event":{"name":"$CSAT_EVENT","context":{"ticketId":"4405","answers":{"path":"/answers"}}}}},{"id":"submit_label","component":"Text","text":"Send feedback"}]}}""",
    )

// ---------------------------------------------------------------- account · ticket #4381

private val AccountForm =
    listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"csat","catalogId":"$SUPPORT_CATALOG_ID","sendDataModel":true}}""",
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"csat","path":"/","value":{"answers":{"overall":0,"trust":0,"effort":"","twoStep":"","comment":""}}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"root","component":"Card","child":"form"},{"id":"form","component":"Column","children":["header","q1","q2","q3","q4","q5","more","submit"]},{"id":"header","component":"Text","text":"Ticket #4381 — you were locked out after changing your number, and back in the next morning. Five questions about that."}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q1","component":"Question","text":"Overall, how satisfied are you with how we got you back into your account?","required":true,"children":["a1"]},{"id":"a1","component":"StarRating","value":{"path":"/answers/overall"},"max":5}]}}""",
        // Nothing like this appears on the other three forms. On an account ticket, feeling safe
        // is the outcome; on a delivery ticket it would be a strange thing to ask.
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q2","component":"Question","text":"How safe did the identity check feel — did we ask for the right things?","required":true,"children":["a2"]},{"id":"a2","component":"StarRating","value":{"path":"/answers/trust"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q3","component":"Question","text":"How much work was it to prove the account was yours?","required":true,"children":["a3"]},{"id":"a3","component":"ChoicePicker","value":{"path":"/answers/effort"},"options":[{"label":"Simple","value":"simple"},{"label":"Fair enough for a locked account","value":"fair"},{"label":"A lot of back and forth","value":"hard"},{"label":"I nearly gave up","value":"nearly_gave_up"}]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q4","component":"Question","text":"Would you switch on two-step sign-in if we walked you through it?","required":false,"children":["a4"]},{"id":"a4","component":"ChoicePicker","value":{"path":"/answers/twoStep"},"options":[{"label":"Yes, show me how","value":"yes"},{"label":"Maybe later","value":"later"},{"label":"I already have it on","value":"already"}]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"q5","component":"Question","text":"Anything else about being locked out?","required":false,"children":["a5"]},{"id":"a5","component":"TextField","label":"Optional","text":{"path":"/answers/comment"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"more","component":"Column","children":[]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"submit","component":"Button","child":"submit_label","variant":"primary","action":{"event":{"name":"$CSAT_EVENT","context":{"ticketId":"4381","answers":{"path":"/answers"}}}}},{"id":"submit_label","component":"Text","text":"Send feedback"}]}}""",
    )

// ---------------------------------------------------------------- the app's own follow-up
//
// Not the agent's work. When the overall rating comes back at one or two stars, the app writes
// these into the surface that is already on screen: three more components, one data-model
// update, and the `more` container filled in last so the whole thing appears in one beat
// instead of as three loading placeholders.
//
// Why the app and not the agent: the rule "two stars means ask why" is policy, and policy that
// a support organisation depends on does not belong in a prompt. The agent wrote the questions;
// deciding when to ask another one is the app's call, and it needs no model to make it.
//
// Why the reasons still differ per category: a menu of causes IS the category. "The tracking
// never moved" cannot be an answer on a refund ticket.

private fun followUp(
    ticketId: String,
    note: String,
    reasons: List<Pair<String, String>>,
    detailPrompt: String,
): List<String> {
    val options =
        reasons.joinToString(",") { (label, value) -> """{"label":"$label","value":"$value"}""" }
    return listOf(
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"csat","path":"/answers/followUp","value":{"reason":"","detail":"","contact":false}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"fnote","component":"Text","text":"$note"},{"id":"fq1","component":"Question","text":"What went wrong for you?","required":true,"children":["fa1"]},{"id":"fa1","component":"ChoicePicker","value":{"path":"/answers/followUp/reason"},"options":[$options]}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"fq2","component":"Question","text":"$detailPrompt","required":false,"children":["fa2"]},{"id":"fa2","component":"TextField","label":"Optional","text":{"path":"/answers/followUp/detail"}},{"id":"fq3","component":"Question","text":"Shall someone get back to you about ticket #$ticketId?","required":false,"children":["fa3"]},{"id":"fa3","component":"CheckBox","label":"Yes, please contact me","value":{"path":"/answers/followUp/contact"}}]}}""",
        // Attaching the container last is what keeps the follow-up from flashing three loading
        // placeholders: a Column child that has not arrived yet renders as a spinner, and here
        // every child has already arrived.
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"csat","components":[{"id":"more","component":"Column","children":["fnote","fq1","fq2","fq3"]}]}}""",
    )
}

/** Every recorded generation, keyed by the ticket it was written for. */
val RecordedCsat: Map<String, CsatScript> =
    mapOf(
        "4417" to
            CsatScript(
                form = DeliveryForm,
                followUp =
                    followUp(
                        ticketId = "4417",
                        note = "That is not the delivery we sold you. Two more questions, and this goes to the team that handled it.",
                        reasons =
                            listOf(
                                "It was late, and that is the whole problem" to "late",
                                "Nobody told me it had slipped" to "no_warning",
                                "The tracking page was wrong" to "tracking_wrong",
                                "Support took too long to answer" to "slow_support",
                            ),
                        detailPrompt = "What should we have done when it slipped?",
                    ),
            ),
        "4392" to
            CsatScript(
                form = RefundForm,
                followUp =
                    followUp(
                        ticketId = "4392",
                        note = "Nine days for your own money back is too long. Two more questions, and this goes to billing.",
                        reasons =
                            listOf(
                                "It took far too long" to "too_slow",
                                "Nobody explained the timeline" to "unexplained",
                                "I had to ask more than once" to "had_to_chase",
                                "The amount was not what I expected" to "wrong_amount",
                            ),
                        detailPrompt = "What would have made the wait bearable?",
                    ),
            ),
        "4405" to
            CsatScript(
                form = TechnicalForm,
                followUp =
                    followUp(
                        ticketId = "4405",
                        note = "Sorry — six days and a rebuild is a lot to ask of you. Two more questions, and this goes to the engineers who shipped the fix.",
                        reasons =
                            listOf(
                                "It still is not fixed for me" to "not_fixed",
                                "The steps did not work on my phone" to "steps_failed",
                                "It took too many messages" to "too_many_rounds",
                                "I lost data along the way" to "lost_data",
                            ),
                        detailPrompt = "What happened when you tried the steps?",
                    ),
            ),
        "4381" to
            CsatScript(
                form = AccountForm,
                followUp =
                    followUp(
                        ticketId = "4381",
                        note = "Being locked out of your own account is frightening, and we made it slower than it had to be. Two more questions, and this goes to trust & safety.",
                        reasons =
                            listOf(
                                "It took too long to get back in" to "too_slow",
                                "The checks felt intrusive" to "intrusive",
                                "I was passed between people" to "passed_around",
                                "I still do not know how it happened" to "unexplained",
                            ),
                        detailPrompt = "What would have made this less frightening?",
                    ),
            ),
    )
