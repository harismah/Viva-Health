package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.ui.BrandMark
import java.util.Locale

// The Viva home screen as plain values and layout: the Android twin of
// Strand/Screens/Home/VivaHomeContent.swift. The host screen owns loading, day navigation and
// sheets and maps its state into [VivaHomeData], so everything below is free of data access and
// reads top to bottom as the screen.

/** Everything the Viva home renders. */
data class VivaHomeData(
    val dayTitle: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val sleepPercent: Double?,
    val recoveryPercent: Double?,
    /** On the 0-21 scale. */
    val strain: Double?,
    val sleepDuration: String?,
    val healthStatus: String,
    /** On the 0-3 scale. */
    val stress: Double?,
    val stressLabel: String,
    val activities: List<Activity>,
    val alarm: VivaAlarmReadout,
    val isToday: Boolean,
    val coachEnabled: Boolean,
    /** Nights recorded so far while scores are still calibrating; null once calibrated. */
    val calibrationNights: Int?,
    val metrics: List<Metric>,
    val week: List<WeekDay>,
    val journal: List<JournalDay>,
) {
    data class Activity(
        val id: String,
        val icon: ImageVector,
        val title: String,
        val value: String,
        val start: String?,
        val end: String?,
        val color: Color,
    )

    data class Metric(
        val id: String,
        val icon: ImageVector,
        val label: String,
        val value: String,
        val unit: String?,
        val baseline: String?,
        val trend: VivaTrend,
        /** False when the metric has no detail screen, so its card is not tappable. */
        val opens: Boolean,
    )

    data class WeekDay(
        val id: String,
        val label: String,
        val date: Int,
        val strain: Double?,
        val recovery: Double?,
        val isToday: Boolean,
    )

    data class JournalDay(val key: String, val label: String, val logged: Boolean, val isToday: Boolean)
}

/** The screens Home pushes. */
sealed interface VivaHomeRoute {
    data object Sleep : VivaHomeRoute
    data object HealthMonitor : VivaHomeRoute
    data object StressMonitor : VivaHomeRoute
    data object SleepPlanner : VivaHomeRoute
    data object BehaviorInsights : VivaHomeRoute
    data object Journal : VivaHomeRoute
    data object AllMetrics : VivaHomeRoute

    /** Recovery and Strain carry the score Home is showing, so the detail ring matches it exactly. */
    data class Recovery(val score: Double?) : VivaHomeRoute
    data class Strain(val score: Double?) : VivaHomeRoute
    data class Metric(val id: String) : VivaHomeRoute
    data class Activity(val id: String) : VivaHomeRoute
}

/** What Home asks its host to do. */
class VivaHomeActions(
    val onPreviousDay: () -> Unit = {},
    val onNextDay: () -> Unit = {},
    val onPickDay: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onStartActivity: () -> Unit = {},
    val onCoach: () -> Unit = {},
    val onCustomize: () -> Unit = {},
    val onOpen: (VivaHomeRoute) -> Unit = {},
)

/**
 * The home column. It does not scroll by itself; the host places it in its own scroll container.
 * [avatar] and [battery] host the app's existing profile and strap controls so their behaviour is
 * unchanged.
 */
