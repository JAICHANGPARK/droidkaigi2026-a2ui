package com.example.a2uicomposelabs.androidxa2ui

import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.model.catalog.functions.A2uiFormatStringFunction
import androidx.a2ui.model.catalog.functions.A2uiRequiredFunction
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults

/** The survey's catalog, quoted by every survey message on either wire. */
const val SURVEY_CATALOG_ID: String = "app.dining.androidx/survey/v1"

/**
 * The catalog behind the survey, and nothing else.
 *
 * Eight components, and the split is worse than the other two catalogs: five come from
 * material3-a2ui and three are written here. Only one of the library's five takes input —
 * CheckBox, which landed 18 Aug 2026. Every star, every line of text and every question
 * wrapper on that screen is ours.
 *
 * Note what is *not* here. There is no Slider and no DateTimeInput, because the survey has no
 * use for either, and a catalog is an allowlist rather than an inventory: listing a component
 * the screen does not need is handing the agent a component it should not draw.
 */
val AndroidxSurveyCatalog: A2uiCatalog =
    A2uiCatalog(
        catalogId = SURVEY_CATALOG_ID,
        components =
            listOf(
                // From material3-a2ui, unmodified.
                MaterialA2uiBasicCatalogV1Defaults.card,
                MaterialA2uiBasicCatalogV1Defaults.column,
                MaterialA2uiBasicCatalogV1Defaults.text,
                MaterialA2uiBasicCatalogV1Defaults.button,
                MaterialA2uiBasicCatalogV1Defaults.checkBox, // its only input control
                // Written in this app.
                QuestionComponent, // AndroidxSurveyComponents.kt
                StarRatingComponent, // AndroidxSurveyComponents.kt
                TextFieldComponent, // AndroidxSharedComponents.kt
            ),
        // A survey asks; it does not validate much. `required` marks a question, and
        // formatString is here because a catalog without it cannot interpolate anything.
        functions =
            listOf(
                A2uiRequiredFunction.INSTANCE,
                A2uiFormatStringFunction.INSTANCE,
            ),
    )
