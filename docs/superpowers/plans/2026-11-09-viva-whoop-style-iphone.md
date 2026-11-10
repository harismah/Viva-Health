# Viva iPhone WHOOP-style Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle and restructure the Viva iPhone app so each screen matches the owner's WHOOP screen recording, fed by Viva's existing data.

**Architecture:** New tokens and reusable views live in `Packages/StrandDesign` under a `Viva*` prefix. iPhone screens in `Strand/Screens` and `StrandiOS/App` are rebuilt on those views, reusing each screen's existing data bindings. Existing shared tokens (`NoopVisualStyle`) are re-pointed so screens outside the video inherit the new look without layout work.

**Tech Stack:** Swift 5.9+, SwiftUI, Swift Charts, XcodeGen, iOS 17+ simulator (iPhone 18 Pro Max), XCTest in `Packages/StrandDesign`.

**Spec:** `docs/superpowers/specs/2026-11-09-viva-whoop-style-iphone-design.md`

## Global Constraints

- iPhone only: build and verify scheme `NOOPiOS`. Do not edit `android/`, `NOOPWatch*`, `StrandiOSWidgets/`.
- In files under `Strand/` shared with macOS, wrap iPhone-only structure in `#if os(iOS)`.
- No commits, no stash, no checkout of tracked files: the working tree holds 69 uncommitted owner files. Every "Checkpoint" step replaces a commit.
- No WHOOP logo, wordmark, "W" mark, photos, article or video content, or copied explainer copy. Wordmark text is `VIVA`.
- No invented data: a section with no data source renders its calibrating / no-data state.
- UI uses design tokens only (`VivaStyle`, `StrandPalette`, `StrandFont`); no colour, font or spacing literals in screen files.
- No changes to scoring, sync, BLE, storage, migrations or analytics.
- User-visible labels: Sleep, Recovery, Strain, Health Monitor, Stress Monitor, Sleep Planner, My Day, My Dashboard. Code identifiers and stored keys keep their names.
- Reference frames: `/Users/haris/Desktop/META/Practice/project-Whoop/whoop-ref/` (`full/tNNN.png` at 1320 px wide = 440 pt; `sheets/s_NN.jpg` numbered by second). Never copy them into the repo.
- Screenshots go to the session scratchpad, never into the repo.

## Standard commands

```bash
# Build + install + launch one screen with seeded data (BUILD_AND_SHOT <demo-screen-name> <out.png>)
cd /Users/haris/Desktop/META/Practice/project-Whoop/noop
xcodebuild -project Strand.xcodeproj -scheme NOOPiOS -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro Max' \
  -derivedDataPath build/dd CODE_SIGNING_ALLOWED=NO build 2>&1 | tail -5      # expect ** BUILD SUCCEEDED **
xcrun simctl install booted build/dd/Build/Products/Debug-iphonesimulator/*.app
xcrun simctl terminate booted com.noopapp.noop; \
xcrun simctl launch booted com.noopapp.noop --demo-seed --demo-screen <name>
xcrun simctl io booted screenshot <out.png>
# Design package tests
cd Packages/StrandDesign && swift test 2>&1 | tail -5                          # expect 0 failures
```

Run `xcodegen generate` first only if a file is added outside `Packages/` or `project.yml` changes. `build/` is already git-ignored; confirm with `git check-ignore build`.

## Review Focus

1. **No data yet (fresh install, strap never synced):** every rebuilt screen shows calibrating / no-data states and does not crash or show `0`. Test: Task 2 `testRingWithNilValueShowsDashes`; Task 5 screenshot without `--demo-seed`.
2. **Small phones and large text:** at iPhone 17e width and Dynamic Type XL, three home rings and two-column tiles do not clip or overlap. Test: Task 5 and Task 9 screenshots on iPhone 17e.
3. **Out-of-range values:** ring fraction and bars clamp for values below 0 or above max (strain above 21, sleep above 100%). Test: Task 2 `testFractionClamps`, Task 3 `testRangeBarClamps`.
4. **Alarm readouts agree:** Home "Tonight's Sleep", Sleep Planner header and alarm tiles show the same wake time and armed state from one source (AGENTS.md "two readouts of one fact"). Test: Task 11 step 4.
5. **Navigation that used to be a tab still reachable:** Trends, Sleep, Coach and every former More destination open from the new shell, including quick actions and deep links. Test: Task 4 step 5.

