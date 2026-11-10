import SwiftUI

/// A seven-day chart card: one column per day, today's column lifted on a lighter band with its
/// value printed above the mark. Bars for totals, dots for vitals, stacks for split totals.
public struct VivaWeekBarCard: View {
    public enum Style: Sendable { case bar, dot, stacked }

    public struct Day: Identifiable {
        public let id = UUID()
        public let label: String
        public let date: Int
        public let segments: [(value: Double, color: Color)]
        public let valueText: String?
        public let isToday: Bool
        public init(label: String, date: Int, segments: [(value: Double, color: Color)],
                    valueText: String?, isToday: Bool) {
            self.label = label
            self.date = date
            self.segments = segments
            self.valueText = valueText
            self.isToday = isToday
        }
        var total: Double { segments.reduce(0) { $0 + max($1.value, 0) } }
    }

    private let title: String
    private let days: [Day]
    private let style: Style
    private let legend: [(String, Color)]
    private let action: (() -> Void)?
    private let chartHeight: CGFloat = 150

    public init(title: String, days: [Day], style: Style = .bar,
                legend: [(String, Color)] = [], action: (() -> Void)? = nil) {
        self.title = title
        self.days = days
        self.style = style
        self.legend = legend
        self.action = action
    }

    /// Tallest day total, the top of the chart's scale.
    public static func peak(_ days: [Day]) -> Double { days.map(\.total).max() ?? 0 }

    /// True when no day carries a value, so the card shows its no-data state instead of a flat axis.
    public static func isEmpty(_ days: [Day]) -> Bool { days.allSatisfy { $0.segments.isEmpty } }

    public var body: some View {
        VivaCard(title: title, accessory: action == nil ? .none : .chevron, action: action) {
            VStack(alignment: .trailing, spacing: 8) {
                if !legend.isEmpty {
                    HStack(spacing: 12) {
                        ForEach(Array(legend.enumerated()), id: \.offset) { _, item in
                            HStack(spacing: 5) {
                                Rectangle().fill(item.1).frame(width: 7, height: 7)
                                Text(item.0).vivaLabel(VivaStyle.textPrimary)
                            }
                        }
                    }
                }
                if Self.isEmpty(days) {
                    VStack(spacing: 8) {
                        Image(systemName: "chart.bar.xaxis").font(.system(size: 18)).foregroundStyle(VivaStyle.textTertiary)
                        Text("No data").vivaLabel(VivaStyle.textPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: chartHeight + 30)
                } else {
                    chart
                }
            }
        }
    }

    private var chart: some View {
        let peak = max(Self.peak(days), .leastNonzeroMagnitude)
        return HStack(alignment: .bottom, spacing: 0) {
            ForEach(days) { day in
                VStack(spacing: 6) {
                    column(day, peak: peak)
                        .frame(height: chartHeight)
                    VStack(spacing: 1) {
                        Text(day.label).font(.system(size: 9, weight: day.isToday ? .bold : .regular))
                        Text("\(day.date)").font(.system(size: 9, weight: day.isToday ? .bold : .regular))
                    }
                    .foregroundStyle(day.isToday ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                }
                .frame(maxWidth: .infinity)
                .background(alignment: .top) {
                    if day.isToday {
                        Rectangle().fill(VivaStyle.chip.opacity(0.75)).frame(width: 22).padding(.bottom, 26)
                    }
                }
            }
        }
        .background(alignment: .top) { gridLines.frame(height: chartHeight) }
    }

    private var gridLines: some View {
        VStack(spacing: 0) {
            ForEach(0..<5, id: \.self) { index in
                Rectangle().fill(VivaStyle.divider.opacity(0.7)).frame(height: 0.5)
                if index < 4 { Spacer(minLength: 0) }
            }
        }
    }

    @ViewBuilder private func column(_ day: Day, peak: Double) -> some View {
        GeometryReader { proxy in
            let usable = proxy.size.height - 16
            let height = CGFloat(day.total / peak) * usable
            VStack(spacing: 3) {
                Spacer(minLength: 0)
                if let text = day.valueText, !day.segments.isEmpty {
                    Text(text)
                        .font(VivaFont.number(9, weight: .bold))
                        .foregroundStyle(day.segments.last?.color ?? VivaStyle.textPrimary)
                        .lineLimit(1)
                        .fixedSize()
                }
                switch style {
                case .dot:
                    if !day.segments.isEmpty {
                        Circle()
                            .stroke(day.segments[0].color, lineWidth: 1.5)
                            .frame(width: 6, height: 6)
                        Spacer().frame(height: max(height - 6, 0))
                    }
                case .bar, .stacked:
                    VStack(spacing: 0) {
                        ForEach(Array(day.segments.enumerated().reversed()), id: \.offset) { _, segment in
                            Rectangle()
                                .fill(segment.color)
                                .frame(height: max(CGFloat(max(segment.value, 0) / peak) * usable, segment.value > 0 ? 2 : 0))
                        }
                    }
                    .frame(width: 10)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }
}
