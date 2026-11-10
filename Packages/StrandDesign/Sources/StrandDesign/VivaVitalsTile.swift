import SwiftUI

/// A half-width vital sign: icon and uppercase label, a large value with its unit, and a status chip.
public struct VivaVitalsTile: View {
    private let icon: String
    private let label: String
    private let value: String?
    private let unit: String?
    private let status: String
    private let statusColor: Color
    private let action: () -> Void

    public init(icon: String, label: String, value: String?, unit: String? = nil,
                status: String, statusColor: Color = VivaStyle.textPrimary, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.value = value
        self.unit = unit
        self.status = status
        self.statusColor = statusColor
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: icon)
                        .font(.system(size: 15))
                        .foregroundStyle(VivaStyle.textSecondary)
                        .frame(width: 22)
                    Text(label)
                        .font(.system(size: 10, weight: .bold)).tracking(1.2).textCase(.uppercase)
                        .foregroundStyle(VivaStyle.textSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(minHeight: 30, alignment: .top)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(value ?? "--").font(VivaFont.number(34)).foregroundStyle(VivaStyle.textPrimary)
                    if let unit, value != nil {
                        Text(unit).font(.system(size: 14, weight: .medium)).foregroundStyle(VivaStyle.textPrimary)
                    }
                }
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                HStack(spacing: 6) {
                    Circle().fill(statusColor).frame(width: 5, height: 5)
                    Text(status).font(VivaFont.number(11, weight: .medium)).foregroundStyle(VivaStyle.textSecondary)
                }
                .padding(.horizontal, 6)
                .frame(height: 20)
                .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 3, style: .continuous))
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: VivaStyle.cardRadius, style: .continuous))
        }
        .buttonStyle(VivaPressStyle())
        .accessibilityElement(children: .combine)
    }
}
