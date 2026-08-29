package com.example.a2uicomposelabs.androidxa2ui

// What the two dialects actually cost, in the order the screens show it. Shown under the
// panes in TwoDialectsDemo so the difference is readable without diffing JSON by eye.

/** What changes between the two dialects, in the order the screens show it. */
data class DialectNote(val title: String, val v091: String, val v10: String)

val DialectNotes: List<DialectNote> =
    listOf(
        DialectNote(
            "Opening a screen",
            "Three messages: createSurface, then updateDataModel, then updateComponents. " +
                "createSurface carries an id and a catalog and nothing else.",
            "One message. createSurface carries components and dataModel with it, so the " +
                "screen can arrive complete.",
        ),
        DialectNote(
            "Validation",
            "There isn't any. A2uiCheckableSchema sits in a2ui-model unread: no component " +
                "looks for checks and no runtime evaluates them. This app's TextField invents " +
                "'valid' and 'error' and aims them at a function call itself.",
            "A 'checks' array on the component. The renderer evaluates it and shows the " +
                "message. Same functions, standard shape.",
        ),
        DialectNote(
            "Buttons",
            "A Button takes a child component id, so a labelled button is two components. Its " +
                "action is wrapped: {\"event\": {\"name\": ..., \"context\": ...}}.",
            "A Button takes a label. Its action is {\"name\": ..., \"context\": ...}, unwrapped.",
        ),
        DialectNote(
            "Who draws the inputs",
            "Mostly you. material3-a2ui has thirteen of the spec's eighteen components now, " +
                "but only two take input: CheckBox (18 August 2026) and Slider (19 August), " +
                "and the Slider on this screen is one of them. TextField, DateTimeInput and " +
                "StarRating are still written in this app.",
            "The renderer ships all of them, because we wrote the renderer.",
        ),
    )
