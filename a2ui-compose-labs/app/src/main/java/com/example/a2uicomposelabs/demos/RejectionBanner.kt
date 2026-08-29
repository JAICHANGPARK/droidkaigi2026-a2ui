package com.example.a2uicomposelabs.demos

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.a2uicomposelabs.a2ui.A2uiRejection

/**
 * What the renderer refused on this turn, and what happened next.
 *
 * A refusal is no longer the end of the road. UI arrives as a tool call, so the reason goes
 * back to the agent as that call's result and the agent gets to write the message again. The
 * wording says so: this is a round trip, not a dead end. Whether the retry worked is visible
 * on the screen itself — the card is either there, or a recording took its place.
 *
 * The three kinds are labelled apart, because on a projector they teach different things:
 *
 * - **Unparseable** — not valid JSON at all. Rare now that the API delivers the whole argument.
 * - **Wrong envelope** — valid JSON, but not an A2UI message: a bare array where the body of
 *   `updateComponents` belongs, a missing `surfaceId`.
 * - **Refused by the catalog** — a real message, and the catalog said no anyway: a component
 *   the app never declared, or a property that does not match the schema. The boundary working.
 *
 * The caller is expected to clear `client.errors` at the start of each turn. Without that this
 * banner shows the last failure *ever*, and keeps showing it over turns that went fine.
 */
@Composable
internal fun RejectionBanner(errors: List<A2uiRejection>, modifier: Modifier = Modifier) {
    val last = errors.lastOrNull() ?: return
    val reason = last.toString()
    val kind = when {
        reason.startsWith(MALFORMED_JSON) -> "Unparseable"
        reason.startsWith(MALFORMED_MESSAGE) -> "Wrong envelope"
        else -> "Refused by the catalog"
    }
    val detail = reason.removePrefix(MALFORMED_JSON).removePrefix(MALFORMED_MESSAGE)
    val more = if (errors.size > 1) " (+${errors.size - 1} earlier this turn)" else ""

    Text(
        "$kind, sent back to the agent to rewrite: $detail$more",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

private const val MALFORMED_JSON = "malformed JSON: "
private const val MALFORMED_MESSAGE = "malformed message: "
