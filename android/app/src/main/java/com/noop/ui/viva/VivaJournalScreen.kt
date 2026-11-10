package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noop.data.JournalEntry
import com.noop.ui.AppViewModel
import com.noop.ui.JOURNAL_DEVICE_ID
import com.noop.ui.JournalCatalogItem
import com.noop.ui.journalDayKey
import com.noop.ui.loadJournalCatalogItems
import com.noop.ui.resolveJournalItems
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The daily journal: pick a day, answer each behaviour yes or no (or log its amount), done. Answers
 * are written with the same repository calls and the same behaviour list as the journal card on the
 * Insights screen, so both write one history. Twin of the iPhone `VivaJournalView`.
 */
@Composable
fun VivaJournalScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onManage: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var dayOffset by remember { mutableIntStateOf(0) }
    var saves by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<JournalCatalogItem>>(emptyList()) }
    var answers by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var numeric by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    val dayKey = journalDayKey(dayOffset.toLong(), today)

    LaunchedEffect(dayKey, saves) {
        val imported = runCatching { viewModel.repo.journal("my-whoop", "0000-01-01", "9999-12-31") }.getOrDefault(emptyList())
        val native = runCatching { viewModel.repo.journal(JOURNAL_DEVICE_ID, dayKey, dayKey) }.getOrDefault(emptyList())
        items = resolveJournalItems(imported.map { it.question }.distinct(), loadJournalCatalogItems(context))
        answers = native.associate { it.question to it.answeredYes }
        numeric = native.mapNotNull { entry -> entry.numericValue?.let { entry.question to it } }.toMap()
        loaded = true
    }

    fun save(entry: JournalEntry?, question: String) {
        scope.launch {
            runCatching {
                if (entry == null) viewModel.repo.deleteJournalEntry(JOURNAL_DEVICE_ID, dayKey, question)
                else viewModel.repo.upsertJournal(listOf(entry))
            }
            saves++
        }
    }

    VivaDetailScaffold(
        title = "Journal", onBack = onBack, leading = VivaBarLeading.CLOSE,
        trailing = VivaBarTrailing.GEAR, onTrailing = onManage,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (6 downTo 0).forEach { offset ->
                DayPill(today.minusDays(offset.toLong()), offset == dayOffset, locale, Modifier.weight(1f)) { dayOffset = offset }
            }
        }
        val dayName = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ofPattern("MMMM d", locale))
        Text(
            if (dayOffset == 0) "What's happening today, $dayName?" else "What happened on $dayName?",
            Modifier.padding(vertical = 8.dp), style = VivaFont.sectionTitle, color = VivaStyle.textPrimary,
        )
        VivaLabel("Behaviors", Modifier.padding(top = 6.dp))
        if (loaded && items.isEmpty()) {
            VivaCallout("No behaviours to log yet. Add some with the settings button above.", fill = VivaStyle.card)
        }
        items.forEach { item ->
            val question = item.canonical
            BehaviorRow(item.displayName ?: question) {
                if (item.kind.isNumeric) {
                    val current = numeric[question]
                    // A numeric log also counts as a yes, the rule the Insights journal card writes by.
                    val log = { value: Double -> save(JournalEntry(JOURNAL_DEVICE_ID, dayKey, question, answeredYes = true, numericValue = value), question) }
                    SquareButton(Icons.Rounded.Remove, "Less", selected = false) { log(maxOf((current ?: 0.0) - 1, 0.0)) }
                    Text(
                        current?.let { if (it == Math.rint(it)) it.toLong().toString() else String.format(Locale.US, "%.1f", it) } ?: "--",
                        Modifier.widthIn(min = 30.dp), style = VivaFont.number(15), color = VivaStyle.textPrimary, textAlign = TextAlign.Center,
                    )
                    SquareButton(Icons.Rounded.Add, "More", selected = false) { log((current ?: 0.0) + 1) }
                } else {
                    listOf(false, true).forEach { value ->
                        val selected = answers[question] == value
                        SquareButton(
                            if (value) Icons.Rounded.Check else Icons.Rounded.Close, if (value) "Yes" else "No", selected,
                            selectedColor = if (value) VivaStyle.positive else VivaStyle.textPrimary,
                        ) {
                            // Tapping the chosen answer again takes it back.
                            save(if (selected) null else JournalEntry(JOURNAL_DEVICE_ID, dayKey, question, value), question)
                        }
                    }
                }
            }
        }
        Text(
            "DONE",
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .vivaPressable(onBack)
                .background(VivaStyle.textPrimary)
                .padding(vertical = 15.dp)
                .semantics { role = Role.Button },
            style = VivaFont.action, color = Color.Black, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayPill(day: LocalDate, isSelected: Boolean, locale: Locale, modifier: Modifier, onClick: () -> Unit) {
    val color = if (isSelected) VivaStyle.textPrimary else VivaStyle.textSecondary
    Column(
        modifier
            .height(62.dp)
            .clip(CircleShape)
            .vivaPressable(onClick)
            .background(VivaStyle.chip.copy(alpha = if (isSelected) 1f else 0.5f))
            .then(if (isSelected) Modifier.border(1.5.dp, VivaStyle.textPrimary, CircleShape) else Modifier)
            .semantics { selected = isSelected; role = Role.Tab },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = VivaFont.number(10, FontWeight.Medium), color = color)
        Text(day.dayOfMonth.toString(), style = VivaFont.number(13), color = color)
    }
}

@Composable
private fun BehaviorRow(label: String, controls: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .background(VivaStyle.card, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
        controls()
    }
}

@Composable
private fun SquareButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    selectedColor: Color = VivaStyle.textPrimary,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            // 44dp touch target around a 30dp square, so the small buttons are still easy to hit.
            .size(44.dp)
            .clip(shape)
            .vivaPressable(onClick)
            .semantics { contentDescription = label; this.selected = selected; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).background(if (selected) selectedColor else VivaStyle.inset, shape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, Modifier.size(14.dp), tint = if (selected) Color.Black else VivaStyle.textPrimary)
        }
    }
}