---

### Task 1: Tokens

**Files:**
- Create: `Packages/StrandDesign/Sources/StrandDesign/VivaStyle.swift`
- Modify: `Packages/StrandDesign/Sources/StrandDesign/NoopVisualStyle.swift` (dark values and `NoopPanelSurface` iOS branch)
- Test: `Packages/StrandDesign/Tests/StrandDesignTests/VivaStyleTests.swift`

**Interfaces:**
- Produces: `public enum VivaStyle` with static `Color`s `canvasTop` #20262C, `canvasBottom` #14171B, `card` #24272B, `inset` #1A1D21, `callout` #2A1D33, `tabBar` #1E2329, `coachButton` #26283A, `textPrimary` #FFFFFF, `textSecondary` #9AA4AC, `textTertiary` #6B7177, `sleep` #7096B4, `strain`, `recoveryGreen`, `recoveryYellow`, `recoveryRed`, `ringTrack`, `stageAwake`, `stageLight`, `stageDeep`, `stageREM`, `magenta`, `link`, `positive` #03E095, `warning` #F0A020; `static let cardRadius: CGFloat = 16`, `pagePadding = 12`, `cardPadding = 14`, `cardGap = 10`, `sectionGap = 28`; `static func recoveryColor(percent: Double?) -> Color` (nil -> `textSecondary`, >=67 green, 34..<67 yellow, <34 red); `public struct VivaCanvas: View` (the vertical gradient, ignores safe area); fonts `VivaFont.cardTitle`, `.sectionTitle`, `.valueLarge`, `.valueMedium`, `.unit`, `.baseline`, `.body`, `.ringSmall`, `.ringLarge`, `.wordmark`.

- [ ] **Step 1:** Sample exact hexes for the colours without a value above from `whoop-ref/full/` (strain ring t000, recovery ring t067 is grey-calibrating so take green from the `ALARM ON` dot in t000, stage bars t024, CUSTOMIZE chip t008, blue link t067) using `ffmpeg -i f.png -vf "crop=6:6:X:Y,scale=1:1" -f rawvideo -pix_fmt rgb24 - | xxd -p`.
- [ ] **Step 2:** Write `VivaStyleTests`: `testRecoveryColorBands` asserts `recoveryColor(percent: 67) == recoveryGreen`, `66.9 == recoveryYellow`, `34 == recoveryYellow`, `33.9 == recoveryRed`, `nil == textSecondary`. Run `swift test --filter VivaStyleTests`; expect compile failure.
- [ ] **Step 3:** Implement `VivaStyle.swift`. Run the test; expect pass.
- [ ] **Step 4:** In `NoopVisualStyle`, set dark values `canvas` -> #14171B, `surface`/`surfaceTop`/`surfaceBottom` -> #24272B, `inset` -> #1A1D21, `primaryText` -> #FFFFFF, `secondaryText` -> #9AA4AC, `tertiaryText` -> #6B7177, `cardRadius` -> 16; in `NoopPanelSurface` iOS branch remove the stroke overlay. Light values unchanged.
- [ ] **Step 5:** `swift test` in `Packages/StrandDesign`; fix tests that pinned old hexes by updating the expected value. Checkpoint: build and screenshot `liquidtoday`; cards are flat and borderless.

### Task 2: Ring, card, rows

**Files:**
- Create: `Packages/StrandDesign/Sources/StrandDesign/VivaScoreRing.swift`, `VivaCard.swift`, `VivaMetricRow.swift`, `VivaCallout.swift`
- Test: `Packages/StrandDesign/Tests/StrandDesignTests/VivaScoreRingTests.swift`

