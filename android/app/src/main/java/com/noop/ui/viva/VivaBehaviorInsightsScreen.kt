package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.BehaviorEffect
import com.noop.analytics.EffectRanker
import com.noop.data.DailyMetric
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.JOURNAL_DEVICE_ID
import com.noop.ui.mergeJournalEntries
import java.util.Locale
import kotlin.math.abs

/**
 * Starter questions that ask the same thing as a question the reference app's export uses. When the
 * wearer's imported history holds the export's wording, the starter folds into it, so one behaviour
 * is one row. Only wordings seen in a real export are listed.
 */
internal val vivaJournalAliases: Map<String, String> = mapOf(
    "Did you eat close to bedtime?" to "Ate food close to bedtime?",
    "Did you drink any alcohol?" to "Have any alcoholic drinks?",
    "Did you read before bed?" to "Read (non-screened device) while in bed?",
)

/** The wording [question] is counted and shown under, given the questions the import holds. */
internal fun vivaJournalQuestion(question: String, imported: Set<String>): String =
    vivaJournalAliases[question]?.takeIf { it in imported } ?: question

/**
 * One journal history out of the import and the app's own answers. An own answer wins over the import
 * for the same day and question, as everywhere else. An own answer left under a starter's wording
 * that has since folded into the import's is different: its row is no longer on the Journal, so it
 * only fills a day the import does not answer and never overrides one it does.
 */
internal fun vivaJournalHistory(imported: List<JournalEntry>, native: List<JournalEntry>): List<JournalEntry> {
    val importedQuestions = imported.map { it.question }.toSet()
    val (leftover, own) = native.partition { vivaJournalQuestion(it.question, importedQuestions) != it.question }
    val folded = leftover.map { it.copy(question = vivaJournalQuestion(it.question, importedQuestions)) }
    return mergeJournalEntries(folded + imported, own)
}

/** An impact is shown only from this many yes days and this many no days, each with a Recovery score. */
internal const val VIVA_MIN_DAYS_PER_ANSWER = 5

/** How many days one behaviour was answered yes and no. */
data class VivaBehaviorCount(val question: String, val yes: Int, val no: Int)

/** What the Behavior Insights screen shows: the yes/no tallies and the behaviours with a measurable effect. */
data class VivaBehaviorInsights(val counts: List<VivaBehaviorCount>, val effects: List<BehaviorEffect>)

/**
 * Journal answers and daily Recovery in, tallies and ranked effects out. All of the maths is the
 * existing `EffectRanker.rankNoLag` (the twin of Swift's `BehaviorInsights.rank`); this only shapes
 * its inputs, the same way the iPhone `VivaBehaviorInsightsView.load` does.
 */
internal fun vivaBehaviorInsights(entries: List<JournalEntry>, days: List<DailyMetric>): VivaBehaviorInsights {
    val yes = mutableMapOf<String, MutableSet<String>>()
    val no = mutableMapOf<String, MutableSet<String>>()
    for (entry in entries) (if (entry.answeredYes) yes else no).getOrPut(entry.question) { mutableSetOf() }.add(entry.day)
    val recoveryByDay = days.mapNotNull { day -> day.recovery?.let { day.day to it } }.toMap()
    return VivaBehaviorInsights(
        counts = (yes.keys + no.keys).sorted().map { VivaBehaviorCount(it, yes[it]?.size ?: 0, no[it]?.size ?: 0) },
        // The ranker reports an effect from three days in all; that is too little to print as a percentage.
        effects = EffectRanker.rankNoLag(yes, no, recoveryByDay, "recovery")
            .filter { it.pctChange != null && minOf(it.nWith, it.nWithout) >= VIVA_MIN_DAYS_PER_ANSWER },
    )
}

/**
 * Which logged behaviours go with a higher or lower Recovery. Twin of the iPhone
 * `VivaBehaviorInsightsView`.
 */
