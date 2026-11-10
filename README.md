<p align="center">
  <img src="docs/assets/viva-icon.png" alt="Viva" width="96">
</p>

<h1 align="center">Viva</h1>

<p align="center"><b>A personal, on-device companion app for the WHOOP 5.0 strap.</b></p>

<p align="center">
  <img alt="Platforms" src="https://img.shields.io/badge/platforms-iPhone%20%C2%B7%20Android-2EE6D6?style=flat-square">
  <img alt="Works with WHOOP 5.0" src="https://img.shields.io/badge/works%20with-WHOOP%205.0-3D8BFF?style=flat-square">
  <img alt="Local first" src="https://img.shields.io/badge/local-first-7CFFB2?style=flat-square">
  <a href="LICENSE"><img alt="License: PolyForm Noncommercial 1.0.0" src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-6B737B?style=flat-square"></a>
</p>

---

Viva is a personal fork of [**NOOP**](https://github.com/ryanbr/noop). It pairs with a WHOOP strap you own over Bluetooth and stores everything in a database on your phone. It computes its own Recovery, Strain, Sleep and HRV scores on the device:
- no account
- no server
- no telemetry

Viva is an independent, unofficial project. It is not affiliated with, endorsed by or sponsored by WHOOP, Inc. "WHOOP" is their trademark, used only to name the hardware Viva works with.

> **New here, or handing this to a coding agent?** Read [`VIVA_HANDOFF.md`](VIVA_HANDOFF.md) first. It covers the ground rules, what has been done and what is still open.

## Status

| | |
|---|---|
| **iPhone** (`NOOPiOS`) | The main target. It is being redesigned with the new Viva look: a dark theme, a floating tab bar and new detail screens. It builds clean in Xcode. |
| **Android** (`android/`) | Upstream NOOP's Kotlin app with Viva branding (name and icon). The Viva screen styling has not been ported yet. Not yet built in this fork. |
| **WHOOP 5.0** | Pairing and live heart rate work. Historical offload and deep data are decoded but **not yet validated on hardware**; they need the experimental toggles in Settings. |
| **Scores** | Approximations computed on the device, not medical measurements. Work is under way to check them against the owner's own WHOOP data export. |

## Build and run

### iPhone

Requirements:
- Xcode
- XcodeGen (`brew install xcodegen`)

```sh
xcodegen generate          # Strand.xcodeproj is generated from project.yml; never edit it by hand
open Strand.xcodeproj
```

1. Copy `Config/BundleIdSecrets.example.xcconfig` to `Config/BundleIdSecrets.xcconfig` and set your own `BUNDLE_ID_PREFIX`.
2. In Xcode, select your Team, pick the **NOOPiOS** scheme and your iPhone, then press Run.
3. A free Apple ID signature expires after 7 days. [SideStore](https://sidestore.io) can refresh it for you.

### Android

You need Android Studio, which bundles JDK 17 and the Android SDK.

1. In Android Studio, choose **File → Open** and select the `android/` folder. Let Gradle sync.
2. Select the **fullDebug** variant, connect the phone with USB debugging on, then press Run.

Or build from the command line with JDK 17:

```sh
cd android && ./gradlew assembleFullDebug
# APK: android/app/build/outputs/apk/full/debug/
```

The app needs Android 8.0 (API 26) or later.

### Tests

```sh
cd Packages/StrandAnalytics && swift test        # also WhoopProtocol, WhoopStore, StrandImport, StrandDesign
cd android && ./gradlew testFullDebugUnitTest
```

## Project layout

| Path | What lives here |
|---|---|
| `Strand/` | Shared Swift app code: screens, Bluetooth, data, coach |
| `StrandiOS/`, `StrandiOSShared/`, `StrandiOSWidgets/` | The iPhone app shell, widgets and app icons |
| `NOOPWatch/`, `NOOPWatchComplications/` | Apple Watch companion (embedded in the iPhone app) |
| `Packages/WhoopProtocol` | Bluetooth frame decoding, including the WHOOP 5.0 protocol |
| `Packages/WhoopStore` | On-device database (GRDB / SQLite) |
| `Packages/StrandAnalytics` | Scoring maths: HRV, recovery, strain, sleep, plus the score comparator used for validation |
| `Packages/StrandImport` | Importers for the WHOOP CSV export and Apple Health |
| `Packages/StrandDesign` | Design tokens and components, including the `Viva*` components |
| `android/` | The Android app (Kotlin, Jetpack Compose, Room), an independent re-implementation of the same logic |
| `docs/superpowers/` | The current iPhone redesign spec and plan |

Code identifiers (`Strand`, `NOOPiOS`, `com.noop.*`) keep their upstream names, so changes from upstream can still be merged. Only what you see in the app says "Viva".

## Privacy

Everything stays on the phone. Viva has no account, no Viva server, no analytics and no crash reporting. The optional AI coach is off until you add your own API key. When it is on, it sends only a short text summary of recent metrics with your question.

## Licence and credits

- Viva is built on [NOOP](https://github.com/ryanbr/noop) by its maintainers and contributors. See [`ATTRIBUTION.md`](ATTRIBUTION.md) and [`NOTICE`](NOTICE).
- It is licensed under [PolyForm Noncommercial 1.0.0](LICENSE): personal, non-commercial use only. It may not be sold.
- Read [`DISCLAIMER.md`](DISCLAIMER.md) and [`TERMS.md`](TERMS.md) before use.
- Using a third-party app with your strap may breach WHOOP's Terms of Service. That is your decision and your risk.
- Viva is not a medical device. Don't use it to diagnose, treat or make health decisions.
