#if os(iOS)
import SwiftUI
import StrandDesign

/// The floating bottom chrome: a pill holding the tab destinations and, beside it, the round
/// coach button. Tags are the shell's own tab tags, so the pill never renumbers a tab.
struct VivaTabBar: View {
    struct Item: Identifiable {
        let tag: Int
        let title: LocalizedStringKey
        let icon: String
        var id: Int { tag }
    }

    static let items: [Item] = [
        Item(tag: 0, title: "Home", icon: "house"),
        Item(tag: 1, title: "Health", icon: "bolt.heart"),
        Item(tag: 4, title: "More", icon: "line.3.horizontal"),
    ]

    /// Height of the pill; screens reserve this plus the bottom margin under their scroll content.
    static let height: CGFloat = 62

    @Binding var selection: Int
    let showsCoach: Bool
    let onCoach: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 0) {
                ForEach(Self.items) { item in
                    Button {
                        selection = item.tag
                    } label: {
                        VStack(spacing: 5) {
                            Image(systemName: item.icon)
                                .font(.system(size: 20, weight: .regular))
                                .frame(height: 24)
                            Text(item.title).font(.system(size: 10, weight: .medium))
                        }
                        .foregroundStyle(selection == item.tag ? VivaStyle.textPrimary : VivaStyle.textTertiary)
                        .frame(maxWidth: .infinity)
                        .frame(height: Self.height)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selection == item.tag ? .isSelected : [])
                }
            }
            .padding(.horizontal, 10)
            .background(VivaStyle.tabBar, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .strokeBorder(VivaStyle.divider, lineWidth: 0.5)
            )

            if showsCoach {
                VivaCoachButton(action: onCoach)
            }
        }
        .padding(.horizontal, 12)
        .shadow(color: .black.opacity(0.35), radius: 12, y: 4)
    }
}

/// The round coach entry point that sits beside the tab pill and floats alone on info screens.
struct VivaCoachButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 24, style: .continuous).fill(VivaStyle.coachButton)
                Circle()
                    .strokeBorder(
                        LinearGradient(colors: [VivaStyle.coachRing, VivaStyle.link],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        lineWidth: 1.5
                    )
                    .frame(width: 32, height: 32)
                BrandMark(size: 17)
            }
            .frame(width: VivaTabBar.height, height: VivaTabBar.height)
        }
        .buttonStyle(VivaPressStyle())
        .accessibilityLabel(Text("Coach"))
    }
}
#endif
