package com.noop.ui.viva

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.noop.analytics.StrainScorer
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.LIVE_HR_BUFFER_CAP
import com.noop.ui.LiveHrSample
import com.noop.ui.ProfileStore
import com.noop.ui.UnitPrefs
import com.noop.ui.appendLiveHrSample
import com.noop.ui.displayHr
import com.noop.ui.latestVitals
import com.noop.ui.logicalDayKeyNow
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// The Viva Health section: the Health tab, the Health Monitor and the dialog that explains one
// reading. Twins of Strand/Screens/HealthViva/*.swift, laid out after the reference app.

/** How one vital reads against the wearer's range. */
enum class VivaVitalState { NO_READING, IN_RANGE, OUT_OF_RANGE, FAR_OUT_OF_RANGE }

/** One row of the Health Monitor, already formatted. */
data class VivaVitalItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val shortLabel: String,
    val value: String?,
    val unit: String,
    val state: VivaVitalState,
    /** The range the reading was judged against, in the app's own words, or null when none applies. */
    val rangeCaption: String?,
    /** True when the range is the wearer's own baseline; false while it is still the typical-adult fallback. */
    val personalRange: Boolean,
    /** The reference-style chip (`within 55 - 60`, `elevated > 17.9`) when the 30-day banding applies, else null. */
    val chip: String? = null,
    /** The earlier night the reading was carried from; null when it is last night's own. */
    val carriedDay: String? = null,
    val about: String,
    val trend: VivaTrendMetric?,
) {
    /** The verdict, stamped with its night when carried: the same stamp Home and the detail rows print. */
    val status: String
        get() = (chip ?: when (state) {
            VivaVitalState.NO_READING -> "No reading"
            VivaVitalState.IN_RANGE -> "Within range"
            VivaVitalState.OUT_OF_RANGE -> "Out of range"
            VivaVitalState.FAR_OUT_OF_RANGE -> "Far out of range"
        }).let { verdict -> carriedDay?.let { "$verdict · ${VivaConsistency.dayLabel(it)}" } ?: verdict }

    val statusColor: Color
        get() = when (state) {
            VivaVitalState.NO_READING -> VivaStyle.textTertiary
            VivaVitalState.IN_RANGE -> VivaStyle.positive
            VivaVitalState.OUT_OF_RANGE -> VivaStyle.warning
            VivaVitalState.FAR_OUT_OF_RANGE -> VivaStyle.critical
        }
}

/**
 * The vitals the Health Monitor shows, read through the same banding the full Health screen uses
 * (`latestVitals`), so "within range" means the same thing on both.
 */
internal object VivaVitals {
    private class Entry(val key: String, val icon: ImageVector, val label: String, val short: String, val about: String, val trend: VivaTrendMetric?)

    private val order = listOf(
        Entry("resp", Icons.Outlined.Air, "Respiratory Rate", "Resp",
            "Breaths per minute while you sleep. It is normally very steady from night to night, so a clear shift from your usual range is worth noticing.",
            VivaTrendMetric.RESPIRATORY_RATE),
        Entry("spo2", Icons.Outlined.WaterDrop, "Blood Oxygen (SpO₂)", "SpO₂",
            "How much oxygen your red blood cells are carrying while you sleep.", VivaTrendMetric.BLOOD_OXYGEN),
        Entry("rhr", Icons.Outlined.FavoriteBorder, "RHR", "RHR",
            "Resting heart rate: your heart rate at its calmest during sleep. A lower figure usually goes with better cardiovascular fitness.",
            VivaTrendMetric.RESTING_HR),
        Entry("hrv", Icons.Outlined.MonitorHeart, "HRV", "HRV",
            "Heart rate variability: the variation in time between heartbeats during sleep. It reflects how well your body is adapting to the load on it.",
            VivaTrendMetric.HRV),
        Entry("skin", Icons.Outlined.Thermostat, "Skin Temp", "Temp",
            "The temperature at the surface of your skin during sleep. It moves with illness, cycle phase and room temperature.", null),
    )