**Interfaces:**
- Consumes: `VivaStyle`, `VivaFont`.
- Produces:
  - `VivaScoreRing(value: Double?, maximum: Double, color: Color, format: VivaScoreRing.Format, size: VivaScoreRing.Size, label: String?)` where `Format` is `.percent` / `.decimal1`, `Size` is `.small` (64 pt, stroke 6) / `.large` (190 pt, stroke 16, shows `VIVA` wordmark above value and label below). `static func fraction(value: Double?, maximum: Double) -> Double` and `static func text(value: Double?, format: Format) -> String` (nil -> `"--"`).
  - `VivaCard<Content: View>(title: String? = nil, accessory: VivaCard.Accessory = .none, action: (() -> Void)? = nil, @ViewBuilder content:)` with `Accessory` `.none / .chevron / .info / .expand`.
  - `VivaMetricRow(icon: String, label: String, value: String, unit: String? = nil, baseline: String? = nil, trend: VivaMetricRow.Trend = .flat, showsChevron: Bool = false)` with `Trend` `.up / .down / .flat / .none`.
  - `VivaCallout(text: String, filled: Int, total: Int)`.
  - `VivaSectionHeader(title: String, action: VivaSectionHeader.Action? = nil)` with `Action(title: String, style: .chip / .plus / .link, handler:)`.

- [ ] **Step 1:** Write tests: `testFractionClamps` (`fraction(value: 25, maximum: 21) == 1`, `-3 -> 0`, `nil -> 0`, `10.5/21 == 0.5`); `testRingWithNilValueShowsDashes` (`text(nil, .percent) == "--"`); `testText` (`text(85, .percent) == "85"`, `text(7.46, .decimal1) == "7.5"`). Run; expect failure.
- [ ] **Step 2:** Implement the four files against frames t000 (small rings, HM/SM cards, dashboard rows from t008/t012) and t020/t067/t104 (large ring, callout). Ring arc starts at 12 o'clock, clockwise, round caps.
- [ ] **Step 3:** Run tests; expect pass. Add a `#Preview` per file showing value and nil states.

### Task 3: Bars and charts

**Files:**
- Create: `Packages/StrandDesign/Sources/StrandDesign/VivaRangeBar.swift`, `VivaWeekBarCard.swift`, `VivaHalfDial.swift`, `VivaVitalsTile.swift`
- Test: `Packages/StrandDesign/Tests/StrandDesignTests/VivaRangeBarTests.swift`

**Interfaces:**
- Consumes: Task 1, Task 2 `VivaCard`; existing `DiagonalHatch` in `TypicalRangeBar.swift`.
- Produces:
  - `VivaRangeBar(value: Double, range: ClosedRange<Double>?, maximum: Double, color: Color)`; `static func layout(value:range:maximum:width:) -> (fill: CGFloat, hatchStart: CGFloat, hatchWidth: CGFloat)`.
  - `VivaStageRow(color: Color, label: String, percent: Int, duration: String, value: Double, range: ClosedRange<Double>?, maximum: Double)`.
  - `VivaWeekBarCard(title: String, days: [VivaWeekBarCard.Day], style: .bar / .dot / .stacked, legend: [(String, Color)] = [], action: (() -> Void)? = nil)`; `Day(label: String, date: Int, segments: [(value: Double, color: Color)], valueText: String?, isToday: Bool)`; empty `segments` on every day renders the centred `NO DATA` state.
  - `VivaHalfDial(value: Double?, maximum: Double, caption: String, time: String?)`.
  - `VivaVitalsTile(icon: String, label: String, value: String?, unit: String?, status: String, action:)`.

- [ ] **Step 1:** Write tests: `testRangeBarClamps` (`layout(value: 150, range: nil, maximum: 100, width: 200).fill == 200`; negative value -> 0; range 20...40 of 100 at width 200 -> hatchStart 40, hatchWidth 40). Run; expect failure.
- [ ] **Step 2:** Implement against frames t024 (stage rows), t034 and sheet seconds 54-64, 73-77, 131-135 (week cards), t165 (dial), t142 (tiles).
- [ ] **Step 3:** Run tests; expect pass. Previews with data, empty and nil states.

### Task 4: App shell

**Files:**
- Create: `StrandiOS/App/VivaTabBar.swift`, `Strand/Screens/VivaDetailScaffold.swift`, `Strand/Screens/VivaCoachBar.swift`
- Modify: `StrandiOS/App/RootTabView.swift`, `StrandiOS/App/StrandiOSApp.swift` (`DemoScreens`), `Strand/Screens/ScreenScaffold.swift`

