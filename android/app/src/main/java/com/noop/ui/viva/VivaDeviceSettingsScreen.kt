package com.noop.ui.viva

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ble.PuffinExperiment
import com.noop.ble.SourceCoordinator
import com.noop.data.DeviceStatus
import com.noop.data.PairedDeviceRow
import com.noop.ui.AddDeviceWizard
import com.noop.ui.AppViewModel
import com.noop.ui.ClockPrefs
import com.noop.ui.ConfirmDialog
import com.noop.ui.RenameDialog
import com.noop.ui.displayName
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

// Twin of Strand/Screens/Device/VivaDeviceSettingsView.swift.

/**
 * Device Settings: which strap is connected, when it last synced, its charge, and the handful of
 * things done to a strap (rename, pair another, unpair, restart). Every action calls the function
 * the upstream Devices screen calls; this screen adds no strap command of its own. Firmware install
 * and erasing the strap's stored data are deliberately absent: neither can be undone.
 */
@Composable
fun VivaDeviceSettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onAllDevices: () -> Unit, onUseFileImport: () -> Unit) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<PairedDeviceRow>>(emptyList()) }
    suspend fun reload() {
        devices = runCatching { viewModel.pairedDevices() }.getOrDefault(emptyList())
    }
    LaunchedEffect(Unit) { reload() }

    val device = devices.firstOrNull { it.status == DeviceStatus.active.name }
    val canCommand = live.connected && live.bonded
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showAddWizard by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    val puffin = remember { PuffinExperiment.from(context) }
    var broadcastHr by remember { mutableStateOf(puffin.broadcastHr) }
    val lastSync = live.lastSyncAt?.let { VivaHomeMapper.clock(it, ZoneId.systemDefault(), Locale.getDefault(), ClockPrefs.uses24Hour(context)) } ?: "--"

    VivaDetailScaffold(
        title = "Device Settings", onBack = onBack, leading = VivaBarLeading.CLOSE,
        trailing = VivaBarTrailing.INFO, onTrailing = { showInfo = true },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VivaLabel(
                    if (live.connected) "Connected to" else "Not connected",
                    color = if (live.connected) VivaStyle.positive else VivaStyle.warning,
                )
                Row(
                    if (device != null) Modifier.clickable(onClickLabel = "Rename", role = Role.Button) { showRename = true } else Modifier,
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VivaCardTitle(device?.let(::displayName) ?: live.advertisingName ?: "No strap paired", maxLines = 1)
                    if (device != null) Icon(Icons.Outlined.Edit, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(14.dp))
                }
            }
            Row(
                (if (canCommand) Modifier.vivaPressable { viewModel.syncNow() } else Modifier.semantics { disabled() })
                    .semantics { contentDescription = "Sync now" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VivaLabel("Last sync")
                    Text(lastSync, style = VivaFont.number(11), color = VivaStyle.textPrimary)
                }
                Icon(
                    if (live.backfilling) Icons.Outlined.Sync else Icons.Outlined.CloudDone, contentDescription = null,
                    tint = VivaStyle.textSecondary, modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            TabButton("Status", selected = !advanced) { advanced = false }
            TabButton("Advanced", selected = advanced) { advanced = true }
        }

        if (!advanced) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VivaBandIllustration(Modifier.weight(1f).height(250.dp))
                // The charge is the strap's reading from this connection, so nothing shows once it drops.
                val charge = live.batteryPct?.takeIf { live.connected }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(
                            charge?.let { VivaScoreRing.roundHalfAwayFromZero(it).toString() } ?: "--",
                            Modifier.alignByBaseline(), style = VivaFont.number(30), color = VivaStyle.textPrimary,
                        )
                        if (charge != null) Text("%", Modifier.alignByBaseline(), style = VivaFont.number(15), color = VivaStyle.textPrimary)
                    }
                    device?.let { VivaLabel(it.model) }
                    if (live.connected && live.charging == true) VivaLabel("Charging", color = VivaStyle.positive)
                }
                BatteryBar(((charge ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat())
            }
            VivaCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(30.dp).background(VivaStyle.strain, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(14.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        VivaCardTitle("Broadcast heart rate")
                        VivaLabel("To compatible apps and devices")
                    }
                    Switch(
                        checked = broadcastHr,
                        // The same reversible strap setting the upstream Settings switch writes, through the same call.
                        onCheckedChange = {
                            broadcastHr = it
                            puffin.broadcastHr = it
                            viewModel.ble.setBroadcastHr(it)
                        },
                        enabled = canCommand,
                        modifier = Modifier.semantics { contentDescription = "Broadcast heart rate" },
                        colors = SwitchDefaults.colors(checkedTrackColor = VivaStyle.positive, checkedThumbColor = VivaStyle.textPrimary),
                    )
                }
            }
            Text(
                if (canCommand) {
                    "Lets the strap show up as a standard heart-rate sensor for bike computers, gym equipment and training apps. Experimental on this strap."
                } else {
                    "Connect your strap to change this."
                },
                style = VivaFont.caption, color = VivaStyle.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Fact(Icons.Outlined.Tag, "Device ID", device?.id ?: "--", Modifier.weight(1f))
                Fact(Icons.Outlined.Memory, "Firmware", live.strapFirmware ?: "--", Modifier.weight(1f))
            }
            SettingRow(Icons.Outlined.AddLink, "Pair a device", "Pair a strap with Viva. You can keep more than one and choose which is active.") {
                showAddWizard = true
            }
            SettingRow(
                Icons.Outlined.LinkOff, "Unpair device",
                "Stop connecting to this strap and release its Bluetooth link. Recorded data is kept.", enabled = device != null,
            ) { confirmUnpair = true }
            // Offered on the terms the upstream Devices screen sets: a connected strap that is not a
            // WHOOP 4.0, which has no frame that restarts it safely.
            SettingRow(
                Icons.Outlined.PowerSettingsNew, "Restart device",
                "Restart the strap. It reconnects on its own after about 30 seconds.",
                enabled = canCommand && !live.rebootInProgress && live.whoop5Detected && device != null && SourceCoordinator.isWhoop(device),
            ) { confirmRestart = true }
            SettingRow(
                Icons.Outlined.Devices, "All devices",
                "Switch the active strap, see every paired device and manage its recorded data.", onClick = onAllDevices,
            )
            Text(
                "Viva never installs firmware on the strap and never erases the data stored on it.",
                style = VivaFont.caption, color = VivaStyle.textTertiary, modifier = Modifier.padding(horizontal = 6.dp).padding(top = 10.dp),
            )
        }
    }

    if (showInfo) {
        VivaInfoSheet(
            title = "Device & Battery",
            paragraphs = listOf(
                "The percentage on Device Settings is the strap's own reading from the current connection. Viva shows it only while the strap is connected, so you never see a stale figure.",
                "50% and up is plenty of charge. From 20% to 50%, charge soon. Under 20%, charge now.",
                "Charge before sleep. A strap that runs flat overnight records no sleep, and without a night's sleep there is no Recovery score the next day.",
                "Sync after charging. Open Viva with the strap nearby so the hours it stored on its own are copied to your phone.",
                "One phone at a time. A strap bonds to one device. Unpair it here before pairing it with another phone or app.",
            ),
            onDismiss = { showInfo = false },
        )
    }
    if (showAddWizard) {
        AddDeviceWizard(
            viewModel = viewModel,
            onClose = { showAddWizard = false; scope.launch { reload() } },
            onUseFileImport = { showAddWizard = false; onUseFileImport() },
        )
    }
    if (showRename && device != null) {
        RenameDialog(
            device = device,
            onSave = { name ->
                scope.launch { viewModel.renamePairedDevice(device.id, name); reload() }
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (confirmUnpair && device != null) {
        ConfirmDialog(
            title = "Unpair this device?",
            message = "Viva will stop connecting to ${displayName(device)}. Its recorded data is kept and you can pair it again any time.",
            confirmLabel = "Unpair",
            destructive = true,
            onConfirm = {
                confirmUnpair = false
                scope.launch {
                    // The same call the Devices screen makes: release the Bluetooth link, archive the row.
                    viewModel.archivePairedDevice(device.id)
                    reload()
                    // No strap is active now; if others are paired, the Devices screen is where one is chosen.
                    if (devices.any { it.status != DeviceStatus.archived.name }) onAllDevices()
                }
            },
            onDismiss = { confirmUnpair = false },
        )
    }
    if (confirmRestart) {
        ConfirmDialog(
            title = "Restart this strap?",
            message = "It disconnects for about 30 seconds while it restarts, then reconnects on its own. Your recorded data is kept.",
            confirmLabel = "Restart",
            onConfirm = { viewModel.rebootStrap(); confirmRestart = false },
            onDismiss = { confirmRestart = false },
        )
    }
}

@Composable
private fun TabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(IntrinsicSize.Max).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        VivaLabel(title, color = if (selected) VivaStyle.textPrimary else VivaStyle.textTertiary)
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (selected) VivaStyle.textPrimary else Color.Transparent))
    }
}

