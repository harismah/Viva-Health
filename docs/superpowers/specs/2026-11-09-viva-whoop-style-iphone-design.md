# Viva iPhone: WHOOP-style redesign (2026-11-09)

## Intent

Viva (formerly NOOP) should look and feel like the WHOOP iPhone app shown in the owner's
screen recording: same screen structure, card styles, information sections, charts, alarm
planner and navigation. The owner uses only the iPhone app.

Reference material (outside the repo, contains personal health data, never commit):
`/Users/haris/Desktop/META/Practice/project-Whoop/whoop-ref/`

- `sheets/s_01..15.jpg`: numbered contact sheets, one frame per second (frame N = second N)
- `frames-1s/f_0001..0231.jpg`: the same frames individually (f_0001 = sheet number 0)
- `full/tNNN.png`: full-resolution stills at key seconds
- Source video: `~/Downloads/ScreenRecording_11-09-2026 20-27-41_1.MP4` (extract more with ffmpeg)

The earlier written reference `2026-06-22-whoop-design-language.md` still applies where it
does not conflict with this document; the video wins on any conflict.

## Goals

1. Every screen listed under "Screens" matches its video frames in layout, spacing, colour,
   type, card shapes and section order, fed by Viva's real data.
2. Navigation matches: floating pill tab bar plus separate round coach button; detail screens
   with back chevron, centred uppercase title and info button.
3. Each screen is checked by a simulator screenshot beside the matching video frame.

## Non-goals

- Android, Mac, Watch and widgets. Their code stays in place untouched. `Strand/` is shared
  with the Mac target; only the iPhone target (`NOOPiOS`) is built and verified. Use
  `#if os(iOS)` where a shared file must diverge.
- Community tab, shop, membership, referrals, "Discover More" promos, Advanced Labs.
- Light theme, Classic style and accent pickers: not tuned. The target is the dark look.
- Changes to scoring, sync, BLE, storage or any analytics.

## Anti-goals

- No WHOOP logo, wordmark, "W" mark, product photos, article or video content, or copied
  explainer copy. Where the video shows the WHOOP wordmark (above the home rings, inside the
  detail ring) use "VIVA" in the same position and weight. The coach button and coach bar
  use Viva's own mark. Explainer text is written fresh from Viva's existing copy.
- No invented data. A section with no Viva data source shows the video's "calibrating" /
  "no data" state, not placeholder numbers.
- Do not lose or revert the owner's uncommitted working-tree changes (69 files, the Viva
  rename among them). No commits unless the owner asks.

## Visual tokens (sampled from the video)

```
canvas            vertical gradient #20262C (top) -> #14171B (below ~40% height)
card              #23262A .. #26292D, radius ~16, no border, no shadow
inset tile        #15181C .. #1D2024 (tiles inside sheets, chart wells, callout rows)
callout (purple)  #2A1D33 with a 4-segment magenta progress strip (calibration notes)
tab bar pill      #1E2329, coach button #26283A with a thin blue-violet ring
text              white primary; grey #9AA4AC secondary; #6B7177 tertiary
sleep             #7096B4 (ring, bars, activity pill)
strain            #0093E7-ish bright blue (ring, bars)
recovery          value-based green #16EC06-ish / yellow / red; calibrating ring is light grey
stages            awake light grey, light #9D9DF2-ish lilac, deep #FA6BD6-ish pink, REM #A855F7-ish purple
links             magenta on the home prompt card and CUSTOMIZE chip; blue on text links
positive / alarm  green #03E095; warning amber #F0A020
```

Builder samples exact hexes from `full/*.png` when implementing each component. Tokens go in
`Packages/StrandDesign` (`Palette.swift` / `NoopVisualStyle.swift`) so screens inherit them.

Type: card titles are small UPPERCASE bold, tracked. Page sections ("My Day", "My Dashboard",
"Weekly Trends", "Last Night's Sleep") are large mixed-case semibold. Values are large bold
white with a small unit and a small grey baseline under them. Use the system font with the
closest weights and widths; no font files are added.

## Shared components (build first, in StrandDesign or Strand/Screens)

- `ScoreRing`: thick rounded ring, dark track, bold centre value; small (home) and large
  (detail, with wordmark above the value and label below).
- `WCard`: flat card with uppercase title and optional chevron / info / expand glyph.
- `MetricRow`: line icon, uppercase label, right-aligned value, grey baseline, trend dot/arrow.
- `CalibrationCallout`: purple card with segmented progress.
- `HatchedRangeBar`: solid fill over diagonal-hatch typical range (stages, time in bed, impact).
- `WeekBarCard`: 7-day chart card with highlighted "today" column and value label; bar, dot
  and stacked variants.
- `TrendView`: metric picker dropdown, W / M / 6M segmented control, date stepper, summary
  sentence, chart, breakdown legend, explainer card.
