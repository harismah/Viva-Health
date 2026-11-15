import SwiftUI

// MARK: - BrandMark — the Viva logo mark
//
// The app's identity glyph, drawn natively so it stays crisp from a 10pt inline mark to a 120pt
// hero. It is the app icon's mark: a "V" whose right arm breaks into a heartbeat step and ends in
// a bright dot, in the icon's mint-to-cyan gradient, on a dark disc. At larger sizes the icon's
// faint open ring sits behind it. Everything is driven off a single `size`.

public struct BrandMark: View {

    /// Edge length of the square mark; everything scales from this.
    public var size: CGFloat

    public init(size: CGFloat = 120) {
        self.size = size
    }

    private var rimWidth: CGFloat { max(1, size * 0.008) }  // ~1px hairline rim

    private static let mint = Color(hex: "#A6FBC9")
    private static let teal = Color(hex: "#3DE8CF")
    private static let cyan = Color(hex: "#35C8E6")

    public var body: some View {
        ZStack {
            navyTile
            if size >= 44 { ring }
            letter
            tipDot
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Viva"))
        .accessibilityAddTraits(.isImage)
    }

    // MARK: Deep-navy tile

    /// The navy disc the gold mark sits on — a deep-navy vertical ramp (lifted at
    /// the top, deeper at the bottom) with a faint cool top sheen and a soft
    /// hairline rim, matching the app icon. No shadow — flat and clean.
    private var navyTile: some View {
        Circle()
            .fill(
                LinearGradient(
                    colors: [Color(hex: "#1A1E24"), Color(hex: "#0E1116")],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            // Faint cool top sheen — a soft light catch across the upper third (flat, no bloom).
            .overlay(
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: "#2A2F37").opacity(0.5), .clear],
                            startPoint: .top,
                            endPoint: .center
                        )
                    )
                    .opacity(0.6)
            )
            // 1px hairline rim so the disc reads cleanly on the navy canvas.
            .overlay(
                Circle().strokeBorder(StrandPalette.hairline, lineWidth: rimWidth)
            )
    }

    // MARK: The V

    /// The stroke of the V in the mark's own unit square: down the left arm, up the right arm to a
    /// short heartbeat step, then on up to the tip.
    private struct VShape: Shape {
        func path(in rect: CGRect) -> Path {
            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: rect.minX + rect.width * x, y: rect.minY + rect.height * y)
            }
            var path = Path()
            path.move(to: point(0.262, 0.298))
            path.addLine(to: point(0.479, 0.728))
            path.addLine(to: point(0.608, 0.479))
            path.addLine(to: point(0.635, 0.537))
            path.addLine(to: point(0.754, 0.285))
            return path
        }
    }

    private var letter: some View {
        VShape()
            .stroke(
                LinearGradient(colors: [Self.mint, Self.teal, Self.cyan],
                               startPoint: .topLeading, endPoint: .bottomTrailing),
                style: StrokeStyle(lineWidth: size * 0.082, lineCap: .round, lineJoin: .round)
            )
            .frame(width: size, height: size)
    }

    /// The bright end of the right arm.
    private var tipDot: some View {
        Circle()
            .fill(Color.white)
            .frame(width: size * 0.062, height: size * 0.062)
            .position(x: size * 0.754, y: size * 0.285)
            .frame(width: size, height: size)
    }

    /// The icon's faint open ring, brighter on the left, open at the bottom.
    private var ring: some View {
        Circle()
            .trim(from: 0, to: 0.76)
            .stroke(
                AngularGradient(colors: [Self.teal.opacity(0.45), Self.teal.opacity(0.4), Color.white.opacity(0.1), Color.white.opacity(0.08)],
                                center: .center, startAngle: .degrees(0), endAngle: .degrees(274)),
                style: StrokeStyle(lineWidth: size * 0.024, lineCap: .round)
            )
            .rotationEffect(.degrees(133))
            .frame(width: size * 0.72, height: size * 0.72)
    }
}

#if DEBUG
#Preview("BrandMark — sizes") {
    VStack(spacing: 40) {
        BrandMark(size: 120)
        HStack(spacing: 28) {
            BrandMark(size: 72)
            BrandMark(size: 44)
            BrandMark(size: 28)
        }
    }
    .padding(48)
    .frame(width: 420, height: 460)
    .background(StrandPalette.surfaceBase)
    .preferredColorScheme(.dark)
}
#endif
