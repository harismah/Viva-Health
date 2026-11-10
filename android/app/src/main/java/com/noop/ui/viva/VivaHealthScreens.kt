package com.noop.ui.viva

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.VitalBands
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.BottomBarStyleStore
import com.noop.ui.UnitPrefs
import com.noop.ui.latestVitals
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// The Viva Health section: the Health tab, the Health Monitor and the dialog that explains one
// reading. Twins of Strand/Screens/HealthViva/*.swift, laid out after the reference app.

/** How one vital reads against the wearer's range. */
enum class VivaVitalState { NO_READING, IN_RANGE, OUT_OF_RANGE }

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
    val about: String,
    val trend: VivaTrendMetric?,
) {
    val status: String
        get() = when (state) {
            VivaVitalState.NO_READING -> "No reading"
            VivaVitalState.IN_RANGE -> "Within range"
            VivaVitalState.OUT_OF_RANGE -> "Out of range"
        }

    val statusColor: Color
        get() = when (state) {
            VivaVitalState.NO_READING -> VivaStyle.textTertiary
            VivaVitalState.IN_RANGE -> VivaStyle.positive
            VivaVitalState.OUT_OF_RANGE -> VivaStyle.warning
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

    fun items(days: List<DailyMetric>, context: android.content.Context): List<VivaVitalItem> {
        val readings = latestVitals(days, UnitPrefs.temperature(context)).associateBy { it.key }
        return order.map { entry ->
            val reading = readings[entry.key]
            val value = reading?.value
            VivaVitalItem(
                key = entry.key, icon = entry.icon, label = entry.label, shortLabel = entry.short,
                value = reading?.let { r -> r.value?.let { r.format(it) } },
                unit = reading?.unit.orEmpty(),
                state = when {
                    value == null -> VivaVitalState.NO_READING
                    reading?.banding?.band == VitalBands.Band.OUT_OF_RANGE -> VivaVitalState.OUT_OF_RANGE
                    reading?.banding?.band == VitalBands.Band.IN_RANGE -> VivaVitalState.IN_RANGE
                    else -> VivaVitalState.NO_READING
                },
                rangeCaption = reading?.rangeCaption,
                personalRange = reading?.banding?.basis == VitalBands.Basis.PERSONAL,
                about = entry.about, trend = entry.trend,
            )
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
    val drift by rememberInfiniteTransition(label = "bubble").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
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
        val inRange = items.count { it.state == VivaVitalState.IN_RANGE }
        val read = items.count { it.value != null }
        Row(
            Modifier.fillMaxWidth().background(VivaStyle.inset, RoundedCornerShape(8.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (read > 0) StateMark(if (inRange == read) VivaVitalState.IN_RANGE else VivaVitalState.OUT_OF_RANGE, 20.dp)
            Text(
                if (read == 0) "No readings yet. Wear your strap to sleep." else "$inRange/$read metrics within range",
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
        VivaVitalState.NO_READING -> VivaStyle.chipActive
    }
    Box(Modifier.size(size).background(fill, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        if (inRange) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = VivaStyle.positive, modifier = Modifier.size(size * 0.7f))
        } else {
            Box(Modifier.size(size * 0.28f).background(if (state == VivaVitalState.OUT_OF_RANGE) VivaStyle.warning else VivaStyle.textSecondary, RoundedCornerShape(50)))
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
    val live by viewModel.live.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val items = remember(days) { VivaVitals.items(days, context) }
    var selected by remember { mutableStateOf<VivaVitalItem?>(null) }

    VivaDetailScaffold(title = "Health Monitor", onBack = onBack) {
        LiveHeartRate(connected = live.connected, heartRate = live.heartRate)
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

/** Live heart rate from the strap, or dashes when it is not streaming. */
@Composable
private fun LiveHeartRate(connected: Boolean, heartRate: Int?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VivaCardTitle("Heart rate")
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = VivaStyle.strain, modifier = Modifier.size(26.dp))
        Text(
            if (connected) heartRate?.toString() ?: "--" else "--",
            style = VivaFont.number(44), color = VivaStyle.textPrimary,
        )
        VivaLabel("BPM")
        Text(
            if (connected) "Live from your strap" else "Strap not connected",
            style = VivaFont.caption, color = VivaStyle.textSecondary,
        )
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
            if (item.value != null) {
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