- `VitalsTile`: two-column tile with icon, label, value + unit and status chip.
- `HalfDialGauge`: stress dial with needle marker and 0.0 / 3.0 end labels.
- `CoachBar`: bottom-pinned rounded bar with plus, one-line message and mic; expands to a sheet.
- `PillTabBar` + round coach button; `DetailTopBar` (back, uppercase title, info / gear).
- `InfoSheet` and centred `ExplainerDialog` (Health Monitor tiles).

## Screens (video second -> Viva source)

| Screen | Video | Built on |
|---|---|---|
| Home | 0-19, 100-102, 203-218 | `LiquidTodayView` / `TodayView`, `RootTabView` |
| Sleep detail | 20-33, 54-64 | `SleepView`, `StagesVsTypicalCard`, `HoursVsNeededCard`, `ConsistencyCard` |
| Trend View | 34-44, 80-97, 120-130 | `TrendsView`, `MetricExplorerView` |
| Recovery detail | 67-79, 87, 91 | Today charge breakdown, `VitalSignsSummary` |
| Strain detail | 103-119, 127-139 | Today effort breakdown, `TrainingLoadCard`, `WorkoutsView` |
| Health Monitor | 142-159 | `HealthView`, `VitalSignsSummary` |
| Stress Monitor | 162-191 | `StressView`, `BreathingView` |
| Sleep Planner + alarm | 99, 196-202 | `SmartAlarmView`, `SleepCustomizationSheet` |
| Journal | 211-214 | `JournalLogCard` |
| Behavior Insights | 206-208 | `InsightsView` |
| Health tab | 219-225 | `HealthView`, `InsightsHubView` |
| More tab | 229-230 (top bar only) | `SettingsView` and existing More list |

Home section order: top bar (profile + streak, `< TODAY >` stepper, battery) ; VIVA wordmark;
three rings Sleep / Recovery / Strain with `LABEL >`; prompt card; Health Monitor + Stress
Monitor half-width cards; My Day (+ button, ask-coach row, Today's Activities with sleep pill
and Add / Start Activity, Tonight's Sleep with bedtime, wake time, alarm state and Edit Alarm,
My Journal week strip and Behavior Insights button); calibration card when still calibrating;
My Dashboard with CUSTOMIZE chip and metric rows; Stress Monitor chart card; Strain &
Recovery week chart. Rings collapse into a compact pinned strip on scroll (video second 2).

Naming: screens use the video's labels (Sleep, Recovery, Strain, Health Monitor, Stress
Monitor, Sleep Planner, My Day, My Dashboard) in place of Viva's Rest / Charge / Effort wording.
This is display text only; code identifiers and stored data keep their names.

## Decisions

The owner decided:
- iPhone only.
- Product name is Viva.
- No Community, shop or membership sections.
- Everything else matches the video in style and feel.

Builder's calls (flagged for the owner):
- Tab bar: Home, Health, More, plus the round coach button. Trends and Sleep stop being tabs
  and are reached from Home, as in the video. Coach opens from the round button and coach bar.
- More tab keeps every existing Viva settings and tools screen, restyled with the new cards.
  Screens not in the video (lift log, hydration, devices, backups, etc.) get the new tokens
  and card style but no layout redesign.
- Healthspan orb area on the Health tab shows Viva's Fitness Age / Vitality in that card style.
  Advanced Labs is omitted.
- "Learn more" article/video carousels are omitted; the explainer text card stays.
- Other platforms' code is left on disk; Mac may drift visually and is not verified.
- Dark look only is tuned.

## Build order

1. Tokens and shared components; pill tab bar, coach button, detail top bar.
2. Home.
3. Sleep, Recovery, Strain detail and Trend View.
4. Health Monitor, Stress Monitor, Health tab.
5. Sleep Planner and alarm, Journal, Behavior Insights.
6. More tab and token sweep over remaining screens.

## Verification

- Build: `xcodegen generate` if `project.yml` changes, then `xcodebuild -project
  Strand.xcodeproj -scheme NOOPiOS -destination 'platform=iOS Simulator,name=iPhone 18 Pro Max' build`
  (confirm the scheme name with `xcodebuild -list`).
- Data: use the existing demo seeding (`Strand/Data/AppleDemoSeeder.swift`, `DemoDayHarness.swift`)
  so every section has values.
- Per screen: `xcrun simctl io booted screenshot`, compared with the listed video frames; the
  owner is shown both at the end of each build-order step.
- `StrandDesign` package tests keep passing; tests asserting on old strings or layout are
  updated, not deleted.

## Left to the builder

Exact spacing and font sizes (measure from `full/*.png`, 1320 px wide = 440 pt), icon choices
from SF Symbols closest to the video's line icons, animation details, and how existing view
code is reused versus rewritten per screen.
