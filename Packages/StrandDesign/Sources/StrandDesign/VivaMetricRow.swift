import SwiftUI

/// One dashboard metric: line icon, uppercase label, and a right-aligned value with its 30-day
/// baseline underneath and a small trend mark beside it.
public struct VivaMetricRow: View {
    public enum Trend: Sendable { case up, down, flat, none }

    private let icon: String
    private let label: String
    private let value: String
    private let unit: String?
    private let baseline: String?
    private let trend: Trend
    private let showsChevron: Bool

    public init(icon: String, label: String, value: String, unit: String? = nil,
                baseline: String? = nil, trend: Trend = .flat, showsChevron: Bool = false) {
        self.icon = icon
        self.label = label
        self.value = value
        self.unit = unit
        self.baseline = baseline
        self.trend = trend
        self.showsChevron = showsChevron
    }

    public var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(VivaStyle.textSecondary)
                .frame(width: 22)
            Text(label).vivaCardTitle().lineLimit(1).minimumScaleFactor(0.75)
            Spacer(minLength: 8)
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(VivaStyle.textPrimary)
            } else {
                VStack(alignment: .trailing, spacing: 1) {
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text(value).font(VivaFont.valueMedium).foregroundStyle(VivaStyle.textPrimary)
                        if let unit { Text(unit).font(VivaFont.unit).foregroundStyle(VivaStyle.textPrimary) }
                    }
                    if let baseline {
                        Text(baseline).font(VivaFont.baseline).foregroundStyle(VivaStyle.textTertiary)
                    }
                }
                trendMark.frame(width: 10)
            }
        }
        .frame(minHeight: 28)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var trendMark: some View {
        switch trend {
        case .up:
            Image(systemName: "arrowtriangle.up.fill").font(.system(size: 7)).foregroundStyle(VivaStyle.positive)
        case .down:
            Image(systemName: "arrowtriangle.down.fill").font(.system(size: 7)).foregroundStyle(VivaStyle.warning)
        case .flat:
            Circle().fill(VivaStyle.textTertiary).frame(width: 5, height: 5)
        case .none:
            Color.clear
        }
    }
}

/// Hairline between rows that share one card.
public struct VivaDivider: View {
    public init() {}
    public var body: some View {
        Rectangle().fill(VivaStyle.divider).frame(height: 1)
    }
}
