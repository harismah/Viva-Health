#if os(iOS)
import SwiftUI
import StrandDesign

/// A plain line over a labelled y-axis: the night heart-rate trace and the day stress trace.
struct VivaLineChart: View {
    let values: [Double]
    var color: Color = VivaStyle.sleep
    var range: ClosedRange<Double>? = nil
    var yLabels: [String] = []
    var startLabel: String? = nil
    var endLabel: String? = nil
    var height: CGFloat = 130
    var fill: Bool = false

    private var bounds: ClosedRange<Double> {
        if let range { return range }
        guard let low = values.min(), let high = values.max(), high > low else { return 0...1 }
        let pad = (high - low) * 0.1
        return (low - pad)...(high + pad)
    }

    private static func area(under line: Path, in size: CGSize) -> Path {
        var area = line
        area.addLine(to: CGPoint(x: size.width, y: size.height))
        area.addLine(to: CGPoint(x: 0, y: size.height))
        area.closeSubpath()
        return area
    }

    var body: some View {
        VStack(spacing: 6) {
            HStack(alignment: .top, spacing: 6) {
                if !yLabels.isEmpty {
                    VStack(alignment: .trailing) {
                        ForEach(Array(yLabels.enumerated()), id: \.offset) { index, label in
                            Text(label).font(VivaFont.number(9, weight: .medium)).foregroundStyle(VivaStyle.textTertiary)
                            if index < yLabels.count - 1 { Spacer(minLength: 0) }
                        }
                    }
                    .frame(height: height)
                }
                GeometryReader { proxy in
                    let bounds = bounds
                    let span = max(bounds.upperBound - bounds.lowerBound, .leastNonzeroMagnitude)
                    let line = Path { path in
                        guard values.count > 1 else { return }
                        for (index, value) in values.enumerated() {
                            let x = proxy.size.width * CGFloat(index) / CGFloat(values.count - 1)
                            let clamped = min(max(value, bounds.lowerBound), bounds.upperBound)
                            let y = proxy.size.height * (1 - CGFloat((clamped - bounds.lowerBound) / span))
                            if index == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                        }
                    }
                    ZStack {
                        VStack(spacing: 0) {
                            ForEach(0..<4, id: \.self) { index in
                                Rectangle().fill(VivaStyle.divider.opacity(0.6)).frame(height: 0.5)
                                if index < 3 { Spacer(minLength: 0) }
                            }
                        }
                        if fill, values.count > 1 {
                            Self.area(under: line, in: proxy.size)
                                .fill(LinearGradient(colors: [color.opacity(0.18), color.opacity(0)],
                                                     startPoint: .top, endPoint: .bottom))
                        }
                        line.stroke(color, style: StrokeStyle(lineWidth: 1.2, lineJoin: .round))
                        if values.count < 2 {
                            Text("No data").vivaLabel(VivaStyle.textPrimary)
                        }
                    }
                }
                .frame(height: height)
            }
            if startLabel != nil || endLabel != nil {
                HStack {
                    Text(startLabel ?? "")
                    Spacer()
                    Text(endLabel ?? "")
                }
                .font(VivaFont.number(10, weight: .semibold))
                .foregroundStyle(VivaStyle.textPrimary)
                .padding(.leading, yLabels.isEmpty ? 0 : 24)
            }
        }
    }
}

/// A big value with a small grey baseline under it and a trend mark beside it: the head of a detail card.
struct VivaValueHeader: View {
    let value: String
    var baseline: String? = nil
    var trend: VivaMetricRow.Trend = .flat

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .center, spacing: 8) {
                Text(value).font(VivaFont.valueLarge).foregroundStyle(VivaStyle.textPrimary)
                switch trend {
                case .up: Image(systemName: "arrowtriangle.up.fill").font(.system(size: 7)).foregroundStyle(VivaStyle.positive)
                case .down: Image(systemName: "arrowtriangle.down.fill").font(.system(size: 7)).foregroundStyle(VivaStyle.warning)
                case .flat: Circle().fill(VivaStyle.textTertiary).frame(width: 5, height: 5)
                case .none: EmptyView()
                }
            }
            if let baseline {
                Text(baseline).font(VivaFont.baseline).foregroundStyle(VivaStyle.textTertiary)
            }
        }
    }
}

/// A labelled horizontal amount bar (`HOURS OF SLEEP ▬▬▬ 6:57`).
struct VivaAmountBar: View {
    let label: String
    let value: String
    let fraction: Double
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label).vivaLabel()
                Spacer()
                Text(value).font(VivaFont.number(12)).foregroundStyle(VivaStyle.textPrimary)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3, style: .continuous).fill(VivaStyle.ringTrack)
                    RoundedRectangle(cornerRadius: 3, style: .continuous).fill(color)
                        .frame(width: proxy.size.width * CGFloat(min(max(fraction, 0), 1)))
                }
            }
            .frame(height: 9)
        }
    }
}

extension VivaMetricRow.Trend {
    /// Up or down when a value sits more than 5% off its baseline, flat inside that band.
    static func comparing(_ value: Double?, to baseline: Double?) -> VivaMetricRow.Trend {
        guard let value, let baseline, baseline != 0 else { return .flat }
        let change = (value - baseline) / abs(baseline)
        if change > 0.05 { return .up }
        if change < -0.05 { return .down }
        return .flat
    }
}
#endif