**Interfaces:**
- Consumes: `VivaStyle`, `VivaCanvas`.
- Produces:
  - `VivaTabBar(selection: Binding<Int>, onCoach: () -> Void)`: pill with Home (0), Health (1), More (2) and a separate round coach button showing the existing `BrandMark`.
  - `VivaDetailScaffold<Content: View>(title: String, trailing: VivaDetailScaffold.Trailing = .none, onTrailing: (() -> Void)? = nil, showsCoachBar: Bool = false, @ViewBuilder content:)` with `Trailing` `.none / .info / .gear`: `VivaCanvas`, back chevron, centred uppercase title, scroll content.
  - `VivaCoachBar(message: String?, onTap: () -> Void)`.
  - `DemoScreens` gains `home`, `sleepdetail`, `recoverydetail`, `straindetail`, `trendview`, `healthmonitor`, `stressmonitor`, `sleepplanner`, `journal`, `behaviorinsights`, `healthtab`, `more`, `shell` (full `RootTabView`). Entries for screens not yet built are added by the task that builds them.

- [ ] **Step 1:** Read `RootTabView.swift` fully. List every `MoreDestination`, `QuickAction` and `NavRouter.Destination` case and where each is reachable today.
- [ ] **Step 2:** Replace the system `TabView` chrome with three roots (Home, Health, More) under `VivaTabBar` as a bottom overlay; keep the existing per-tab `NavigationPath`s, reselect-to-scroll-top and quick-action handling. Trends and Sleep roots move to pushes from Home; Coach presents from `onCoach` as a sheet using the existing `CoachView`. `ScreenScaffold` background becomes `VivaCanvas`.
- [ ] **Step 3:** Implement `VivaDetailScaffold` and `VivaCoachBar` against frames t020 and t067.
- [ ] **Step 4:** Build, launch `--demo-seed --demo-screen shell`, screenshot. Compare tab bar with t000 bottom 250 px.
- [ ] **Step 5:** Reachability check: for each case listed in Step 1, state the tap path in the new shell; any case with no path gets a row on the More tab. Trigger each `QuickAction` via its existing entry point and confirm it opens.
- [ ] **Step 6:** Checkpoint: show the owner the shell screenshot beside t000.

### Task 5: Home

**Files:**
- Create: `Strand/Screens/Home/VivaHomeView.swift`, `VivaHomeRings.swift`, `VivaHomeMyDay.swift`, `VivaHomeDashboard.swift`, `VivaHomeCharts.swift`
- Modify: `StrandiOS/App/RootTabView.swift` (Home root -> `VivaHomeView`), `StrandiOS/App/StrandiOSApp.swift` (`home` demo entry)

**Interfaces:**
- Consumes: Tasks 1-4. Data: read `Strand/Liquid/LiquidTodayView.swift` and `Strand/Screens/TodayView.swift` and reuse the same environment objects, repository queries and customization prefs (`TodayCustomizationMetadata`, `HostedCardPrefs`) they use; do not add new queries.
- Produces: `VivaHomeView()`; `enum VivaHomeRoute: Hashable { case sleep, recovery, strain, healthMonitor, stressMonitor, sleepPlanner, journal, behaviorInsights, trend(VivaTrendMetric) }` pushed on the Home `NavigationPath` (destinations registered as later tasks land; until then route to the existing screen: `SleepView`, charge breakdown, `WorkoutsView`, `HealthView`, `StressView`, `SmartAlarmView`, journal sheet, `InsightsView`, `MetricExplorerView`).

- [ ] **Step 1:** Record which model property supplies each home value: sleep %, recovery %, strain, HRV, RHR, steps, HR-zone minutes, strength time, VO2 max, calories, stress now, last sleep start/end/duration, recommended bedtime, alarm time and armed state, journal week completion, streak, battery. Put the mapping in a comment block at the top of `VivaHomeView.swift`.
- [ ] **Step 2:** Build the sections in spec order (see spec "Home section order") against t000, t008, t012 and sheet seconds 0-19. Top bar: profile + streak left, `< TODAY >` day stepper (reuse `DayNavBar` logic), battery right. Rings pin as a compact strip after scrolling past them (second 2).
- [ ] **Step 3:** My Dashboard rows come from the existing customizable card list; CUSTOMIZE opens `TodayCustomizationSheet`.
- [ ] **Step 4:** Build, screenshot `home` with `--demo-seed` at scroll top, then scrolled (use `xcrun simctl io booted` after a swipe via `xcrun simctl ui` is unavailable, so add a DEBUG launch arg `--demo-scroll <fraction>` read by `VivaHomeView` to set initial scroll position). Compare with t000, t008, t012.
- [ ] **Step 5:** Screenshot without `--demo-seed` on a fresh simulator data container (`xcrun simctl uninstall` then install): all values show `--` or calibrating; no crash.
- [ ] **Step 6:** Screenshot on iPhone 17e and with `xcrun simctl ui booted content_size extra-large`: no clipped rings or rows.
- [ ] **Step 7:** Checkpoint: show the owner home screenshots beside the frames.

