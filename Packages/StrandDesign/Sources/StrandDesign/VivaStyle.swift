import SwiftUI

// MARK: - Viva visual tokens
//
// The dark, flat look the iPhone app is built to: a cool blue-grey canvas that darkens down the
// page, borderless cards, white type, and one colour per pillar. Values were sampled from the
// owner's reference recording (see docs/superpowers/specs/2026-11-09-viva-whoop-style-iphone-design.md).
// The look is dark-only, so each token carries the same hex for both schemes.

public enum VivaStyle {
    private static func fixed(_ hex: String) -> Color { Color(light: hex, dark: hex) }

    // Surfaces
    public static let canvasTop = fixed("#20262C")
    public static let canvasBottom = fixed("#14171B")
    public static let card = fixed("#24272B")
    public static let inset = fixed("#1A1D21")
    public static let chip = fixed("#2B3137")
    public static let chipActive = fixed("#3D4349")
    public static let callout = fixed("#2A1D33")
    public static let tabBar = fixed("#1E2329")
    public static let coachButton = fixed("#26283A")
    public static let divider = fixed("#2E3237")

    // Text
    public static let textPrimary = fixed("#FFFFFF")
    public static let textSecondary = fixed("#9AA4AC")
    public static let textTertiary = fixed("#6B7177")

    // Pillars
    public static let sleep = fixed("#7096B6")
    public static let strain = fixed("#0088E3")
    public static let recoveryGreen = fixed("#16EC06")
    public static let recoveryYellow = fixed("#FFDE00")
    public static let recoveryRed = fixed("#FF0026")
    public static let recoveryCalibrating = fixed("#909297")
    public static let ringTrack = fixed("#2F343A")

    // Sleep stages
    public static let stageAwake = fixed("#C9CDD2")
    public static let stageLight = fixed("#9B9BF0")
    public static let stageDeep = fixed("#F88BF2")
    public static let stageREM = fixed("#A44BE8")

    // Accents and status
    public static let magenta = fixed("#CF42DD")
    public static let link = fixed("#4C9FEA")
    public static let positive = fixed("#01ED9A")
    public static let warning = fixed("#F0A020")
    public static let critical = fixed("#FF0026")
    public static let coachRing = fixed("#5B6CFF")
    public static let chipGradientStart = fixed("#F9837C")
    public static let chipGradientEnd = fixed("#A231F0")

    // Metrics
    public static let cardRadius: CGFloat = 14
    public static let tileRadius: CGFloat = 10
    public static let pagePadding: CGFloat = 16
    public static let cardPadding: CGFloat = 16
    public static let cardGap: CGFloat = 12
    public static let sectionGap: CGFloat = 28

    /// Recovery is the one pillar whose colour carries its value: green from 67, yellow from 34,
    /// red below. A missing score reads as neutral grey so an empty ring never looks like a bad one.
    public static func recoveryColor(percent: Double?) -> Color {
        guard let percent else { return textSecondary }
        if percent >= 67 { return recoveryGreen }
        if percent >= 34 { return recoveryYellow }
        return recoveryRed
    }
}

/// Type scale for the Viva look: tight uppercase card titles, mixed-case page sections, and bold
/// slightly condensed numerals. System faces only.
public enum VivaFont {
    private static func numeral(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        Font.system(size: size, weight: weight).width(.condensed).monospacedDigit()
    }

    public static let cardTitle = Font.system(size: 12, weight: .bold)
    public static let cardTitleTracking: CGFloat = 1.2
    public static let sectionTitle = Font.system(size: 22, weight: .semibold)
    public static let body = Font.system(size: 14, weight: .regular)
    public static let bodyStrong = Font.system(size: 15, weight: .semibold)
    public static let caption = Font.system(size: 12, weight: .regular)
    public static let label = Font.system(size: 10, weight: .bold)
    public static let labelTracking: CGFloat = 1.0
    public static let valueLarge = numeral(28)
    public static let valueMedium = numeral(22)
    public static let valueSmall = numeral(16, .semibold)
    public static let unit = Font.system(size: 12, weight: .semibold)
    public static let baseline = numeral(10, .medium)
    public static let ringSmall = numeral(29)
    public static let ringLarge = numeral(72)
    public static let wordmark = Font.system(size: 15, weight: .light)
    public static let wordmarkTracking: CGFloat = 3
    public static func number(_ size: CGFloat, weight: Font.Weight = .bold) -> Font { numeral(size, weight) }
}

/// The page background: lighter blue-grey behind the top chrome, settling to near-black.
public struct VivaCanvas: View {
    public init() {}
    public var body: some View {
        LinearGradient(
            stops: [
                .init(color: VivaStyle.canvasTop, location: 0),
                .init(color: VivaStyle.canvasBottom, location: 0.42),
                .init(color: VivaStyle.canvasBottom, location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

public extension Text {
    /// Small uppercase tracked title used at the top of every card.
    func vivaCardTitle(_ color: Color = VivaStyle.textPrimary) -> some View {
        self.font(VivaFont.cardTitle)
            .tracking(VivaFont.cardTitleTracking)
            .textCase(.uppercase)
            .foregroundStyle(color)
    }

    /// Tiny uppercase tracked caption under values and on chart legends.
    func vivaLabel(_ color: Color = VivaStyle.textSecondary) -> some View {
        self.font(VivaFont.label)
            .tracking(VivaFont.labelTracking)
            .textCase(.uppercase)
            .foregroundStyle(color)
    }
}
