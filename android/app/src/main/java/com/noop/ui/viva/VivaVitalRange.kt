package com.noop.ui.viva

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Where one vital sits against the wearer's last 30 days, in the reference app's terms: a typical
 * range, a chip that says within / near / low / elevated, and a green, orange or red band.
 *
 * The typical range is the middle half of the window (25th to 75th percentile, displayed night
 * included). That rule reproduced the reference app's ranges on three days of one wearer's data.
 * The chip names the cut-off rounded as the tile shows it (`elevated > 67` for an orange band that
 * starts at 67), as the reference app's does.
 *
 * shortcut: the orange and red cut-offs are offsets from the window's median fitted to one wearer's
 * 17 Nov 2026 bands (resting HR also matched an earlier fit); a spread-based rule did not reproduce
 * all five, so the reference app's own rule is not known. Refit when another day or wearer differs.
 */
internal object VivaVitalRange {
    enum class Level { GREEN, ORANGE, RED }

    class Band(
        val level: Level,
        /** The chip: `within 55 - 60`, `near 60`, `low < 30`, `elevated > 67`. */
        val chip: String,
        /** The info dialog's text: the reading against its typical range, then the three bands; paragraphs split on a blank line. */
        val detail: String,
    )

    /** [limits] maps the window's median to the raw cut-offs where orange and red begin; red is null when there is no red band. */
    private class Rule(
        val name: String,
        val decimals: Int,
        val highIsBad: Boolean,
        val fixedRange: ClosedFloatingPointRange<Double>? = null,
        val limits: (Double) -> Pair<Double, Double?>,
    )

    private val rules = mapOf(
        "resp" to Rule("respiratory rate", 1, highIsBad = true) { it + 1.05 to it + 1.55 },
        "rhr" to Rule("resting heart rate", 0, highIsBad = true) { it + 10.7 to it + 14.7 },
        "hrv" to Rule("HRV", 0, highIsBad = false) { it * 0.643 to it * 0.5 },
        "skin" to Rule("skin temperature", 1, highIsBad = true) { it + 1.1 to it + 1.5 },
        // Blood oxygen is judged against the same fixed range for everyone, as in the reference app.
        "spo2" to Rule("blood oxygen", 0, highIsBad = false, fixedRange = 95.0..100.0) { 89.5 to null },
    )

    const val WINDOW_DAYS = 30L

    /** Below this many nights the window says too little; the caller keeps its earlier banding. */
    const val MIN_NIGHTS = 14

    /** Linear-interpolated quantile of an ascending list. */
    fun quantile(sorted: List<Double>, p: Double): Double {
        val position = p * (sorted.size - 1)
        val below = floor(position).toInt()
        val above = minOf(below + 1, sorted.lastIndex)
        return sorted[below] + (position - below) * (sorted[above] - sorted[below])
    }

    /**
     * Bands [value]. [window] is every reading in the last [WINDOW_DAYS] days, [value]'s own night
     * included, in the same unit as [value]. [format] renders a number the way the tile shows it.
     * The dialog text may state the figures on another scale: each is shifted by [detailShift] and
     * rendered with [detailFormat] (skin temperature is banded as a deviation and explained in degrees). Null when the vital has no rule here or the
     * window is too short to judge by.
     */
    fun band(
        key: String,
        value: Double,
        window: List<Double>,
        unit: String,
        format: (Double) -> String,
        detailShift: Double = 0.0,
        detailFormat: (Double) -> String = format,
    ): Band? {
        val rule = rules[key] ?: return null
        if (rule.fixedRange == null && window.size < MIN_NIGHTS) return null
        val sorted = window.sorted()
        val scale = 10.0.pow(rule.decimals)
        val step = 1 / scale
        // Every comparison is made on the number as displayed, so a chip can never contradict its tile.
        fun shown(x: Double) = floor(x * scale + 0.5) / scale
        fun down(x: Double) = floor(x * scale + 1e-6) / scale
        fun up(x: Double) = ceil(x * scale - 1e-6) / scale
        val low = shown(rule.fixedRange?.start ?: quantile(sorted, 0.25))
        val high = shown(rule.fixedRange?.endInclusive ?: quantile(sorted, 0.75))
        val (orangeAt, redAt) = rule.limits(if (rule.fixedRange == null) quantile(sorted, 0.5) else 0.0)
        val v = shown(value)
        val suffix = if (unit.isEmpty()) "" else " $unit"
        fun d(x: Double) = detailFormat(x + detailShift) + suffix
        val span = if (key == "skin") " to " else " - "
        val where = when {
            v < low -> "below"
            v > high -> "above"
            else -> "within"
        }
        val reading = "Your ${rule.name} of ${d(v)} is $where " +
            "${if (rule.fixedRange == null) "your" else "the"} typical range of ${d(low)} to ${d(high)}."
        val basis = if (rule.fixedRange == null) "Your ranges are worked out from your last 30 days:" else "These ranges are the same for everyone:"

        if (rule.highIsBad) {
            // A reading inside the typical range is never flagged, however tight the limits fall.
            val orangeCut = maxOf(orangeAt, high)
            val greenTop = down(orangeCut)
            val redCut = redAt?.let { maxOf(it, greenTop + step) }
            val level = when {
                redCut != null && v > down(redCut) -> Level.RED
                v > greenTop -> Level.ORANGE
                else -> Level.GREEN
            }
            val chip = when {
                level != Level.GREEN -> "elevated > ${format(orangeCut)}"
                v > high -> "near ${format(high)}"
                v < low -> "low < ${format(low)}"
                else -> "within ${format(low)}$span${format(high)}"
            }
            val bands = "Green = ${d(greenTop)} and below\n" + (
                if (redCut == null) "Orange = ${d(greenTop + step)} and above"
                else "Orange = ${d(greenTop + step)} to ${d(down(redCut))}\nRed = ${d(down(redCut) + step)} and above"
            )
            return Band(level, chip, "$reading\n\n$basis\n$bands")
        }
        val orangeCut = minOf(orangeAt, low)
        val greenBottom = up(orangeCut)
        val redCut = redAt?.let { minOf(it, greenBottom - step) }
        val level = when {
            redCut != null && v < up(redCut) -> Level.RED
            v < greenBottom -> Level.ORANGE
            else -> Level.GREEN
        }
        val chip = when {
            level != Level.GREEN -> "low < ${format(orangeCut)}"
            v < low -> "near ${format(low)}"
            v > high -> "elevated > ${format(high)}"
            else -> "within ${format(low)}$span${format(high)}"
        }
        val bands = "Green = ${d(greenBottom)} and above\n" + (
            if (redCut == null) "Orange = ${d(greenBottom - step)} and below"
            else "Orange = ${d(up(redCut))} to ${d(greenBottom - step)}\nRed = ${d(up(redCut) - step)} and below"
        )
        return Band(level, chip, "$reading\n\n$basis\n$bands")
    }
}