    /** The one sentence for "how many vitals are in range", shared by the Health tab and the Home card; null with no readings. */
    fun summary(items: List<VivaVitalItem>): String? {
        val read = items.count { it.value != null }
        return if (read == 0) null else "${items.count { it.state == VivaVitalState.IN_RANGE }}/$read metrics within range"
    }

    fun items(days: List<DailyMetric>, context: android.content.Context): List<VivaVitalItem> {
        // Viva: skin temperature leads with the night's difference from baseline, as the reference app shows it.
        val readings = latestVitals(days, UnitPrefs.temperature(context), skinTempPreferred = com.noop.analytics.SkinTempDisplay.Kind.DEVIATION)
            .associateBy { it.key }
        val fahrenheit = UnitPrefs.temperature(context) == com.noop.ui.TemperatureUnit.FAHRENHEIT
        return order.map { entry ->
            // Viva: breathing rate has no strap estimate yet, so an older imported night is not shown as tonight's.
            val reading = readings[entry.key]?.takeUnless { entry.key == "resp" && it.readingDay != null && it.readingDay != logicalDayKeyNow() }
            val raw = reading?.value
            // Viva: an imported night carries absolute degrees; shown, as the reference shows it, as its
            // difference from the average of the 30 nights before.
            val skinDev = if (entry.key == "skin" && raw != null && VitalBands.isAbsoluteSkinTemp(raw)) absoluteDeviation(raw, reading.readingDay, days) else null
            val value = skinDev ?: raw
            val fromBaseline = entry.key == "skin" && value != null && !VitalBands.isAbsoluteSkinTemp(value)
            val format: (Double) -> String = if (skinDev != null) {
                { v -> com.noop.analytics.SkinTempDisplay.numberString(v, com.noop.analytics.SkinTempDisplay.Kind.DEVIATION, fahrenheit) }
            } else { v -> reading?.format?.invoke(v) ?: v.toString() }
            val band = if (reading != null && value != null) {
                VivaVitalRange.band(entry.key, value, window(entry.key, value, reading.readingDay, days), reading.unit, format)
            } else null
            VivaVitalItem(
                key = entry.key, icon = entry.icon, label = if (fromBaseline) "Skin Temp (From Baseline)" else entry.label, shortLabel = entry.short,
                value = value?.let(format),
                // The label already says "from baseline", so the unit reads plain degrees, as on the reference.
                unit = reading?.unit.orEmpty().let { if (fromBaseline) it.removePrefix("Δ") else it },
                state = when {
                    value == null -> VivaVitalState.NO_READING
                    band != null -> when (band.level) {
                        VivaVitalRange.Level.GREEN -> VivaVitalState.IN_RANGE
                        VivaVitalRange.Level.ORANGE -> VivaVitalState.OUT_OF_RANGE
                        VivaVitalRange.Level.RED -> VivaVitalState.FAR_OUT_OF_RANGE
                    }
                    reading?.banding?.band == VitalBands.Band.OUT_OF_RANGE -> VivaVitalState.OUT_OF_RANGE
                    reading?.banding?.band == VitalBands.Band.IN_RANGE -> VivaVitalState.IN_RANGE
                    else -> VivaVitalState.NO_READING
                },
                rangeCaption = band?.detail ?: reading?.rangeCaption,
                personalRange = band != null || reading?.banding?.basis == VitalBands.Basis.PERSONAL,
                chip = band?.chip,
                carriedDay = reading?.readingDay?.takeIf { value != null && it != logicalDayKeyNow() },
                about = entry.about, trend = entry.trend,
            )
        }
    }

    /** An absolute skin temperature minus the average absolute reading of the 30 nights before [readingDay]; null without history. */
    private fun absoluteDeviation(value: Double, readingDay: String?, days: List<DailyMetric>): Double? {
        val prior = days.filter { readingDay == null || it.day < readingDay }.takeLast(VivaVitalRange.WINDOW_DAYS.toInt())
            .mapNotNull { row -> listOfNotNull(row.skinTempC, row.skinTempDevC).firstOrNull { VitalBands.isAbsoluteSkinTemp(it) } }
        return if (prior.isEmpty()) null else value - prior.average()
    }

