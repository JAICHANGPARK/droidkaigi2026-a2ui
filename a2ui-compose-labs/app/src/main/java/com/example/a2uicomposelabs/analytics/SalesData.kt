package com.example.a2uicomposelabs.analytics

import kotlin.math.roundToInt

/**
 * The app's own data. **The agent never sees any of it.**
 *
 * This is the whole point of the analytics demo: a model that invents a survey question is
 * annoying, a model that invents a revenue number is dangerous. So the agent is only ever told
 * *how* to display something — "sales, by region, as a bar chart" — and every number on screen
 * is aggregated here, on the device, from data the prompt never touched.
 *
 * A real app would swap this for a repository. The shape of the contract would not change.
 */
object SalesData {

    /** One row of the fictional ledger. */
    private data class Row(
        val month: Int,
        val region: String,
        val category: String,
        val amount: Int,
        val orders: Int,
    )

    // Weights, so the charts actually say something. A chart where every bar is the same
    // height demonstrates the plumbing and nothing else.
    private val regions = mapOf(
        "Seoul" to 210, "Busan" to 120, "Incheon" to 95, "Daegu" to 70, "Gwangju" to 45,
    )
    private val categories = mapOf(
        "Phones" to 220, "Audio" to 150, "Wearables" to 95, "Accessories" to 40,
    )

    /**
     * Twelve months of plausible-looking numbers, generated once from a fixed seed so the demo
     * shows the same figures on every run — a chart that changes between rehearsal and stage is
     * worse than a boring one. March dips on purpose, so "why did March drop?" has an answer.
     */
    private val rows: List<Row> = buildList {
        var seed = 20260817L
        fun next(bound: Int): Int {
            // Small deterministic LCG; Random(seed) would also work but this keeps it obvious.
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33).toInt().and(Int.MAX_VALUE)) % bound
        }
        for (month in 1..12) {
            val seasonal = when (month) {
                3 -> 55 // the deliberate dip
                11, 12 -> 145 // year-end push
                else -> 100
            }
            for ((region, regionWeight) in regions) {
                for ((category, categoryWeight) in categories) {
                    // ±15% of noise on top of the weights, so it reads as real data.
                    val noise = 85 + next(30)
                    val base = regionWeight * categoryWeight / 100 * seasonal / 100 * noise / 100
                    add(
                        Row(
                            month = month,
                            region = region,
                            category = category,
                            amount = base * 1_000,
                            orders = base / 20 + 1,
                        )
                    )
                }
            }
        }
    }

    private val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    /** A label/value series, ready to be written into a surface data model. */
    data class Series(val labels: List<String>, val values: List<Float>)

    private fun measure(row: Row, metric: String): Int =
        if (metric == "orders") row.orders else row.amount

    /**
     * Aggregates [metric] over [dimension]. Everything the demo asks for is one of these three
     * groupings, which is exactly why the agent only has to name them.
     */
    fun series(
        metric: String,
        dimension: String,
        top: Int = 12,
        /** Narrows the rows first — this is what a drill-down is. */
        filterRegion: String? = null,
        filterCategory: String? = null,
    ): Series {
        val scoped = rows.filter { row ->
            (filterRegion == null || row.region == filterRegion) &&
                (filterCategory == null || row.category == filterCategory)
        }
        val grouped: Map<String, Int> = when (dimension) {
            "region" -> scoped.groupBy(Row::region).mapValues { (_, r) -> r.sumOf { measure(it, metric) } }
            "category" -> scoped.groupBy(Row::category).mapValues { (_, r) -> r.sumOf { measure(it, metric) } }
            else -> scoped.groupBy(Row::month)
                .toSortedMap()
                .mapKeys { (month, _) -> monthNames[month - 1] }
                .mapValues { (_, r) -> r.sumOf { measure(it, metric) } }
        }
        // Month keeps calendar order; the categorical dimensions are most-first.
        val ordered = if (dimension == "month") grouped.entries.toList()
        else grouped.entries.sortedByDescending(Map.Entry<String, Int>::value)
        val limited = ordered.take(top.coerceIn(1, 12))
        return Series(limited.map { it.key }, limited.map { it.value.toFloat() })
    }

    /** Headline numbers for StatTiles, in the same aggregation as [series]. */
    fun summary(metric: String): List<Pair<String, String>> {
        val byMonth = series(metric, "month")
        val total = byMonth.values.sum()
        val best = byMonth.values.withIndex().maxByOrNull(IndexedValue<Float>::value)
        val worst = byMonth.values.withIndex().minByOrNull(IndexedValue<Float>::value)
        return listOf(
            "Total" to format(total, metric),
            "Best month" to (best?.let { byMonth.labels[it.index] } ?: "—"),
            "Weakest month" to (worst?.let { byMonth.labels[it.index] } ?: "—"),
        )
    }

    /** Percentage change from the previous month, as a signed label. */
    fun monthOverMonth(metric: String, monthLabel: String): String {
        val byMonth = series(metric, "month")
        val index = byMonth.labels.indexOf(monthLabel)
        if (index <= 0) return "—"
        val previous = byMonth.values[index - 1]
        if (previous == 0f) return "—"
        val change = (byMonth.values[index] - previous) / previous * 100f
        return "%+.0f%%".format(change)
    }

    fun format(value: Float, metric: String): String = when {
        metric == "orders" -> "${value.roundToInt()}"
        value >= 1_000_000f -> "₩%.1fM".format(value / 1_000_000f)
        value >= 1_000f -> "₩%.0fK".format(value / 1_000f)
        else -> "₩${value.roundToInt()}"
    }
}
