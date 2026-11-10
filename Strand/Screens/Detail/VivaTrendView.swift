#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore

/// One metric over a week, a month or six months: a picker to switch metric, the period average,
/// a stepper through earlier periods, the chart, and a plain explanation of what the metric is.
struct VivaTrendView: View {
    enum Period: String, CaseIterable, Identifiable {
        case week = "W", month = "M", sixMonths = "6M"
        var id: String { rawValue }
        var days: Int { switch self { case .week: 7; case .month: 30; case .sixMonths: 182 } }
    }

    @EnvironmentObject private var repo: Repository
    @State private var metric: VivaTrendMetric
    @State private var period: Period = .week
    @State private var page = 0
    @State private var pickerOpen = false

    init(metric: VivaTrendMetric) { _metric = State(initialValue: metric) }

    private var points: [VivaTrendMetric.Point] {
        metric.points(days: repo.days, count: period.days, endingDaysAgo: page * period.days)
    }

    /// Mean of the recorded days in the period; nil when fewer than two exist, so one day never
    /// reads as an average.
    private func average(_ points: [VivaTrendMetric.Point]) -> Double? {
        let values = points.compactMap(\.value)
        guard values.count >= 2 else { return nil }
        return values.reduce(0, +) / Double(values.count)
    }

    private var rangeText: String {
        guard let first = points.first?.date, let last = points.last?.date else { return "" }
        let style = Date.FormatStyle().month(.abbreviated).day().locale(AppLanguage.activeLocale)
        return "\(first.formatted(style)) - \(last.formatted(style.year(.twoDigits)))"
    }

    private var hasEarlierData: Bool {
        guard let earliest = repo.days.first?.day, let first = points.first?.id else { return false }
        return earliest < first
    }

    var body: some View {
        let points = points
        let average = average(points)
        VivaDetailScaffold(title: "Trend view") {
            picker
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Average").vivaLabel()
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(average.map { metric.text($0).replacingOccurrences(of: "%", with: "") } ?? "---")
                            .font(VivaFont.valueLarge).foregroundStyle(VivaStyle.textPrimary)
                        Text(metric.unit).font(VivaFont.unit).foregroundStyle(VivaStyle.textPrimary)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 10) {
                    periodControl
                    HStack(spacing: 10) {
                        stepButton("chevron.left", enabled: hasEarlierData) { page += 1 }
                            .accessibilityLabel(Text("Earlier period"))
                        Text(rangeText).vivaLabel(VivaStyle.textPrimary)
                        stepButton("chevron.right", enabled: page > 0) { page -= 1 }
                            .accessibilityLabel(Text("Later period"))
                    }
                }
            }
            .padding(.horizontal, 4)
            .padding(.top, 8)
            Text(metric.summary)
                .font(VivaFont.body).foregroundStyle(VivaStyle.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
                .padding(.vertical, 8)
            VivaTrendChart(metric: metric, points: points, average: period == .week ? nil : average)
                .padding(.horizontal, 4)
            if metric == .sleepPerformance || metric == .recovery {
                breakdown(points).padding(.top, 8)
            }
            VivaCard {
                Text(String(localized: "What is \(metric.title)?"))
                    .font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                Text(metric.summary).font(VivaFont.body).foregroundStyle(VivaStyle.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.top, 12)
        }
        .onChange(of: period) { _, _ in page = 0 }
        .onChange(of: metric) { _, _ in page = 0 }
    }

