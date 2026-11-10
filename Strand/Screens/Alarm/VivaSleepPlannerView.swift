#if os(iOS)
import SwiftUI
import StrandDesign

/// Sleep Planner: when to go to bed for the wake time that is set, and the strap alarm that will
/// wake you. Every time on this screen comes from one `VivaAlarmReadout`, the same value Home's
/// Tonight card renders, and the controls write the same settings the Alarms screen does.
struct VivaSleepPlannerView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var behavior: BehaviorStore
    @State private var showInfo = false
    @State private var showWakePicker = false

    private var wakeBinding: Binding<Date> {
        Binding(
            get: {
                Calendar.current.date(bySettingHour: behavior.smartAlarmMinutes / 60,
                                      minute: behavior.smartAlarmMinutes % 60, second: 0, of: Date()) ?? Date()
            },
            set: { date in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                behavior.smartAlarmMinutes = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            }
        )
    }

    var body: some View {
        let readout = VivaAlarmSource.readout(behavior: behavior, isWhoop5: model.whoop5Detected)
        let bed = VivaAlarmReadout.clockParts(minutes: readout.bedtimeMinutes, locale: AppLanguage.activeLocale)
        let wake = VivaAlarmReadout.clockParts(minutes: readout.wakeMinutes, locale: AppLanguage.activeLocale)
        VivaDetailScaffold(title: "Sleep Planner", leading: .close, trailing: .info, onTrailing: { showInfo = true }) {
            VStack(spacing: 14) {
                Text(readout.isArmed ? "On" : "Off")
                    .font(.system(size: 9, weight: .bold)).tracking(1)
                    .textCase(.uppercase)
                    .foregroundStyle(readout.isArmed ? VivaStyle.positive : VivaStyle.textSecondary)
                    .padding(.horizontal, 6).frame(height: 16)
                    .background((readout.isArmed ? VivaStyle.positive : VivaStyle.textSecondary).opacity(0.15),
                                in: RoundedRectangle(cornerRadius: 3))
                    .frame(maxWidth: .infinity, alignment: .trailing)
                BrandMark(size: 22)
                Text("Go to bed at \(readout.bedtimeText) to get your \(readout.timeInBedText) in bed before your \(readout.wakeText) wake time.")
                    .font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
            }
            .padding(.bottom, 26)
            VivaDivider()
            HStack(alignment: .top) {
                timeColumn(bed, caption: String(localized: "Suggested time to bed"), alignment: .leading)
                Spacer()
                timeColumn(wake, caption: String(localized: "Your wake time"), alignment: .trailing)
            }
            .padding(.horizontal, 12)
            .padding(.top, 26)
            VStack(spacing: 4) {
                Text("Time in bed").vivaLabel(VivaStyle.textPrimary)
                ZStack {
                    DiagonalHatch(spacing: 6).stroke(VivaStyle.textSecondary, lineWidth: 2)
                        .frame(height: 14)
                        .clipped()
                        .overlay(alignment: .leading) { Rectangle().fill(VivaStyle.textPrimary).frame(width: 1, height: 26) }
                        .overlay(alignment: .trailing) { Rectangle().fill(VivaStyle.textPrimary).frame(width: 1, height: 26) }
                    Text(readout.timeInBedText).font(VivaFont.number(12)).foregroundStyle(VivaStyle.textPrimary)
                        .padding(.horizontal, 12).frame(height: 22)
                        .background(VivaStyle.canvasBottom, in: Capsule())
                        .overlay(Capsule().strokeBorder(VivaStyle.textPrimary, lineWidth: 1.5))
                }
            }
            .padding(.horizontal, 48)
            .padding(.vertical, 14)
            alarmPanel(readout).padding(.top, 24)
            NavigationLink(value: TabRoute.alarmSettings) {
                VivaCard(padding: 14) {
                    VivaMetricRow(icon: "slider.horizontal.3", label: String(localized: "Alarm days and wind-down"),
                                  value: "", showsChevron: true)
                }
            }
            .buttonStyle(VivaPressStyle())
            if !readout.isArmed && behavior.smartAlarmEnabled {
                VivaCallout(text: String(localized: "The alarm is switched on but will not arm on this strap yet. Keep a backup alarm."))
            }
        }
        .onChangeCompat(of: behavior.smartAlarmEnabled) { _ in model.applySmartAlarm() }
        .onChangeCompat(of: behavior.smartAlarmMinutes) { _ in model.applySmartAlarm() }
        .sheet(isPresented: $showWakePicker) {
            VStack(spacing: 12) {
                Text("Wake time").vivaCardTitle().padding(.top, 20)
                DatePicker("", selection: wakeBinding, displayedComponents: .hourAndMinute)
                    .datePickerStyle(.wheel).labelsHidden()
                VivaCardButton(String(localized: "Save")) { showWakePicker = false }.padding(.horizontal, 20)
            }
            .presentationDetents([.height(330)])
            .presentationBackground(VivaStyle.card)
        }
        .sheet(isPresented: $showInfo) {
            VivaInfoSheet(title: String(localized: "How the Sleep Planner works"), paragraphs: [
                String(localized: "Your suggested bedtime is your wake time minus the time in bed you need."),
                String(localized: "The alarm is armed on the strap itself, so it can buzz at your wake time even when your phone is asleep. Keep a backup alarm for anything you cannot miss."),
            ])
        }
    }

    private func timeColumn(_ parts: (time: String, period: String), caption: String,
                            alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text(parts.time).font(VivaFont.number(26)).foregroundStyle(VivaStyle.textPrimary)
                Text(parts.period).font(VivaFont.number(11)).foregroundStyle(VivaStyle.textPrimary)
            }
            Text(caption).vivaLabel().multilineTextAlignment(alignment == .leading ? .leading : .trailing)
                .frame(maxWidth: 110, alignment: alignment == .leading ? .leading : .trailing)
        }
    }

    private func alarmPanel(_ readout: VivaAlarmReadout) -> some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "alarm").font(.system(size: 20, weight: .light)).foregroundStyle(VivaStyle.textPrimary)
                Spacer()
                VStack(spacing: 2) {
                    Text("Alarm").vivaCardTitle()
                    if let fire = readout.nextFire {
                        Text(String(localized: "For \(fire.formatted(.dateTime.weekday(.abbreviated).locale(AppLanguage.activeLocale)))"))
                            .vivaLabel()
                    }
                }
                Spacer()
                Toggle("", isOn: $behavior.smartAlarmEnabled).labelsHidden().tint(VivaStyle.positive)
                    .accessibilityLabel(Text("Alarm"))
            }
            HStack(spacing: VivaStyle.cardGap) {
                tile(String(localized: "Alarm set to"), readout.modeText, action: nil)
                tile(String(localized: "Wake time set to"), readout.wakeText) { showWakePicker = true }
            }
        }
        .padding(VivaStyle.cardPadding)
        .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    @ViewBuilder private func tile(_ caption: String, _ value: String, action: (() -> Void)?) -> some View {
        let label = VStack(spacing: 4) {
            Text(caption).vivaLabel()
            Text(value).vivaCardTitle()
        }
        .frame(maxWidth: .infinity).frame(height: 54)
        .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 4, style: .continuous).strokeBorder(VivaStyle.divider, lineWidth: 1))
        if let action {
            Button(action: action) { label }.buttonStyle(VivaPressStyle())
        } else {
            label
        }
    }
}
#endif
