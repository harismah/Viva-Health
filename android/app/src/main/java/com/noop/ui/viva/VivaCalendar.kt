package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Viva's day picker, laid out after the reference's: a panel dropping from the top over a dimmed page,
 * the month with arrows, Monday-first weekday columns, each past day's number in its recovery colour,
 * future days greyed and not tappable, the picked day ringed, and the recovery bands as a legend.
 */
@Composable
fun VivaCalendarPanel(
    selected: LocalDate,
    today: LocalDate,
    recovery: Map<String, Double>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val locale = Locale.getDefault()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = VivaStyle.cardRadius, bottomEnd = VivaStyle.cardRadius))
                    .background(VivaStyle.card)
                    // Swallow taps on the panel itself so only the dimmed page dismisses.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MonthArrow(Icons.Rounded.ChevronLeft, "Previous month", enabled = true) { month = month.minusMonths(1) }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale) +
                            if (month.year != today.year) " ${month.year}" else "",
                        Modifier.weight(1f), style = VivaFont.bodyStrong, color = VivaStyle.textPrimary, textAlign = TextAlign.Center,
                    )
                    MonthArrow(Icons.Rounded.ChevronRight, "Next month", enabled = month < YearMonth.from(today)) { month = month.plusMonths(1) }
                }
                Row(Modifier.fillMaxWidth()) {
                    DayOfWeek.values().forEach { d ->
                        Text(
                            d.getDisplayName(TextStyle.SHORT, locale).uppercase(locale), Modifier.weight(1f),
                            style = VivaFont.label, color = VivaStyle.textSecondary, textAlign = TextAlign.Center,
                        )
                    }
                }
                monthWeeks(month).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                                if (date != null) DayCell(date, date == selected, date <= today, recovery[date.toString()]) { onPick(date) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Legend(VivaStyle.recoveryRed, "<34%")
                    Legend(VivaStyle.recoveryYellow, "34% - 66%")
                    Legend(VivaStyle.recoveryGreen, ">66%")
                }
            }
        }
    }
}

/** The month as Monday-first weeks, null for the blank cells before the 1st and after the last day. */
internal fun monthWeeks(month: YearMonth): List<List<LocalDate?>> {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val cells = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return cells.chunked(7).map { it + List(7 - it.size) { null } }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, past: Boolean, recovery: Double?, onClick: () -> Unit) {
    val color = when {
        !past -> VivaStyle.textTertiary
        recovery == null -> VivaStyle.textSecondary
        else -> VivaStyle.recoveryColor(recovery)
    }
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .then(if (selected) Modifier.background(VivaStyle.inset).border(1.5.dp, VivaStyle.textTertiary, CircleShape) else Modifier)
            .then(if (past) Modifier.vivaPressable(onClick).semantics { role = Role.Button } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = VivaFont.number(15), color = color)
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .then(if (enabled) Modifier.vivaPressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) VivaStyle.textPrimary else VivaStyle.textTertiary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(text, style = VivaFont.caption, color = color)
    }
}
