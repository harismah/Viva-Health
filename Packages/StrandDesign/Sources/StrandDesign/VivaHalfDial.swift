import SwiftUI

/// The stress dial: an open arc with a short needle tick at the current value, the number in the
/// middle, and the scale ends printed under the arc's feet.
public struct VivaHalfDial: View {
    private let value: Double?
    private let maximum: Double
    private let caption: String
    private let time: String?

    private static let startAngle = 135.0   // lower-left foot, degrees clockwise from 3 o'clock
    private static let sweep = 270.0

    public init(value: Double?, maximum: Double, caption: String, time: String? = nil) {
        self.value = value
        self.maximum = maximum
        self.caption = caption
        self.time = time
    }

    public static func fraction(value: Double?, maximum: Double) -> Double {
        guard let value, maximum > 0, value.isFinite else { return 0 }
        return min(max(value / maximum, 0), 1)
    }

    public var body: some View {
        let fraction = Self.fraction(value: value, maximum: maximum)
        VStack(spacing: 2) {
            ZStack {
                Circle()
                    .trim(from: 0, to: Self.sweep / 360)
                    .stroke(
                        AngularGradient(
                            colors: [VivaStyle.ringTrack, VivaStyle.textSecondary.opacity(0.6), VivaStyle.textPrimary],
                            center: .center, startAngle: .degrees(0), endAngle: .degrees(Self.sweep)
                        ),
                        style: StrokeStyle(lineWidth: 6, lineCap: .round)
                    )
                    .rotationEffect(.degrees(Self.startAngle))
                if value != nil {
                    Capsule()
                        .fill(LinearGradient(colors: [VivaStyle.textPrimary, VivaStyle.textPrimary.opacity(0)],
                                             startPoint: .trailing, endPoint: .leading))
                        .frame(width: 44, height: 6)
                        .offset(x: 105)
                        .rotationEffect(.degrees(Self.startAngle + Self.sweep * fraction))
                }
                VStack(spacing: 6) {
                    Text(value.map { String(format: "%.1f", $0) } ?? "--")
                        .font(VivaFont.number(52))
                        .foregroundStyle(VivaStyle.textPrimary)
                    Text(caption)
                        .font(.system(size: 13, weight: .bold)).tracking(1.6).textCase(.uppercase)
                        .foregroundStyle(VivaStyle.textPrimary)
                    if let time {
                        Text(time).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                    }
                }
            }
            .frame(width: 250, height: 250)
            HStack {
                Text("0.0")
                Spacer()
                Text(String(format: "%.1f", maximum))
            }
            .font(VivaFont.number(11, weight: .medium))
            .foregroundStyle(VivaStyle.textTertiary)
            .frame(width: 210)
            .padding(.top, -34)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(caption))
        .accessibilityValue(Text(value.map { String(format: "%.1f", $0) } ?? ""))
    }
}
