package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.TimeZone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


// What the booking form needs and material3-a2ui does not have.
//
// The rest of that screen — Card, Column, Text, Slider, Button — comes from the library
// unmodified. Slider used to be here too: upstream shipped MaterialSliderComponent on
// 19 Aug 2026 with the same four property names this file had invented, so the hand-written
// one was deleted and the same JSONL now drives Google's implementation.

/** An ISO date and time, picked through the Material 3 date and time dialogs. */
object DateTimeInputComponent : A2uiComponent {
    private val valueProp = A2uiProperty.dynamicString("value", required = true,
        description = "The current value, ISO-8601, e.g. 2026-03-17T19:00.")
    private val labelProp = A2uiProperty.dynamicString("label", description = "The label.")
    private val enableDateProp = A2uiProperty.boolean("enableDate", description = "Ask for a date.")
    private val enableTimeProp = A2uiProperty.boolean("enableTime", description = "Ask for a time.")

    override val name = "DateTimeInput"
    override val description = "Picks a date, a time, or both."
    override val properties: List<A2uiProperty<*>> =
        listOf(valueProp, labelProp, enableDateProp, enableTimeProp)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(valueProp) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val value = properties.bind(valueProp).orEmpty()
        val onValueChange = properties.bindUpdater(valueProp)
        val label = properties.bind(labelProp)
        val wantsTime = properties[enableTimeProp] ?: false
        // Both flags default to false in the schema; treat that as date-only so the control
        // still does something rather than opening nothing.
        val wantsDate = (properties[enableDateProp] ?: false) || !wantsTime

        var showDate by remember { mutableStateOf(false) }
        var showTime by remember { mutableStateOf(false) }
        // The date half of a date-and-time pick, held until the time half arrives.
        var pendingDate by remember { mutableStateOf<String?>(null) }

        OutlinedButton(
            onClick = { if (wantsDate) showDate = true else showTime = true },
            enabled = onValueChange != null,
            modifier = modifier,
        ) {
            Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = value.ifEmpty { label ?: "Pick a date" },
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (showDate) {
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDate = false
                        val picked = state.selectedDateMillis?.let(::isoDate)
                        when {
                            picked == null -> Unit
                            wantsTime -> { pendingDate = picked; showTime = true }
                            else -> onValueChange?.invoke(picked)
                        }
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            ) { DatePicker(state = state) }
        }

        if (showTime) {
            val state = rememberTimePickerState(initialHour = 19, initialMinute = 0, is24Hour = true)
            DatePickerDialog(
                onDismissRequest = { showTime = false },
                confirmButton = {
                    TextButton(onClick = {
                        showTime = false
                        val time = String.format(Locale.US, "%02d:%02d", state.hour, state.minute)
                        val date = pendingDate ?: value.substringBefore('T')
                        onValueChange?.invoke(if (wantsDate) "${date}T$time" else time)
                        pendingDate = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            ) {
                TimePicker(state = state, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

private fun isoDate(utcMillis: Long): String {
    val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.timeInMillis = utcMillis
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.DAY_OF_MONTH),
    )
}