@Composable
fun VivaHomeContent(
    data: VivaHomeData,
    actions: VivaHomeActions,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = {},
    battery: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
        TopBar(data, actions, avatar, battery)
        Text(
            "VIVA",
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clearAndSetSemantics {},
            style = VivaFont.wordmark.copy(letterSpacing = 5.sp),
            color = VivaStyle.textPrimary,
            textAlign = TextAlign.Center,
        )
        Rings(data, actions.onOpen, Modifier.padding(vertical = 6.dp))
        if (data.sleepDuration != null) SleepPrompt(data.sleepDuration) { actions.onOpen(VivaHomeRoute.Sleep) }
        Monitors(data, actions.onOpen)

        VivaSectionHeader(
            "My Day",
            modifier = Modifier.padding(top = VivaStyle.sectionGap - VivaStyle.cardGap),
            action = VivaSectionAction("Add", VivaSectionAction.Style.PLUS, actions.onAdd),
        )
        if (data.coachEnabled) AskRow(actions.onCoach)
        ActivitiesCard(data, actions)
        if (data.isToday) {
            TonightCard(data.alarm) { actions.onOpen(VivaHomeRoute.SleepPlanner) }
            VivaJournalWeekCard(
                days = data.journal,
                onOpen = { actions.onOpen(VivaHomeRoute.Journal) },
                onInsights = { actions.onOpen(VivaHomeRoute.BehaviorInsights) },
            )
        }
        data.calibrationNights?.let { CalibrationCard(it) }

        VivaSectionHeader(
            "My Dashboard",
            modifier = Modifier.padding(top = VivaStyle.sectionGap - VivaStyle.cardGap),
            action = VivaSectionAction("Customize", VivaSectionAction.Style.CHIP, actions.onCustomize),
        )
        data.metrics.forEach { metric ->
            VivaCard(
                padding = 14.dp,
                onClick = if (metric.opens) ({ actions.onOpen(VivaHomeRoute.Metric(metric.id)) }) else null,
            ) {
                VivaMetricRow(metric.icon, metric.label, metric.value, unit = metric.unit, baseline = metric.baseline, trend = metric.trend)
            }
        }
        VivaCard(padding = 14.dp, onClick = { actions.onOpen(VivaHomeRoute.AllMetrics) }) {
            VivaMetricRow(Icons.Outlined.GridView, "All metrics", "", showsChevron = true)
        }
        VivaCard(modifier = Modifier.padding(top = 6.dp), title = "Strain & Recovery") {
            if (data.week.all { it.strain == null && it.recovery == null }) {
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp), contentAlignment = Alignment.Center) {
                    VivaLabel("No data", color = VivaStyle.textPrimary)
                }
            } else {
                VivaStrainRecoveryChart(data.week)
            }
        }
    }
}

// Top bar

@Composable
private fun TopBar(data: VivaHomeData, actions: VivaHomeActions, avatar: @Composable () -> Unit, battery: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            avatar()
            Spacer(Modifier.weight(1f))
            battery()
        }
        val shape = RoundedCornerShape(9.dp)
        Row(Modifier.background(VivaStyle.chip, shape), verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Rounded.ChevronLeft, "Previous day", data.canGoBack, actions.onPreviousDay)
            Box(
                Modifier
                    .height(28.dp)
                    .clip(shape)
                    .vivaPressable(actions.onPickDay)
                    .background(VivaStyle.chipActive)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(data.dayTitle.uppercase(), style = VivaFont.action, color = VivaStyle.textPrimary, maxLines = 1)
            }
            StepButton(Icons.Rounded.ChevronRight, "Next day", data.canGoForward, actions.onNextDay)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 30.dp, height = 28.dp)
            .then(if (enabled) Modifier.vivaPressable(onClick) else Modifier)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) VivaStyle.textPrimary else VivaStyle.textTertiary, modifier = Modifier.size(18.dp))
    }
}

// Rings

@Composable
private fun Rings(data: VivaHomeData, onOpen: (VivaHomeRoute) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RingButton("Sleep", { onOpen(VivaHomeRoute.Sleep) }) {
            VivaScoreRing(data.sleepPercent, 100.0, VivaStyle.sleep, VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL, label = "Sleep")
        }
        RingButton("Recovery", { onOpen(VivaHomeRoute.Recovery(data.recoveryPercent)) }) {
            VivaScoreRing(
                data.recoveryPercent, 100.0, VivaStyle.recoveryColor(data.recoveryPercent),
                VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL, label = "Recovery",
            )
        }
        RingButton("Strain", { onOpen(VivaHomeRoute.Strain(data.strain)) }) {
            VivaScoreRing(data.strain, 21.0, VivaStyle.strain, VivaScoreRing.Format.DECIMAL1, VivaScoreRing.Size.SMALL, label = "Strain")
        }
    }
}

