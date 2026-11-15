#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore

/// Device Settings: which strap is connected, when it last synced, its charge, and the handful of
/// things you do to a strap (rename, pair another, unpair, restart). Every action calls the same
/// function the Devices screen calls; this screen adds no strap command of its own.
///
/// Deliberately absent: firmware install and erasing the strap's stored data. Both alter the strap
/// in ways that cannot be undone, which this app never does.
struct VivaDeviceSettingsView: View {
    enum Tab { case status, advanced }

    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var live: LiveState
    @Environment(\.dismiss) private var dismiss
    @AppStorage(PuffinExperiment.broadcastHrKey) private var broadcastHrEnabled = false

    @State private var tab: Tab = VivaDemo.anchor == "advanced" ? .advanced : .status
    @State private var showInfo = false
    @State private var showAddWizard = false
    @State private var showRename = false
    @State private var renameDraft = ""
    @State private var confirmUnpair = false
    @State private var confirmRestart = false

    var body: some View {
        Group {
            if let registry = model.deviceRegistry {
                content(registry: registry)
            } else {
                VivaDetailScaffold(title: "Device Settings", leading: .close) {
                    VivaCallout(text: String(localized: "Opening your on-device data. Your strap will appear here in a moment."))
                }
            }
        }
    }

    private func content(registry: DeviceRegistry) -> some View {
        VivaDeviceSettingsContent(registry: registry, tab: $tab, showInfo: $showInfo,
                                  showAddWizard: $showAddWizard, showRename: $showRename,
                                  renameDraft: $renameDraft, confirmUnpair: $confirmUnpair,
                                  confirmRestart: $confirmRestart, broadcastHrEnabled: $broadcastHrEnabled)
    }
}

private struct VivaDeviceSettingsContent: View {
    @ObservedObject var registry: DeviceRegistry
    @Binding var tab: VivaDeviceSettingsView.Tab
    @Binding var showInfo: Bool
    @Binding var showAddWizard: Bool
    @Binding var showRename: Bool
    @Binding var renameDraft: String
    @Binding var confirmUnpair: Bool
    @Binding var confirmRestart: Bool
    @Binding var broadcastHrEnabled: Bool

    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var live: LiveState

    private var device: PairedDevice? { registry.devices.first { $0.status == .active } }
    private var canCommand: Bool { live.connected && live.bonded }

    private var lastSyncText: String {
        guard let at = live.lastSyncedAt else { return "--" }
        return Date(timeIntervalSince1970: at)
            .formatted(.dateTime.hour().minute().locale(AppLanguage.activeLocale))
    }

