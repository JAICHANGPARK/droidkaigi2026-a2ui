package com.example.a2uicomposelabs.androidxa2ui

/**
 * The four closed tickets the CSAT demo can ask about.
 *
 * These are the app's own facts, not the agent's. The agent never sees a customer database; it
 * is handed one ticket as text and writes the survey for that ticket. Everything the questions
 * can legitimately be about is in the seven fields below.
 */
enum class InquiryKind(val label: String) {
    Delivery("Delivery"),
    Refund("Refund"),
    Technical("Technical support"),
    Account("Account & security"),
}

data class SupportTicket(
    val id: String,
    val kind: InquiryKind,
    val subject: String,
    /** What actually happened, in one line. This is what makes the questions specific. */
    val history: String,
    val channel: String,
    val daysToClose: Int,
    val handledBy: String,
) {
    /** The turn's user message: one ticket, and what to do with it. */
    fun briefing(): String =
        """
        Write the satisfaction survey for this support ticket, which we closed today.

        - Ticket: #$id
        - Category: ${kind.label}
        - Subject: $subject
        - What happened: $history
        - Channel: $channel
        - Days from the first message to the fix: $daysToClose
        - Handled by: $handledBy

        Ask about what happened on THIS ticket. A question that would fit any ticket in any
        category is a wasted question.
        """
            .trimIndent()
}

val SupportTickets: List<SupportTicket> =
    listOf(
        SupportTicket(
            id = "4417",
            kind = InquiryKind.Delivery,
            subject = "Order #A-90321 arrived three days late",
            history =
                "The courier missed the promised Tuesday window, the tracking page kept showing " +
                    "'out for delivery' for two days, and we only told the customer it had " +
                    "slipped after they asked. The parcel arrived Friday, intact.",
            channel = "Chat",
            daysToClose = 4,
            handledBy = "Rina, delivery team",
        ),
        SupportTicket(
            id = "4392",
            kind = InquiryKind.Refund,
            subject = "Refund for cancelled order #A-88117",
            history =
                "The customer cancelled within the hour. The refund was approved the same day " +
                    "but the money took nine working days to appear, and they wrote in twice " +
                    "to ask where it was. The full amount was returned.",
            channel = "Email",
            daysToClose = 11,
            handledBy = "Marc, billing",
        ),
        SupportTicket(
            id = "4405",
            kind = InquiryKind.Technical,
            subject = "App closes when I attach a photo",
            history =
                "A crash on photo upload on Android 15. The customer sent a screen recording, " +
                    "we asked them to repeat the steps twice, and the fix shipped in 8.2.1 six " +
                    "days later. They have confirmed it no longer crashes.",
            channel = "In-app support",
            daysToClose = 6,
            handledBy = "Dae-ho, mobile engineering",
        ),
        SupportTicket(
            id = "4381",
            kind = InquiryKind.Account,
            subject = "Locked out after changing my phone number",
            history =
                "Sign-in codes went to a number the customer no longer owns. They were asked " +
                    "for a photo of an ID and a recent order number, which took two rounds, and " +
                    "the account was unlocked the next morning.",
            channel = "Phone, then email",
            daysToClose = 2,
            handledBy = "Priya, trust & safety",
        ),
    )