@Composable
private fun RowScope.RingButton(label: String, onClick: () -> Unit, ring: @Composable () -> Unit) {
    Column(
        Modifier.weight(1f).vivaPressable(onClick).semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ring()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            VivaCardTitle(label)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

/** The three pillar rings in miniature, pinned under the status bar once the hero trio scrolls away. */
@Composable
fun VivaHomePinnedRings(data: VivaHomeData, onOpen: (VivaHomeRoute) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(VivaStyle.canvasTop).height(40.dp).padding(horizontal = VivaStyle.pagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PinnedRing("Sleep", { onOpen(VivaHomeRoute.Sleep) }) {
            VivaScoreRing(data.sleepPercent, 100.0, VivaStyle.sleep, VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.MINI)
        }
        PinnedRing("Recovery", { onOpen(VivaHomeRoute.Recovery(data.recoveryPercent)) }) {
            VivaScoreRing(data.recoveryPercent, 100.0, VivaStyle.recoveryColor(data.recoveryPercent), VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.MINI)
        }
        PinnedRing("Strain", { onOpen(VivaHomeRoute.Strain(data.strain)) }) {
            VivaScoreRing(data.strain, 21.0, VivaStyle.strain, VivaScoreRing.Format.DECIMAL1, VivaScoreRing.Size.MINI)
        }
    }
}

@Composable
private fun RowScope.PinnedRing(label: String, onClick: () -> Unit, ring: @Composable () -> Unit) {
    Row(
        Modifier.weight(1f).vivaPressable(onClick).semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        ring()
        VivaCardTitle(label)
    }
}

// Prompt and monitors

@Composable
private fun SleepPrompt(duration: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VivaStyle.cardRadius))
            .vivaPressable(onClick)
            .background(VivaStyle.inset)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How did you sleep?", style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
            Text("You were in bed for $duration. See how the night broke down.", style = VivaFont.caption, color = VivaStyle.textSecondary)
            VivaLinkLabel("Open sleep details", Modifier.padding(top = 2.dp), color = VivaStyle.magenta)
        }
        Box(Modifier.padding(end = 8.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Box(Modifier.size(58.dp).rotate(12f).background(VivaStyle.chipActive, RoundedCornerShape(14.dp)))
            Icon(Icons.Outlined.NightsStay, contentDescription = null, tint = VivaStyle.magenta, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun Monitors(data: VivaHomeData, onOpen: (VivaHomeRoute) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap), verticalAlignment = Alignment.Top) {
        VivaCard(Modifier.weight(1f), title = "Health Monitor", accessory = VivaCardAccessory.CHEVRON, onClick = { onOpen(VivaHomeRoute.HealthMonitor) }) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp).background(VivaStyle.chipActive, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(6.dp).background(VivaStyle.textSecondary, CircleShape))
                }
                Text(data.healthStatus, style = VivaFont.caption, color = VivaStyle.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        VivaCard(Modifier.weight(1f), title = "Stress Monitor", accessory = VivaCardAccessory.CHEVRON, onClick = { onOpen(VivaHomeRoute.StressMonitor) }) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(width = 28.dp, height = 24.dp).background(VivaStyle.chipActive, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        data.stress?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        style = VivaFont.number(13), color = VivaStyle.textPrimary, maxLines = 1,
                    )
                }
                VivaCardTitle(data.stressLabel, maxLines = 1)
            }
        }
    }
}

// My Day

