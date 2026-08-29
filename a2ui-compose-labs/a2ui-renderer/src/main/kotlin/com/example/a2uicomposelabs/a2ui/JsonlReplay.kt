package com.example.a2uicomposelabs.a2ui

import android.content.Context
import kotlinx.coroutines.delay

/**
 * Replays a JSONL asset line by line, like an agent streaming over the wire.
 * Each applied line is one state change; each change recomposes — the UI grows on screen.
 */
suspend fun A2uiClient.replayAsset(
    context: Context,
    assetName: String,
    lineDelayMs: Long = 400L,
    /** Override to watch each line go by; the default just applies it. */
    onLine: (String) -> Unit = { apply(it) },
) {
    val lines = context.assets.open(assetName).bufferedReader().useLines { it.toList() }
    for (line in lines) {
        if (line.isBlank()) continue
        delay(lineDelayMs)
        onLine(line)
    }
}
