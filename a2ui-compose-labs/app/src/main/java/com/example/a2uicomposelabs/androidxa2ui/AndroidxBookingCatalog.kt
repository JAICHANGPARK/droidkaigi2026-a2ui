package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.model.catalog.functions.A2uiAndFunction
import androidx.a2ui.model.catalog.functions.A2uiEmailFunction
import androidx.a2ui.model.catalog.functions.A2uiFormatStringFunction
import androidx.a2ui.model.catalog.functions.A2uiLengthFunction
import androidx.a2ui.model.catalog.functions.A2uiNotFunction
import androidx.a2ui.model.catalog.functions.A2uiNumericFunction
import androidx.a2ui.model.catalog.functions.A2uiOrFunction
import androidx.a2ui.model.catalog.functions.A2uiRegexFunction
import androidx.a2ui.model.catalog.functions.A2uiRequiredFunction
import androidx.compose.material3.a2ui.MaterialSliderComponent
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults

/** The booking form's catalog, quoted by every booking message on either wire. */
const val BOOKING_CATALOG_ID: String = "app.dining.androidx/booking/v1"

/**
 * The catalog behind the booking form, and nothing else.
 *
 * Seven components: five from material3-a2ui as they are, two written in this app. That ratio
 * is the honest state of the library for this screen — it can lay out a form and take a slider,
 * but it cannot collect a name or a date.
 *
 * A catalog is scoped to a screen here on purpose. It could just as well be one catalog for the
 * whole app; what it must never be is a superset that happens to contain everything, because
 * the catalog IS the allowlist. Anything in it is something the agent may ask for, and a survey
 * component listed here would be a survey component the booking agent could draw.
 *
 * `Text`, `Card`, `Column` and `Button` are the odd ones out. None is a public object any more:
 * upstream moved Text behind A2uiBasicCatalogV1.Text on 19 Aug 2026, Card behind
 * A2uiBasicCatalogV1.Card on 21 Aug, and Column and Button (with Row, unused here) followed on
 * 25 Aug — those interfaces are what a design system implements to claim the basic catalog, and
 * material3-a2ui's implementations of all four are internal. The instances are reached through
 * MaterialA2uiBasicCatalogV1Defaults.text, .card, .column and .button. The catalog is being
 * emptied into the contract one component at a time, so expect the rest of this list to follow.
 */
val AndroidxBookingCatalog: A2uiCatalog =
    A2uiCatalog(
        catalogId = BOOKING_CATALOG_ID,
        components =
            listOf(
                // From material3-a2ui, unmodified.
                MaterialA2uiBasicCatalogV1Defaults.card,
                MaterialA2uiBasicCatalogV1Defaults.column,
                MaterialA2uiBasicCatalogV1Defaults.text,
                MaterialA2uiBasicCatalogV1Defaults.button,
                MaterialSliderComponent, // landed 19 Aug 2026
                // Written in this app.
                TextFieldComponent, // AndroidxSharedComponents.kt
                DateTimeInputComponent, // AndroidxBookingComponents.kt
            ),
        // Every function the form's checks may call. These ship in a2ui-model; the dynamic
        // evaluator runs them whenever a property's payload is a {"call": ..., "args": ...} tree.
        functions =
            listOf(
                A2uiRequiredFunction.INSTANCE,
                A2uiRegexFunction.INSTANCE,
                A2uiEmailFunction.INSTANCE,
                A2uiLengthFunction.INSTANCE,
                A2uiNumericFunction.INSTANCE,
                A2uiNotFunction.INSTANCE,
                A2uiAndFunction.INSTANCE,
                A2uiOrFunction.INSTANCE,
                A2uiFormatStringFunction.INSTANCE,
            ),
    )
