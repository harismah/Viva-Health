package com.noop.ui.viva

import com.noop.ui.Destination

/** The score a screen already showed, passed to the screen it opens so the two cannot disagree. */
data class VivaHandedScore(val value: Double?)

/**
 * The switch for the redesigned Viva shell: the home layout in place of the Today screen's own, and
 * the Home / Health / More pill with its coach button in place of the five-tab bar. One flag, read
 * by both, so the app is never half in each look.
 */
object VivaShell {
    /** A plain property, not a constant, so the old layout stays compiled and reachable for comparison. */
    var enabled: Boolean = true

    /** Routes of the Viva screens that have no entry in the app's own destination list. */
    const val HEALTH_MONITOR = "viva_health_monitor"
    const val STRESS_MONITOR = "viva_stress_monitor"
    const val SLEEP_PLANNER = "viva_sleep_planner"
    /** The full health report keeps the tab pill, so it is deliberately outside the `viva_` prefix. */
    const val FULL_HEALTH = "health_full"
    const val TREND = "viva_trend/{metric}"

    fun trendRoute(metric: VivaTrendMetric): String = "viva_trend/${metric.id}"

    /** The pillar detail screens. Each takes the score the home ring showed, and the day it showed it for. */
    const val SLEEP = "viva_sleep?score={score}"
    const val RECOVERY = "viva_recovery?day={day}&score={score}"
    const val STRAIN = "viva_strain?day={day}&score={score}"

    fun sleepRoute(score: Double?): String = "viva_sleep?score=${scoreArgument(score)}"
    fun recoveryRoute(dayKey: String, score: Double?): String = "viva_recovery?day=$dayKey&score=${scoreArgument(score)}"
    fun strainRoute(dayKey: String, score: Double?): String = "viva_strain?day=$dayKey&score=${scoreArgument(score)}"

    private const val NO_SCORE = "none"

    /** A handed-over score as a route argument; a ring that showed no score says so explicitly. */
    fun scoreArgument(score: Double?): String = score?.takeIf { it.isFinite() }?.toString() ?: NO_SCORE

    /**
     * Reads [scoreArgument] back. Null means nothing was handed over, so the screen reads the stored
     * score for itself; a [VivaHandedScore] holding null means the ring that opened it had no score,
     * and the screen must not find one of its own to show in its place.
     */
    fun handedScore(argument: String?): VivaHandedScore? = when (argument) {
        null -> null
        NO_SCORE -> VivaHandedScore(null)
        else -> argument.toDoubleOrNull()?.let { VivaHandedScore(it) }
    }

    private val routes: Map<Int, String> = mapOf(
        0 to Destination.Today.route,
        1 to Destination.Health.route,
        4 to Destination.More.route,
    )

    /** The pill tab that owns [route], or -1 when the screen belongs to no tab (nothing highlighted). */
    fun tabFor(route: String?): Int = routes.entries.firstOrNull { it.value == route }?.key ?: -1

    fun routeFor(tag: Int): String? = routes[tag]

    /**
     * Pushed Viva screens carry their own back button and take the whole screen, as in the
     * reference app; every other screen keeps the tab pill so it is never a dead end.
     */
    fun showsTabBar(route: String?): Boolean = route == null || !route.startsWith("viva_")
}
