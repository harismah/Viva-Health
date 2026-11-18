package com.noop.ui.viva

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AirlineSeatReclineExtra
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.HotTub
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/**
 * What an activity is, for the icon and colour every Viva surface marks it with: Home's activity rows and
 * the stress chart's spans read this one table, so a kind added here shows on both.
 */
enum class VivaActivityKind(val icon: ImageVector, val color: Color) {
    SLEEP(Icons.Outlined.NightsStay, VivaStyle.sleep),
    NAP(Icons.Outlined.AirlineSeatReclineExtra, VivaStyle.sleep),
    SAUNA(Icons.Outlined.HotTub, VivaStyle.strain),
    STRENGTH(Icons.Outlined.FitnessCenter, VivaStyle.strain),
    WALK(Icons.AutoMirrored.Outlined.DirectionsWalk, VivaStyle.strain),
    CYCLING(Icons.Outlined.DirectionsBike, VivaStyle.strain),
    SWIM(Icons.Outlined.Pool, VivaStyle.strain),
    WORKOUT(Icons.AutoMirrored.Outlined.DirectionsRun, VivaStyle.strain),
    ;

    companion object {
        /** The kind a logged activity's sport name names; anything unrecognised is a plain [WORKOUT]. */
        fun forSport(sport: String): VivaActivityKind {
            val s = sport.lowercase(Locale.US)
            return when {
                "sauna" in s -> SAUNA
                VivaDashboard.isStrength(sport) -> STRENGTH
                "walk" in s || "hik" in s -> WALK
                "cycl" in s || "bik" in s -> CYCLING
                "swim" in s -> SWIM
                else -> WORKOUT
            }
        }
    }
}
