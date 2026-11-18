package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.unit.Dp
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
    /** The vitals' one verdict ([VivaVitals.overall]) when today's are banded; null leaves the mark neutral. */
    val healthState: VivaVitalState? = null,
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
    /** The reference's dashboard rows that follow the stress and strain cards. */
    val moreMetrics: List<Metric> = emptyList(),
    val week: List<WeekDay>,
    val journal: List<JournalDay>,
    /**
     * The earlier day a carried Sleep or Recovery score belongs to. Its ring is captioned with that
     * night, so an older score is never passed off as today's.
     */
    val sleepCarriedDay: String? = null,
    val recoveryCarriedDay: String? = null,
    /** Today's stress stretches on the 0-3 scale, oldest first, with the clock times of the first and the newest. */
    val stressTrace: List<Double> = emptyList(),
    val stressStart: String? = null,
    val stressUpdated: String? = null,
    val stressTicks: List<Pair<Float, String>> = emptyList(),
    /** The sleep and activity stretches of [stressTrace]. */
    val stressSpans: List<VivaChartSpan> = emptyList(),
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

    /** [joinsNext]: this day and the next are one day still open (awake past midnight), drawn as one pill. */
    data class JournalDay(
        val key: String, val label: String, val logged: Boolean, val isToday: Boolean, val joinsNext: Boolean = false,
    )
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
 * [suggestion] is the "looks like a workout" card, when there is one to show. [avatar] and [battery] host the app's existing profile and strap controls so their behaviour is
 * unchanged.
 */
@Composable
fun VivaHomeContent(
    data: VivaHomeData,
    actions: VivaHomeActions,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = {},
    battery: @Composable () -> Unit = {},
    suggestion: @Composable () -> Unit = {},
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
        if (data.sleepDuration != null) {
            HomePrompt(
                "How did you sleep?", "You were in bed for ${data.sleepDuration}. See how the night broke down.",
                "Open sleep details", Icons.Outlined.NightsStay,
            ) { actions.onOpen(VivaHomeRoute.Sleep) }
        } else if (data.isToday) {
            // A day with no sleep to review: the nudge towards logging an activity instead.
            HomePrompt(
                "Ready to get moving?", "Start an activity and come back to see how it adds to today's Strain.",
                "Start activity", Icons.Outlined.Timer, actions.onStartActivity,
            )
        }
        suggestion()
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
        )
        data.metrics.forEach { metric -> DashboardRow(metric, actions) }
        if (data.stressTrace.size > 1) {
            VivaCard(
                modifier = Modifier.padding(top = 6.dp), title = "Stress Monitor", accessory = VivaCardAccessory.CHEVRON,
                onClick = { actions.onOpen(VivaHomeRoute.StressMonitor) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Last updated ${data.stressUpdated.orEmpty()}",
                        modifier = Modifier.weight(1f), style = VivaFont.body, color = VivaStyle.textSecondary, maxLines = 1,
                    )
                    // The band name takes the colour the trace has at that height.
                    val bandColor = data.stress?.let { if (it >= 2.0) VivaStyle.recoveryYellow else if (it >= 1.0) VivaStyle.positive else VivaStyle.sleep }
                    VivaCardTitle(data.stressLabel, color = bandColor ?: VivaStyle.textSecondary, maxLines = 1)
                    Text(
                        data.stress?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        style = VivaFont.number(20), color = VivaStyle.textPrimary, maxLines = 1,
                    )
                }
                VivaLineChart(
                    values = data.stressTrace, color = VivaStyle.textPrimary, range = 0.0..3.0,
                    yLabels = listOf("3.0", "2.0", "1.0", "0.0"), startLabel = data.stressStart, endLabel = data.stressUpdated,
                    height = 200.dp, brush = VivaStress.lineBrush(), spans = data.stressSpans, stroke = 2.dp, nowMarker = true, ticks = data.stressTicks,
                )
            }
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
        data.moreMetrics.forEach { metric -> DashboardRow(metric, actions) }
        VivaCard(padding = 14.dp, onClick = { actions.onOpen(VivaHomeRoute.AllMetrics) }) {
            VivaMetricRow(Icons.Outlined.GridView, "All metrics", "", showsChevron = true)
        }
    }
}

@Composable
private fun DashboardRow(metric: VivaHomeData.Metric, actions: VivaHomeActions) {
    VivaCard(
        padding = 14.dp,
        onClick = if (metric.opens) ({ actions.onOpen(VivaHomeRoute.Metric(metric.id)) }) else null,
    ) {
        VivaMetricRow(metric.icon, metric.label, metric.value, unit = metric.unit, baseline = metric.baseline, trend = metric.trend)
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
            VivaStepButton(Icons.Rounded.ChevronLeft, "Previous day", data.canGoBack, actions.onPreviousDay)
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
            VivaStepButton(Icons.Rounded.ChevronRight, "Next day", data.canGoForward, actions.onNextDay)
        }
    }
}

