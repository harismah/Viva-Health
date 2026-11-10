# Viva: handoff context

Give this file to your coding agent before it touches the repo. It covers what Viva is, the rules the work follows, what has been done so far and what is still open. Last updated: 2026-11-09.

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
- the Android parity rule (`android/` was deleted)
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
  | StrandDesign | 135 |

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

**Android (`android/`), restored for a friend's phone.** It is upstream's Kotlin/Compose app with only the branding changed:
- The launcher name, widget names and on-screen wordmarks say "Viva", in all locales.
- The launcher icon is the Viva mark, drawn as vector layers in `res/drawable/ic_launcher_*.xml`, in teal and navy variants.
- None of the Viva screen styling is there yet.
- It is not built or tested in this fork; that needs JDK 17 and the Android SDK.

**Android setup (for the friend using Android):**
1. Install Android Studio. It bundles JDK 17 and the Android SDK; a system JDK 15 or older will not build.
2. **File → Open** and select `android/`. Let Gradle sync; `android/gradlew` provisions Gradle 8.7.
3. Build variant **fullDebug**. `demo` is a fake-data flavour, and `release` needs a signing key.
4. Enable USB debugging on the phone, connect it, then press Run.
   - Command-line alternative: `./gradlew assembleFullDebug`. The APK lands in `app/build/outputs/apk/full/debug/`.
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
- **Look.**

Compose shares no UI code with the SwiftUI app, so every Viva screen and style must be re-implemented in Kotlin, in `android/app/src/main/java/com/noop/ui/`. `Tools/parity_cases/` was restored only because the Android tests read it.

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

### 5.6 UI redesign (in progress, done in a separate session)
- **Spec:** `docs/superpowers/specs/2026-11-09-viva-whoop-style-iphone-design.md`
- **Plan:** `docs/superpowers/plans/2026-11-09-viva-whoop-style-iphone.md`
- **Look:** a dark look with a floating pill tab bar (Home, Health, More) plus a round coach button, and detail screens with a back chevron and a centred uppercase title.
- **Reference material:** the visual reference is the owner's own screen recording. The spec's **anti-goals** must hold:
  - no WHOOP marks; "VIVA" sits wherever the reference shows a logo
  - no copied text; explainers are written fresh
  - no invented data; sections with no data show a "calibrating" / "no data" state
  - no changes to scoring, sync or BLE as part of the redesign
- **Built so far:**
  - **StrandDesign tokens and components:** `VivaStyle`, `VivaScoreRing`, `VivaCard`, `VivaMetricRow`, `VivaCallout`, `VivaRangeBar`, `VivaWeekBarCard`, `VivaVitalsTile`, `VivaHalfDial` and `VivaAlarmReadout`, with tests.
  - **App shell and Home:** `StrandiOS/App/VivaTabBar.swift`, `Strand/Screens/VivaCoachBar.swift`, `VivaDetailScaffold.swift`, `Home/VivaHomeContent.swift` and `Alarm/VivaAlarmSource.swift`.
- **In progress (uncommitted):**
  - `Detail/VivaSleepDetailView.swift`, `VivaCharts.swift` and `VivaTrendMetric.swift`
  - the `.trend(VivaTrendMetric)` route
  - demo-screen entries
- **Still placeholders:** screens not built yet resolve through `Detail/VivaPendingScreens.swift` to the existing upstream screens. Delete each alias when its real screen lands.
- **Remaining plan tasks, roughly from Task 7 on:**
  - Recovery and Strain detail
  - Trend View
  - Health Monitor, Stress Monitor and the Health tab
  - Journal and Behavior Insights
  - Sleep Planner and alarm
  - the More tab sweep
- **Verification method:** a simulator screenshot (`--demo-screen`) beside the matching reference frame.

## 6. Open work (priority order)

1. **Request the WHOOP data export now**, before the subscription ends. In the WHOOP app go to Profile → Data Export, or use app.whoop.com.
   - The export contains `physiological_cycles.csv`, `sleeps.csv`, `workouts.csv` and `journal_entries.csv`.
   - It is the only ground truth available for validation.
2. **Finish the redesign plan** (section 5.6).
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
