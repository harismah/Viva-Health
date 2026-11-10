import SwiftUI

/// The pillar score ring: a thick rounded arc over a dark track with the value centred.
/// `.small` is the home trio; `.large` is the detail hero and carries the wordmark and label inside.
public struct VivaScoreRing: View {
    public enum Format: Sendable { case percent, decimal1 }
    public enum Size: Sendable {
        case small, large, mini
        var diameter: CGFloat { switch self { case .small: 87; case .large: 260; case .mini: 22 } }
        var stroke: CGFloat { switch self { case .small: 5.5; case .large: 16; case .mini: 2.5 } }
    }

    private let value: Double?
    private let maximum: Double
    private let color: Color
    private let format: Format
    private let size: Size
    private let label: String?
    private let wordmark: String?

    public init(value: Double?, maximum: Double, color: Color, format: Format,
                size: Size, label: String? = nil, wordmark: String? = "VIVA") {
        self.value = value
        self.maximum = maximum
        self.color = color
        self.format = format
        self.size = size
        self.label = label
        self.wordmark = wordmark
    }

    /// Share of the ring to fill, clamped so an out-of-range score never over- or under-draws.
    public static func fraction(value: Double?, maximum: Double) -> Double {
        guard let value, maximum > 0, value.isFinite else { return 0 }
        return min(max(value / maximum, 0), 1)
    }

    /// The centred number without its unit; a missing score reads `--`, never `0`.
    public static func text(value: Double?, format: Format) -> String {
        guard let value, value.isFinite else { return "--" }
        switch format {
        case .percent: return String(Int(value.rounded()))
        case .decimal1: return String(format: "%.1f", value)
        }
    }

    public var body: some View {
        let fraction = Self.fraction(value: value, maximum: maximum)
        ZStack {
            Circle().stroke(VivaStyle.ringTrack, lineWidth: size.stroke)
            Circle()
                .trim(from: 0, to: fraction)
                .stroke(color, style: StrokeStyle(lineWidth: size.stroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
            center
        }
        .padding(size.stroke / 2)
        .frame(width: size.diameter, height: size.diameter)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label ?? ""))
        .accessibilityValue(Text(Self.text(value: value, format: format) + (format == .percent ? "%" : "")))
    }

    @ViewBuilder private var center: some View {
        switch size {
        case .mini:
            EmptyView()
        case .small:
            number(font: VivaFont.ringSmall, unitFont: VivaFont.number(17))
        case .large:
            VStack(spacing: 6) {
                if let wordmark {
                    Text(wordmark)
                        .font(VivaFont.wordmark)
                        .tracking(VivaFont.wordmarkTracking)
                        .foregroundStyle(VivaStyle.textSecondary)
                }
                number(font: VivaFont.ringLarge, unitFont: VivaFont.number(36))
                if let label {
                    Text(label)
                        .font(.system(size: 13, weight: .bold))
                        .tracking(1.6)
                        .textCase(.uppercase)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(VivaStyle.textPrimary)
                        .frame(maxWidth: 150)
                }
            }
        }
    }

    private func number(font: Font, unitFont: Font) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 1) {
            Text(Self.text(value: value, format: format)).font(font)
            if format == .percent, value != nil {
                Text("%").font(unitFont)
            }
        }
        .foregroundStyle(VivaStyle.textPrimary)
        .lineLimit(1)
        .minimumScaleFactor(0.6)
    }
}

#Preview {
    VStack(spacing: 24) {
        HStack(spacing: 24) {
            VivaScoreRing(value: 85, maximum: 100, color: VivaStyle.sleep, format: .percent, size: .small)
            VivaScoreRing(value: nil, maximum: 100, color: VivaStyle.recoveryCalibrating, format: .percent, size: .small)
            VivaScoreRing(value: 7.5, maximum: 21, color: VivaStyle.strain, format: .decimal1, size: .small)
        }
        VivaScoreRing(value: 85, maximum: 100, color: VivaStyle.sleep, format: .percent, size: .large, label: "Sleep Performance")
    }
    .padding()
    .background(VivaCanvas())
}