    /** Every reading of [key] in the 30 days ending on the displayed night, that night included. */
    private fun window(key: String, value: Double, readingDay: String?, days: List<DailyMetric>): List<Double> {
        val from = readingDay?.let { runCatching { java.time.LocalDate.parse(it).minusDays(VivaVitalRange.WINDOW_DAYS).toString() }.getOrNull() }
        val rows = if (readingDay == null || from == null) days.takeLast(VivaVitalRange.WINDOW_DAYS.toInt()) else days.filter { it.day > from && it.day <= readingDay }
        if (key == "skin" && !VitalBands.isAbsoluteSkinTemp(value)) {
            // Imported nights carry absolute degrees. As differences from their own mean they give a deviation
            // the month of history the reference app judges by; a strap night's own deviation wins.
            fun absolute(row: DailyMetric) = listOfNotNull(row.skinTempC, row.skinTempDevC).firstOrNull { VitalBands.isAbsoluteSkinTemp(it) }
            val mean = rows.mapNotNull(::absolute).takeIf { it.isNotEmpty() }?.average()
            return rows.mapNotNull { row -> row.skinTempDevC?.takeUnless { VitalBands.isAbsoluteSkinTemp(it) } ?: mean?.let { m -> absolute(row)?.minus(m) } }
        }
        return rows.mapNotNull { row ->
            when (key) {
                "resp" -> row.respRateBpm
                "rhr" -> row.restingHr?.toDouble()
                "hrv" -> row.avgHrv
                "spo2" -> row.spo2Pct
                // Absolute degrees and plus-or-minus deviations share these columns; keep the kind being shown.
                else -> listOfNotNull(row.skinTempC, row.skinTempDevC).firstOrNull { VitalBands.isAbsoluteSkinTemp(it) == VitalBands.isAbsoluteSkinTemp(value) }
            }
        }
    }
}

// Health tab

@Composable
fun VivaHealthTabScreen(
    viewModel: AppViewModel,
    onHealthMonitor: () -> Unit,
    onStress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val items = remember(days) { VivaVitals.items(days, context) }
    val stress = viewModel.todayStressCache

    VivaCanvas(modifier) {
        // The one tinted canvas in the app: magenta at the top, gone by a third of the way down.
        Box(
            Modifier.fillMaxWidth().height(420.dp)
                .background(Brush.verticalGradient(listOf(VivaStyle.magenta.copy(alpha = 0.32f), VivaStyle.magenta.copy(alpha = 0f)))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VivaStyle.pagePadding)
                .padding(bottom = BottomBarStyleStore.barHeightForContent() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(VivaStyle.cardGap),
        ) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text("HEALTH", style = VivaFont.barTitle, color = VivaStyle.textPrimary)
            }
            HealthspanCard()
            MonitorCard(items, onHealthMonitor)
            StressCard(stress, onStress)
            Text(
                "Viva is not a medical device and cannot diagnose or manage medical conditions. Talk to your doctor about health concerns.",
                Modifier.padding(horizontal = 4.dp).padding(top = 8.dp),
                style = VivaFont.caption, color = VivaStyle.textSecondary,
            )
        }
    }
}