@Composable
fun VivaBehaviorInsightsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onJournal: () -> Unit,
) {
    val days by viewModel.recentDays.collectAsStateWithLifecycle()
    var insights by remember { mutableStateOf<VivaBehaviorInsights?>(null) }

    LaunchedEffect(days) {
        val imported = runCatching { viewModel.repo.journal("my-whoop", "0000-01-01", "9999-12-31") }.getOrDefault(emptyList())
        val native = runCatching { viewModel.repo.journal(JOURNAL_DEVICE_ID, "0000-01-01", "9999-12-31") }.getOrDefault(emptyList())
        insights = vivaBehaviorInsights(vivaJournalHistory(imported, native), days)
    }

    VivaDetailScaffold(title = "Behavior Insights", onBack = onBack) {
        Text("Log your behaviors to reveal personalized insights", style = VivaFont.sectionTitle, color = VivaStyle.textPrimary)
        Text(
            "Answer yes and no in your journal on enough days and Viva compares your Recovery on the days you did with the days you did not.",
            style = VivaFont.caption, color = VivaStyle.textSecondary,
        )
        val loaded = insights ?: return@VivaDetailScaffold
        if (loaded.counts.isEmpty()) VivaCallout("Nothing logged yet. Open your journal to start.")
        loaded.counts.forEach { entry ->
            VivaCard(padding = 12.dp) {
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = "${entry.question}: ${entry.yes} yes, ${entry.no} no" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(entry.question, Modifier.weight(1f), style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
                    Tally(Icons.Rounded.Close, entry.no)
                    Tally(Icons.Rounded.Check, entry.yes)
                }
            }
        }
        VivaCardButton("Open journal", icon = Icons.Outlined.Edit, onClick = onJournal)
        if (loaded.effects.isNotEmpty()) {
            VivaCard(title = "Impact on Recovery") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Direction(Icons.Rounded.ArrowDropDown, "Hurts", VivaStyle.warning)
                    VivaLabel("% impact", Modifier.weight(1f).clearAndSetSemantics {})
                    Direction(Icons.Rounded.ArrowDropUp, "Helps", VivaStyle.positive)
                }
                val scale = maxOf(loaded.effects.maxOf { abs(it.pctChange ?: 0.0) }, 1.0)
                loaded.effects.forEach { ImpactRow(it, scale) }
            }
        } else if (loaded.counts.isNotEmpty()) {
            VivaCallout("Record at least five yes days and five no days for a behaviour to see how it moves your Recovery.")
        }
    }
}

@Composable
private fun Tally(icon: ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(16.dp).border(1.dp, VivaStyle.textSecondary, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, Modifier.size(11.dp), tint = VivaStyle.textSecondary)
        }
        Text(count.toString(), style = VivaFont.number(12, FontWeight.Medium), color = VivaStyle.textSecondary)
    }
}

@Composable
private fun Direction(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(16.dp), tint = color)
        VivaLabel(text, color = color)
    }
}

/** One behaviour's bar: it grows right of the centre dot when the behaviour helps, left when it hurts. */
@Composable
private fun ImpactRow(effect: BehaviorEffect, scale: Double) {
    val percent = effect.pctChange ?: 0.0
    val color = if (percent >= 0) VivaStyle.positive else VivaStyle.warning
    val label = String.format(Locale.US, "%+.0f%%", percent)
    Column(
        Modifier
            .fillMaxWidth()
            .background(VivaStyle.inset, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = "${effect.behavior}: $label Recovery" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(effect.behavior, style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp))) {
                val step = 6.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(VivaStyle.ringTrack, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                    x += step
                }
                val half = size.width / 2
                val width = maxOf(half * minOf(abs(percent) / scale, 1.0).toFloat(), 3.dp.toPx())
                drawRoundRect(
                    color, topLeft = Offset(if (percent >= 0) half else half - width, 0f),
                    size = Size(width, size.height), cornerRadius = CornerRadius(2.dp.toPx()),
                )
                drawCircle(VivaStyle.textPrimary, radius = 2.dp.toPx(), center = Offset(half, size.height / 2))
            }
            Text(label, Modifier.width(44.dp), style = VivaFont.number(12), color = color, textAlign = TextAlign.End)
        }
    }
}