### Task 6: Sleep detail

**Files:**
- Create: `Strand/Screens/Detail/VivaSleepDetailView.swift`
- Modify: `StrandiOS/App/StrandiOSApp.swift` (`sleepdetail`), Home route registration

**Interfaces:**
- Consumes: Tasks 1-4; data from `SleepView` / `SleepModel` (same properties `StagesVsTypicalCard`, `HoursVsNeededCard`, `ConsistencyCard`, `SleepHRChart` read).
- Produces: `VivaSleepDetailView()`.

- [ ] **Step 1:** Build in video order (seconds 20-33, 54-64): large ring `SLEEP PERFORMANCE`; contributor rows (hours vs needed, consistency, efficiency, sleep stress) with Poor / Sufficient / Optimal legend; `Last Night's Sleep` header with EDIT (opens existing sleep edit flow); Hours of Sleep card with sleep HR chart; stage rows via `VivaStageRow`; Restorative Sleep; Hours vs Needed; Sleep Consistency; Sleep Efficiency with asleep/awake strip; Wake Events; Sleep Stress chart with High / Medium / Low bars; `Weekly Trends` of `VivaWeekBarCard`s each pushing `.trend(...)`.
- [ ] **Step 2:** Sections whose data `SleepModel` lacks render the callout / `NO DATA` state.
- [ ] **Step 3:** Build, screenshot `sleepdetail` at three scroll positions; compare with t020, t024, t026. Checkpoint with owner at end of Task 8.

### Task 7: Recovery and Strain detail

**Files:**
- Create: `Strand/Screens/Detail/VivaRecoveryDetailView.swift`, `VivaStrainDetailView.swift`
- Modify: `StrandiOSApp.swift` (`recoverydetail`, `straindetail`), Home route registration

**Interfaces:**
- Consumes: Tasks 1-4; recovery data as read by `ChargeBreakdownWiring.swift` and `VitalSignsSummary.swift`; strain data as read by `TrainingLoadCard.swift` and `WorkoutsView.swift`.
- Produces: `VivaRecoveryDetailView()`, `VivaStrainDetailView()`.

- [ ] **Step 1:** Recovery (seconds 67-79): large ring coloured by `VivaStyle.recoveryColor`; calibration callout when the existing model reports calibrating; rows HRV, RHR, respiratory rate, sleep performance with `Today vs. last 30 days` footer; behaviour-insights card; weekly cards Recovery, HRV, RHR, Respiratory Rate, Sleep Performance.
- [ ] **Step 2:** Strain (seconds 103-105, 127-139): large ring `STRAIN` on 0-21; callout; rows HR zones 1-3, HR zones 4-5, strength activity time, steps; weekly cards Strain, HR Zones 1-3, HR Zones 4-5, Calories, Steps, Strength Activity Time.
- [ ] **Step 3:** Both set `showsCoachBar: true`; the bar message is the existing coach brief if one exists, else hidden.
- [ ] **Step 4:** Build, screenshot both; compare with t067 and t104.

### Task 8: Trend View

**Files:**
- Create: `Strand/Screens/Detail/VivaTrendView.swift`, `VivaTrendMetric.swift`
- Modify: `StrandiOSApp.swift` (`trendview`), Home route registration

