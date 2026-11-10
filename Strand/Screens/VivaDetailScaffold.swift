#if os(iOS)
import SwiftUI
import StrandDesign

/// Page chrome for a pushed Viva screen: the canvas, a custom top bar (back chevron, centred
/// uppercase title, optional info or settings button) and a scrolling column of cards. The system
/// navigation bar is hidden so the bar matches on every screen.
struct VivaDetailScaffold<Content: View>: View {
    enum Trailing { case none, info, gear }
    enum Leading { case back, close }

    let title: LocalizedStringKey
    var leading: Leading = .back
    var trailing: Trailing = .none
    var onTrailing: (() -> Void)? = nil
    /// One-line coach message pinned above the home indicator; nil hides the bar.
    var coachMessage: String? = nil
    var onCoach: (() -> Void)? = nil
    var spacing: CGFloat = VivaStyle.cardGap
    @ViewBuilder var content: () -> Content

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: spacing) {
                    content()
                }
                .padding(.horizontal, VivaStyle.pagePadding)
                .padding(.top, 8)
                .padding(.bottom, coachMessage == nil ? 32 : 96)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .vivaDemoScroll(proxy)
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        .safeAreaInset(edge: .top, spacing: 0) { topBar }
        .overlay(alignment: .bottom) {
            if let coachMessage, let onCoach {
                VivaCoachBar(message: coachMessage, onTap: onCoach)
                    .padding(.horizontal, 28)
                    .padding(.bottom, 6)
            }
        }
        .background(VivaCanvas())
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
    }

    private var topBar: some View {
        ZStack {
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .tracking(1.6)
                .textCase(.uppercase)
                .foregroundStyle(VivaStyle.textPrimary)
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: leading == .back ? "chevron.left" : "xmark")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(VivaStyle.textPrimary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(leading == .back ? "Back" : "Close"))
                Spacer()
                if trailing != .none, let onTrailing {
                    Button(action: onTrailing) {
                        Image(systemName: trailing == .info ? "info.circle" : "gearshape")
                            .font(.system(size: 22, weight: .regular))
                            .foregroundStyle(VivaStyle.textSecondary)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(trailing == .info ? "About this screen" : "Settings"))
                }
            }
            .padding(.horizontal, 14)
        }
        .frame(height: 52)
        .background(VivaStyle.canvasTop.opacity(0.001))
    }
}

/// A bottom sheet of plain explanation: a heading and paragraphs on the canvas.
struct VivaInfoSheet: View {
    let title: String
    let paragraphs: [String]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(title).font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                ForEach(paragraphs, id: \.self) { paragraph in
                    Text(paragraph)
                        .font(VivaFont.body)
                        .foregroundStyle(VivaStyle.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(VivaStyle.canvasBottom.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
/// DEBUG screenshot aid: `--demo-anchor <id>` scrolls a Viva screen to the view carrying that id
/// shortly after it appears, so a seeded simulator build can be captured below the fold.
enum VivaDemo {
    static var anchor: String? {
        #if DEBUG
        let args = CommandLine.arguments
        guard let index = args.firstIndex(of: "--demo-anchor"), index + 1 < args.count else { return nil }
        return args[index + 1]
        #else
        return nil
        #endif
    }
}

extension View {
    func vivaDemoScroll(_ proxy: ScrollViewProxy) -> some View {
        task {
            guard let anchor = VivaDemo.anchor else { return }
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            proxy.scrollTo(anchor, anchor: .top)
        }
    }
}
#endif
