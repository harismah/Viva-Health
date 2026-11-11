package com.noop.ui.viva

import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.alarm.SmartAlarmStore
import com.noop.ui.AppViewModel
import com.noop.ui.BrandMark
import com.noop.ui.ClockPrefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Sleep Planner: when to go to bed for the wake time that is set, and the alarm that will wake you.
 * Every time on this screen comes from one [vivaAlarmReadout], the same value Home's Tonight card
 * renders, and the controls write the same settings the Alarms screen does. Twin of the iPhone
 * `VivaSleepPlannerView`; on Android the alarm it reads and writes is the phone alarm.
 */
@Composable
fun VivaSleepPlannerScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onAlarmSettings: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val is24Hour = ClockPrefs.uses24Hour(context)
    val enabled by viewModel.phoneAlarmEnabled.collectAsStateWithLifecycle()
    val target by viewModel.phoneAlarmTargetMinutes.collectAsStateWithLifecycle()
    // Re-resolved whenever a control here changes the stored settings; the setters re-arm before returning.
    val readout = remember(enabled, target, is24Hour) { vivaAlarmReadout(context, zone, is24Hour) }
    val bed = VivaAlarmReadout.clockParts(readout.bedtimeMinutes, locale, is24Hour)
    val wake = VivaAlarmReadout.clockParts(readout.wakeMinutes, locale, is24Hour)
    var showInfo by remember { mutableStateOf(false) }

    val pickWakeTime = {
        // A per-day override makes "the" wake time several; only the Alarms screen can edit those.
        val store = SmartAlarmStore.from(context)
        if (store.targetOverrides.isNotEmpty()) {
            onAlarmSettings()
        } else {
            // The picker edits the time this screen shows: the guaranteed alarm, a window after the stored earliest wake.
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    viewModel.setPhoneAlarmTargetMinutes(Math.floorMod(hour * 60 + minute - store.windowMinutes, SmartAlarmStore.MINUTES_PER_DAY))
                },
                readout.wakeMinutes / 60, readout.wakeMinutes % 60, is24Hour,
            ).show()
        }
    }

    VivaDetailScaffold(
        title = "Sleep Planner", onBack = onBack, leading = VivaBarLeading.CLOSE,
        trailing = VivaBarTrailing.INFO, onTrailing = { showInfo = true },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val statusColor = if (readout.isArmed) VivaStyle.positive else VivaStyle.textSecondary
            Text(
                if (readout.isArmed) "ON" else "OFF",
                Modifier
                    .align(Alignment.End)
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = VivaFont.number(9), color = statusColor,
            )
            BrandMark(size = 22.dp)
            Text(
                "Go to bed at ${readout.bedtimeText} to get your ${readout.timeInBedText} in bed before your ${readout.wakeText} wake time.",
                Modifier.padding(horizontal = 20.dp),
                style = VivaFont.bodyStrong, color = VivaStyle.textPrimary, textAlign = TextAlign.Center,
            )
        }
        VivaDivider()
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 26.dp)) {
            TimeColumn(bed, "Suggested time to bed", Alignment.Start)
            Spacer(Modifier.weight(1f))
            TimeColumn(wake, "Your wake time", Alignment.End)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VivaLabel("Time in bed", color = VivaStyle.textPrimary)
            Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
                Hatch(Modifier.fillMaxWidth().height(14.dp))
                Box(Modifier.align(Alignment.CenterStart).width(1.dp).height(26.dp).background(VivaStyle.textPrimary))
                Box(Modifier.align(Alignment.CenterEnd).width(1.dp).height(26.dp).background(VivaStyle.textPrimary))
                Text(
                    readout.timeInBedText,
                    Modifier
                        .background(VivaStyle.canvasBottom, CircleShape)
                        .border(1.5.dp, VivaStyle.textPrimary, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    style = VivaFont.number(12), color = VivaStyle.textPrimary,
                )
            }
        }
        AlarmPanel(
            readout = readout, enabled = enabled, zone = zone, locale = locale,
            // Enabling without the exact-alarm permission is refused; the Alarms screen owns that prompt.
            onToggle = { on -> if (!viewModel.setPhoneAlarmEnabled(on)) onAlarmSettings() },
            onWake = pickWakeTime,
            modifier = Modifier.padding(top = 24.dp),
        )
        VivaCard(padding = 14.dp, onClick = onAlarmSettings) {
            VivaMetricRow(Icons.Outlined.Tune, "Alarm days and wind-down", "", showsChevron = true)
        }
        if (!readout.isArmed && enabled) {
            VivaCallout("The alarm is switched on but is not scheduled yet. Keep a backup alarm.")
        }
    }

    if (showInfo) {
        VivaInfoSheet(
            title = "How the Sleep Planner works",
            paragraphs = listOf(
                "Your suggested bedtime is your wake time minus the time in bed you need.",
                "The alarm is scheduled on this phone, so it rings at your wake time whether or not your strap is connected. Keep a backup alarm for anything you cannot miss.",
            ),
            onDismiss = { showInfo = false },
        )
    }
}

@Composable
private fun TimeColumn(parts: Pair<String, String>, caption: String, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(parts.first, Modifier.alignByBaseline(), style = VivaFont.number(26), color = VivaStyle.textPrimary)
            Text(parts.second, Modifier.alignByBaseline(), style = VivaFont.number(11), color = VivaStyle.textPrimary)
        }
        Text(
            caption.uppercase(), Modifier.widthIn(max = 110.dp),
            style = VivaFont.label, color = VivaStyle.textSecondary,
            textAlign = if (alignment == Alignment.Start) TextAlign.Start else TextAlign.End,
        )
    }
}

/** Diagonal strokes filling the bar that stands for the hours in bed. */
@Composable
private fun Hatch(modifier: Modifier) {
    val color = VivaStyle.textSecondary
    Canvas(modifier.clipToBounds()) {
        val step = 6.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
            x += step
        }
    }
}

@Composable
private fun AlarmPanel(
    readout: VivaAlarmReadout,
    enabled: Boolean,
    zone: ZoneId,
    locale: Locale,
    onToggle: (Boolean) -> Unit,
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(VivaStyle.card, RoundedCornerShape(22.dp)).padding(VivaStyle.cardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, Modifier.size(22.dp), tint = VivaStyle.textPrimary)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                VivaCardTitle("Alarm")
                readout.nextFireEpochMs?.let { fire ->
                    val weekday = Instant.ofEpochMilli(fire).atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    VivaLabel("For $weekday")
                }
            }
            Switch(
                checked = enabled, onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "Alarm" },
                colors = SwitchDefaults.colors(checkedTrackColor = VivaStyle.positive, checkedThumbColor = VivaStyle.textPrimary),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
            Tile("Alarm set to", readout.modeText, Modifier.weight(1f), onClick = null)
            Tile("Wake time set to", readout.wakeText, Modifier.weight(1f), onClick = onWake)
        }
    }
}

@Composable
private fun Tile(caption: String, value: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .height(54.dp)
            .clip(shape)
            .then(if (onClick != null) Modifier.vivaPressable(onClick) else Modifier)
            .background(VivaStyle.inset)
            .border(1.dp, VivaStyle.divider, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        VivaLabel(caption)
        VivaCardTitle(value)
    }
}
