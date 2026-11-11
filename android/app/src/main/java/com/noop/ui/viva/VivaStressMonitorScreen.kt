package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ui.AppViewModel
import com.noop.ui.StressLoadCancellation
import com.noop.ui.StressModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stress Monitor: today's stress level on the 0-3 dial, the recent trend, and the breathing sessions
 * that bring it down or lift alertness. Builds the same `StressModel` the full Stress screen and the
 * home card build, so the three cannot disagree. Twin of the iPhone `VivaStressMonitorView`.
 */
@Composable
fun VivaStressMonitorScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onBreathe: () -> Unit,
    onFullAnalysis: () -> Unit,
    onCoach: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    var model by remember { mutableStateOf<StressModel?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(days) {
        // Off the main thread: the stored series spans all history (same read as the home card).
        model = StressLoadCancellation.read {
            withContext(Dispatchers.Default) {
                val stored = viewModel.repo.metricSeries("my-whoop", "stress", "0000-01-01", "9999-12-31")
                    .associate { it.day to it.value.coerceIn(0.0, 3.0) }
                StressModel.build(days, stored)
            }
        }
        loaded = true
    }

    val current = model
    val caption = if (current == null && !loaded) "Calculating" else VivaHomeMapper.stressLabel(current?.score)

    VivaDetailScaffold(
        title = "Stress Monitor",
        onBack = onBack,
        trailing = VivaBarTrailing.INFO,
        onTrailing = { showInfo = true },
        coachMessage = current?.let { "Stress is ${caption.lowercase()} today. Ask what is driving it." },
        onCoach = onCoach,
    ) {
        VivaHalfDial(
            value = current?.score, maximum = 3.0, caption = caption,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            time = current?.let { "Today" },
        )
        if (current != null) {
            VivaCard(title = "Last 14 days") {
                VivaLineChart(
                    values = current.fullTrend.takeLast(14).map { it.value },
                    color = VivaStyle.textPrimary, range = 0.0..3.0,
                    yLabels = listOf("3.0", "2.0", "1.0", "0.0"), height = 120.dp, fill = true,
                )
                Text(current.explanation, style = VivaFont.caption, color = VivaStyle.textSecondary)
            }
        } else if (loaded) {
            VivaCallout("Stress needs a few nights of heart-rate variability and resting heart rate to calibrate.")
        }
        VivaLabel("Sessions", Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
            Session("Increase relaxation", listOf(VivaStyle.sleep, VivaStyle.canvasTop), Modifier.weight(1f), onBreathe)
            Session("Increase alertness", listOf(VivaStyle.recoveryRed.copy(alpha = 0.75f), VivaStyle.callout), Modifier.weight(1f), onBreathe)
        }
        VivaCard(padding = 14.dp, onClick = onFullAnalysis) {
            VivaMetricRow(Icons.AutoMirrored.Outlined.ShowChart, "Full stress analysis", "", showsChevron = true)
        }
    }

    if (showInfo) {
        VivaInfoSheet(
            title = "What is Stress?",
            paragraphs = listOf(
                "Stress here is your body's physiological activation, read from your heart rate variability and resting heart rate against your own baseline. It is scored from 0 to 3.",
                "0.0 to 0.9 is low: calm, relaxed or sleepy.",
                "1.0 to 1.9 is medium: neutral, alert or mildly activated.",
                "2.0 to 3.0 is high: excited, stressed or highly activated.",
                "Stress is not good or bad in itself. You want it low when you are winding down, and some activation helps when you need to perform.",
            ),
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun Session(title: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).vivaPressable(onClick)) {
        Box(Modifier.fillMaxWidth().height(78.dp).background(Brush.verticalGradient(colors)))
        Column(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 86.dp).background(VivaStyle.card).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Air, contentDescription = null, Modifier.size(13.dp), tint = VivaStyle.textSecondary)
            VivaCardTitle(title)
            Text("Guided breathing", style = VivaFont.caption, color = VivaStyle.textSecondary)
        }
    }
}