    var body: some View {
        VivaDetailScaffold(title: "Device Settings", leading: .close, trailing: .info,
                           onTrailing: { showInfo = true }) {
            header
            tabs
            switch tab {
            case .status: status
            case .advanced: advanced
            }
        }
        .fullScreenCover(isPresented: $showInfo) { VivaDeviceBatteryInfoView() }
        .sheet(isPresented: $showAddWizard) { AddDeviceWizard(live: live) { showAddWizard = false } }
        .alert("Rename device", isPresented: $showRename) {
            TextField("Name", text: $renameDraft)
            Button("Cancel", role: .cancel) {}
            Button("Save") { if let device { registry.rename(device.id, to: renameDraft) } }
        } message: {
            Text("Give your strap a name you'll recognise.")
        }
        .alert("Unpair this device?", isPresented: $confirmUnpair) {
            Button("Cancel", role: .cancel) {}
            Button("Unpair", role: .destructive) {
                guard let device else { return }
                // The same two steps the Devices screen takes: release the Bluetooth link so the strap
                // can pair elsewhere, then archive the registry row. Recorded data is kept.
                model.ble.forgetDevice(device.peripheralId)
                registry.archive(device.id)
            }
        } message: {
            Text("Viva will stop connecting to \(device?.displayName ?? String(localized: "this strap")). Its recorded data is kept and you can pair it again any time.")
        }
        .alert("Restart this strap?", isPresented: $confirmRestart) {
            Button("Cancel", role: .cancel) {}
            Button("Restart") { model.rebootStrap() }
        } message: {
            Text("It disconnects for about 30 seconds while it restarts, then reconnects on its own. Your recorded data is kept.")
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(live.connected ? "Connected to" : "Not connected")
                    .font(.system(size: 9, weight: .bold)).tracking(1).textCase(.uppercase)
                    .foregroundStyle(live.connected ? VivaStyle.positive : VivaStyle.warning)
                Button {
                    renameDraft = device?.nickname ?? ""
                    showRename = true
                } label: {
                    HStack(spacing: 8) {
                        Text(verbatim: device?.displayName ?? live.advertisingName ?? String(localized: "No strap paired"))
                            .font(.system(size: 14, weight: .bold)).tracking(1.2).textCase(.uppercase)
                            .foregroundStyle(VivaStyle.textPrimary)
                            .lineLimit(1).minimumScaleFactor(0.7)
                        if device != nil {
                            Image(systemName: "pencil").font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(VivaStyle.textSecondary)
                        }
                    }
                }
                .buttonStyle(.plain)
                .disabled(device == nil)
                .accessibilityHint(Text("Rename"))
            }
            Spacer(minLength: 12)
            Button { model.ble.syncNow() } label: {
                HStack(spacing: 8) {
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("Last sync").vivaLabel()
                        Text(lastSyncText).font(VivaFont.number(11)).foregroundStyle(VivaStyle.textPrimary)
                    }
                    Image(systemName: live.backfilling ? "arrow.triangle.2.circlepath" : "checkmark.icloud")
                        .font(.system(size: 17, weight: .light)).foregroundStyle(VivaStyle.textSecondary)
                }
            }
            .buttonStyle(VivaPressStyle())
            .disabled(!canCommand)
            .accessibilityLabel(Text("Sync now"))
            .accessibilityValue(Text(lastSyncText))
        }
        .padding(.horizontal, 4)
        .padding(.top, 10)
    }

    private var tabs: some View {
        HStack(spacing: 28) {
            tabButton("Status", .status)
            tabButton("Advanced", .advanced)
            Spacer()
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
    }

    private func tabButton(_ title: LocalizedStringKey, _ value: VivaDeviceSettingsView.Tab) -> some View {
        Button { tab = value } label: {
            VStack(spacing: 5) {
                Text(title).font(.system(size: 10, weight: .bold)).tracking(1.2).textCase(.uppercase)
                    .foregroundStyle(tab == value ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                Rectangle().fill(tab == value ? VivaStyle.textPrimary : Color.clear).frame(height: 1.5)
            }
            .fixedSize()
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(tab == value ? .isSelected : [])
    }

    // MARK: Status

    @ViewBuilder private var status: some View {
        HStack(alignment: .bottom, spacing: 12) {
            VivaBandIllustration()
                .frame(maxWidth: .infinity)
                .frame(height: 250)
            VStack(alignment: .trailing, spacing: 4) {
                HStack(alignment: .firstTextBaseline, spacing: 1) {
                    Text(live.connected ? (live.batteryPct.map { String(Int($0.rounded())) } ?? "--") : "--")
                        .font(VivaFont.number(30)).foregroundStyle(VivaStyle.textPrimary)
                    if live.connected, live.batteryPct != nil {
                        Text("%").font(VivaFont.number(15)).foregroundStyle(VivaStyle.textPrimary)
                    }
                }
                if let device {
                    Text(verbatim: device.model).vivaLabel().lineLimit(1)
                }
                if live.connected, live.charging == true {
                    Text("Charging").vivaLabel(VivaStyle.positive)
                }
            }
            batteryBar
        }
        .padding(.vertical, 8)
        VivaCard(padding: 12) {
            HStack(spacing: 12) {
                Image(systemName: "heart.fill").font(.system(size: 13)).foregroundStyle(VivaStyle.textPrimary)
                    .frame(width: 30, height: 30)
                    .background(VivaStyle.strain, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text("Broadcast heart rate").vivaCardTitle()
                    Text("To compatible apps and devices").vivaLabel()
                }
                Spacer(minLength: 4)
                Toggle("", isOn: $broadcastHrEnabled).labelsHidden().tint(VivaStyle.positive)
                    .disabled(!canCommand)
                    .accessibilityLabel(Text("Broadcast heart rate"))
            }
        }
        // The same reversible strap setting the Test Centre toggle writes, through the same call.
        .onChangeCompat(of: broadcastHrEnabled) { model.ble.setBroadcastHr($0) }
        Text(canCommand
             ? "Lets the strap show up as a standard heart-rate sensor for bike computers, gym equipment and training apps. Experimental on this strap."
             : "Connect your strap to change this.")
            .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
            .padding(.horizontal, 4)
    }

    private var batteryBar: some View {
        let fraction = live.connected ? min(max((live.batteryPct ?? 0) / 100, 0), 1) : 0
        let color: Color = fraction >= 0.5 ? VivaStyle.recoveryGreen : (fraction >= 0.2 ? VivaStyle.recoveryYellow : VivaStyle.recoveryRed)
        return GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Capsule().fill(VivaStyle.ringTrack)
                Capsule().fill(color).frame(height: proxy.size.height * CGFloat(fraction))
            }
        }
        .frame(width: 3, height: 86)
        .accessibilityHidden(true)
    }

    // MARK: Advanced

    @ViewBuilder private var advanced: some View {
        HStack(spacing: 0) {
            fact("number", String(localized: "Device ID"), device?.id ?? "--")
            fact("cpu", String(localized: "Firmware"), live.strapFirmware ?? "--")
        }
        .padding(.vertical, 14)
        row("link.badge.plus", String(localized: "Pair a device"),
            String(localized: "Pair a strap with Viva. You can keep more than one and choose which is active.")) {
            showAddWizard = true
        }
        row("link", String(localized: "Unpair device"),
            String(localized: "Stop connecting to this strap and release its Bluetooth link. Recorded data is kept."),
            enabled: device != nil) { confirmUnpair = true }
        row("power", String(localized: "Restart device"),
            String(localized: "Restart the strap. It reconnects on its own after about 30 seconds."),
            enabled: canCommand && !live.rebootInProgress) { confirmRestart = true }
        NavigationLink {
            DevicesView()
                .background(VivaCanvas())
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(.hidden, for: .navigationBar)
        } label: {
            rowLabel("list.bullet", String(localized: "All devices"), enabled: true)
        }
        .buttonStyle(VivaPressStyle())
        caption(String(localized: "Switch the active strap, see every paired device and manage its recorded data."))
        Text("Viva never installs firmware on the strap and never erases the data stored on it.")
            .font(VivaFont.caption).foregroundStyle(VivaStyle.textTertiary)
            .padding(.horizontal, 6).padding(.top, 10)
    }

    private func fact(_ icon: String, _ label: String, _ value: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 16, weight: .light)).foregroundStyle(VivaStyle.textTertiary)
            Text(label).vivaLabel()
            Text(verbatim: value).font(VivaFont.number(11)).foregroundStyle(VivaStyle.textPrimary)
                .lineLimit(1).minimumScaleFactor(0.6)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private func row(_ icon: String, _ title: String, _ detail: String, enabled: Bool = true,
                                  action: @escaping () -> Void) -> some View {
        Button(action: action) { rowLabel(icon, title, enabled: enabled) }
            .buttonStyle(VivaPressStyle())
            .disabled(!enabled)
        caption(detail)
    }

    private func rowLabel(_ icon: String, _ title: String, enabled: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 15)).foregroundStyle(VivaStyle.textSecondary).frame(width: 24)
            Text(title).vivaCardTitle(enabled ? VivaStyle.textPrimary : VivaStyle.textTertiary)
            Spacer()
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
    }

    private func caption(_ text: String) -> some View {
        Text(text).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 10)
            .padding(.bottom, 8)
    }
}