@Composable
private fun HealthspanCard() {
    val shape = RoundedCornerShape(VivaStyle.cardRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .background(VivaStyle.card.copy(alpha = 0.55f), shape)
            .border(1.dp, VivaStyle.magenta.copy(alpha = 0.7f), shape)
            .padding(VivaStyle.cardPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            VivaBubble(size = 210.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VivaCardTitle("Healthspan", Modifier.weight(1f))
                Box(Modifier.background(VivaStyle.chip, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    VivaLabel("Calibrating", color = VivaStyle.textPrimary)
                }
            }
            // No figure and no countdown: Viva does not compute this yet, so it promises neither.
            Text("This reading is not available yet.", style = VivaFont.caption, color = VivaStyle.textSecondary)
        }
    }
}

/**
 * The healthspan bubble: a soft grey sphere with a glowing magenta rim and a scatter of magenta
 * specks, its outline never quite round and drifting slowly. Decorative; it carries no value.
 */
@Composable
fun VivaBubble(size: Dp, modifier: Modifier = Modifier) {
    // Battery saver and reduced motion hold the bubble still, like every other looping animation in the app.
    val drift = if (com.noop.ui.rememberPoseStill()) 0f else rememberInfiniteTransition(label = "bubble").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    ).value
    // Fixed seed: the same specks every time, so the bubble is a shape and not noise.
    val specks = remember {
        val random = Random(7)
        List(90) {
            floatArrayOf(sqrt(random.nextFloat()), random.nextFloat() * (2 * Math.PI).toFloat(), 0.8f + random.nextFloat() * 2.6f, 0.35f + random.nextFloat() * 0.65f)
        }
    }
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f - 10.dp.toPx()
        val outline = Path()
        val steps = 96
        for (step in 0..steps) {
            val angle = (2 * Math.PI * step / steps).toFloat()
            val wobble = 1f + 0.035f * sin(3 * angle + drift) + 0.02f * sin(5 * angle - drift * 2)
            val point = Offset(centre.x + radius * wobble * cos(angle), centre.y + radius * wobble * sin(angle))
            if (step == 0) outline.moveTo(point.x, point.y) else outline.lineTo(point.x, point.y)
        }
        outline.close()

        // Rim glow, widest and faintest first.
        drawPath(outline, VivaStyle.magenta.copy(alpha = 0.10f), style = Stroke(14.dp.toPx()))
        drawPath(outline, VivaStyle.magenta.copy(alpha = 0.22f), style = Stroke(7.dp.toPx()))
        drawPath(
            outline,
            Brush.radialGradient(
                listOf(Color(0xFF6C727A), Color(0xFF4A4F56), Color(0xFF33373D)),
                center = Offset(centre.x - radius * 0.25f, centre.y - radius * 0.3f), radius = radius * 1.4f,
            ),
        )
        clipPath(outline) {
            specks.forEach { speck ->
                val angle = speck[1] + drift * 0.15f
                val reach = radius * speck[0]
                // Specks near the rim read brighter, which is what gives the sphere its edge.
                val alpha = speck[3] * (0.35f + 0.65f * speck[0])
                drawCircle(VivaStyle.magenta.copy(alpha = alpha), speck[2].dp.toPx(), Offset(centre.x + reach * cos(angle), centre.y + reach * sin(angle)))
            }
        }
        drawPath(outline, VivaStyle.magenta, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun MonitorCard(items: List<VivaVitalItem>, onClick: () -> Unit) {
    VivaCard(title = "Health Monitor", accessory = VivaCardAccessory.CHEVRON, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, item ->
                Column(
                    Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${item.label}: ${item.status}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(item.icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(24.dp))
                    VivaLabel(item.shortLabel, color = VivaStyle.textPrimary)
                    StateMark(item.state, 22.dp)
                }
                if (index < items.lastIndex) Box(Modifier.size(width = 1.dp, height = 64.dp).background(VivaStyle.divider))
            }
        }
        val read = items.count { it.value != null }
        val inRange = items.count { it.state == VivaVitalState.IN_RANGE }
        Row(
            Modifier.fillMaxWidth().background(VivaStyle.inset, RoundedCornerShape(8.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (read > 0) StateMark(if (inRange == read) VivaVitalState.IN_RANGE else VivaVitalState.OUT_OF_RANGE, 20.dp)
            Text(
                VivaVitals.summary(items) ?: "No readings yet. Wear your strap to sleep.",
                style = VivaFont.body, color = VivaStyle.textPrimary,
            )
        }
    }
}

/** The small square that states a reading's band: a tick when in range, a dot otherwise. */
@Composable
private fun StateMark(state: VivaVitalState, size: Dp) {
    val inRange = state == VivaVitalState.IN_RANGE
    val fill = when (state) {
        VivaVitalState.IN_RANGE -> VivaStyle.positive.copy(alpha = 0.22f)
        VivaVitalState.OUT_OF_RANGE -> VivaStyle.warning.copy(alpha = 0.22f)
        VivaVitalState.FAR_OUT_OF_RANGE -> VivaStyle.critical.copy(alpha = 0.22f)
        VivaVitalState.NO_READING -> VivaStyle.chipActive
    }
    Box(Modifier.size(size).background(fill, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        if (inRange) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = VivaStyle.positive, modifier = Modifier.size(size * 0.7f))
        } else {
            Box(Modifier.size(size * 0.28f).background(when (state) { VivaVitalState.OUT_OF_RANGE -> VivaStyle.warning; VivaVitalState.FAR_OUT_OF_RANGE -> VivaStyle.critical; else -> VivaStyle.textSecondary }, RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun StressCard(stress: Double?, onClick: () -> Unit) {
    VivaCard(title = "Stress Monitor", accessory = VivaCardAccessory.CHEVRON, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VivaLabel("Today's stress")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stress?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    Modifier.alignByBaseline(), style = VivaFont.number(28), color = VivaStyle.textPrimary,
                )
                Text("/ 3", Modifier.alignByBaseline(), style = VivaFont.caption, color = VivaStyle.textSecondary)
            }
            Box(Modifier.background(VivaStyle.chip, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(VivaHomeMapper.stressLabel(stress), style = VivaFont.number(11, androidx.compose.ui.text.font.FontWeight.Medium), color = VivaStyle.textSecondary)
            }
        }
    }
}

// Health Monitor

@Composable
fun VivaHealthMonitorScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onTrend: (VivaTrendMetric) -> Unit,
    onFullReport: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val items = remember(days) { VivaVitals.items(days, context) }
    val restingHr = remember(days) { days.filter { it.restingHr != null }.maxByOrNull { it.day }?.restingHr?.toDouble() ?: StrainScorer.defaultRestingHR }
    var selected by remember { mutableStateOf<VivaVitalItem?>(null) }

    VivaDetailScaffold(title = "Health Monitor", onBack = onBack) {
        LiveHeartRate(viewModel, restingHr)
        if (items.any { it.value != null && !it.personalRange }) {
            VivaCallout("Your typical ranges firm up as more nights are recorded. Until then readings are judged against a typical adult range.")
        }
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
                pair.forEach { item ->
                    VivaVitalsTile(
                        icon = item.icon, label = item.label, value = item.value, unit = item.unit.takeIf { it.isNotEmpty() },
                        status = item.status, statusColor = item.statusColor, modifier = Modifier.weight(1f),
                    ) { selected = item }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        VivaCard(padding = 14.dp, onClick = onFullReport) {
            VivaMetricRow(Icons.AutoMirrored.Outlined.ListAlt, "Full health report", "", showsChevron = true)
        }
        Text(
            "Viva is not a medical device. Talk to a medical professional before making medical decisions.",
            Modifier.padding(horizontal = 4.dp), style = VivaFont.caption, color = VivaStyle.textSecondary,
        )
    }

    selected?.let { item ->
        VivaExplainerDialog(
            item = item,
            onClose = { selected = null },
            onTrend = item.trend?.let { metric -> { selected = null; onTrend(metric) } },
        )
    }
}

/**
 * Heart-rate zones by share of heart-rate reserve, as the reference app counts them: zone 0 under
 * 50%, then one zone per 10% up to zone 5 from 90%.
 */
internal object VivaHeartZone {
    /** The zone, 0 to 5, a heart rate falls in. */
    fun zone(bpm: Int, restingHr: Double, maxHr: Double): Int =
        (((bpm - restingHr) / (maxHr - restingHr).coerceAtLeast(1.0) * 100.0 - 40.0) / 10.0).toInt().coerceIn(0, 5)

    /** One colour per zone: muted under zone 1, then blue, green, yellow, orange and red. */
    fun color(zone: Int): Color = when (zone) {
        1 -> VivaStyle.strain
        2 -> VivaStyle.recoveryGreen
        3 -> VivaStyle.recoveryYellow
        4 -> VivaStyle.warning
        5 -> VivaStyle.recoveryRed
        else -> VivaStyle.sleep
    }
}

/**
 * Live heart rate from the strap: a heart beating at the current rate in the colour of its zone, the
 * last three minutes as a line ending at "now", and the zone under the figure. Dashes when nothing streams.
 */
@Composable
private fun LiveHeartRate(viewModel: AppViewModel, restingHr: Double) {
    // Collected here, not by the screen, so the once-a-second update redraws this section alone.
    val live by viewModel.live.collectAsStateWithLifecycle()
    val smoothed by viewModel.bpm.collectAsStateWithLifecycle()
    val heartRate = if (live.connected) displayHr(smoothed, live) else null
    val context = LocalContext.current
    val maxHr = remember { ProfileStore.from(context).hrMax.toDouble() }
    val zone = heartRate?.let { VivaHeartZone.zone(it, restingHr, maxHr) }
    // The colour steps when the zone does, with a short fade so the change is seen, not missed.
    val color by animateColorAsState(zone?.let(VivaHeartZone::color) ?: VivaStyle.textTertiary, label = "zone")

    // One reading a second while the screen is in front: the same banking the upstream live chart uses.
    val history = remember { mutableStateListOf<LiveHrSample>() }
    // The app already stores the live stream, so the trace opens on the stored three minutes and
    // nothing new is kept for it. The readings from here on are added in front as they arrive.
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis() / 1000L
        val stored = runCatching { viewModel.repo.hrSamplesUnion(viewModel.activeStrapId, now - LIVE_HR_BUFFER_CAP, now) }.getOrDefault(emptyList())
        history.addAll(0, stored.filter { it.bpm in 30..220 }.map { LiveHrSample(it.ts * 1000L, it.bpm.toDouble()) })
    }
    val latest by rememberUpdatedState(heartRate)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                appendLiveHrSample(history, latest, System.currentTimeMillis())
                delay(1000)
            }
        }
    }
    // How far through the current beat the heart is, 0 to 1, advanced each frame at the live rate.
    // Battery saver and reduced motion hold it still, like every other looping animation in the app.
    val beat = remember { mutableFloatStateOf(0f) }
    val still = com.noop.ui.rememberPoseStill()
    LaunchedEffect(still) {
        if (still) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            beat.floatValue = (beat.floatValue + (now - last) / 1e9f * (latest ?: 0) / 60f) % 1f
            last = now
        }
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val left = 110.dp.toPx()
            val nowX = size.width - 10.dp.toPx()
            val hair = 0.5.dp.toPx()
            val cell = 14.dp.toPx()
            // A grid that fades out toward the figure.
            var gridY = cell
            while (gridY < size.height) {
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, VivaStyle.divider), startX = left, endX = size.width), Offset(left, gridY), Offset(size.width, gridY), hair)
                gridY += cell
            }
            var gridX = size.width
            while (gridX > left) {
                drawLine(VivaStyle.divider.copy(alpha = (gridX - left) / (size.width - left)), Offset(gridX, 0f), Offset(gridX, size.height), hair)
                gridX -= cell
            }
            val newest = history.lastOrNull()
            if (heartRate == null || newest == null) return@Canvas
            val dash = 3.dp.toPx()
            drawLine(VivaStyle.textSecondary, Offset(nowX, 0f), Offset(nowX, size.height), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
            val low = history.minOf { it.bpm } - 20.0
            val high = history.maxOf { it.bpm } + 20.0
            fun x(timeMs: Long) = nowX - (newest.timeMs - timeMs) / (LIVE_HR_BUFFER_CAP * 1000f) * (nowX - left)
            fun y(bpm: Double) = size.height * (1f - ((bpm - low) / (high - low)).toFloat())
            // Whole-number readings a second apart would draw as stairs, so each point is averaged with
            // its two neighbours on either side and the points are joined by curves.
            val line = Path()
            var end = Offset(nowX, y(newest.bpm))
            var run = mutableListOf<LiveHrSample>()
            fun drawRun() {
                val points = run.indices.map { index ->
                    val near = run.subList(maxOf(0, index - 2), minOf(run.size, index + 3))
                    Offset(x(run[index].timeMs), y(near.sumOf { it.bpm } / near.size))
                }
                points.forEachIndexed { index, point ->
                    if (index == 0) {
                        line.moveTo(point.x, point.y)
                    } else {
                        val before = points[index - 1]
                        line.quadraticBezierTo(before.x, before.y, (before.x + point.x) / 2f, (before.y + point.y) / 2f)
                    }
                }
                points.lastOrNull()?.let {
                    line.lineTo(it.x, it.y)
                    end = it
                }
                run = mutableListOf()
            }
            history.forEach { sample ->
                // A pause in the stream breaks the line; joining across it would draw readings nobody took.
                // Stored readings trail the live ones by up to half a minute (they are written in
                // batches), so a join that long is still one stream and stays joined.
                if (run.isNotEmpty() && sample.timeMs - run.last().timeMs > 40_000) drawRun()
                run.add(sample)
            }
            drawRun()
            clipRect(left = left) {
                // A soft glow: the curve drawn wide and faint, then narrower and stronger, under a bright core.
                listOf(14.dp to 0.05f, 9.dp to 0.09f, 5.dp to 0.18f).forEach { (width, alpha) ->
                    drawPath(line, color.copy(alpha = alpha), style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(line, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, VivaStyle.textPrimary.copy(alpha = 0.55f), style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(color.copy(alpha = 0.25f), 6.dp.toPx(), end)
            drawCircle(VivaStyle.textPrimary, 3.dp.toPx(), end)
        }
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VivaCardTitle("Heart rate")
            Icon(
                Icons.Rounded.Favorite, contentDescription = null, tint = color,
                modifier = Modifier.size(26.dp).graphicsLayer {
                    // The beat swells and settles in the first third of each cycle.
                    val swell = 1f + 0.16f * sin(Math.PI.toFloat() * (beat.floatValue / 0.33f).coerceAtMost(1f))
                    scaleX = swell
                    scaleY = swell
                },
            )
            Text(heartRate?.toString() ?: "--", style = VivaFont.number(44), color = VivaStyle.textPrimary)
            VivaLabel("BPM")
            Text(
                zone?.let { "Zone $it" } ?: if (live.connected) "Live from your strap" else "Strap not connected",
                style = VivaFont.caption, color = VivaStyle.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { index ->
                    Box(Modifier.width(22.dp).height(3.dp).background(if (zone != null && index < zone) color else VivaStyle.ringTrack, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

/** A centred card over a dimmed screen that explains one reading, with the way into its trend. */
@Composable
fun VivaExplainerDialog(item: VivaVitalItem, onClose: () -> Unit, onTrend: (() -> Unit)?) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(VivaStyle.chip, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(item.icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(34.dp))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).vivaPressable(onClose).semantics { role = Role.Button; contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StateMark(item.state, 30.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    VivaCardTitle(item.label)
                    VivaCardTitle(item.status, color = item.statusColor)
                }
            }
            Text(item.about, style = VivaFont.body.copy(fontSize = 15.sp), color = VivaStyle.textSecondary)
            if (item.value != null) {
                val reading = listOf(item.value, item.unit).filter { it.isNotEmpty() }.joinToString(" ")
                Text("Latest reading: $reading.", style = VivaFont.body.copy(fontSize = 15.sp), color = VivaStyle.textSecondary)
            }
            // The range is quoted from the same banding that set the status above, never restated.
            item.rangeCaption?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = VivaFont.body.copy(fontSize = 15.sp), color = VivaStyle.textSecondary)
            }
            if (item.value != null && item.chip == null) {
                Text(
                    if (item.personalRange) "Your range is worked out from your own recent nights."
                    else "Your own range is still being learned, so this is judged against a typical adult range.",
                    style = VivaFont.body.copy(fontSize = 15.sp), color = VivaStyle.textSecondary,
                )
            }
            Text("This reading is not a medical measurement.", style = TextStyle(fontSize = 11.sp), color = VivaStyle.textTertiary)
            if (onTrend != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .vivaPressable(onTrend)
                        .border(1.dp, VivaStyle.textSecondary, RoundedCornerShape(8.dp))
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("OPEN TREND VIEW", style = VivaFont.action, color = VivaStyle.textPrimary)
                }
            }
        }
    }
}
