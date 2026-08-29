package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The conversation switcher: which chat you are in, and how to get to the others.
 *
 * Worth having in a demo app for one reason beyond convenience — an old conversation is not a
 * transcript here, it is a set of live surfaces. Switching back to a delivery placed five
 * minutes ago shows where that delivery has actually got to, because the session kept running
 * while you were elsewhere.
 */
@Composable
internal fun ChatSessionBar(
    store: ChatSessionStore,
    current: ChatSession,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { open = true }, modifier = Modifier.weight(1f, fill = false)) {
            Text(
                current.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch conversation")
        }

        if (store.sessions.size > 1) {
            Text(
                "${store.sessions.size} chats",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        IconButton(onClick = { store.newSession() }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New conversation",
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            store.sessions.asReversed().forEach { session ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.widthIn(max = 260.dp)) {
                            Text(
                                session.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                sessionSubtitle(session),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    },
                    onClick = {
                        store.select(session.id)
                        open = false
                    },
                    trailingIcon = {
                        if (store.sessions.size > 1) {
                            IconButton(
                                onClick = { store.delete(session.id) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete this conversation",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("New chat", style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    store.newSession()
                    open = false
                },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
}

/** Enough to tell two conversations apart at a glance. */
private fun sessionSubtitle(session: ChatSession): String {
    val surfaces = session.client.surfaces.size
    val turns = session.transcript.count { it is ChatTurn.User }
    return buildString {
        append(if (turns == 1) "1 message" else "$turns messages")
        if (surfaces > 0) append(" · ").append(if (surfaces == 1) "1 surface" else "$surfaces surfaces")
    }
}
