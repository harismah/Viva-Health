package com.noop.ui.viva

import kotlin.math.floor
import kotlin.math.pow

/**
 * Where one vital sits against the wearer's last 30 days, in the reference app's terms: a typical
 * range, a chip that says within / near / low / elevated, and a green, orange or red band.
 *
 * The typical range is the middle half of the window (25th to 75th percentile, displayed night
 * included). That rule reproduced the reference app's ranges on two consecutive days of one
 * wearer's data for respiratory rate, resting heart rate and skin temperature, and to within 1 ms
 * for HRV.
 *
 * shortcut: the orange and red limits are offsets from the window's median fitted to those same two
 * days, one wearer; the reference app's own rule is not known. Refit when more days or a second
 * wearer can be compared.
 */
internal object VivaVitalRange {
    enum class Level { GREEN, ORANGE, RED }

    class Band(
        val level: Level,
        /** The chip: `within 55 - 60`, `near 60`, `low < 55`, `elevated > 17.9`. */
        val chip: String,
        /** The typical range and the three bands, spelled out for the info dialog. */
        val detail: String,
    )

    /** [limits] maps the window's median to where orange and red begin; red is null when there is no red band. */
    private class Rule(
        val decimals: Int,
        val highIsBad: Boolean,
        val fixedRange: ClosedFloatingPointRange<Double>? = null,
        val limits: (Double) -> Pair<Double, Double?>,
    )

    private val rules = mapOf(
        "resp" to Rule(1, highIsBad = true) { it + 0.88 to it + 1.27 },
        "rhr" to Rule(0, highIsBad = true) { it + 10.5 to it + 14.5 },
        "hrv" to Rule(0, highIsBad = false) { it * 0.625 to it * 0.48 },
        "skin" to Rule(1, highIsBad = true) { it + 1.0 to it + 1.4 },
        // Blood oxygen is judged against the same fixed range for everyone, as in the reference app.
        "spo2" to Rule(0, highIsBad = false, fixedRange = 95.0..100.0) { 90.0 to null },
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
     * Null when the vital has no rule here or the window is too short to judge by.
     */
    fun band(key: String, value: Double, window: List<Double>, unit: String, format: (Double) -> String): Band? {
        val rule = rules[key] ?: return null
        if (rule.fixedRange == null && window.size < MIN_NIGHTS) return null
        val sorted = window.sorted()
        val scale = 10.0.pow(rule.decimals)
        val step = 1 / scale
        // Every comparison is made on the number as displayed, so a chip can never contradict its tile.
        fun shown(x: Double) = floor(x * scale + 0.5) / scale
        val low = shown(rule.fixedRange?.start ?: quantile(sorted, 0.25))
        val high = shown(rule.fixedRange?.endInclusive ?: quantile(sorted, 0.75))
        val (orangeAt, redAt) = rule.limits(if (rule.fixedRange == null) quantile(sorted, 0.5) else 0.0)
        val v = shown(value)
        val suffix = if (unit.isEmpty()) "" else " $unit"
        val typical = "Typical range: ${format(low)} to ${format(high)}$suffix" +
            if (rule.fixedRange == null) ", the middle half of your last 30 days." else "."

        if (rule.highIsBad) {
            // A reading inside the typical range is never flagged, however tight the limits fall.
            val orange = maxOf(shown(orangeAt), shown(high + step))
            val red = redAt?.let { maxOf(shown(it), shown(orange + step)) }
            val level = when {
                red != null && v >= red -> Level.RED
                v >= orange -> Level.ORANGE
                else -> Level.GREEN
            }
            val chip = when {
                level != Level.GREEN -> "elevated > ${format(orange - step)}"
                v > high -> "near ${format(high)}"
                v < low -> "low < ${format(low)}"
                else -> "within ${format(low)} - ${format(high)}"
            }
            val bands = "Green: ${format(orange - step)} and below." + (
                if (red == null) " Orange: ${format(orange)} and above."
                else " Orange: ${format(orange)} to ${format(red - step)}. Red: ${format(red)} and above."
            )
            return Band(level, chip, "$typical\n$bands")
        }
        val orange = minOf(shown(orangeAt), low)
        val red = redAt?.let { minOf(shown(it), shown(orange - step)) }
        val level = when {
            red != null && v < red -> Level.RED
            v < orange -> Level.ORANGE
            else -> Level.GREEN
        }
        val chip = when {
            level != Level.GREEN -> "low < ${format(orange)}"
            v < low -> "near ${format(low)}"
            v > high -> "elevated > ${format(high)}"
            else -> "within ${format(low)} - ${format(high)}"
        }
        val bands = "Green: ${format(orange)} and above." + (
            if (red == null) " Orange: ${format(orange - step)} and below."
            else " Orange: ${format(red)} to ${format(orange - step)}. Red: ${format(red - step)} and below."
        )
        return Band(level, chip, "$typical\n$bands")
    }
}