/** The charge as a thin upright bar: green from half, yellow from a fifth, red under it. */
@Composable
private fun BatteryBar(fraction: Float) {
    val color = if (fraction >= 0.5f) VivaStyle.recoveryGreen else if (fraction >= 0.2f) VivaStyle.recoveryYellow else VivaStyle.recoveryRed
    Box(Modifier.width(3.dp).height(86.dp).background(VivaStyle.ringTrack, CircleShape).clearAndSetSemantics {}, contentAlignment = Alignment.BottomCenter) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(color, CircleShape))
    }
}

@Composable
private fun Fact(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = VivaStyle.textTertiary, modifier = Modifier.size(18.dp))
        VivaLabel(label)
        Text(value, style = VivaFont.number(11), color = VivaStyle.textPrimary, maxLines = 1)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(VivaStyle.chip, RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.vivaPressable(onClick) else Modifier.semantics { disabled() })
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(20.dp))
        VivaCardTitle(title, color = if (enabled) VivaStyle.textPrimary else VivaStyle.textTertiary)
    }
    Text(detail, style = VivaFont.caption, color = VivaStyle.textSecondary, modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp))
}

/** A plain drawn band, not a product photograph: a strap loop with its sensor clasp. */
@Composable
fun VivaBandIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds().clearAndSetSemantics {}) {
        val tilt = -24f
        val loop = Size(size.width * 0.62f, size.height * 0.86f)
        rotate(tilt) {
            drawRoundRect(
                Brush.verticalGradient(listOf(VivaStyle.chipActive, VivaStyle.inset)),
                topLeft = Offset((size.width - loop.width) / 2f, (size.height - loop.height) / 2f), size = loop,
                cornerRadius = CornerRadius(size.height * 0.34f), style = Stroke(size.height * 0.2f),
            )
        }
        val clasp = Size(size.width * 0.36f, size.height * 0.2f)
        val centre = Offset(size.width * 0.7f, size.height * 0.77f)
        val corner = CornerRadius(9.dp.toPx())
        rotate(tilt, pivot = centre) {
            val topLeft = Offset(centre.x - clasp.width / 2f, centre.y - clasp.height / 2f)
            drawRoundRect(
                Brush.verticalGradient(listOf(VivaStyle.chipActive, VivaStyle.chip), startY = topLeft.y, endY = topLeft.y + clasp.height),
                topLeft = topLeft, size = clasp, cornerRadius = corner,
            )
            drawRoundRect(VivaStyle.textTertiary.copy(alpha = 0.5f), topLeft = topLeft, size = clasp, cornerRadius = corner, style = Stroke(1.dp.toPx()))
        }
    }
}