**Interfaces:**
- Consumes: Tasks 1-4; series queries used by `TrendsView.swift` / `MetricExplorerView.swift`.
- Produces: `enum VivaTrendMetric: String, CaseIterable, Hashable` (cases: sleepPerformance, hoursVsNeeded, restorativeSleep, sleepConsistency, timeInBed, sleepEfficiency, sleepDebt, sleepStress, recovery, hrv, restingHR, respiratoryRate, strain, hrZones13, hrZones45, calories, steps, strengthTime, vo2max) with `title`, `icon`, `unit`, `group` (`.strain / .recovery / .sleep`), `explainerTitle`, `explainerBody`; `VivaTrendView(metric: VivaTrendMetric)`.

- [ ] **Step 1:** Build against t034 and seconds 34-44, 80-97, 120-130: grouped metric dropdown; AVERAGE value; W / M / 6M segmented control (reuse `SegmentedPillControl` restyled); date-range stepper; one-sentence summary; chart (bars for totals, dots with typical-range band for vitals, dashed AVG line on M and 6M); breakdown legend where the metric has bands; explainer card.
- [ ] **Step 2:** `explainerBody` text is taken from Viva's existing copy in `ScoringGuideView.swift` / `HowNoopWorksView.swift`, shortened; no WHOOP text.
- [ ] **Step 3:** Build, screenshot `trendview` for sleepPerformance (W) and hrv (M); compare with t034 and sheet second 80.
- [ ] **Step 4:** Checkpoint: show the owner Sleep, Recovery, Strain and Trend screenshots beside frames.

### Task 9: Health Monitor, Stress Monitor, Health tab

**Files:**
- Create: `Strand/Screens/Health/VivaHealthMonitorView.swift`, `VivaStressMonitorView.swift`, `VivaHealthTabView.swift`, `VivaExplainerDialog.swift`
- Modify: `RootTabView.swift` (Health root -> `VivaHealthTabView`), `StrandiOSApp.swift` demo entries, Home route registration

**Interfaces:**
- Consumes: Tasks 1-4, 8; data from `HealthView.swift`, `VitalSignsSummary.swift`, `StressView.swift`, `BreathingView.swift`, Fitness Age / Vitality sources used by the `fitnessage` and `vitality` demo screens.
- Produces: `VivaHealthMonitorView()`, `VivaStressMonitorView()`, `VivaHealthTabView()`, `VivaExplainerDialog(icon:title:status:body:footnote:buttonTitle:action:)`.

- [ ] **Step 1:** Health Monitor (seconds 142-159, t142): live HR strip (value, BPM, zone); callout; two-column `VivaVitalsTile` grid (respiratory rate, blood oxygen, RHR, HRV, skin temp); tile tap shows `VivaExplainerDialog` with `OPEN TREND VIEW`; share-report row wired to the existing export if `HealthView` has one, else omitted.
- [ ] **Step 2:** Stress Monitor (seconds 162-191, t165): `< TODAY >` stepper, `VivaHalfDial`, day stress chart with sleep band, callout, SESSIONS cards opening `BreathingView` (gradient tiles from tokens, no photos), info sheet with fresh text and the High / Medium / Low scale.
- [ ] **Step 3:** Health tab (seconds 219-225, t220): top card shows Fitness Age and Vitality in the orb-card layout with a token gradient; Health Monitor summary card (five icon columns); Stress Monitor summary card; medical disclaimer from Viva's existing `DISCLAIMER` copy.
- [ ] **Step 4:** Build, screenshot all three on iPhone 18 Pro Max and iPhone 17e; compare with t142, t165, t220. Checkpoint with owner.

### Task 10: Journal and Behavior Insights

**Files:**
- Create: `Strand/Screens/Journal/VivaJournalView.swift`, `VivaBehaviorInsightsView.swift`
- Modify: `StrandiOSApp.swift` demo entries, Home route registration

**Interfaces:**
- Consumes: Tasks 1-4; data and save calls from `JournalLogCard.swift` and `InsightsView.swift`.
- Produces: `VivaJournalView(date: Date)`, `VivaBehaviorInsightsView()`.

- [ ] **Step 1:** Journal (t211): close button, `< TODAY >`, day-pill strip, date question heading, BEHAVIORS rows with X / check toggles, NOTES field, white `DONE` button. Saves through the existing journal write path unchanged. The "Smart log" AI card is included only if `JournalLogCard` already has a text-entry path; otherwise omitted.
- [ ] **Step 2:** Behavior Insights (t206): heading, tracked-behaviour rows with yes/no counts, impact list with Hurts / Helps bars from `InsightsView` correlation results; rows without enough data show the "record at least 5" note.
- [ ] **Step 3:** Build, screenshot both; compare with t211 and t206.

