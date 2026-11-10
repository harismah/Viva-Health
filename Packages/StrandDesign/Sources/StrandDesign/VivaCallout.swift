import SwiftUI

/// The purple note shown while a score is still calibrating, with a segmented strip for how many
/// nights are in.
public struct VivaCallout: View {
    private let text: String
    private let filled: Int
    private let total: Int
    private let fill: Color

    public init(text: String, filled: Int = 0, total: Int = 0, fill: Color = VivaStyle.callout) {
        self.text = text
        self.filled = filled
        self.total = total
        self.fill = fill
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(text)
                .font(VivaFont.caption)
                .foregroundStyle(VivaStyle.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            if total > 0 {
                HStack(spacing: 4) {
                    ForEach(0..<total, id: \.self) { index in
                        Capsule()
                            .fill(index < filled ? VivaStyle.magenta : VivaStyle.textTertiary.opacity(0.45))
                            .frame(width: 28, height: 3)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(fill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

/// Three-step quality strip (poor / sufficient / optimal) beside a contributor value.
public struct VivaLevelStrip: View {
    public enum Level: Int, Sendable { case poor = 0, sufficient = 1, optimal = 2 }
    private let level: Level?
    public init(level: Level?) { self.level = level }

    public static func color(_ level: Level) -> Color {
        switch level {
        case .poor: VivaStyle.warning
        case .sufficient: VivaStyle.textSecondary
        case .optimal: VivaStyle.positive
        }
    }

    public var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { index in
                Capsule()
                    .fill(index == level?.rawValue ? Self.color(level!) : VivaStyle.ringTrack)
                    .frame(width: 20, height: 3)
            }
        }
    }
}
