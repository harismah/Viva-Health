package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * DEBUG aid, the Android answer to the iPhone app's `--demo-screen`: every Viva component on one
 * page with fixed sample values, so each can be compared against its iPhone twin on a real phone.
 * Nothing here reads the database. Open it with:
 *
 *     adb shell am start -n <applicationId>/com.noop.IconDefault --es viva_demo_screen gallery
 */
@Composable
fun VivaGalleryScreen(onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        VivaDetailScaffold(
            title = "Components",
            onBack = onClose,
            trailing = VivaBarTrailing.INFO,
            onTrailing = {},
        ) {
            Wordmark()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingWithLabel("Sleep") {
                    VivaScoreRing(85.0, 100.0, VivaStyle.sleep, VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL, label = "Sleep")
                }
                RingWithLabel("Recovery") {
                    VivaScoreRing(72.0, 100.0, VivaStyle.recoveryColor(72.0), VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL, label = "Recovery")
                }
                RingWithLabel("Strain") {
                    VivaScoreRing(7.5, 21.0, VivaStyle.strain, VivaScoreRing.Format.DECIMAL1, VivaScoreRing.Size.SMALL, label = "Strain")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                VivaScoreRing(45.0, 100.0, VivaStyle.recoveryColor(45.0), VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL)
                VivaScoreRing(21.0, 100.0, VivaStyle.recoveryColor(21.0), VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL)
                VivaScoreRing(null, 100.0, VivaStyle.recoveryCalibrating, VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.SMALL)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                VivaScoreRing(85.0, 100.0, VivaStyle.sleep, VivaScoreRing.Format.PERCENT, VivaScoreRing.Size.LARGE, label = "Sleep Performance")
            }

            VivaCallout("Recovery is calibrating. Wear your strap for 4 nights to unlock your score.", filled = 2, total = 4)

            Row(horizontalArrangement = Arrangement.spacedBy(VivaStyle.cardGap)) {
                VivaVitalsTile(Icons.Outlined.MonitorHeart, "Health Monitor", "5/5", status = "Within range", statusColor = VivaStyle.positive, modifier = Modifier.weight(1f)) {}
                VivaVitalsTile(Icons.Outlined.Air, "Stress Monitor", "1.2", status = "Low", modifier = Modifier.weight(1f)) {}
            }

            Spacer(Modifier.height(VivaStyle.sectionGap - VivaStyle.cardGap))
            VivaSectionHeader("My Day", action = VivaSectionAction("Add", VivaSectionAction.Style.PLUS) {})
            VivaCard(title = "Tonight's Sleep", accessory = VivaCardAccessory.CHEVRON, onClick = {}) {
                val readout = VivaAlarmReadout.resolve(nextFireEpochMs = null, baseWakeMinutes = 7 * 60, sleepNeedMinutes = 8 * 60 + 30)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ClockColumn("Bedtime", readout.bedtimeMinutes)
                    ClockColumn("Wake", readout.wakeMinutes)
                    Column {
                        VivaLabel("Alarm")
                        Text(readout.statusText, style = VivaFont.bodyStrong, color = VivaStyle.textPrimary)
                    }
                }
                VivaCardButton("Edit alarm", icon = Icons.Outlined.Alarm) {}
            }

            Spacer(Modifier.height(VivaStyle.sectionGap - VivaStyle.cardGap))
            VivaSectionHeader("My Dashboard", action = VivaSectionAction("Customize", VivaSectionAction.Style.CHIP) {})
            VivaCard {
                VivaMetricRow(Icons.Outlined.MonitorHeart, "HRV", "71", unit = "ms", baseline = "68", trend = VivaTrend.UP)
                VivaDivider()
                VivaMetricRow(Icons.Outlined.FavoriteBorder, "Resting heart rate", "55", unit = "bpm", baseline = "54", trend = VivaTrend.DOWN)
                VivaDivider()
                VivaMetricRow(Icons.Outlined.WaterDrop, "Blood oxygen", "97", unit = "%", baseline = "97", trend = VivaTrend.FLAT)
                VivaDivider()
                VivaMetricRow(Icons.Outlined.Thermostat, "Skin temperature", "--", trend = VivaTrend.NONE)
                VivaDivider()
                VivaMetricRow(Icons.Outlined.NightsStay, "Sleep details", "", showsChevron = true)
            }

            VivaCard(title = "Stages vs. typical", accessory = VivaCardAccessory.INFO) {
                VivaStageRow(VivaStyle.stageAwake, "Awake", 8, "0:36", value = 36.0, maximum = 240.0, range = 20.0..45.0)
                VivaStageRow(VivaStyle.stageLight, "Light", 49, "3:42", value = 222.0, maximum = 240.0, range = 180.0..230.0)
                VivaStageRow(VivaStyle.stageDeep, "Deep", 19, "1:27", value = 87.0, maximum = 240.0, range = 70.0..110.0)
                VivaStageRow(VivaStyle.stageREM, "REM", 24, "1:48", value = 108.0, maximum = 240.0)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VivaLevelStrip(VivaLevel.POOR)
                    VivaLevelStrip(VivaLevel.SUFFICIENT)
                    VivaLevelStrip(VivaLevel.OPTIMAL)
                    VivaLevelStrip(null)
                }
                VivaLinkLabel("Open sleep details")
            }

            VivaWeekBarCard("Strain", weekDays(listOf(8.2, 12.4, 5.1, 14.8, 10.3, 6.7, 7.5)) { VivaStyle.strain }, onClick = {})
            VivaWeekBarCard(
                title = "Hours of sleep",
                days = stackedDays(),
                style = VivaWeekBarCard.Style.STACKED,
                legend = listOf("Light" to VivaStyle.stageLight, "Deep" to VivaStyle.stageDeep, "REM" to VivaStyle.stageREM),
            )
            VivaWeekBarCard("Resting heart rate", weekDays(listOf(54.0, 56.0, 55.0, 53.0, 57.0, 55.0, 55.0)) { VivaStyle.textPrimary }, style = VivaWeekBarCard.Style.DOT)
            VivaWeekBarCard("Skin temperature", weekDays(emptyList()) { VivaStyle.textPrimary })

            VivaCard(title = "Stress Monitor", accessory = VivaCardAccessory.EXPAND) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    VivaHalfDial(value = 1.2, maximum = 3.0, caption = "Low", time = "Last updated 1:44 PM")
                }
            }

            VivaCoachBar("Your recovery is in the green today.") {}
            Spacer(Modifier.height(VivaTabBar.height + 24.dp))
        }
        VivaTabBar(
            selection = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun Wordmark() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text("VIVA", style = VivaFont.wordmark, color = VivaStyle.textPrimary)
    }
}

