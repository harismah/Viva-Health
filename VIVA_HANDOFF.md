# Viva: handoff context

Give this file to your coding agent before it touches the repo. It covers what Viva is, the rules the work follows, what has been done so far and what is still open. Last updated: 2026-11-10.

Two people work on this repo: the owner on the iPhone app (macOS), and a second contributor on the Android app (Windows). If you are picking up Android work, read sections 2, 4 (the Android part) and 5.7.

## 1. What this is

**Viva** is a personal iPhone app for the **WHOOP 5.0** strap. It is a fork of [ryanbr/noop](https://github.com/ryanbr/noop), an open-source, clean-room app that reads the strap over Bluetooth and computes its own Recovery, Strain and Sleep scores on the phone. The owner has a short WHOOP subscription and wants an app they control, sideloaded onto their own iPhone.

- Remotes:
  - `origin` is upstream `ryanbr/noop`.
  - `viva-health` is the owner's fork, `github.com/harismah/Viva-Health`.
- Commit `3a00c3a8 "Import Viva Health project"` snapshots everything described in section 4.
- Code identifiers keep the upstream names: the Xcode project is `Strand.xcodeproj`, the iPhone target is `NOOPiOS`, and the module, folder and bundle-ID names are unchanged. Only what the user sees says "Viva".

## 2. Ground rules (non-negotiable)

1. **No WHOOP proprietary material.** That means:
   - no decrypting, dumping or decompiling the WHOOP IPA, and no getting around its DRM
   - no WHOOP logos, wordmark, "W" mark, product photos, articles or copied explainer text
   This is upstream policy (`AGENTS.md`: clean-room interoperability), and it was the starting condition for this fork.
2. **Don't copy reverse-engineered constants or tables from other projects.** This includes [tigercraft4/goose](https://github.com/tigercraft4/goose), which was reviewed for feature ideas only. Specifically, do not import its `recovery_to_strain.json` or `strain_raw_scale_lookup.json`. Use published science (Banister TRIMP, Plews ln‑RMSSD, and so on) or fit against the owner's own data.
3. **Licence.** The code is PolyForm Noncommercial: personal use only, and the app can never be sold. Keep `LICENSE`, `NOTICE`, `ATTRIBUTION.md`, `DISCLAIMER.md` and `TERMS.md`. The in-app Terms screen reads `TERMS.md`.
4. **Strap-writing actions are opt-in by the owner.** Examples are the WHOOP 5.0 experimental "deep data" / R22 toggles and arming the alarm. The agent must not flip these itself.
5. **Personal health data never goes in git.** The WHOOP data export and the reference screen recording at `../whoop-ref/` stay outside the repo.
6. **No commits or pushes unless the owner asks.**

**Stale upstream docs.** `AGENTS.md`/`CLAUDE.md` describe upstream noop, and several parts no longer apply to this fork:
- (the Android parity rule applies again: `android/` was deleted in the cleanup and then restored)
- the macOS `Strand` scheme and `StrandTests` (both removed)
- CI workflows (`.github/` was removed)
- links into `docs/` and `Tools/` (both removed)

Their BLE safety contract, migration rules and design-token rules still apply.

## 3. Build and run

You need Xcode (full, not just the command-line tools) and XcodeGen (`brew install xcodegen`).

```sh
cd noop
xcodegen generate                      # Strand.xcodeproj is generated output; edit project.yml, not the project
open Strand.xcodeproj                  # scheme: NOOPiOS → your iPhone → ▶
```

- **Signing:** copy `Config/BundleIdSecrets.example.xcconfig` to `Config/BundleIdSecrets.xcconfig`, then set your own `BUNDLE_ID_PREFIX` and Team.
  - A free Apple ID signature expires after 7 days. Use SideStore to refresh it.
- **Build from the command line:**
  ```sh
  xcodebuild -project Strand.xcodeproj -scheme NOOPiOS -destination 'generic/platform=iOS Simulator' build
  ```
- **Tests:** run `swift test` inside each package folder. Last known passing counts:

  | Package | Tests |
  |---|---|
  | StrandAnalytics | 2159 |
  | WhoopProtocol | 857 |
  | StrandImport | 327 |
  | StrandDesign | 159 |

- **Gotcha:** every Xcode build rewrites the `*.xcstrings` string catalogs with huge diffs. Restore them after building:
  ```sh
  git checkout -- '*.xcstrings'
  ```
- **Gotcha:** after `xcodegen generate`, close and reopen the project in Xcode.
- **Demo screens for screenshots:** launch with `--demo-screen <name>`, for example `sleepdetail`, `recoverydetail`, `straindetail`, `healthmonitor`, `stressmonitor`, `sleepplanner`, `behaviorinsights`, `trendview` or `today`. The list lives in `StrandiOS/App/StrandiOSApp.swift` (`enum DemoScreens`).

## 4. Repo layout (iPhone-only after cleanup)

| Path | What it is |
|---|---|
| `project.yml` | XcodeGen spec. The only scheme is `NOOPiOS`, which embeds the widgets and the Watch app. |
| `Strand/` | Shared app code. Most of the iPhone app lives here: screens, BLE, data, coach, system. |
| `StrandiOS/` | iPhone-only shell: app entry, tab bar, iOS screens, resources and app icons. |
| `StrandiOSShared/` | Code shared by the app and the widgets. |
| `StrandiOSWidgets/` | Home-screen widgets (embedded in the app). |
| `NOOPWatch/`, `NOOPWatchComplications/` | The Apple Watch app (embedded). It can be removed if the owner doesn't use a Watch. |
| `Packages/WhoopProtocol` | BLE frame parsing, including the WHOOP 5.0 "puffin" protocol. |
| `Packages/WhoopStore` | GRDB database. |
| `Packages/StrandAnalytics` | Pure scoring functions: recovery, strain, sleep staging, HRV, and so on. |
| `Packages/StrandImport` | Importers, including `WhoopExportImporter.swift` for the WHOOP CSV export. |
| `Packages/StrandDesign` | Design tokens and components, including all the `Viva*` components. |
| `Packages/OuraProtocol`, `PolarProtocol` | Other wearables (left in place). |
| `docs/superpowers/` | The current UI redesign spec and plan (section 5.6). |

**Android (`android/`), restored for a friend's phone.** It is upstream's Kotlin/Compose app with the Viva branding, and the Viva redesign is being ported onto it screen by screen (section 5.7):
- The launcher name, widget names and on-screen wordmarks say "Viva", in all locales. The demo variant is "Viva Demo"; the debug-only "NOOP Staging" label override was removed.
- The launcher icon is the Viva mark, drawn as vector layers in `res/drawable/ic_launcher_*.xml`, in teal and navy variants.
- It builds and runs in this fork (JDK 17, Android SDK, tested on a Pixel 7 Pro).

**Android setup (for the friend using Android):**
1. Install Android Studio. It bundles JDK 17 and the Android SDK; a system JDK 15 or older will not build.
   - Android Studio is optional. The port so far was built on Windows with only Temurin JDK 17 and the SDK command-line tools (`platform-tools`, `platforms;android-35`, `build-tools;34.0.0`), with `sdk.dir` set in `android/local.properties` (git-ignored).
2. **File → Open** and select `android/`. Let Gradle sync; `android/gradlew` provisions Gradle 8.7.
3. Build variant **fullDebug**. `demo` is a fake-data flavour, and `release` needs a signing key.
4. Enable USB debugging on the phone, connect it, then press Run.
   - Command-line alternative: `./gradlew assembleFullDebug` (`gradlew.bat` on Windows). The APK lands in `app/build/outputs/apk/full/debug/`.
   - `./gradlew assembleDemoDebug` builds "Viva Demo" (`com.noop.whoop.demo.debug`), which installs beside the real app and is the quickest way to look at a screen without a strap. A first clean build takes about 15 minutes per variant; later ones a few minutes.
5. Requirements and known prompts:
   - Android 8.0 (API 26) or later.
   - Allow "install unknown apps" if sideloading the APK.
   - Allow the battery-optimisation exemption the app asks for, or overnight sync gets cut off.
6. The application ID is `com.noop.whoop.debug` for fullDebug. Upstream NOOP installed side by side would clash only on the same variant.
7. A WHOOP strap bonds to one device at a time. Unpair it from the WHOOP app or from another phone running Viva before pairing.
8. Run the JVM unit tests with `./gradlew testFullDebugUnitTest`.
   - Some tests read shared fixtures outside `android/`: `Packages/*/Tests/**/Resources/*.json` and `Tools/parity_cases/device_raw_sources.json`. Keep those files.

**Keeping Android in step with iPhone:**
- **Scoring.** Kotlin re-implements the Swift scoring maths (`android/app/src/main/java/com/noop/analytics/`). Any scoring change on iPhone must be mirrored there, or the two phones will show different numbers for the same data. Upstream's rule is byte-identical results, checked with a Swift oracle whose output is pasted into a Kotlin test (see `AGENTS.md`).
- **Look.** Compose shares no UI code with the SwiftUI app, so every Viva screen and style is re-implemented in Kotlin, in `android/app/src/main/java/com/noop/ui/viva/` (section 5.7). `Tools/parity_cases/` was restored only because the Android tests read it.

**Removed from upstream:**
- the macOS app and its target, and `StrandTests` (they were macOS-hosted)
- `Tools/`, `.github/`, `marketing/`
- the upstream `docs/` folder
- `Packages/NoopLocalAccess`

The upstream protocol notes can still be read from history:
```sh
git show origin/main:docs/PROTOCOL.md
```

## 5. Work done so far

### 5.1 Xcode warnings: all first-party warnings fixed (about 42 files)
- **Swift 6 concurrency:**
  - `nonisolated static let` on constants and regexes
  - `@preconcurrency import UserNotifications`
  - `Sendable` on `HRSample`, `RRInterval` and `GravitySample` (WhoopProtocol `Streams.swift`)
  - `Task { @MainActor in … }` around notification authorisation (`CoachBriefScheduler`, `WindDownNudge`, `AppModel`)
- **Deprecated `onChange(of:perform:)`** replaced with the repo's `onChangeCompat` shim from StrandDesign. Now that the app is iOS-only, plain two-argument `onChange` would also work.
- **Other API updates:**
  - `AVAudioApplication.requestRecordPermission`
  - HealthKit `.mostRecent` and `.cardioDance`
  - `Archive(…, pathEncoding: nil)` for ZIPFoundation
  - missing `import Combine` lines
  - accessibility labels built with `Text(verbatim:)`
- **Remaining warnings** come only from the third-party GRDB and ZIPFoundation packages, which are not ours to fix.

### 5.2 Rename to Viva and new icon
- `CFBundleDisplayName` and `CFBundleName` are now `Viva` in `project.yml`, and the visible "NOOP" strings on screens, widgets and complications are now "Viva".
  - About 580 other "NOOP" strings remain (translations and internal text). They were left alone on purpose, because translations are keyed on the English text.
- The icon is original artwork: a "V" whose right arm breaks into a heartbeat spike, with a mint→blue gradient on a dark background.
  - It was generated by a Core Graphics Swift script (graphite and navy variants).
  - It is installed in `StrandiOS/Resources/Assets.xcassets/AppIcon.appiconset`, `AppIcon-Navy.appiconset` and the Watch `AppIcon.appiconset`.

### 5.3 Validation harness (Phase 1, started)
- `Packages/StrandAnalytics/Sources/StrandAnalytics/ScoreComparator.swift` compares two `[yyyy-MM-dd: Double]` series: Viva versus WHOOP.
  - It returns n, bias, MAE, RMSE, Bland–Altman limits of agreement, Pearson r (when n ≥ 3) and the worst days.
  - It is pure, with no database access. Its tests are in `ScoreComparatorTests.swift` (7 tests).

### 5.4 WHOOP 5.0 status
- **Live heart rate:** works.
- **Pairing and connection:** BLE `connectCore` and the scans already wait for `poweredOn`.
- **Historical offload and deep data:** decoding exists (the puffin protocol and `PuffinExperiment.deepDataKey`, the R22 unlock, which is reversible) but has **not been validated on real hardware**. The owner must turn on the experimental toggles in Settings; then the decoded streams need checking and any gaps filling.
- **Smart alarm:** on a 5.0 strap the alarm only arms when the experimental protocol probes are on (see `SmartAlarmView` and `VivaAlarmSource`).

### 5.5 iPhone-only cleanup
- The repo went from about 1.9 GB to about 28 MB.
- The `NOOPiOS` build was verified clean after the cleanup.
- Note: `docs/superpowers/specs/...design.md` says "Mac code stays untouched". That line predates the cleanup. The Mac target is gone now.

### 5.6 UI redesign (iPhone: all 12 plan tasks built)
- **Spec:** `docs/superpowers/specs/2026-11-09-viva-whoop-style-iphone-design.md`
- **Plan:** `docs/superpowers/plans/2026-11-09-viva-whoop-style-iphone.md`
- **Look:** a dark look with a floating pill tab bar (Home, Health, More) plus a round coach button, and detail screens with a back chevron and a centred uppercase title.
- **Reference material:** the visual reference is the owner's own screen recording. The spec's **anti-goals** must hold:
  - no WHOOP marks; "VIVA" sits wherever the reference shows a logo
  - no copied text; explainers are written fresh
  - no invented data; sections with no data show a "calibrating" / "no data" state
  - no changes to scoring, sync or BLE as part of the redesign
- **Built:**

  | Area | Files |
  |---|---|
  | Tokens and components (`Packages/StrandDesign`, with tests) | `VivaStyle`, `VivaScoreRing`, `VivaCard`, `VivaMetricRow`, `VivaCallout`, `VivaRangeBar`, `VivaWeekBarCard`, `VivaVitalsTile`, `VivaHalfDial`, `VivaAlarmReadout`, `VivaConsistency` |
  | App shell | `StrandiOS/App/VivaTabBar.swift`, `RootTabView.swift`, `Strand/Screens/VivaDetailScaffold.swift`, `VivaCoachBar.swift` |
  | Home | `Home/VivaHomeContent.swift`; data mapped in the "Viva home adapter" extension at the end of `Strand/Liquid/LiquidTodayView.swift` |
  | Sleep, Recovery and Strain detail | `Detail/VivaSleepDetailView.swift`, `VivaPillarDetailView.swift`, `VivaCharts.swift` |
  | Trend View | `Detail/VivaTrendMetric.swift`, `VivaTrendView.swift` |
  | Health tab, Health Monitor, Stress Monitor | `HealthViva/VivaHealthTabView.swift`, `VivaHealthMonitorView.swift`, `VivaStressMonitorView.swift` |
  | Journal and Behavior Insights | `Journal/VivaJournalView.swift` |
  | Sleep Planner and alarm | `Alarm/VivaSleepPlannerView.swift`, `VivaAlarmSource.swift` |
  | Device Settings (from Home's battery chip), Device & Battery help | `Device/VivaDeviceSettingsView.swift` |
  | Profile, Achievements, Edit Profile, Settings list | `Profile/VivaProfileView.swift`; counting rules in `VivaProfileMath` (StrandDesign, with tests) |
  | More tab and the restyle of older screens | `RootTabView.swift`, `ScreenScaffold.swift`, and the shared tokens in `NoopVisualStyle`, `Typography`, `Appearance`, `Palette`, `NoopButton`, `Components` |

- **Rules both phones share.** `VivaConsistency` (Swift) holds the small pure rules that stop two screens contradicting each other; each has a Kotlin twin (`VivaSleepDetail.nightOffset`, `VivaVitals.summary`). Change both together:
  - `nightOffset`: the Sleep detail titles itself with the night it shows ("Today", "Yesterday" or the weekday), says so in a note when that is not last night, and heads the section "That Night's Sleep".
  - `vitalsSummary`: Home's Health Monitor card and the Health tab state one sentence ("N/M metrics within range") from `VivaVitals.summary`.
  - Home's sleep prompt says "You were in bed for h:mm", because the figure is time in bed.
- **Device Settings (second reference recording, 2026-11-10).** Status tab: charge, strap model, Broadcast heart rate. Advanced tab: device ID, firmware version, Pair, Unpair, Restart, All devices. Every action calls the function the Devices screen already calls. Left out on purpose: firmware check/install and erase-device-data (ground rules: no irreversible strap commands), the manufacturer's regulatory pages and product photos (ground rule 1). Not yet on Android.
- **Profile (third reference recording, 2026-11-10).** Home's avatar opens Profile: name and age, "Recording since", recoveries scored, Fitness Age, day streak, achievements, Data Highlights (1M / 3M / all time: best scores, longest streaks, notable stats) and Activity Summary by sport. The gear opens a short Settings list that links to the existing settings screens. Every figure is counted from stored days and workouts; thresholds are in `VivaProfileStats`. Achievement names are Viva's own. Edit Profile is a form with one Save button (nothing is written until Save): first and last name, country and city are local preferences (`viva.profile.*`), not in backups and not used by any score; birthday, gender, units, height and weight save to the existing `ProfileStore` and unit preference. Left out: referrals, membership, gifting, logout, the coach "memory" row and the manufacturer's age score (no account, shop or data behind them). Not yet on Android.
- **AI Settings (reference screenshot, 2026-11-10).** `VivaAISettingsView` (in `Profile/VivaProfileView.swift`), opened from Profile's "Coach memory" row and from Settings. Its Memory switch is `AICoachEngine.memoryEnabled` (key `ai.conversationMemory`, on by default): on, the conversation is saved on the phone and restored as before; off, nothing is saved or restored and the stored conversation is deleted. This is a **behaviour change in `Strand/AI/AICoach.swift`**, so the Kotlin twin `CoachViewModel` needs the same gate. Not yet on Android.
- **App Settings list (reference screenshot, 2026-11-10).** `VivaAppSettingsView`, opened from Settings. One row per topic; each opens an existing screen. Activity settings (Activity Detection, Heart Rate Settings), Data export, Integrations, Notifications and Language each have their own Viva page in `Profile/VivaSettingsPages.swift` (fifth reference recording). Every page writes the existing stored key or calls the existing function (`ProfileStore` heart-rate values, `DataBackup.runExport`, `CsvExport.run`, `CoachBriefScheduler`, `WindDownNudge`, `AppLanguage.apply`), so there is one value per setting. Integrations opens `VivaAppleHealthConnectView` (link diagram, Connect / Sync now), which calls the same authorisation and sync the full Apple Health screen calls and links to it for the imported data. `SettingsView(focus:)` still exists for "Restore a backup and more options". "Cycle insights" shows only when cycle tracking applies to the profile. "All settings" opens the full page. Not yet on Android.
- **Screenshot aids (debug builds):** `--demo-seed` fills an empty store with sample data; `--demo-screen <name>` opens one screen; `--demo-anchor <id>` scrolls a Viva screen to a section; `--demo-tab <0|1|4>` opens the shell on a tab.
- **Decisions made while building,** in short:
  - Recovery and Strain detail are handed the score Home showed, so the rings match.
  - Dashboard rows are the wearer's Key Metrics plus VO₂ max. There are no heart-rate-zone or strength-time rows, on Home or Strain detail, because no per-day source exists.
  - Stress Monitor shows a 14-day line; the hour-by-hour graph stays on the full Stress screen it links to.
  - Sleep Planner has no sleep-goal sheet (sleep need has no setter); weekday and wind-down settings stay on the Alarms screen it links to.
  - Journal has no notes field and no AI smart-log card.
  - The Viva screens use fixed type sizes, so Dynamic Type does not enlarge them.
- **Disagreements closed on iPhone on 2026-11-10** (items 1.2, 1.4 and 1.6 of the Android list in 5.7; **Android still needs the same three**, as twins of the Swift below):
  - **A carried score says whose it is.** When today has no Sleep or Recovery score and Home shows an earlier night's, the ring carries a caption ("Last night · Nov 9"). Recovery detail is handed that caption and Home's Sleep score with the Recovery score (`VivaHomeRoute.recovery(score:note:sleep:sleepNote:)`), so its ring and its Sleep Performance row match Home.
  - **HRV, resting heart rate, respiratory rate and blood oxygen have one answer.** `VivaTrendMetric.todayOrCarried` uses the same carry selectors Home and the Health Monitor already read (`Repository.lastHrvDay` and friends), and every screen stamps a carried value with its day in place of the baseline. Detail screens no longer treat the newest stored row as today's.
  - **Recovery detail names the real gap.** `VivaConsistency.recoveryGap` picks, in order: still calibrating, no sleep recorded, no HRV recorded, not scored yet.
  - Rules with tests: `VivaConsistency.recoveryGap`, `VivaConsistency.carriedDay` (`VivaConsistencyTests`).
  - Found on the way: `BodyVitalSigns.dayFormatter` printed day labels in the local time zone while parsing them as UTC, so west of UTC every vitals day label named the previous day. It now formats in UTC.
- **Later iPhone work** (Device Settings, Profile, Settings pages, coach memory, logo) is listed for the Android port in section 5.8.
- **Not done:** the final independent code review of the redesign did not complete; about 580 internal and translated "NOOP" strings remain (see 5.2).
- **Verification method:** a simulator screenshot (`--demo-screen`) beside the matching reference frame, plus a fresh install with no data on a small phone.

### 5.7 Android port of the redesign (in progress)

The Android app is getting the same look as the iPhone redesign in 5.6, in the same order as the iPhone plan. The goal is that the two phones look and behave like the same app.

- **Where the code is.** Everything new is in one package, `android/app/src/main/java/com/noop/ui/viva/`, with tests in `android/app/src/test/java/com/noop/ui/viva/`. Each file is the twin of a Swift file of the same name and says so in its header comment. Upstream files are touched as little as possible, so upstream changes can still be merged:
  - `AppRoot.kt`: shows the Viva tab bar and registers the Viva routes.
  - `TodayScreen.kt`: keeps all its data loading and renders the Viva home in place of its own layout.
  - `MainActivity.kt`: a debug-only hook that opens the component gallery.
  - `Components.kt`: list screens now leave room for the tab bar, so it no longer covers the last rows.
- **One switch.** `VivaShell.enabled` (in `VivaShell.kt`) turns the whole redesign on or off. It is on. The upstream screens stay compiled and reachable; nothing was deleted.
- **Colours and fonts.** Viva is dark-only, so these screens use the `VivaStyle` / `VivaFont` tokens (value-for-value copies of the Swift ones), not upstream's `Palette`.
- **Built so far:**

  | Area | Files | Twin of |
  |---|---|---|
  | Tokens and components | `VivaStyle`, `VivaScoreRing`, `VivaCard`, `VivaMetricRow`, `VivaCallout`, `VivaRangeBar`, `VivaWeekBarCard`, `VivaVitalsTile`, `VivaHalfDial`, `VivaAlarmReadout`, `VivaCharts`, `VivaChrome` (tab bar, coach bar, detail scaffold) | the `Viva*` files in `Packages/StrandDesign` |
  | Home | `VivaHomeContent`, `VivaHomeHost` | `Home/VivaHomeContent.swift` |
  | Health tab, Health Monitor, vital explainer | `VivaHealthScreens` | `HealthViva/VivaHealthTabView.swift`, `VivaHealthMonitorView.swift` |
  | Trend View (week, month, 6 months, metric picker) | `VivaTrendMetric`, `VivaTrendScreen` | `Detail/VivaTrendMetric.swift`, `VivaTrendView.swift` |
  | Sleep detail | `VivaSleepDetailScreen` | `Detail/VivaSleepDetailView.swift` |
  | Recovery and Strain detail | `VivaPillarDetailScreens` | `Detail/VivaPillarDetailView.swift` |
  | Stress Monitor | `VivaStressMonitorScreen` | `HealthViva/VivaStressMonitorView.swift` |
  | Sleep Planner | `VivaSleepPlannerScreen` | `Alarm/VivaSleepPlannerView.swift` |
  | Journal | `VivaJournalScreen` | `Journal/VivaJournalView.swift` (the journal half) |
  | Behavior Insights | `VivaBehaviorInsightsScreen` | `Journal/VivaJournalView.swift` (`VivaBehaviorInsightsView`) |
  | Vital ranges (typical range, chip, green / orange / red) | `VivaVitalRange` | none yet: the iPhone still bands with `VitalBands` |
  | Routes and the on/off switch | `VivaShell` | `TabRoute.swift` |
  | Component gallery (debug builds) | `VivaGalleryScreen` | `enum DemoScreens` |

- **Where Android differs from iPhone on purpose:**
  - **Sleep Planner controls the phone alarm.** Android's dependable alarm is the one the phone itself schedules (`SmartAlarmStore` / `SmartAlarmScheduler`), so that is what the screen reads and writes, through the same view-model setters the upstream Alarms screen uses. Its help text says so and does not claim the strap is armed. If a different wake time is set for some weekdays, tapping the wake time opens the full Alarms screen, because one time picker cannot edit several times.
  - **Home and Sleep Planner share one alarm resolver** (`vivaAlarmReadout()` in `VivaHomeHost.kt`), so they cannot show different wake times or a different on/off state. The wake time they show is the alarm that is guaranteed to ring: the stored "wake me no earlier than" time plus the smart-wake window (6:30 + 30 minutes shows as 7:00), whether the alarm is on or off. The Sleep Planner's time picker edits that same shown time and stores it minus the window.
  - **Stress Monitor, the Home stress card and the full Stress screen all build the same `StressModel`**, for the same reason.
  - **A screen opened from a Home ring is handed the score that ring showed** (`VivaHandedScore`), so the two never disagree while data is still loading.
  - **The healthspan area on the Health tab reads "Calibrating".** There is no data behind it yet, and the spec forbids invented numbers.
- **How it is checked.**
  - Unit tests for the pure rules: `VivaComponentsTest`, `VivaHomeMapperTest`, `VivaDetailScreensTest` (43 tests, passing on 2026-11-10). Run them with `./gradlew testDemoDebugUnitTest --tests "com.noop.ui.viva.*"`.
    - **Gotcha (seen on Windows):** the test task can stop before running anything with `property 'roomSchemas' specifies directory '...\app\build\roomSchemaOracle' which doesn't exist`. Room's schema export came out empty on that machine, so the folder the build copies it into was never made. Creating the empty folder `android/app/build/roomSchemaOracle` lets the Viva tests run, but the build removes it again whenever its schema-copy step actually runs, so it may need recreating before each test run. The cause was not investigated, and the full `testFullDebugUnitTest` suite (which includes a test that reads that schema) has not been run in this fork.
  - On a phone, with the demo build. The gallery opens with:
    ```sh
    adb shell am start -S -n com.noop.whoop.demo.debug/com.noop.IconDefault --es viva_demo_screen gallery
    ```
  - Against the contributor's own screenshots of the WHOOP Android app, kept outside the repo in `../whoop-ref/android-app/` with written layout notes (`NOTES-health.md`, `NOTES-pillars.md`). Ground rule 1 applies: these are for layout comparison only; no WHOOP text, marks or assets are copied.
- **Known gaps:**
  - All Viva strings are hard-coded English; they are not in `strings.xml` yet, so they are not translated.
  - Home: no sync status or pull-to-sync in the new header; tapping the day title only jumps back to today; the activity icon is generic.
  - Stress Monitor shows a 14-day line, like the iPhone screen. WHOOP shows today's hour-by-hour graph and time-in-band cards; the upstream Stress screen already computes both (`DaytimeStress`), so they can be adopted without new maths.
  - The Sleep Planner says the alarm is set to "Exact time", but the phone alarm can ring earlier inside its smart-wake window.
  - The Trend chart has no typical-range band yet.
  - `VivaTrendMetric`, Stress Monitor and Sleep Planner have no unit tests of their own.
  - No scoring, sync or Bluetooth code was changed for any of this, and nothing was tested against a real strap.
- **Health Monitor ranges follow the reference app (2026-11-11, Android only).** `VivaVitalRange` judges each vital against the last 30 days: the typical range is the 25th to 75th percentile (displayed night included), the chip reads `within lo - hi`, `near x`, `low < x` or `elevated > x`, and the band is green, orange or red. The typical-range rule reproduced the reference app's figures on two consecutive days of one wearer's data. The orange and red limits are offsets from the window's median **fitted to those same two days**, not a known rule; refit them when more days or a second wearer can be compared. Under 14 nights the earlier `VitalBands` verdict is kept. The iPhone twin is not written, so until it is the two phones can give different verdicts for the same reading.
- **Import and strap on the same day (2026-11-11, Android only; the Swift twins are unchanged and need the same edits).**
  - `WhoopRepository.mergeDaily` no longer lays the strap's absolute skin temperature beside an imported one. A WHOOP CSV stores the night's absolute temperature in `skinTempDevC`; the merge used to fill `skinTempC` from the strap's row as well, and the tile leads with `skinTempC`, so it showed the strap's figure for a night the import had measured differently. Covered in `DailySkinTempAbsoluteCarryTest`. The Swift `Repository` merge is the twin to change.
  - The Viva home shows no strain figure for today while the strap's heart rate does not reach back to within two hours of the day's start (`VivaHomeMapper.strainIsPartial`, fed from the Today screen's own heart-rate read). An export never carries the open day's strain, so the figure would otherwise be the app's own over part of a day. Past days are not covered by this rule: a part-covered day that is never re-imported keeps its low stored figure.
  - Behavior Insights prints an impact only from five yes days and five no days that each have a Recovery score (`VIVA_MIN_DAYS_PER_ANSWER`), the number its own hint already named. The iPhone screen still prints any effect the ranker returns.
  - Three starter journal questions fold into the export's wording for the same behaviour when the import holds that wording (`vivaJournalAliases`): the Journal lists the behaviour once, and Behavior Insights counts both wordings together. Only wordings seen in a real export are listed.
- **Still to port:** the More tab, and the remaining visible "NOOP" strings such as the terms screen.

#### Android to-do list (in order, as of 2026-11-11)

**Start here next session.** The build of 2026-11-11 (Behavior Insights, reference-style vital ranges, the import-and-strap fixes) is on the test phone and is being used day to day. Issues found in that use come first. After them, in order:

- Re-check items 1.2, 1.4 and 1.6 below on the phone now that a full export is imported and the strap is paired; fix what still shows.
- Re-read the reference app's Health Monitor on a few more days and refit the orange and red limits in `VivaVitalRange` if they drift (they were fitted to two days).
- Strain scoring: with about seven hours of heart rate near 75 bpm the app scored 0.0 where the reference app gives a quiet day about 4. Compare a full strap-covered day before changing anything.
- A part-covered PAST day keeps its low stored strain until an export covering it is imported; `strainIsPartial` only guards today.
- The More tab (item 4), then the rest of the list.
- iPhone twins still to write: `VivaVitalRange`, the `mergeDaily` skin-temperature rule, the five-day minimum and the question folding in Behavior Insights.

**Read section 5.8 first.** It lists the iPhone work done after this list was written; its item 1 gives the Swift for to-do items 1.2, 1.4 and 1.6 below, and items 2 to 8 are new screens to port. Its "Android status" paragraph says which of them Android has: as of 2026-11-14, items 1, 2 and 7, which covers 1.2, 1.4 and 1.6 below. The Profile and Settings screens of 5.8 take the place of the More tab restyle (item 4).

A first look at the real app with imported data (one night imported, compared with the WHOOP app the same day) found screens that contradict each other. Those come first.

1. **Fix screens that disagree with each other.**
   1. (Fixed 2026-11-10: the Sleep detail now titles itself with the night it shows and says so when that is not last night.) Home and Sleep detail can describe different nights. When today's row has hours of sleep but no stage minutes, `VivaSleepDetail.anchorDay` falls back to the newest night that has stages, without saying so, while Home shows last night.
   2. The Sleep ring can show the previous day's score as today's when today has none.
   3. (Fixed 2026-11-10: now worded "You were in bed for h:mm".) Home's "You slept h:mm" is time in bed, not time asleep.
   4. HRV has three answers: "--" on Home and on Recovery detail, a carried-over older value in Health Monitor.
   5. (Fixed 2026-11-10: both now use `VivaVitals.summary`.) Home's "N of 5 vitals in" count disagrees with the Health tab, which shows all five.
   6. Recovery detail blames a missing baseline ("needs a few nights") when the real cause is that today's HRV is missing; it also shows Sleep Performance "--" while the Sleep ring shows a score.
2. (Answered 2026-11-11.) A later export carried every past day in full. The one gap is by design of the export: the cycle still in progress has no Day Strain or energy until it closes, so today's strain cannot come from an import. With a strap paired part-way through the day, Home now shows no strain figure for today (see `strainIsPartial` above).
3. (Done 2026-11-11: `VivaBehaviorInsightsScreen`, route `viva_behavior_insights`, opened from Home's My Journal card and the Recovery detail's "See impacts" card.) Behavior Insights. It needs five yes and five no days before printing an impact.
4. **More tab** restyle.
5. **Remaining visible "NOOP" wording**, starting with the first-run terms screen. The terms screen is waiting on a decision from the repo owner: `TERMS.md` itself says "NOOP" throughout and the screen repeats its legal statements, so renaming there changes what the terms say.
6. **Closer to WHOOP:**
   - Stress Monitor: today's hour-by-hour graph, time-in-band cards and a day stepper (the upstream Stress screen already computes these).
   - Strain detail: heart-rate zone rows, strength time and steps, with their weekly charts. Today it shows only Calories when no workout is logged.
   - Sleep detail: a sleep-stress contributor and card.
7. **Smaller gaps:** the ones listed under "Known gaps" above.
8. **Housekeeping:** merge `android-viva-port` into `main` when both sides are ready; run the full Android test suite once; find why the test task needs the hand-made `roomSchemaOracle` folder.
9. **With the strap paired to Viva:** check live sync and offload on real hardware, then compare Viva's scores with WHOOP's for the same days. ECG for the WHOOP MG comes after the core functions.

### 5.8 iPhone changes made after the Android port: what Android still has to mirror

Everything in this section was built on iPhone on 2026-11-10, after `android-viva-port` was merged. It is written for whoever ports it: each item names the Swift file to read and what the Kotlin twin has to do. Work top to bottom; items 1 and 2 change what existing Android screens say, the rest are new screens.

**Android status (2026-11-14).** Items 1, 2 and 7 are written on `android-viva-port`; items 3 to 6 and 8 are not started. What exists was compiled and unit-tested (`VivaConsistencyTest`, 11 cases, including the Swift cases for `recoveryGap` and `carriedDay`). It has **not** yet been looked at on a phone.

- **Item 1.** `viva/VivaConsistency.kt` holds `recoveryGap`, `carriedDay` and the day stamp. `VivaTrendMetric.todayOrCarried` is the one resolver behind Home's rows, the detail rows and (through the same carry window) the Health Monitor tiles. Home's Sleep and Recovery rings print "Last night · 9 Nov" when the score is carried; the Recovery route hands over that night and Home's Sleep score (`viva_recovery?day&score&from&sleep&sleepFrom`); `VivaPillarDetail.recoveryEmptyNote` names the real gap. The day-label time-zone fault did not exist on Android, where day keys are printed through `LocalDate`.
- **One deliberate difference from iPhone.** `todayOrCarried` on iPhone carries HRV and resting heart rate from any age (`lastHrvDay`, `lastRestingHrDay`) while the Health Monitor stops at seven days, so the two can still disagree on a reading older than a week. Android bounds all four vitals by the Health Monitor's seven-day window (`Baselines.freshestCarried`). iPhone should follow.
- **Item 2.** `BrandMark` in `AppRoot.kt` draws the "V" from `VShape`; the default avatar uses it too.
- **Item 7.** `CoachViewModel.memoryEnabled` gates save and load, and switching it off deletes the stored conversation. The preference is `conversation_memory` in the coach's own preference file, on by default. Until App settings exists on Android, the switch sits under the data-consent card on the upstream Coach settings screen.

**How the iPhone side was checked.** Every screen was built, opened in the iOS simulator with the built-in sample data and compared with the owner's reference recordings. `swift test` in `Packages/StrandDesign` passes (159 tests). What was **not** done: no screen was tapped through by hand (the simulator was driven by launch arguments only), nothing was tried on a real strap, and the independent code review of the redesign never completed. Treat behaviour below as "built and seen", not "proven".

**Reference recordings** (owner's own, outside the repo, never commit): `../whoop-ref/` (first recording), `../whoop-ref/video2/` (Device Settings), `video3/` (Profile), `video4/` (Edit Profile), `video5/` (settings pages). Ground rule 1 applies to all of them: layout only, no WHOOP text, marks, photos or badge names.

#### 1. Rules that stop screens contradicting each other (port first)

These close items 1.2, 1.4 and 1.6 of the Android to-do list in 5.7. The pure rules are in `Packages/StrandDesign/Sources/StrandDesign/VivaConsistency.swift`, with tests in `VivaConsistencyTests.swift`; copy the test cases into a Kotlin test.

| Rule | Swift | What the Kotlin twin must do |
|---|---|---|
| A carried score says whose it is | `VivaHomeData.sleepNote` / `recoveryNote`, set in the "Viva home adapter" at the end of `Strand/Liquid/LiquidTodayView.swift`; drawn under the ring label in `Home/VivaHomeContent.swift` | When today has no Sleep or Recovery score and Home shows an earlier night's, print the caption ("Last night · Nov 9") under that ring. |
| The detail screen is handed what Home showed | `VivaHomeRoute.recovery(score:note:sleep:sleepNote:)`; `VivaRecoveryDetailView(homeScore:note:sleep:sleepNote:)` in `Detail/VivaPillarDetailView.swift` | Extend `VivaHandedScore` to carry the caption and Home's Sleep score, so Recovery detail's ring and its Sleep Performance row equal Home's. |
| One answer for HRV, resting HR, respiratory rate, blood oxygen | `VivaTrendMetric.todayOrCarried` in `Detail/VivaTrendMetric.swift`; `VivaConsistency.carriedDay` | Use the same carry selectors Home and Health Monitor use. A value carried from an earlier day shows that day ("9 Nov") where the baseline normally sits, on Home rows, detail rows and Health Monitor tiles. |
| "Today's row" means today's row | `todayRow` in `VivaPillarDetailView.swift` | Never treat the newest stored day as today when today has no row. |
| Recovery detail names the real gap | `VivaConsistency.recoveryGap`; `emptyNote` in `VivaRecoveryDetailView` | Pick in this order: still calibrating (N of M nights), no sleep recorded, no HRV recorded, not scored yet. |

Also fixed on iPhone, check whether Android has the same fault: `BodyVitalSigns.dayFormatter` (`Strand/Screens/VitalSignsSummary.swift`) parsed day keys as UTC but printed them in local time, so west of UTC every vitals day label was one day early.

#### 2. Logo

`Packages/StrandDesign/Sources/StrandDesign/BrandMark.swift` now draws the app icon's mark: a "V" whose right arm has a heartbeat step and ends in a bright dot, mint to cyan, on a dark disc, with the icon's faint open ring behind it at 44pt and above. It replaced the old green open ring everywhere the mark appears (coach button, default avatar, "Ask a question" row, Sleep Planner header). The path coordinates are in `VShape`. Android's in-app mark should match.

#### 3. Home additions (`Home/VivaHomeContent.swift`)

- **Battery chip** (`VivaBatteryChip`, in `Device/VivaDeviceSettingsView.swift`): charge percentage, a small strap glyph and a connection dot; opens Device Settings.
- **Avatar opens Profile**, not Settings. A day-streak chip sits beside it when the streak is above zero. With a name set and no photo, the avatar shows initials (`VivaProfileAvatar`).
- **"Ready to get moving?" card** replaces the sleep prompt on a day with no sleep to review; its link opens the quick actions.
- **Pinned ring strip** now appears after 310pt of scroll (was 250).

#### 4. Device Settings (`Device/VivaDeviceSettingsView.swift`)

Full-screen cover from Home's battery chip and from Settings.
- **Header:** connected / not connected, strap name with a pencil (rename), last sync time (tap to sync).
- **Status tab:** a drawn band (not a product photo), charge with a coloured bar, strap model, Broadcast heart rate switch.
- **Advanced tab:** device ID, firmware version, Pair a device, Unpair device, Restart device, All devices.
- **Device & Battery** help page from the info button, text written fresh.
- Every action calls the function the upstream Devices screen already calls; no new strap command was added.
- **Deliberately absent, keep it that way on Android:** firmware check or install, erase device data (ground rule: no irreversible strap commands), the manufacturer's regulatory pages.
- Broadcast heart rate writes to the strap and was previously only in Test Centre. The owner has not yet confirmed it should stay on this screen.

#### 5. Profile, Achievements, Edit Profile (`Profile/VivaProfileView.swift`)

- **Profile:** name, age, city and country, "Recording since", recoveries scored, Fitness Age, day streak, a "Coach memory" row, three achievements with View All, Data Highlights and Activity Summary.
- **Counting rules:** `VivaProfileStats` (same file) and `VivaProfileMath` (StrandDesign, tests in `VivaProfileMathTests.swift`). Thresholds: strong sleep 70%, optimal sleep 85%, green recovery 67%, big effort Strain 10 on the 0-21 scale. `currentStreak` does not break on a day that simply has not been recorded yet.
- **Achievements:** nine badges across Sleep, Recovery and Strain with Viva's own names (Deep Rest, Steady Sleeper, Eight Hours, Green Light, Green Streak, Fully Charged, Big Effort, All Out, On the Move). Do not use WHOOP's badge names.
- **Data Highlights:** 1M / 3M / All time over best sleep, peak recovery, max strain, longest streaks, and lowest and highest resting HR and HRV, longest sleep, lowest recovery. No max heart rate (no per-day figure stored).
- **Edit Profile:** photo with a pencil badge and the full name at the top, scrolling with the form; First name, Last name, Birthday, Country, City, Gender, Units, Height, Weight; Save pinned at the bottom and disabled until something changes. Nothing is written until Save, except the photo, which saves at once.
  - Names, country and city are **local preferences only** (`viva.profile.firstName`, `lastName`, `name`, `country`, `city`): not in backups, not used by any score.
  - Birthday, gender, units, height and weight write the existing profile store and unit preference, so they change heart-rate zones, calories and Fitness Age.
- **Left out on purpose:** referrals, membership, gifting, logout, the manufacturer's age score.

#### 6. Settings (`Profile/VivaProfileView.swift`, `Profile/VivaSettingsPages.swift`)

Profile's gear opens **Settings**: Device settings, App settings, Data sources, Backup, How Viva works, Test centre, app version. There is no "My profile" row (the owner removed it).

**App settings** is a full-screen cover listing one row per topic. Every page writes the stored key or calls the function that already existed, so there is one value per setting:

| Row | Swift view | Existing setting or call it uses |
|---|---|---|
| Activity settings | `VivaActivitySettingsView` | list of the two pages below |
| Activity detection | `VivaActivityDetectionView` | auto-detect workouts key; keep-screen-on-during-workout key |
| Heart rate settings | `VivaHeartRateSettingsView` | `ProfileStore.hrMaxOverride`, `hrZoneThresholds`; draft with Save / Cancel; confirm prompts before switching manual zones on or off |
| AI settings | `VivaAISettingsView` | see item 7 |
| Data export | `VivaDataExportView` | `DataBackup.runExport`, `CsvExport.run`; saves a file straight away, no email step |
| Integrations | `VivaIntegrationsView`, `VivaAppleHealthConnectView` | Apple Health authorisation and sync (iPhone only; on Android the equivalent is Health Connect, if the upstream app has it); Mi Band; Shortcuts export (iPhone only); All data sources |
| Journal | upstream Insights screen | unchanged |
| Notifications | `VivaNotificationsView` | morning brief, wind-down reminder, journal reminder, and the three Lock Screen activities (iPhone only) |
| Language preferences | `VivaLanguageView` | the app's own language preference; takes effect on next launch |
| Cycle insights | upstream cycle card | row shown only when cycle tracking applies to the profile |
| Hide metrics | `VivaHideMetricsSheet` | opens the upstream Home customisation editor |
| All settings | upstream Settings page | unchanged |

`SettingsView(focus:)` (`Strand/Screens/SettingsView.swift`) shows the upstream Settings page narrowed to one topic; only "Restore a backup and more options" on the Data export page still uses it.

Text on these pages describes what Viva really does (activity detection only *offers* to save a workout; export is a local file). Do not copy the reference app's wording or its list of third-party services.

#### 7. Coach memory: a behaviour change, not only a screen

`Strand/AI/AICoach.swift` gained `AICoachEngine.memoryEnabled` (preference `ai.conversationMemory`, **on by default**).
- **On:** unchanged. Each completed exchange is saved on the phone and restored next launch, still retired at the day boundary.
- **Off:** nothing is saved or restored, and switching it off deletes the stored conversation. The transcript on screen is left alone.
- The Kotlin twin `CoachViewModel` needs the same gate on its save and load, and the same delete when it is switched off.
- `VivaAISettingsView` is the switch plus a Data privacy card whose text depends on the existing "let the coach use my data" consent.

#### 8. Presentation

Screens that close with an X are full-screen covers, not sheets: App settings, AI settings, Device Settings, Device & Battery. Small pickers (wake time, birthday, height, weight) stay as bottom sheets.

#### 9. Debug screenshot entries added

`--demo-screen` now also accepts `home`, `healthtab`, `journal`, `profile`, `editprofile`, `settingshub`, `appsettings`, `aisettings`, `devicesettings`, `devicebattery`, `activitydetection`, `hrsettings`, `dataexport`, `integrations`, `applehealthconnect`, `notifications`, `language`. `--demo-anchor advanced` opens Device Settings on its Advanced tab.

#### 10. Not done on either phone

- The independent code review of the redesign.
- A manual tap-through of the screens in items 3 to 7, and any check on a real strap.
- Journal, Cycle insights and Hide metrics still open upstream screens.
- About 580 internal and translated "NOOP" strings (5.2), including the terms screen.

### 5.9 Android changes made on 2026-11-14 to 16 that iPhone still has to mirror

These came from four days of wearing a WHOOP MG paired to the Android app and comparing it with the reference app's export for the same days. The wearer falls asleep between 03:00 and 07:30, which is what exposed them. Everything here was compiled and unit-tested, and the sleep, Recovery, calendar, Today and Home rows were then checked on a Pixel 7 Pro against the reference app.

| Change | Kotlin | What the Swift twin must do |
|---|---|---|
| **Sleep is bounded by the strap's own sleep state.** The motion-based detector banked still late-night desk time as sleep: onsets came out 2 to 4 hours early and two evenings became sleep outright, which skewed sleep, resting heart rate, HRV and Recovery together. The strap's banked `sleep_state` (2 = asleep) opened and closed within about 20 minutes of the reference app on four nights and a nap as the onset moved from 03:14 to 07:21. | `SleepStager.bandTrimmedPeriod`, switch `bandSpanTrimEnabled`, applied at the top of the run loop in `detectSleepUncached`; `IntelligenceEngine.retireBandRejectedNights`; tests in `BandSpanTrimTest` | Narrow each detected run to the band's sustained-asleep span (an asleep sample counts only when the band reads at least half asleep for the 60 minutes beyond it), place each edge midway to the neighbouring sample, drop a run the band covers and never scores asleep, and skip the daytime guards for a band-confirmed run. A run the band does not cover (WHOOP 4.0) is unchanged. |
| **Today lasts until the wearer has slept.** The fixed 04:00 rollover gave a night owl an empty new day in the middle of their evening. | `sleepAnchoredDay`, `SleepAnchor` in `LogicalDay.kt`; `resolveTodayRow` uses the same main-night test | Today is the previous day until a night of 180 minutes or more is banked for the local date, and from noon regardless. |
| **Resting heart rate is the night's typical low, not its floor.** The lowest five-minute mean read 2 to 5 bpm under the reference app on every night. The 30th percentile of the night's five-minute means landed within 1 bpm on all four (53, 55, 57, 63). | `SleepStager.sessionSleepingHR`, `sleepingHRPercentile`; `AnalyticsEngine.analyzeDay` stores it as the day's value and Recovery reads it, while strain, workout detection and calories keep the floor (`restingHRFloor`); `PrimaryNightRestingHRDailyTest` updated | Same statistic for the stored and displayed daily value; keep the floor for effort maths. |
| Home's day title opens the calendar. | `DayPickerDialog` in `TodayScreen.kt` | Open the date picker from the Viva day title. |
| Strain and Recovery card: a label sits on the side of its dot away from the day's other dot. | `VivaStrainRecoveryChart` | Same rule. |
| **The night does not end at a turn in bed (2026-11-15).** The strap banks gravity in the same half-hourly bursts as its sleep state, so one change of position between two bursts ended the gravity run and the rest of the night, under an hour, was dropped: two nights of five lost their last 45 minutes. | `bandTrimmedPeriod(limit:)`, `bandSpanTrimMaxGapMin`; the sustain rule is now 90 minutes at 0.6 | The wake edge follows the band forward while it keeps reading asleep with no silence over 45 minutes, never past the next run's start; the onset still only narrows. After it, both nights ended within 7 minutes of the reference. |
| **Sleep score refit (2026-11-15).** The Rest composite read 12 to 13 points over the reference on long nights. | `RestScorer`: `wDuration` 0.41, `wEfficiency` 0.58, restorative and consistency 0, `offset` -10 | **Superseded** by "Sleep score carries debt and bed-time consistency" below: use those weights, not these. |
| **Recovery eases below its midpoint.** Below-baseline nights scored 13 to 22 where the reference gives 40 to 44. | `RecoveryScorer.logisticKBelow` = 0.4 | Same slope below the midpoint. |
| **Strap nights appear on Home.** Home's activity list and Sleep detail read only imported sessions. | `sleepSessionsMerged` in `VivaHomeHost` and `VivaSleepDetailScreen` | Read imported and computed sessions together. |
| **Stress reads the moment, over 24 hours.** The upstream intraday read scores 06:00 to 22:00 against the day's own calm hours, so a wearer asleep from 05:00 to 13:00 read HIGH through their sleep. | `VivaStress`, `vivaStressNow` (`viva/VivaStress.kt`); Home tile, the new Home graph card above Strain and Recovery, and the Stress Monitor dial, 24-hour graph and Whole day / Awake / Asleep band cards all read it | Ten-minute mean heart rate against the median resting heart rate of the last 14 nights, `3 / (1 + e^-((hr - resting - 16) / 5.8))`. Heart rate only, fitted to seven reference readings from one day: a first calibration, to be refitted with HRV. |
| Home shows "Ready to get moving?" on a day with no sleep to review. | `HomePrompt` in `VivaHomeContent.kt` | Already on iPhone (5.8 item 3). |
| **The stress trace is coloured and stepped.** | `VivaLineChart(brush:shaded:)`, `VivaStress.lineBrush`, `VivaStressNow.sleepShading`, `vivaStressDaily`, `VivaStepButton` | Colour the 24-hour trace by height, shade the sleep span, add a day stepper (a past day runs midnight to midnight and the dial shows its average), and plot the 14-day chart from each day's average of the same reading. The Viva screen no longer links to the upstream stress screen, which scores by another model. |
| **Light load counts towards Strain.** The reference states that a day's strain includes everything that raises the heart rate, and scored quiet days 4 to 6.5 where zones starting at 50% of heart-rate reserve scored 0 to 4. | `StrainScorer.edwardsTRIMP`: `lightLoadWeight` 0.1 a minute from `lightLoadFloorPctHRR` 30 up to the first zone | Same weight. Checked on a phone: 8.6 against 8.4 and 6.3 against 6.5 on two full days; a third reads 8.3 against 6.6 and was already 1.5 high. Three days is thin, so treat the weight as provisional. |
| **Sleep score carries debt and bed-time consistency (2026-11-15, second fit).** The stored score read about 6 high on a long night after a short one and ignored irregular bed times. | `RestScorer`: `wDuration` 0.291, `wEfficiency` 0.723, `wConsistency` 0.264, `offset` -29.2, `NEUTRAL_CONSISTENCY` 0.709; `carriedNeedHours` (8 h + half the previous night's shortfall); `overlapConsistency` (mean overlap / union of tonight's bed span with each of the three nights before, moved by whole days); applied to the stored `sleep_performance` rows in `IntelligencePersistence.withNightHistory` | Same weights and the two helpers where the iPhone writes its sleep score. On 19 export nights the error falls from 3.9 to 2.9 points; readers that score one day alone keep the neutral consistency and 8-hour need and stay at 4. |
| **Awake minutes from heart rate (2026-11-16).** The stager calls wake only on sustained motion, and a 5.0 / MG banks motion as two-minute bursts every half hour, so nights read 98 to 100% efficient where the reference gave 94 to 97%. | `SleepStager.heartRateWake`, `hrWakeMedianMult` = 1.16: a minute whose mean heart rate reaches 1.16 x the night's median minute is awake, overlaid after the band veto and before efficiency | Same pass after staging. Fitted on four nights against the reference's in-bed minus asleep minutes (33 / 16 / 22 / 19); provisional. |
| **Sleep edges follow heart rate inside band gaps (2026-11-16).** The band arrives half an hour apart and each edge sat midway between two bursts: onsets came out 5 to 20 min late and wake up to 11 min early, while the reference's edges sat on the heart-rate drop (onset) and rise (wake) every night. | `SleepStager.hrEdgeInGap`, `hrEdgeWindowS` 300, `hrOnsetSettleMult` 1.06 (wake reuses `hrWakeMedianMult` 1.16), passed to `bandTrimmedPeriod(hr = hrS)` | Same search inside the gap the band trim picks. On the phone 12 to 14 Nov now start -8..+6 min and end -6..0 min from the reference; 11 Nov still ends 97 min late because the band itself read asleep after the reference's wake. |
| Pull-to-sync on Home shows its spinner. It ended the refresh as soon as the sync was asked for, so the indicator snapped back and looked like it had failed. | `delay(1500)` before `endRefresh()` in `TodayScreen.kt` | Keep the refresh indicator visible for a moment after a pull. |
| **Home shows the reference's 24 My Dashboard rows (2026-11-16).** Eight above the stress and strain cards (HRV, resting HR, steps, HR zones 1-3 and 4-5 weekly, strength activity time, VO2 max, calories), sixteen after them in alphabetical order, each against the mean of the 30 days before. The weekly zone rows and strength time count logged activities only, zones by %HRR: on the wearer's week the reference's 1:03 in zones 1-3 and 1:05 in all zones were exactly a 43-minute lift and a 22-minute walk. | `ui/viva/VivaDashboard.kt` (`loadVivaDashboard`, `VivaDashboard.rows`, `zoneMinutes`), `VivaHomeData.moreMetrics`; `IntelligencePersistence.withNightHistory` also stores `sleep_consistency`, `sleep_need_min`, `sleep_debt_min`; the Customize chip and the old key-metric rows are gone | Same rows and order. An imported workout's own zone shares win; otherwise band the strap's one-minute means inside it. |
| **Steps across burst gaps (2026-11-16).** A 5.0 / MG streams the step counter at 1 Hz only in bursts; one increment between bursts carries up to half an hour of walking and was dropped (judged by the later sample's class, usually still, and by the 512 cap): 259 shown against the reference's 1,507. | `StepsCounter.STREAM_GAP_S` 10, `MAX_GAP_TICKS_PER_SECOND` 3, `MAX_GAP_DELTA` 6,000; callers pass the elapsed time to `shouldCountDelta` | Same rule in Swift `StepsCounter`. **Known gap kept on purpose (owner's decision):** still-labelled ticks inside 1 Hz bursts stay uncounted, so Viva reads about 12% under the reference (1,324 against 1,507 on one day; counting them gives 1,523). The owner may switch this later. |
| **Respiratory rate from the breathing rhythm, read from the densest R-R channel (2026-11-16).** Two faults stacked. The R-R read pinned each window to the lowest channel (v18 history, 5), which on a 5.0 / MG holds a tenth of the night (3,500 to 9,600 beats a day against 42,000 to 63,000 on standard BLE, 7). And the peak picker read 2 to 3 low in fixed steps (14.1, 15.0). | `WHOOP5_RR_INTERVALS_SQL` takes the channel with the most beats per window (lower code on a tie); `SleepStager.respRateSpectral` (shares `rsaTachogram`): strongest frequency between 9 and 30 breaths a minute per five-minute window, median; Viva's vitals still never show an older night as tonight's | Both in Swift: the densest-channel read (it also moves HRV: 52.4 / 45.1 / 47.0 / 36.9 / 51.8 against the reference 55 / 44 / 42 / 38 / 50, mean gap 2.3 ms, was 2.5) and the spectral estimate (16.4 / 17.0 / 17.1 / 17.9 / 16.3 against 16.7 / 16.9 / 17.2 / 17.7 / 16.8). |
| **Skin temperature shown as the change from baseline, judged against a month.** | `VivaVitals.items` leads with the deviation under "Skin Temp (From Baseline)"; its range window turns imported absolute nights into differences from their own mean so the 30-day rule applies (only high is flagged) | Same display and window. |
| Recovery points on Home's Strain & Recovery chart are joined by a grey line. | `VivaStrainRecoveryChart` | Same. |
| **Long physiological days keep their resting energy; imported skin temp shows as a deviation; average HR per sleep cycle (2026-11-16).** Resting energy was capped at 24 h of observed time, but sleep-to-sleep days ran 26 and 28 h, reading 10 to 12% under the reference. A re-import puts the reference's absolute skin temperature on the merged row, which the tile showed as 33.8. | `WorkoutDetector.dayMaxObservedSpanS` = 36 h; `VivaVitals.items` turns an absolute skin reading into its difference from the 30 nights before (`absoluteDeviation`); `loadVivaDashboard` averages HR from `day_cycle_onset_ts` to the next onset | Same three in Swift. |
| **The import keeps the reference's daily energy.** cycles.csv "Energy burned (cal)" was never read, so the Calories row's 30-day baseline averaged only the strap's first (partly partial) days: 1,618 against the reference's 2,187. | `WhoopCsvImporter` cycle rows set `activeKcalEst`; `mergeRow` keeps it | Same in Swift `parseCycles` and its merge. Days still differ a little: the reference counts sleep onset to sleep onset (21 to 28 hours on these days), Viva the calendar day. |
| **Sleep score and recovery hold at any night length (2026-11-16).** The additive sleep weights were fitted on nights of 62 percent of need and up and read 13 to 15 points over the reference on nights near half the need (65 against 48 on a 4-hour night). Recovery read 74 against 47 on the same night: with HRV and resting HR at their usual level, the reference scores a night at half the need about two thirds of a full night's value, and nights at 0.62 to 0.69 of the need about nine tenths. This supersedes the additive sleep weights in the row above. | `RestScorer.rest` = `sufficiencyFactor(share of need)` x (0.63 efficiency + 0.38 consistency); the factor is 1.35 x share below `shortSleepKnee` 0.68, then a line to 1 at the full need (25 reference sleeps including a 50-minute nap, root-mean-square error 2.0). `RecoveryScorer.shortSleepFactor` = min(1, asleep / 8 h / 0.75), multiplied onto the composite in `AnalyticsEngine.analyzeDay` and `IntelligenceEngine.recomputeRecovery`, with a `charge shortSleepFactor=` trace line. | Same two curves and constants. Both are provisional below half the need: the fit has only the nap there. |
| **Recovery curve refit (2026-11-16).** Fed the reference's own HRV, resting HR, respiratory rate, skin temperature and sleep for 15 nights after its calibrating first week, the composite read 5.5 points low on average (root-mean-square error 6.3). Moving the curve's midpoint and flattening its lower side brings that to 2.6, and 3.1 on nights left out of the fit. Refitting the weights or the upper spread did not help on left-out nights, so they stayed. On the strap's own inputs the six compared days are mixed (three closer, three further): the strap's HRV reads up to 5 ms from the reference's, worth about 3.5 points per ms mid-range, so HRV fidelity is the next lever. | `RecoveryScorer.logisticZ0` -0.20 to -0.43 and `logisticKBelow` 0.4 to 0.26; pinned recovery tests re-derived (half-point ties, trace fixture, optional-baseline table). | Same two constants. The Swift oracle literals quoted in `RecoveryRequiredHrvBaselineTest` and `WatchRecoveryTest` then need regenerating. |

**Still wrong against the reference app, not yet touched:** skin temperature reads about 1 °C low; blood oxygen is not computed from the strap (a 5.0 / MG record carries no red/IR channels and its @82 byte was 0 on four of five nights; only raw PPG bursts remain to study); stress reads heart rate alone, so it runs high when heart rate is up but HRV is normal. Resting heart rate, the sleep window and quiet-day Strain were fixed in the rows above.

**Findings behind these rows, for whoever mirrors them (2026-11-16).** Method: each score was fitted against the reference app's data export (or values read off the reference app) for the same nights, one function changed at a time, then re-scored on the phone and compared again. Facts that apply to the iPhone side too:

- **A 5.0 / MG banks gravity and the band's sleep state only as two-minute bursts every half hour**; heart rate arrives every second. Anything that needs motion (wake detection, the end of a gravity run) sees almost nothing, so heart rate has to carry it. This is why nights read 98 to 100% efficient and why one turn in bed used to end the night.
- **Reference sleep score inputs.** Its sleep page shows four: hours against need, consistency, efficiency, sleep stress. On the reference's own inputs, `0.294 x hours-vs-need + 0.659 x efficiency + 0.293 x consistency - 25.1` fits to 2 points (20 nights). Its need is about 462 min base + about half the previous night's shortfall + 0 to 36 min for strain - naps; Viva uses 8 h + half the shortfall (no strain or nap terms yet). Its consistency is matched best by the overlap / union of the night's span with each of the previous three nights (correlation 0.70); a plain bed-time spread only reaches 0.4 to 0.5.
- **Efficiency drives the score**: every point of efficiency is worth about 0.7 points of sleep score, so wake detection matters more than the formula.
- **Still off on Android:** efficiency reads about 2 points above the reference (96.7 to 97% against 94 to 95%), which keeps two nights' sleep score 3 to 5 high; 11 Nov still ends at 14:17 against the reference's 12:40 (the band kept reading asleep after the reference's wake), which drops that night's efficiency to 88%.
- **Home dashboard (next after the sleep edges).** The reference's My Dashboard can show 24 rows, each today's value against a 30-day baseline: HRV, Resting HR, Steps, HR Zones 1-3 / 4-5 / All (weekly), Strength Activity Time, VO2 Max, Calories, Average Heart Rate, Day Strain, Hours of Sleep, Lean Body Mass, Recovery, Respiratory Rate, Restorative Sleep (% and hours), Sleep Consistency, Sleep Debt, Sleep Efficiency, Sleep Needed, Sleep Performance, Time in Bed, Weight. Where the data lives: the daily metrics row has strain, recovery, respiratory rate, efficiency, hours, deep + REM and time in bed (asleep / efficiency); the metric series has sleep performance, `vo2max_est` (weekly) and Health Connect / Apple Health weight, body fat and BMI (lean body mass = 100% - body fat); sleep consistency, need and debt are computed during scoring but not yet stored; average heart rate, the weekly heart-rate zone times and strength activity time still need computing.

## 6. Open work (priority order)

1. **Request the WHOOP data export now**, before the subscription ends. In the WHOOP app go to Profile → Data Export, or use app.whoop.com.
   - The export contains `physiological_cycles.csv`, `sleeps.csv`, `workouts.csv` and `journal_entries.csv`.
   - It is the only ground truth available for validation.
2. **Bring Android level with iPhone:** port section 5.8 (the consistency rules first, then the new screens), then finish the earlier Android list in 5.7 (Behavior Insights, More tab, the known gaps).
   - On iPhone, the outstanding checks are a manual tap-through of the newer screens and the code review (5.8 item 10).
3. **Finish Phase 1 validation:**
   - Write a reader that parses the WHOOP export **directly** for comparison.
     - Do not use the importer: importing into the database overwrites Viva's own computed rows.
     - Key each day with `WhoopDayKeying.wakeDayKey`.
     - Keep strain on WHOOP's 0–21 scale and convert Viva's value to match.
   - Add a `ValidationView` reached from Settings / More. Use design tokens only. It shows a row per metric (MAE, bias, r) and a scatter plot.
4. **WHOOP 5.0 hardware pass.** Once the owner enables the deep-data toggles, check the decoded historical, HRV and sleep streams against the export, and fix the decoders.
5. **Phase 2: metric tuning**, driven by the validation error. Each change goes in as a parameter alongside the old path, with an XCTest.
   - **HRV:** nightly RMSSD from a late-sleep window, compared against an ln‑RMSSD baseline.
   - **Resting HR:** the lowest sustained 5‑minute mean.
   - **Recovery:** expose the weights in a config struct and fit them to the owner's export.
   - **Strain:** Banister TRIMP, with the log constant fitted to the 0–21 scale and HRmax calibrated.
   - **Sleep:** tune the stage gates in `SleepStagerV2`.
   - **Targets:** recovery MAE below 10, RMSSD MAE below 8 ms, sleep-duration MAE below 20 min.
6. **Optional:**
   - remove the Watch app if it isn't used
   - drop the `onChangeCompat` shim
   - bulk-rename the remaining "NOOP" strings
