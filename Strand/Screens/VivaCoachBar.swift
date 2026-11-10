#if os(iOS)
import SwiftUI
import StrandDesign

/// The coach line pinned to the bottom of a detail screen: a ringed plus, one line of the coach's
/// current note, and a mic glyph. Tapping anywhere opens the coach.
struct VivaCoachBar: View {
    let message: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .strokeBorder(
                            LinearGradient(colors: [VivaStyle.coachRing, VivaStyle.link],
                                           startPoint: .topLeading, endPoint: .bottomTrailing),
                            lineWidth: 1.5
                        )
                    Image(systemName: "plus").font(.system(size: 14, weight: .semibold))
                }
                .frame(width: 30, height: 30)
                Text(message).font(.system(size: 15)).lineLimit(1)
                Spacer(minLength: 4)
                Image(systemName: "mic").font(.system(size: 15))
            }
            .foregroundStyle(VivaStyle.textPrimary)
            .padding(.horizontal, 9)
            .frame(height: 48)
            .background(VivaStyle.coachButton, in: Capsule())
            .overlay(Capsule().strokeBorder(VivaStyle.coachRing.opacity(0.35), lineWidth: 0.8))
            .shadow(color: .black.opacity(0.4), radius: 10, y: 4)
        }
        .buttonStyle(VivaPressStyle())
        .accessibilityLabel(Text("Coach"))
        .accessibilityValue(Text(message))
    }
}
#endif