/// A plain drawn band, not a product photograph: a strap loop with its sensor clasp.
struct VivaBandIllustration: View {
    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width, height = proxy.size.height
            ZStack {
                RoundedRectangle(cornerRadius: height * 0.34, style: .continuous)
                    .stroke(
                        LinearGradient(colors: [VivaStyle.chipActive, VivaStyle.inset], startPoint: .top, endPoint: .bottom),
                        lineWidth: height * 0.2
                    )
                    .frame(width: width * 0.62, height: height * 0.86)
                    .rotationEffect(.degrees(-24))
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(LinearGradient(colors: [VivaStyle.chipActive, VivaStyle.chip], startPoint: .top, endPoint: .bottom))
                    .overlay(RoundedRectangle(cornerRadius: 9, style: .continuous).strokeBorder(VivaStyle.textTertiary.opacity(0.5), lineWidth: 1))
                    .frame(width: width * 0.36, height: height * 0.2)
                    .rotationEffect(.degrees(-24))
                    .offset(x: width * 0.2, y: height * 0.27)
            }
            .frame(width: width, height: height)
            .clipped()
        }
        .accessibilityHidden(true)
    }
}

/// Device & Battery: what the battery figure means and how to keep the strap charged.
struct VivaDeviceBatteryInfoView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VivaDetailScaffold(title: "Device & Battery", leading: .close) {
                Text("Check the battery level").font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                paragraph("The percentage on Device Settings is the strap's own reading from the current connection. Viva shows it only while the strap is connected, so you never see a stale figure.")
                level(VivaStyle.recoveryGreen, "50% and up: plenty of charge")
                level(VivaStyle.recoveryYellow, "20% to 50%: charge soon")
                level(VivaStyle.recoveryRed, "Under 20%: charge now")
                Text("Keep recording while you charge").font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                    .padding(.top, 18)
                tip("bolt", "Charge before sleep", "A strap that runs flat overnight records no sleep, and without a night's sleep there is no Recovery score the next day.")
                tip("arrow.triangle.2.circlepath", "Sync after charging", "Open Viva with the strap nearby so the hours it stored on its own are copied to your phone.")
                tip("antenna.radiowaves.left.and.right", "One phone at a time", "A strap bonds to one device. Unpair it here before pairing it with another phone or app.")
            }
        }
    }

    private func paragraph(_ text: LocalizedStringKey) -> some View {
        Text(text).font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func level(_ color: Color, _ text: LocalizedStringKey) -> some View {
        HStack(spacing: 10) {
            Rectangle().fill(color).frame(width: 18, height: 8)
            Text(text).font(VivaFont.caption).foregroundStyle(VivaStyle.textPrimary)
        }
    }

    private func tip(_ icon: String, _ title: LocalizedStringKey, _ text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).font(.system(size: 14)).foregroundStyle(VivaStyle.link).frame(width: 22)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                Text(text).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.top, 8)
    }
}