@Composable
private fun AskRow(onClick: () -> Unit) {
    VivaCard(padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(20.dp).border(1.dp, VivaStyle.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
                BrandMark(size = 10.dp)
            }
            Text("Ask a question, get support...", Modifier.weight(1f), style = VivaFont.bodyStrong, color = VivaStyle.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ActivitiesCard(data: VivaHomeData, actions: VivaHomeActions) {
    VivaCard(title = if (data.isToday) "Today's Activities" else "Activities", padding = 12.dp) {
        if (data.activities.isEmpty()) {
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp), contentAlignment = Alignment.CenterStart) {
                Text("Nothing logged yet", style = VivaFont.caption, color = VivaStyle.textTertiary)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.activities.forEach { activity ->
                    ActivityRow(activity) { actions.onOpen(VivaHomeRoute.Activity(activity.id)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
            VivaCardButton("Add activity", Modifier.weight(1f), icon = Icons.Rounded.Add, onClick = actions.onAdd)
            VivaCardButton("Start activity", Modifier.weight(1f), icon = Icons.Outlined.Timer, onClick = actions.onStartActivity)
        }
    }
}

@Composable
private fun ActivityRow(activity: VivaHomeData.Activity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .vivaPressable(onClick)
            .background(VivaStyle.chip)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.size(width = 92.dp, height = 38.dp).background(activity.color, RoundedCornerShape(7.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(activity.icon, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(17.dp))
            Text(activity.value, style = VivaFont.number(15), color = VivaStyle.textPrimary, maxLines = 1)
        }
        VivaCardTitle(activity.title, Modifier.weight(1f), maxLines = 1)
        if (activity.start != null || activity.end != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val timeStyle = VivaFont.number(10, FontWeight.Medium)
                Text(activity.start.orEmpty(), style = timeStyle, color = VivaStyle.textSecondary, maxLines = 1)
                Text(activity.end.orEmpty(), style = timeStyle, color = VivaStyle.textSecondary, maxLines = 1)
            }
            Box(Modifier.size(width = 1.5.dp, height = 26.dp).background(activity.color))
        }
    }
}

@Composable
private fun TonightCard(alarm: VivaAlarmReadout, onClick: () -> Unit) {
    VivaCard(title = "Tonight's Sleep", accessory = VivaCardAccessory.CHEVRON, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PlanColumn(Icons.Outlined.Bed, alarm.bedtimeText, "Recommended bedtime", status = null, armed = alarm.isArmed)
            Box(Modifier.padding(top = 12.dp).size(width = 44.dp, height = 1.dp).background(VivaStyle.divider))
            PlanColumn(Icons.Outlined.Alarm, alarm.wakeText, alarm.modeText, status = alarm.statusText, armed = alarm.isArmed)
        }
        // Drawn as a label, not a second button: the whole card already opens the planner.
        Row(
            Modifier.fillMaxWidth().height(40.dp).background(VivaStyle.chip, RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(15.dp))
            Text("EDIT ALARM", style = VivaFont.action, color = VivaStyle.textPrimary)
        }
    }
}

@Composable
private fun RowScope.PlanColumn(icon: ImageVector, time: String, caption: String, status: String?, armed: Boolean) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(16.dp))
            Text(time, style = VivaFont.number(19), color = VivaStyle.textPrimary, maxLines = 1)
        }
        if (status != null) {
            val color = if (armed) VivaStyle.positive else VivaStyle.warning
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(5.dp).background(color, CircleShape))
                VivaLabel(status, color = color)
            }
        }
        Text(
            caption.uppercase(), Modifier.widthIn(max = 110.dp),
            style = VivaFont.label, color = VivaStyle.textSecondary, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CalibrationCard(nights: Int) {
    val recorded = nights.coerceIn(0, 7)
    VivaCard(title = "Calibration timeline") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Your scores sharpen as Viva learns your baseline. $nights of 7 nights recorded.",
                Modifier.weight(1f), style = VivaFont.caption, color = VivaStyle.textSecondary,
            )
            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(50.dp)) {
                    val stroke = 3.dp.toPx()
                    val inset = Offset(stroke / 2f, stroke / 2f)
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        VivaStyle.ringTrack, 0f, 360f, useCenter = false, topLeft = inset, size = arc,
                        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 4.dp.toPx()))),
                    )
                    if (recorded > 0) {
                        drawArc(
                            VivaStyle.magenta, -90f, 360f * recorded / 7f, useCenter = false, topLeft = inset, size = arc,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Text("$recorded/7", style = VivaFont.number(13), color = VivaStyle.textPrimary)
            }
        }
    }
}

