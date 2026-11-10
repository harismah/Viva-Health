#if os(iOS)
import Foundation
import StrandDesign

/// The single place an alarm readout is resolved for the Viva screens. It applies the same gate
/// and calls the same pure resolver `AppModel.applySmartAlarm` arms the strap from, so a screen can
/// only ever show an alarm that will actually fire.
@MainActor
enum VivaAlarmSource {
    static func readout(behavior: BehaviorStore, isWhoop5: Bool, now: Date = Date()) -> VivaAlarmReadout {
        // A WHOOP 5/MG strap only arms with the experimental protocol probes on (see SmartAlarmView).
        let willArm = !(isWhoop5 && !PuffinExperiment.isEnabled)
        let fire: Date? = behavior.smartAlarmEnabled && willArm
            ? AppModel.nextSmartAlarmDate(minutes: behavior.smartAlarmMinutes,
                                          weekdays: behavior.smartAlarmWeekdays,
                                          overrides: WindDownNudge.perDayWakeOverrides,
                                          from: now)
            : nil
        return VivaAlarmReadout.resolve(nextFire: fire,
                                        baseWakeMinutes: behavior.smartAlarmMinutes,
                                        sleepNeedMinutes: WindDownNudge.sleepNeedMinutes,
                                        locale: AppLanguage.activeLocale)
    }
}
#endif
