import SwiftUI

/// The flat card every Viva screen is built from: one fill, no rim, an optional uppercase title
/// and a trailing glyph that says what a tap does.
public struct VivaCard<Content: View>: View {
    public enum Accessory: Sendable {
        case none, chevron, info, expand
        var symbol: String? {
            switch self {
            case .none: nil
            case .chevron: "chevron.right"
            case .info: "info.circle"
            case .expand: "arrow.up.left.and.arrow.down.right"
            }
        }
    }

    private let title: String?
    private let accessory: Accessory
    private let fill: Color
    private let padding: CGFloat
    private let action: (() -> Void)?
    private let content: Content

    public init(title: String? = nil, accessory: Accessory = .none, fill: Color = VivaStyle.card,
                padding: CGFloat = VivaStyle.cardPadding, action: (() -> Void)? = nil,
                @ViewBuilder content: () -> Content) {
        self.title = title
        self.accessory = accessory
        self.fill = fill
        self.padding = padding
        self.action = action
        self.content = content()
    }

    public var body: some View {
        if let action {
            Button(action: action) { card }.buttonStyle(VivaPressStyle())
        } else {
            card
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 14) {
            if title != nil || accessory.symbol != nil {
                HStack(alignment: .center) {
                    if let title { Text(title).vivaCardTitle() }
                    Spacer(minLength: 8)
                    if let symbol = accessory.symbol {
                        Image(systemName: symbol)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(VivaStyle.textSecondary)
                    }
                }
            }
            content
        }
        .padding(padding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(fill, in: RoundedRectangle(cornerRadius: VivaStyle.cardRadius, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: VivaStyle.cardRadius, style: .continuous))
    }
}

/// Press feedback for tappable cards and rows: a slight dim, no scale.
public struct VivaPressStyle: ButtonStyle {
    public init() {}
    public func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Large mixed-case page section title with an optional trailing action.
public struct VivaSectionHeader: View {
    public struct Action {
        public enum Style: Sendable { case chip, plus, link }
        let title: String
        let style: Style
        let handler: () -> Void
        public init(title: String, style: Style, handler: @escaping () -> Void) {
            self.title = title
            self.style = style
            self.handler = handler
        }
    }

    private let title: String
    private let subtitle: String?
    private let action: Action?

    public init(title: String, subtitle: String? = nil, action: Action? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.action = action
    }

    public var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                if let subtitle {
                    Text(subtitle).font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                }
            }
            Spacer(minLength: 8)
            if let action {
                Button(action: action.handler) { actionLabel(action) }
                    .buttonStyle(VivaPressStyle())
                    .accessibilityLabel(Text(action.title))
            }
        }
        .padding(.horizontal, 4)
    }

    @ViewBuilder private func actionLabel(_ action: Action) -> some View {
        switch action.style {
        case .plus:
            Image(systemName: "plus")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.black)
                .frame(width: 36, height: 36)
                .background(VivaStyle.textPrimary, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        case .chip:
            HStack(spacing: 8) {
                Text(action.title).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                Image(systemName: "pencil").font(.system(size: 12, weight: .bold))
            }
            .foregroundStyle(VivaStyle.textPrimary)
            .padding(.horizontal, 9)
            .frame(height: 24)
            .background(
                LinearGradient(colors: [VivaStyle.chipGradientStart, VivaStyle.chipGradientEnd],
                               startPoint: .leading, endPoint: .trailing),
                in: RoundedRectangle(cornerRadius: 4, style: .continuous)
            )
        case .link:
            HStack(spacing: 6) {
                Text(action.title).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
                Image(systemName: "arrow.right").font(.system(size: 11, weight: .bold))
            }
            .foregroundStyle(VivaStyle.textPrimary)
        }
    }
}

/// Uppercase text link with a trailing arrow (`OPEN SLEEP DETAILS →`).
public struct VivaLinkLabel: View {
    private let title: String
    private let color: Color
    public init(_ title: String, color: Color = VivaStyle.link) {
        self.title = title
        self.color = color
    }
    public var body: some View {
        HStack(spacing: 8) {
            Text(title).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
            Image(systemName: "arrow.right").font(.system(size: 12, weight: .semibold))
        }
        .foregroundStyle(color)
    }
}

/// Full-width quiet button inside a card (`EDIT ALARM`, `ADD ACTIVITY`).
public struct VivaCardButton: View {
    private let title: String
    private let icon: String?
    private let action: () -> Void
    public init(_ title: String, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.action = action
    }
    public var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { Image(systemName: icon).font(.system(size: 13, weight: .semibold)) }
                Text(title).font(.system(size: 11, weight: .bold)).tracking(1.4).textCase(.uppercase)
            }
            .foregroundStyle(VivaStyle.textPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .background(VivaStyle.chip, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        }
        .buttonStyle(VivaPressStyle())
    }
}