@Composable
private fun RingWithLabel(label: String, ring: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ring()
        VivaCardTitle("$label  ›")
    }
}

@Composable
private fun ClockColumn(label: String, minutes: Int) {
    val (time, period) = VivaAlarmReadout.clockParts(minutes)
    Column {
        VivaLabel(label)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(time, Modifier.alignByBaseline(), style = VivaFont.valueLarge, color = VivaStyle.textPrimary)
            if (period.isNotEmpty()) Text(period, Modifier.alignByBaseline(), style = VivaFont.unit, color = VivaStyle.textPrimary)
        }
    }
}

private val dayLabels = listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue")

private fun weekDays(values: List<Double>, color: () -> androidx.compose.ui.graphics.Color): List<VivaWeekBarCard.Day> =
    dayLabels.mapIndexed { index, label ->
        val value = values.getOrNull(index)
        VivaWeekBarCard.Day(
            label = label,
            date = 4 + index,
            segments = if (value == null) emptyList() else listOf(VivaWeekBarCard.Segment(value, color())),
            valueText = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
            isToday = index == dayLabels.lastIndex,
        )
    }

private fun stackedDays(): List<VivaWeekBarCard.Day> {
    val nights = listOf(
        Triple(3.4, 1.2, 1.6), Triple(3.9, 1.5, 1.9), Triple(2.8, 1.0, 1.3), Triple(4.1, 1.6, 2.0),
        Triple(3.6, 1.3, 1.7), Triple(3.2, 1.1, 1.5), Triple(3.7, 1.45, 1.8),
    )
    return dayLabels.mapIndexed { index, label ->
        val (light, deep, rem) = nights[index]
        val total = light + deep + rem
        val hours = total.toInt()
        val minutes = ((total - hours) * 60).toInt()
        VivaWeekBarCard.Day(
            label = label,
            date = 4 + index,
            segments = listOf(
                VivaWeekBarCard.Segment(light, VivaStyle.stageLight),
                VivaWeekBarCard.Segment(deep, VivaStyle.stageDeep),
                VivaWeekBarCard.Segment(rem, VivaStyle.stageREM),
            ),
            valueText = "%d:%02d".format(hours, minutes),
            isToday = index == dayLabels.lastIndex,
        )
    }
}

