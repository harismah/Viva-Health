import SwiftUI

/// "Solid is you, hatch is the context": a solid fill for the value over a hatched track, with a
/// dashed bracket marking the typical range when one is known.
public struct VivaRangeBar: View {
    private let value: Double
    private let range: ClosedRange<Double>?
    private let maximum: Double
    private let color: Color
    private let height: CGFloat

    public init(value: Double, range: ClosedRange<Double>? = nil, maximum: Double,
                color: Color, height: CGFloat = 16) {
        self.value = value
        self.range = range
        self.maximum = maximum
        self.color = color
        self.height = height
    }

    /// Bar geometry in points. `fill` is the solid width; `hatchStart`/`hatchWidth` place the
    /// typical-range bracket. Everything is clamped inside `width`.
    public static func layout(value: Double, range: ClosedRange<Double>?, maximum: Double,
                              width: CGFloat) -> (fill: CGFloat, hatchStart: CGFloat, hatchWidth: CGFloat) {
        guard maximum > 0, width > 0 else { return (0, 0, 0) }
        func x(_ v: Double) -> CGFloat {
            guard v.isFinite else { return 0 }
            return CGFloat(min(max(v / maximum, 0), 1)) * width
        }
        guard let range else { return (x(value), 0, 0) }
        let start = x(range.lowerBound)
        return (x(value), start, x(range.upperBound) - start)
    }

    public var body: some View {
        GeometryReader { proxy in
            let layout = Self.layout(value: value, range: range, maximum: maximum, width: proxy.size.width)
            ZStack(alignment: .leading) {
                DiagonalHatch(spacing: 7)
                    .stroke(VivaStyle.ringTrack, lineWidth: 2.5)
                    .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))
                if layout.fill > 0 {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .fill(color)
                        .frame(width: max(layout.fill, 4))
                }
                if layout.hatchWidth > 0 {
                    RoundedRectangle(cornerRadius: 1)
                        .stroke(VivaStyle.textSecondary, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        .frame(width: max(layout.hatchWidth, 8), height: proxy.size.height + 8)
                        .offset(x: layout.hatchStart)
                }
            }
        }
        .frame(height: height)
    }
}

/// One sleep-stage line: ring swatch, uppercase name, coloured share, duration, and its bar.
public struct VivaStageRow: View {
    private let color: Color
    private let label: String
    private let percent: Int?
    private let duration: String
    private let value: Double
    private let range: ClosedRange<Double>?
    private let maximum: Double

    public init(color: Color, label: String, percent: Int?, duration: String,
                value: Double, range: ClosedRange<Double>? = nil, maximum: Double) {
        self.color = color
        self.label = label
        self.percent = percent
        self.duration = duration
        self.value = value
        self.range = range
        self.maximum = maximum
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Circle().stroke(VivaStyle.textPrimary, lineWidth: 1.5).frame(width: 24, height: 24)
                Text(label).vivaCardTitle()
                if let percent {
                    Text("\(percent)%").font(VivaFont.number(12, weight: .semibold)).foregroundStyle(color)
                }
                Spacer(minLength: 8)
                Text(duration).font(VivaFont.valueSmall).foregroundStyle(VivaStyle.textPrimary)
            }
            VivaRangeBar(value: value, range: range, maximum: maximum, color: color)
        }
        .accessibilityElement(children: .combine)
    }
}
