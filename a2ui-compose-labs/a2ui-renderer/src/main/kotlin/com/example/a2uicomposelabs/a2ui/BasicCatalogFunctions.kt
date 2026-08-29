package com.example.a2uicomposelabs.a2ui

import android.icu.text.PluralRules
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Where `openUrl` sends a URL. Supply one from the app; the default refuses everything. */
fun interface A2uiUrlOpener {
    fun open(url: String): Boolean

    companion object {
        /** Opens nothing. An app that wants `openUrl` to work has to say so. */
        val Refuse: A2uiUrlOpener = A2uiUrlOpener { false }
    }
}

/** Which locale the formatting functions use. Swappable so tests are not machine-dependent. */
fun interface A2uiLocaleProvider {
    fun locale(): Locale

    companion object {
        val Default: A2uiLocaleProvider = A2uiLocaleProvider { Locale.getDefault() }
    }
}

/**
 * The Basic Catalog's 14 functions (spec v1.0), mirroring
 * `androidx.a2ui.model.catalog.basiccatalog.createBasicCatalogFunctions`.
 *
 * Five validate (`required`, `length`, `numeric`, `email`, `regex`), three combine those
 * results (`and`, `or`, `not`), five format (`formatString`, `formatNumber`, `formatCurrency`,
 * `formatDate`, `pluralize`), and one acts (`openUrl`).
 *
 * Note what is absent: no arithmetic, no comparison, no assignment, no way to reach anything
 * the app did not publish. An agent can ask for these fourteen and nothing else.
 */
fun basicCatalogFunctions(
    urlOpener: A2uiUrlOpener = A2uiUrlOpener.Refuse,
    localeProvider: A2uiLocaleProvider = A2uiLocaleProvider.Default,
): List<A2uiFunction> = listOf(
    AndFunction,
    OrFunction,
    NotFunction,
    RequiredFunction,
    LengthFunction,
    NumericFunction,
    EmailFunction,
    RegexFunction,
    FormatStringFunction,
    FormatNumberFunction(localeProvider),
    FormatCurrencyFunction(localeProvider),
    FormatDateFunction(localeProvider),
    PluralizeFunction(localeProvider),
    OpenUrlFunction(urlOpener),
)

/** The default set: formatting in the device locale, and `openUrl` disabled. */
val BasicCatalogFunctions: List<A2uiFunction> by lazy { basicCatalogFunctions() }

// ---------------------------------------------------------------------------
// Small helper so each definition below stays one readable block.
// ---------------------------------------------------------------------------

private fun definition(
    name: String,
    description: String,
    returnType: A2uiFunctionReturnType,
    properties: Map<String, A2uiSchema>,
    required: Set<String>,
) = A2uiFunctionDefinition(
    name = name,
    description = description,
    returnType = returnType,
    argumentSchema = A2uiObjectSchema(
        properties = properties,
        required = required,
        isAdditionalPropertiesAllowed = false,
    ),
)

// ---------------------------------------------------------------------------
// Logic
// ---------------------------------------------------------------------------