/// Home's top-right strap status: the charge as a number beside a small strap glyph, with a dot
/// that is green while the strap is connected. Opens Device Settings.
struct VivaBatteryChip: View {
    @EnvironmentObject private var live: LiveState
    @EnvironmentObject private var router: NavRouter

    var body: some View {
        Button { router.openDevices() } label: {
            HStack(spacing: 6) {
                if live.connected, let percent = live.batteryPct {
                    Text("\(Int(percent.rounded()))%").font(VivaFont.number(12, weight: .semibold))
                        .foregroundStyle(VivaStyle.textSecondary)
                }
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .stroke(VivaStyle.textPrimary, lineWidth: 1.5)
                        .frame(width: 15, height: 24)
                        .overlay(alignment: .leading) {
                            Capsule().fill(VivaStyle.textPrimary).frame(width: 7, height: 1.5).offset(x: 1.5)
                        }
                    Circle()
                        .fill(live.connected ? VivaStyle.positive : VivaStyle.textTertiary)
                        .frame(width: 7, height: 7)
                        .offset(x: 3, y: -2)
                }
                .opacity(live.backfilling ? 0.55 : 1)
            }
            .frame(height: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(VivaPressStyle())
        .accessibilityLabel(Text("Device settings"))
        .accessibilityValue(Text(live.connected
                                 ? (live.batteryPct.map { "\(Int($0.rounded())) percent" } ?? "Connected")
                                 : "Not connected"))
    }
}
#endif