    private var picker: some View {
        VStack(spacing: 0) {
            Button { withAnimation(.easeInOut(duration: 0.2)) { pickerOpen.toggle() } } label: {
                HStack(spacing: 10) {
                    Image(systemName: metric.icon).font(.system(size: 14)).foregroundStyle(VivaStyle.textSecondary).frame(width: 22)
                    Text(metric.title).vivaCardTitle()
                    Spacer()
                    Image(systemName: pickerOpen ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .bold)).foregroundStyle(VivaStyle.textPrimary)
                }
                .padding(.horizontal, 14)
                .frame(height: 44)
                .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .buttonStyle(.plain)
            if pickerOpen {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(VivaTrendMetric.Group.allCases, id: \.self) { group in
                        Text(group.rawValue).vivaLabel(VivaStyle.textTertiary)
                            .padding(.horizontal, 14).padding(.top, 12).padding(.bottom, 4)
                        ForEach(VivaTrendMetric.allCases.filter { $0.group == group }) { option in
                            Button {
                                metric = option
                                withAnimation(.easeInOut(duration: 0.2)) { pickerOpen = false }
                            } label: {
                                HStack(spacing: 10) {
                                    Image(systemName: option.icon).font(.system(size: 14))
                                        .foregroundStyle(VivaStyle.textSecondary).frame(width: 22)
                                    Text(option.title).vivaCardTitle()
                                    Spacer()
                                }
                                .padding(.horizontal, 14)
                                .frame(height: 42)
                                .background(option == metric ? VivaStyle.card : Color.clear,
                                            in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.bottom, 8)
                .background(Color.black, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
        }
    }

    private var periodControl: some View {
        HStack(spacing: 0) {
            ForEach(Period.allCases) { option in
                Button { period = option } label: {
                    Text(option.rawValue)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(period == option ? VivaStyle.textPrimary : VivaStyle.textSecondary)
                        .frame(width: 42, height: 26)
                        .background(period == option ? VivaStyle.chipActive : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(period == option ? .isSelected : [])
            }
        }
        .padding(2)
        .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
    }

    private func stepButton(_ symbol: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 11, weight: .bold))
                .foregroundStyle(enabled ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                .frame(width: 24, height: 24).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func breakdown(_ points: [VivaTrendMetric.Point]) -> some View {
        let values = points.compactMap(\.value)
        let high = metric == .recovery ? 67.0 : 85.0
        let low = metric == .recovery ? 34.0 : 70.0
        let bands: [(String, Color, Int)] = [
            (metric == .recovery ? String(localized: "Green (67% and up)") : String(localized: "Optimal (85% and up)"),
             VivaStyle.positive, values.filter { $0 >= high }.count),
            (metric == .recovery ? String(localized: "Yellow (34 to 66%)") : String(localized: "Sufficient (70 to 85%)"),
             metric == .recovery ? VivaStyle.recoveryYellow : VivaStyle.textSecondary,
             values.filter { $0 >= low && $0 < high }.count),
            (metric == .recovery ? String(localized: "Red (under 34%)") : String(localized: "Poor (under 70%)"),
             metric == .recovery ? VivaStyle.recoveryRed : VivaStyle.warning, values.filter { $0 < low }.count),
        ]
        return VStack(alignment: .leading, spacing: 8) {
            Text(String(localized: "\(metric.title) breakdown (days)")).vivaLabel(VivaStyle.textPrimary)
            GeometryReader { proxy in
                HStack(spacing: 0) {
                    ForEach(Array(bands.enumerated()), id: \.offset) { _, band in
                        Rectangle().fill(band.1)
                            .frame(width: values.isEmpty ? 0 : proxy.size.width * CGFloat(band.2) / CGFloat(values.count))
                    }
                }
                .background(VivaStyle.ringTrack)
            }
            .frame(height: 6)
            ForEach(Array(bands.enumerated()), id: \.offset) { _, band in
                HStack(spacing: 8) {
                    Rectangle().fill(band.1).frame(width: 6, height: 6)
                    Text("\(band.2)x").font(VivaFont.number(12)).foregroundStyle(VivaStyle.textPrimary)
                    Text(band.0).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                }
            }
        }
        .padding(.horizontal, 4)
    }
}

/// The Trend View plot: bars or dots over a labelled grid, with a dashed average line on longer periods.
struct VivaTrendChart: View {
    let metric: VivaTrendMetric
    let points: [VivaTrendMetric.Point]
    let average: Double?
    private let height: CGFloat = 190

    private var bounds: ClosedRange<Double> {
        let values = points.compactMap(\.value)
        guard let high = values.max(), let low = values.min() else { return 0...1 }
        if metric.unit == "%" { return 0...100 }
        if metric.chartsFromZero { return 0...(high * 1.1) }
        let floor = VivaTrendMetric.dotFloor(values)
        return floor...(high + max((high - low) * 0.3, 1))
    }

    private func axisText(_ value: Double) -> String {
        switch metric {
        case .hoursOfSleep, .restorativeSleep: VivaTrendMetric.clock(minutes: value)
        case .respiratoryRate, .strain: String(format: "%.1f", value)
        default: metric.unit == "%" ? "\(Int(value.rounded()))%" : Int(value.rounded()).formatted()
        }
    }

    private var xLabels: [(index: Int, text: String)] {
        guard !points.isEmpty else { return [] }
        let locale = AppLanguage.activeLocale
        if points.count <= 7 {
            return points.enumerated().map { ($0.offset, $0.element.date.formatted(.dateTime.weekday(.abbreviated).locale(locale))
                + "\n" + $0.element.date.formatted(.dateTime.day().locale(locale))) }
        }
        let step = points.count <= 31 ? 7 : 30
        return stride(from: 0, to: points.count, by: step).map { index in
            let date = points[index].date
            return (index, points.count <= 31
                    ? date.formatted(.dateTime.month(.abbreviated).locale(locale)) + "\n" + date.formatted(.dateTime.day().locale(locale))
                    : date.formatted(.dateTime.month(.abbreviated).locale(locale)))
        }
    }

    var body: some View {
        let bounds = bounds
        let span = max(bounds.upperBound - bounds.lowerBound, .leastNonzeroMagnitude)
        HStack(alignment: .top, spacing: 6) {
            VStack(alignment: .trailing) {
                ForEach(0..<5, id: \.self) { index in
                    Text(axisText(bounds.upperBound - span * Double(index) / 4))
                        .font(VivaFont.number(8, weight: .medium)).foregroundStyle(VivaStyle.textTertiary)
                    if index < 4 { Spacer(minLength: 0) }
                }
            }
            .frame(width: 30, height: height + 6).offset(y: -3)
            VStack(spacing: 6) {
                GeometryReader { proxy in
                    let step = proxy.size.width / CGFloat(max(points.count, 1))
                    let y: (Double) -> CGFloat = { value in
                        proxy.size.height * (1 - CGFloat((min(max(value, bounds.lowerBound), bounds.upperBound) - bounds.lowerBound) / span))
                    }
                    ZStack(alignment: .topLeading) {
                        ForEach(0..<5, id: \.self) { index in
                            Rectangle().fill(VivaStyle.divider.opacity(0.7)).frame(height: 0.5)
                                .offset(y: proxy.size.height * CGFloat(index) / 4)
                        }
                        ForEach(Array(points.enumerated()), id: \.element.id) { index, point in
                            if let value = point.value {
                                let x = step * (CGFloat(index) + 0.5)
                                if metric.style == .dot {
                                    Circle().stroke(metric.markColor(value), lineWidth: 1.5).frame(width: 6, height: 6)
                                        .position(x: x, y: y(value))
                                } else {
                                    let barHeight = max(proxy.size.height - y(value), 2)
                                    Rectangle().fill(metric.markColor(value))
                                        .frame(width: min(max(step * 0.45, 1.5), 10), height: barHeight)
                                        .position(x: x, y: proxy.size.height - barHeight / 2)
                                }
                                if points.count <= 7 {
                                    Text(metric.text(value)).font(VivaFont.number(9)).foregroundStyle(metric.markColor(value))
                                        .fixedSize().position(x: x, y: max(y(value) - 10, 6))
                                }
                            }
                        }
                        if let average {
                            Path { path in
                                path.move(to: CGPoint(x: 0, y: y(average)))
                                path.addLine(to: CGPoint(x: proxy.size.width, y: y(average)))
                            }
                            .stroke(VivaStyle.textPrimary, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                            Text("AVG.").font(.system(size: 8, weight: .bold)).foregroundStyle(Color.black)
                                .padding(.horizontal, 4).padding(.vertical, 2)
                                .background(VivaStyle.textPrimary, in: RoundedRectangle(cornerRadius: 2))
                                .position(x: 14, y: y(average))
                        }
                        if points.allSatisfy({ $0.value == nil }) {
                            Text("No data").vivaLabel(VivaStyle.textPrimary)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        }
                    }
                }
                .frame(height: height)
                GeometryReader { proxy in
                    let step = proxy.size.width / CGFloat(max(points.count, 1))
                    ForEach(xLabels, id: \.index) { label in
                        Text(label.text).font(.system(size: 9)).multilineTextAlignment(.center)
                            .foregroundStyle(VivaStyle.textTertiary).fixedSize()
                            .position(x: step * (CGFloat(label.index) + 0.5), y: 12)
                    }
                }
                .frame(height: 26)
            }
        }
    }
}
#endif