private object AndFunction : A2uiFunction {
    override val definition = definition(
        name = "and",
        description = "Performs a logical AND over a list of boolean values.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf(
            "values" to A2uiArraySchema(
                items = dynamicBoolean(),
                description = "The boolean values to evaluate.",
            )
        ),
        required = setOf("values"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
        JsonPrimitive(A2uiFunctionArgs.booleanList(args, "values").all { it })
}

private object OrFunction : A2uiFunction {
    override val definition = definition(
        name = "or",
        description = "Performs a logical OR over a list of boolean values.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf(
            "values" to A2uiArraySchema(
                items = dynamicBoolean(),
                description = "The boolean values to evaluate.",
            )
        ),
        required = setOf("values"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
        JsonPrimitive(A2uiFunctionArgs.booleanList(args, "values").any { it })
}

private object NotFunction : A2uiFunction {
    override val definition = definition(
        name = "not",
        description = "Negates a boolean value.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf("value" to dynamicBoolean("The value to negate.")),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
        JsonPrimitive(!A2uiFunctionArgs.boolean(args, "value"))
}

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

private object RequiredFunction : A2uiFunction {
    override val definition = definition(
        name = "required",
        description = "Checks that the value is present: not null and not empty.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf("value" to A2uiAnySchema(description = "The value to check.")),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
        JsonPrimitive(A2uiFunctionArgs.isPresent(args["value"]))
}

private object LengthFunction : A2uiFunction {
    override val definition = definition(
        name = "length",
        description = "Checks that a string's length falls within min and max.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf(
            "value" to dynamicString("The string to measure."),
            "min" to A2uiNumberSchema("The minimum allowed length."),
            "max" to A2uiNumberSchema("The maximum allowed length."),
        ),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val length = A2uiFunctionArgs.string(args, "value").length
        val min = A2uiFunctionArgs.optionalInt(args, "min") ?: 0
        val max = A2uiFunctionArgs.optionalInt(args, "max") ?: Int.MAX_VALUE
        return JsonPrimitive(length in min..max)
    }
}

private object NumericFunction : A2uiFunction {
    override val definition = definition(
        name = "numeric",
        description = "Checks that a number falls within min and max.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf(
            "value" to dynamicNumber("The number to check."),
            "min" to A2uiNumberSchema("The lowest allowed value."),
            "max" to A2uiNumberSchema("The highest allowed value."),
        ),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val value = A2uiFunctionArgs.double(args, "value")
        val min = A2uiFunctionArgs.optionalDouble(args, "min") ?: Double.NEGATIVE_INFINITY
        val max = A2uiFunctionArgs.optionalDouble(args, "max") ?: Double.POSITIVE_INFINITY
        return JsonPrimitive(value in min..max)
    }
}

private object EmailFunction : A2uiFunction {
    override val definition = definition(
        name = "email",
        description = "Checks that the value looks like an email address.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf("value" to dynamicString("The address to check.")),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
        JsonPrimitive(EMAIL.matches(A2uiFunctionArgs.string(args, "value")))

    /** The shape `android.util.Patterns.EMAIL_ADDRESS` uses, kept here so this stays pure Kotlin. */
    private val EMAIL = Regex(
        "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+"
    )
}

private object RegexFunction : A2uiFunction {
    override val definition = definition(
        name = "regex",
        description = "Checks that the value matches a regular expression.",
        returnType = A2uiFunctionReturnType.BOOLEAN,
        properties = mapOf(
            "value" to dynamicString("The string to test."),
            "pattern" to A2uiStringSchema("The pattern to match against."),
        ),
        required = setOf("value", "pattern"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val value = A2uiFunctionArgs.string(args, "value")
        val pattern = A2uiFunctionArgs.string(args, "pattern")
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            throw A2uiFunctionException("regex was given an invalid pattern: $pattern")
        }
        return JsonPrimitive(regex.matches(value))
    }
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

private object FormatStringFunction : A2uiFunction {
    override val definition = definition(
        name = "formatString",
        description =
            "Interpolates data model values and other function calls into a string. Holes are " +
                "written \${...} and hold either a JSON Pointer (\${/user/name}) or a call with " +
                "named arguments (\${formatDate(value:\${/when}, format:'MM-dd')}). Escape a " +
                "literal hole as \\\${.",
        returnType = A2uiFunctionReturnType.STRING,
        properties = mapOf("value" to dynamicString("The template to render.")),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext) =
        JsonPrimitive(
            A2uiStringTemplate.render(
                A2uiFunctionArgs.string(args, "value"),
                context,
                context.evaluator,
            )
        )
}

private class FormatNumberFunction(private val locales: A2uiLocaleProvider) : A2uiFunction {
    override val definition = definition(
        name = "formatNumber",
        description = "Formats a number with a given number of decimals and optional grouping.",
        returnType = A2uiFunctionReturnType.STRING,
        properties = mapOf(
            "value" to dynamicNumber("The number to format."),
            "decimals" to A2uiNumberSchema("How many decimal places. Defaults to none."),
            "grouping" to A2uiBooleanSchema("Use locale grouping separators. Defaults to true."),
        ),
        required = setOf("value"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val decimals = A2uiFunctionArgs.optionalInt(args, "decimals") ?: 0
        val formatter = NumberFormat.getNumberInstance(locales.locale()).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = A2uiFunctionArgs.optionalBoolean(args, "grouping") ?: true
        }
        return JsonPrimitive(formatter.format(A2uiFunctionArgs.double(args, "value")))
    }
}

private class FormatCurrencyFunction(private val locales: A2uiLocaleProvider) : A2uiFunction {
    override val definition = definition(
        name = "formatCurrency",
        description = "Formats a number as an amount of money in the given ISO 4217 currency.",
        returnType = A2uiFunctionReturnType.STRING,
        properties = mapOf(
            "value" to dynamicNumber("The amount."),
            "currency" to dynamicString("ISO 4217 code, e.g. JPY, USD, KRW."),
            "decimals" to A2uiNumberSchema("Override the currency's own decimal count."),
            "grouping" to A2uiBooleanSchema("Use locale grouping separators. Defaults to true."),
        ),
        required = setOf("value", "currency"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val code = A2uiFunctionArgs.string(args, "currency")
        val currency = try {
            Currency.getInstance(code)
        } catch (e: Exception) {
            throw A2uiFunctionException("formatCurrency was given an unknown currency: $code")
        }
        val formatter = NumberFormat.getCurrencyInstance(locales.locale()).apply {
            this.currency = currency
            A2uiFunctionArgs.optionalInt(args, "decimals")?.let {
                minimumFractionDigits = it
                maximumFractionDigits = it
            }
            isGroupingUsed = A2uiFunctionArgs.optionalBoolean(args, "grouping") ?: true
        }
        return JsonPrimitive(formatter.format(A2uiFunctionArgs.double(args, "value")))
    }
}

private class FormatDateFunction(private val locales: A2uiLocaleProvider) : A2uiFunction {
    override val definition = definition(
        name = "formatDate",
        description =
            "Formats a Unix timestamp using a pattern such as 'yyyy-MM-dd' or 'HH:mm'. Pass " +
                "'iso' for an ISO 8601 UTC string. Seconds and milliseconds are both accepted.",
        returnType = A2uiFunctionReturnType.STRING,
        properties = mapOf(
            "value" to dynamicNumber("The timestamp."),
            "format" to dynamicString("The pattern, or 'iso'."),
        ),
        required = setOf("value", "format"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val raw = A2uiFunctionArgs.long(args, "value")
        // Ten-digit values are seconds; anything larger is already milliseconds.
        val millis = if (kotlin.math.abs(raw) < SECONDS_CUTOFF) raw * 1000 else raw
        val format = A2uiFunctionArgs.string(args, "format")
        val formatter = try {
            if (format == "iso") {
                SimpleDateFormat(ISO_PATTERN, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            } else {
                SimpleDateFormat(format, locales.locale())
            }
        } catch (e: Exception) {
            throw A2uiFunctionException("formatDate was given an invalid pattern: $format")
        }
        return JsonPrimitive(formatter.format(Date(millis)))
    }

    private companion object {
        const val SECONDS_CUTOFF = 100_000_000_000L
        const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}

private class PluralizeFunction(private val locales: A2uiLocaleProvider) : A2uiFunction {
    override val definition = definition(
        name = "pluralize",
        description =
            "Picks a wording by the CLDR plural category of a count (zero, one, two, few, many, " +
                "other). 'other' is required and is the fallback; English only needs one and other.",
        returnType = A2uiFunctionReturnType.STRING,
        properties = mapOf(
            "value" to dynamicNumber("The count."),
            "zero" to dynamicString("Wording for the zero category."),
            "one" to dynamicString("Wording for the one category."),
            "two" to dynamicString("Wording for the two category."),
            "few" to dynamicString("Wording for the few category."),
            "many" to dynamicString("Wording for the many category."),
            "other" to dynamicString("Fallback wording. Required."),
        ),
        required = setOf("value", "other"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement {
        val count = A2uiFunctionArgs.double(args, "value")
        // Real CLDR categories on a device, so "few"/"many" behave correctly outside English.
        // Off-device (unit tests) android.icu is absent, so fall back to the English rule.
        val category = runCatching { PluralRules.forLocale(locales.locale()).select(count) }
            .getOrElse { if (count == 1.0) "one" else "other" }
        val chosen = A2uiFunctionArgs.optionalString(args, category)
            ?: A2uiFunctionArgs.string(args, "other")
        return JsonPrimitive(chosen)
    }
}

// ---------------------------------------------------------------------------
// Action
// ---------------------------------------------------------------------------

private class OpenUrlFunction(private val opener: A2uiUrlOpener) : A2uiFunction {
    override val definition = definition(
        name = "openUrl",
        description = "Opens an https URL outside the app. Returns nothing.",
        returnType = A2uiFunctionReturnType.VOID,
        properties = mapOf("url" to dynamicString("The https URL to open.")),
        required = setOf("url"),
    )

    override fun execute(args: Map<String, JsonElement>, context: A2uiExecutionContext): JsonElement? {
        val url = A2uiFunctionArgs.string(args, "url")
        // The same policy that gates images and video gates navigation: https only.
        if (!A2uiUrlPolicy.allows(url)) {
            throw A2uiFunctionException("openUrl refused a URL the policy does not allow: $url")
        }
        opener.open(url)
        return null
    }
}