@Composable
internal fun VivaStepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
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
        RingButton("Sleep", { onOpen(VivaHomeRoute.Sleep) }, note = data.sleepCarriedDay?.let { vivaCarriedNote(it) }) {
            VivaScoreRing(data.sleepPercent, 100.0, VivaStyle.sleep, VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL, label = "Sleep")
        }
        RingButton("Recovery", { onOpen(VivaHomeRoute.Recovery(data.recoveryPercent)) }, note = data.recoveryCarriedDay?.let { vivaCarriedNote(it) }) {
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
private fun RowScope.RingButton(label: String, onClick: () -> Unit, note: String? = null, ring: @Composable () -> Unit) {
    Column(
        Modifier.weight(1f).vivaPressable(onClick).semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ring()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                VivaCardTitle(label)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(14.dp))
            }
            if (note != null) {
                Text(note, style = VivaFont.baseline, color = VivaStyle.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
            }
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

/**
 * "Looks like a workout": a stretch of raised heart rate the app noticed, which the wearer saves as
 * an activity or waves away. The finding, the saving and the dismissing are the upstream card's.
 */
@Composable
fun VivaWorkoutSuggestion(prompt: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    VivaCard(title = "Looks like a workout") {
        Text(prompt, style = VivaFont.caption, color = VivaStyle.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VivaCardButton("Save it", Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.DirectionsRun, onClick = onSave)
            VivaCardButton("Not a workout", Modifier.weight(1f), onClick = onDismiss)
        }
    }
}

@Composable
private fun HomePrompt(title: String, body: String, link: String, icon: ImageVector, onClick: () -> Unit) {
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
            Text(title, style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
            Text(body, style = VivaFont.caption, color = VivaStyle.textSecondary)
            VivaLinkLabel(link, Modifier.padding(top = 2.dp), color = VivaStyle.magenta)
        }
        Box(Modifier.padding(end = 8.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Box(Modifier.size(58.dp).rotate(12f).background(VivaStyle.chipActive, RoundedCornerShape(14.dp)))
            Icon(icon, contentDescription = null, tint = VivaStyle.magenta, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun Monitors(data: VivaHomeData, onOpen: (VivaHomeRoute) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap), verticalAlignment = Alignment.Top) {
        VivaCard(Modifier.weight(1f), title = "Health Monitor", accessory = VivaCardAccessory.CHEVRON, onClick = { onOpen(VivaHomeRoute.HealthMonitor) }) {
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateMark(data.healthState ?: VivaVitalState.NO_READING, 24.dp)
                // The verdict in its colour over the count, as the reference's card reads: OUT OF RANGE / 4/5 Metrics.
                Column {
                    data.healthState?.let { VivaCardTitle(VivaVitals.overallLabel(it), color = VivaVitals.overallColor(it), maxLines = 1) }
                    Text(data.healthStatus, style = VivaFont.caption, color = VivaStyle.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        VivaCard(Modifier.weight(1f), title = "Stress Monitor", accessory = VivaCardAccessory.CHEVRON, onClick = { onOpen(VivaHomeRoute.StressMonitor) }) {
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tinted by band, as the reference's card is: low blue, medium green, high yellow.
                val tint = VivaHomeMapper.stressColor(data.stress)
                Box(
                    Modifier.size(width = 28.dp, height = 24.dp)
                        .background(if (data.stress == null) VivaStyle.chipActive else tint.copy(alpha = 0.22f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        data.stress?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        style = VivaFont.number(13), color = if (data.stress == null) VivaStyle.textPrimary else tint, maxLines = 1,
                    )
                }
                // The level over the time of the reading, as the reference card reads: MEDIUM / 1:19 a.m.
                Column {
                    VivaCardTitle(data.stressLabel, color = if (data.stress == null) VivaStyle.textPrimary else tint, maxLines = 1)
                    data.stressUpdated?.let { Text(it, style = VivaFont.caption, color = VivaStyle.textSecondary, maxLines = 1) }
                }
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
            days.forEachIndexed { index, day ->
                // The second half of a joined pair was drawn with the first.
                if (index > 0 && days[index - 1].joinsNext) return@forEachIndexed
                val pair = if (day.joinsNext) days.getOrNull(index + 1) else null
                val logged = day.logged || pair?.logged == true
                Column(
                    Modifier.weight(if (pair != null) 2f else 1f).clearAndSetSemantics {
                        contentDescription = "${listOfNotNull(day.label, pair?.label).joinToString(" and ")}: ${if (logged) "Logged" else "Not logged"}"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        listOfNotNull(day, pair).forEach {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                VivaLabel(it.label, color = if (it.isToday) VivaStyle.textPrimary else VivaStyle.textTertiary)
                            }
                        }
                    }
                    val fill = when {
                        logged -> VivaStyle.positive
                        day.isToday && pair == null -> VivaStyle.textTertiary
                        else -> Color.Transparent
                    }
                    val rim = if (day.isToday) VivaStyle.textPrimary else VivaStyle.textTertiary.copy(alpha = 0.6f)
                    // A joined pair is one pill from the first day's circle to the second's.
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(width = if (pair != null) maxWidth / 2 + 20.dp else 20.dp, height = 20.dp)
                                .background(fill, CircleShape)
                                .then(if (logged) Modifier else Modifier.border(1.dp, rim, CircleShape)),
                            contentAlignment = Alignment.Center,
                        ) {
                            // A logged day carries a tick, so it reads as done without relying on colour.
                            if (logged) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = VivaStyle.canvasBottom, modifier = Modifier.size(14.dp))
                            }
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
    val chartHeight = 260.dp
    // Room above the top gridline, so a figure over a dot near 100% still fits.
    val headroom = 16.dp
    val measurer = rememberTextMeasurer()
    val markerText = VivaFont.number(12)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartAxis(listOf("21", "14", "7", "0"), List(4) { VivaStyle.strain }, chartHeight, headroom)
        Column(
            Modifier.weight(1f).drawBehind {
                // Today's column is lit from above the plot down through its day label.
                val today = days.indexOfFirst { it.isToday }
                if (today >= 0) {
                    val step = size.width / days.size
                    drawRoundRect(
                        VivaStyle.chipActive.copy(alpha = 0.6f), Offset(step * (today + 0.1f), 0f),
                        Size(step * 0.8f, size.height), CornerRadius(6.dp.toPx()),
                    )
                }
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().padding(top = headroom).height(chartHeight)) {
                val step = size.width / maxOf(days.size, 1)
                fun x(index: Int) = step * (index + 0.5f)
                fun y(share: Double) = size.height * (1f - share.coerceIn(0.0, 1.0).toFloat())

                repeat(4) { index ->
                    val lineY = size.height * index / 3f
                    drawLine(VivaStyle.gridLine, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
                }
                val line = Path()
                var started = false
                days.forEachIndexed { index, day ->
                    val strain = day.strain ?: return@forEachIndexed
                    if (started) line.lineTo(x(index), y(strain / 21.0)) else { line.moveTo(x(index), y(strain / 21.0)); started = true }
                }
                val lineStyle = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(line, VivaStyle.strain.copy(alpha = 0.65f), style = lineStyle)
                // Recovery joined by a grey line, as on the reference chart; its dots keep their zone colour.
                val recoveryLine = Path()
                var recoveryStarted = false
                days.forEachIndexed { index, day ->
                    val recovery = day.recovery ?: return@forEachIndexed
                    val point = Offset(x(index), y(recovery / 100.0))
                    if (recoveryStarted) recoveryLine.lineTo(point.x, point.y) else { recoveryLine.moveTo(point.x, point.y); recoveryStarted = true }
                }
                drawPath(recoveryLine, VivaStyle.textSecondary, style = lineStyle)

                // A label sits on the side of its dot away from the day's other dot, so a figure never
                // lands on the other series' marker; at the chart's edge it flips back inside.
                fun marker(centre: Offset, color: Color, text: String?, above: Boolean) {
                    val radius = 4.5.dp.toPx()
                    drawCircle(VivaStyle.card, radius, centre)
                    drawCircle(color, radius, centre, style = Stroke(2.dp.toPx()))
                    if (text != null) {
                        val layout = measurer.measure(text, markerText.copy(color = color), maxLines = 1)
                        val gap = 6.5.dp.toPx()
                        val over = centre.y - gap - layout.size.height
                        val under = centre.y + gap
                        val top = if (above) over.takeIf { it >= -headroom.toPx() } ?: under else under.takeIf { it + layout.size.height <= size.height } ?: over
                        drawText(layout, topLeft = Offset(centre.x - layout.size.width / 2f, top))
                    }
                }
                days.forEachIndexed { index, day ->
                    val strainY = day.strain?.let { y(it / 21.0) }
                    val recoveryY = day.recovery?.let { y(it / 100.0) }
                    val strainOnTop = strainY != null && (recoveryY == null || strainY <= recoveryY)
                    day.strain?.let { marker(Offset(x(index), strainY!!), VivaStyle.strain, String.format(Locale.US, "%.1f", it), above = strainOnTop) }
                    day.recovery?.let {
                        marker(Offset(x(index), recoveryY!!), VivaStyle.recoveryColor(it), "${VivaScoreRing.roundHalfAwayFromZero(it)}%", above = !strainOnTop)
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    val style = TextStyle(fontSize = 11.sp, fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal)
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
            chartHeight, headroom,
        )
    }
}

@Composable
private fun ChartAxis(labels: List<String>, colors: List<Color>, chartHeight: Dp, headroom: Dp) {
    // Half a text line above and below the plot, so the first and last labels centre on their gridlines.
    Column(Modifier.padding(top = headroom - 7.dp).height(chartHeight + 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        val style = VivaFont.number(10, FontWeight.SemiBold)
        labels.forEachIndexed { index, label -> Text(label, style = style, color = colors[index], maxLines = 1) }
    }
}