/** Routes a `viva_demo_screen` name to its screen; an unknown name falls back to the gallery. */
@Composable
fun VivaDemoScreen(name: String, onClose: () -> Unit) {
    when (name) {
        "home" -> VivaHomeDemoScreen()
        else -> VivaGalleryScreen(onClose)
    }
}

/**
 * The home layout over fixed sample values, with the tab bar and the pinned ring strip, so the
 * screen can be judged against the iPhone build before any real data is wired to it.
 */
@Composable
fun VivaHomeDemoScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val pinThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 190.dp.toPx() }
    VivaCanvas {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = VivaStyle.pagePadding)
                .padding(top = 8.dp, bottom = VivaTabBar.height + 40.dp)
                .navigationBarsPadding(),
        ) {
            VivaHomeContent(sampleHome(), VivaHomeActions())
        }
        if (scroll.value > pinThreshold) {
            Column(Modifier.align(Alignment.TopCenter).background(VivaStyle.canvasTop).statusBarsPadding()) {
                VivaHomePinnedRings(sampleHome(), onOpen = {})
            }
        }
        VivaTabBar(
            selection = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
        )
    }
}

private fun sampleHome(): VivaHomeData {
    val recovery = listOf(62.0, 81.0, 45.0, 30.0, 74.0, 88.0, 49.0)
    val strain = listOf(8.2, 12.4, 5.1, 14.8, 10.3, 6.7, 7.2)
    return VivaHomeData(
        dayTitle = "Today",
        canGoBack = true,
        canGoForward = false,
        sleepPercent = 93.0,
        recoveryPercent = 49.0,
        strain = 7.2,
        sleepDuration = "7:32",
        healthStatus = "5 of 5 vitals in",
        stress = 1.1,
        stressLabel = "Medium",
        activities = listOf(
            VivaHomeData.Activity("sleep", Icons.Outlined.NightsStay, "Sleep", "7:32", "11:05 PM", "6:38 AM", VivaStyle.sleep),
            VivaHomeData.Activity("lift", Icons.Outlined.FavoriteBorder, "Weightlifting", "11.9", "8:22 PM", "9:05 PM", VivaStyle.strain),
        ),
        alarm = VivaAlarmReadout.resolve(nextFireEpochMs = null, baseWakeMinutes = 7 * 60, sleepNeedMinutes = 8 * 60),
        isToday = true,
        coachEnabled = true,
        calibrationNights = 3,
        metrics = listOf(
            VivaHomeData.Metric("hrv", Icons.Outlined.MonitorHeart, "Heart rate variability", "59", null, "47", VivaTrend.UP, opens = true),
            VivaHomeData.Metric("rhr", Icons.Outlined.FavoriteBorder, "Resting heart rate", "54", null, "57", VivaTrend.DOWN, opens = true),
            VivaHomeData.Metric("spo2", Icons.Outlined.WaterDrop, "Blood oxygen", "97", "%", "97", VivaTrend.FLAT, opens = true),
            VivaHomeData.Metric("temp", Icons.Outlined.Thermostat, "Skin temperature", "--", null, null, VivaTrend.NONE, opens = false),
        ),
        week = dayLabels.mapIndexed { index, label ->
            VivaHomeData.WeekDay(label, label, 4 + index, strain[index], recovery[index], index == dayLabels.lastIndex)
        },
        journal = dayLabels.mapIndexed { index, label ->
            VivaHomeData.JournalDay(label, label, logged = index < 5, isToday = index == dayLabels.lastIndex)
        },
    )
}
