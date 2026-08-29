package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.model.catalog.functions.A2uiFormatStringFunction
import androidx.a2ui.model.catalog.functions.A2uiNumericFunction
import androidx.a2ui.model.catalog.functions.A2uiRequiredFunction
import androidx.compose.material3.a2ui.MaterialCheckBoxComponent
import androidx.compose.material3.a2ui.MaterialSliderComponent
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults

/** The support survey's catalog, quoted by every CSAT message the agent and the app send. */
const val SUPPORT_CATALOG_ID: String = "app.support.androidx/csat/v1"

/**
 * The catalog behind the customer-satisfaction form.
 *
 * Ten components: six from material3-a2ui, four written in this app. It is the widest of the
 * three androidx catalogs here, and it has to be — a CSAT form asks in whatever shape the
 * question needs. "Did the parcel arrive damaged" is three alternatives, "how clear was the
 * explanation" is five stars, "how many times did you have to explain it" is a number between
 * one and five, and "anything else" is a sentence. Give the agent stars alone and it will ask
 * every one of those as stars.
 *
 * This is a different catalog from [AndroidxSurveyCatalog] on purpose, even though both draw a
 * survey. That one has no ChoicePicker and no Slider, because the café form does not need them;
 * a catalog is an allowlist, not an inventory. What the two share is the *file* the app's own
 * components live in, not the permission to draw them.
 */
val AndroidxSupportCatalog: A2uiCatalog =
    A2uiCatalog(
        catalogId = SUPPORT_CATALOG_ID,
        components =
            listOf(
                // From material3-a2ui, unmodified.
                MaterialA2uiBasicCatalogV1Defaults.card,
                MaterialA2uiBasicCatalogV1Defaults.column,
                MaterialA2uiBasicCatalogV1Defaults.text,
                MaterialA2uiBasicCatalogV1Defaults.button,
                MaterialCheckBoxComponent, // landed 18 Aug 2026
                MaterialSliderComponent, // landed 19 Aug 2026
                // Written in this app.
                QuestionComponent, // AndroidxSurveyComponents.kt
                StarRatingComponent, // AndroidxSurveyComponents.kt
                ChoicePickerComponent, // AndroidxSurveyComponents.kt
                TextFieldComponent, // AndroidxSharedComponents.kt
            ),
        // `required` and `numeric` are here for TextField's invented `valid` property — the
        // v0.9.1 wire has no `checks`, so validation is a function call aimed at a component.
        // formatString is what lets a question quote the ticket it is asking about.
        functions =
            listOf(
                A2uiRequiredFunction.INSTANCE,
                A2uiNumericFunction.INSTANCE,
                A2uiFormatStringFunction.INSTANCE,
            ),
    )