### Task 11: Sleep Planner and alarm

**Files:**
- Create: `Strand/Screens/Alarm/VivaSleepPlannerView.swift`
- Modify: `StrandiOSApp.swift` (`sleepplanner`), Home route registration, `VivaHomeMyDay.swift`

**Interfaces:**
- Consumes: Tasks 1-4; state and commands from `SmartAlarmView.swift` and `SleepCustomizationSheet.swift` (same model object, same arm / cancel calls).
- Produces: `VivaSleepPlannerView()`; `struct VivaAlarmReadout { let wakeTime: Date?; let isArmed: Bool; let mode: String; let bedtime: Date? }` with `static func resolve(from: <SmartAlarm model type>, now: Date) -> VivaAlarmReadout`, placed next to the existing alarm model.

- [ ] **Step 1:** Read `SmartAlarmView.swift` and `StrandTests/AlarmWakeTimeLabellingTests.swift`; identify the existing single resolver for wake time and armed state. `VivaAlarmReadout.resolve` wraps it; it adds no second computation.
- [ ] **Step 2:** Build against t196 and t201: close, title, ON chip, recommendation sentence, goal pill opening the sleep-goal sheet (radio options from `SleepCustomizationSheet`, SAVE), bedtime and wake time, hatched time-in-bed bar, bottom panel with ALARM toggle and two tiles (alarm mode, wake time) opening the existing pickers.
- [ ] **Step 3:** Home "Tonight's Sleep" card and the planner both render from one `VivaAlarmReadout` value.
- [ ] **Step 4:** Test in `StrandTests` (or the package holding the alarm model): `testHomeAndPlannerShareReadout` builds a model with an override day and asserts the readout's `wakeTime` equals the existing resolver's result and `isArmed == false` yields no countdown text. Run with the test command the existing alarm tests use.
- [ ] **Step 5:** Build, toggle the alarm in the simulator, confirm Home reflects it. Screenshot; compare with t196. Checkpoint with owner (Tasks 10-11).

### Task 12: More tab and sweep

**Files:**
- Modify: `StrandiOS/App/RootTabView.swift` (`moreTab`, `MoreRow`, `moreSection`), `Strand/Screens/ScreenScaffold.swift`, `Packages/StrandDesign/Sources/StrandDesign/Components.swift` (`NoopCard`, `SectionHeader`, button styles), `Packages/StrandDesign/Sources/StrandDesign/StrandCard.swift`, `Palette.swift`

**Interfaces:**
- Consumes: Tasks 1-2.

- [ ] **Step 1:** More tab (t230 top bar): gear left, `MORE` title, list rows as `VivaCard` rows with chevrons; every existing destination kept.
- [ ] **Step 2:** Re-point `NoopCard`, `StrandCard`, `SectionHeader` and the three button styles to `VivaStyle` values (flat fill, radius 16, uppercase tracked titles, white primary button with dark text, outlined secondary). Default accent in `Palette.swift` becomes `VivaStyle.link` for links and `VivaStyle.positive` for on-states.
- [ ] **Step 3:** `grep -rn -E 'Color\(red:|Color\(hex|\.mint|#[0-9A-Fa-f]{6}' Strand/Screens StrandiOS/App` and replace literals in iPhone-visible screens with tokens.
- [ ] **Step 4:** Screenshot `settings`, `workouts`, `devices`, `insights`, `explore` demo screens; each shows canvas gradient, flat cards, no green-mint accents, no borders.
- [ ] **Step 5:** `swift test` in `Packages/StrandDesign`; full `NOOPiOS` build. Checkpoint: final side-by-side set for the owner.

## Self-review notes

- Spec coverage: tokens (T1), shared components (T2-T4), every screen row in the spec table (T5-T12), naming (Global Constraints), verification (Standard commands, checkpoints), anti-goals (Global Constraints; T8 step 2; T9 steps 2-3).
- Spec components `InfoSheet` and `ExplainerDialog` are produced in T9; `CoachBar`, `PillTabBar`, `DetailTopBar` in T4.
- No task commits; checkpoints replace commits per the owner's working-tree constraint.