/** The week strip of journal completion with the entry point to behaviour insights. */
@Composable
fun VivaJournalWeekCard(days: List<VivaHomeData.JournalDay>, onOpen: () -> Unit, onInsights: () -> Unit, modifier: Modifier = Modifier) {
    VivaCard(modifier, title = "My Journal", accessory = VivaCardAccessory.CHEVRON, onClick = onOpen) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            days.forEach { day ->
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "${day.label}: ${if (day.logged) "Logged" else "Not logged"}"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VivaLabel(day.label, color = if (day.isToday) VivaStyle.textPrimary else VivaStyle.textTertiary)
                    val fill = when {
                        day.logged -> VivaStyle.positive
                        day.isToday -> VivaStyle.textTertiary
                        else -> Color.Transparent
                    }
                    val rim = if (day.isToday) VivaStyle.textPrimary else VivaStyle.textTertiary.copy(alpha = 0.6f)
                    Box(
                        Modifier.size(20.dp).background(fill, CircleShape)
                            .then(if (day.logged) Modifier else Modifier.border(1.dp, rim, CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // A logged day carries a tick, so it reads as done without relying on colour.
                        if (day.logged) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = VivaStyle.canvasBottom, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        VivaCardButton("Behavior insights", icon = Icons.Outlined.Lightbulb, onClick = onInsights)
    }
}

/** Seven days of strain (blue line, left 0-21 axis) against recovery (banded dots, right % axis). */
@Composable
fun VivaStrainRecoveryChart(days: List<VivaHomeData.WeekDay>, modifier: Modifier = Modifier) {
    val chartHeight = 150.dp
    val measurer = rememberTextMeasurer()
    val markerText = VivaFont.number(9)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartAxis(listOf("21", "14", "7", "0"), List(4) { VivaStyle.strain })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                val step = size.width / maxOf(days.size, 1)
                fun x(index: Int) = step * (index + 0.5f)
                fun y(share: Double) = size.height * (1f - share.coerceIn(0.0, 1.0).toFloat())

                repeat(4) { index ->
                    val lineY = size.height * index / 3f
                    drawLine(VivaStyle.divider.copy(alpha = 0.7f), Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 0.5.dp.toPx())
                }
                val today = days.indexOfFirst { it.isToday }
                if (today >= 0) {
                    val band = 22.dp.toPx()
                    drawRect(VivaStyle.chip.copy(alpha = 0.75f), Offset(x(today) - band / 2f, 0f), Size(band, size.height))
                }

                val line = Path()
                var started = false
                days.forEachIndexed { index, day ->
                    val strain = day.strain ?: return@forEachIndexed
                    if (started) line.lineTo(x(index), y(strain / 21.0)) else { line.moveTo(x(index), y(strain / 21.0)); started = true }
                }
                drawPath(line, VivaStyle.strain, style = Stroke(1.5.dp.toPx()))

                fun marker(centre: Offset, color: Color, text: String?) {
                    val radius = 3.5.dp.toPx()
                    drawCircle(VivaStyle.card, radius, centre)
                    drawCircle(color, radius, centre, style = Stroke(1.5.dp.toPx()))
                    if (text != null) {
                        val layout = measurer.measure(text, markerText.copy(color = color), maxLines = 1)
                        drawText(layout, topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - 5.5.dp.toPx() - layout.size.height))
                    }
                }
                days.forEachIndexed { index, day ->
                    day.strain?.let { marker(Offset(x(index), y(it / 21.0)), VivaStyle.strain, String.format(Locale.US, "%.1f", it)) }
                    day.recovery?.let {
                        val text = if (day.isToday) "${VivaScoreRing.roundHalfAwayFromZero(it)}%" else null
                        marker(Offset(x(index), y(it / 100.0)), VivaStyle.recoveryColor(it), text)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    val style = TextStyle(fontSize = 9.sp, fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal)
                    val color = if (day.isToday) VivaStyle.textPrimary else VivaStyle.textTertiary
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(day.label, style = style, color = color, maxLines = 1)
                        Text(day.date.toString(), style = style, color = color, maxLines = 1)
                    }
                }
            }
        }
        ChartAxis(
            listOf("100%", "66%", "33%", "0%"),
            listOf(VivaStyle.recoveryGreen, VivaStyle.recoveryYellow, VivaStyle.recoveryRed, VivaStyle.recoveryRed),
        )
    }
}

@Composable
private fun ChartAxis(labels: List<String>, colors: List<Color>) {
    Column(Modifier.height(156.dp).padding(bottom = 3.dp), verticalArrangement = Arrangement.SpaceBetween) {
        val style = VivaFont.number(8, FontWeight.SemiBold)
        labels.forEachIndexed { index, label -> Text(label, style = style, color = colors[index], maxLines = 1) }
    }
}
