#if os(iOS)
import SwiftUI
import StrandDesign
import WhoopStore
import StrandAnalytics

/// The daily journal: pick a day, answer each behaviour yes or no (or log its amount), done.
/// Answers save through the same Repository calls the journal card on the Insights screen uses, so
/// both write one history.
struct VivaJournalView: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @Environment(\.dismiss) private var dismiss
    @StateObject private var catalog = JournalCatalogStore()

    @State private var dayOffset = 0
    @State private var answers: [String: Bool] = [:]
    @State private var numeric: [String: Double] = [:]
    @State private var imported: [String] = []
    @State private var showManage = false

    private func date(_ offset: Int) -> Date {
        Calendar.current.date(byAdding: .day, value: -offset, to: Date()) ?? Date()
    }
    private var dayKey: String { Repository.localDayKey(date(dayOffset)) }

    private var items: [JournalCatalogItem] { catalog.resolvedItems(imported: imported) }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: VivaStyle.cardGap) {
                    dayStrip
                    Text(dayOffset == 0
                         ? String(localized: "What's happening today, \(date(0).formatted(.dateTime.month(.wide).day().locale(AppLanguage.activeLocale)))?")
                         : String(localized: "What happened on \(date(dayOffset).formatted(.dateTime.month(.wide).day().locale(AppLanguage.activeLocale)))?"))
                        .font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.vertical, 8)
                    Text("Behaviors").vivaLabel().padding(.top, 6)
                    if items.isEmpty {
                        VivaCallout(text: String(localized: "No behaviours to log yet. Add some with the pencil above."),
                                    fill: VivaStyle.card)
                    }
                    ForEach(items) { item in row(item) }
                }
                .padding(.horizontal, VivaStyle.pagePadding)
                .padding(.bottom, 24)
            }
            Button { dismiss() } label: {
                Text("Done").font(.system(size: 12, weight: .bold)).tracking(1.4).textCase(.uppercase)
                    .foregroundStyle(Color.black)
                    .frame(maxWidth: .infinity).frame(height: 46)
                    .background(VivaStyle.textPrimary, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .buttonStyle(VivaPressStyle())
            .padding(.horizontal, VivaStyle.pagePadding)
            .padding(.vertical, 10)
        }
        .background(VivaCanvas())
        .task(id: "\(dayKey)-\(repo.refreshSeq)") { await load() }
        .onAppear {
            if let pending = router.pendingJournalDayOffset {
                dayOffset = max(pending, 0)
                router.pendingJournalDayOffset = nil
            }
        }
        .sheet(isPresented: $showManage) {
            NavigationStack {
                InsightsView()
                    .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Done") { showManage = false } } }
            }
        }
    }

    private var header: some View {
        ZStack {
            VStack(spacing: 8) {
                Text("Journal").font(.system(size: 13, weight: .bold)).tracking(1.6).textCase(.uppercase)
                    .foregroundStyle(VivaStyle.textPrimary)
            }
            HStack {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(VivaStyle.textPrimary).frame(width: 44, height: 44)
                }
                .buttonStyle(.plain).accessibilityLabel(Text("Close"))
                Spacer()
                Button { showManage = true } label: {
                    Image(systemName: "pencil").font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(VivaStyle.textPrimary).frame(width: 44, height: 44)
                }
                .buttonStyle(.plain).accessibilityLabel(Text("Edit behaviours"))
            }
            .padding(.horizontal, 8)
        }
        .frame(height: 52)
    }

    private var dayStrip: some View {
        HStack(spacing: 4) {
            ForEach((0..<7).reversed(), id: \.self) { offset in
                let day = date(offset)
                Button { dayOffset = offset } label: {
                    VStack(spacing: 6) {
                        Text(day.formatted(.dateTime.weekday(.abbreviated).locale(AppLanguage.activeLocale)))
                            .font(.system(size: 10, weight: .medium))
                        Text(day.formatted(.dateTime.day().locale(AppLanguage.activeLocale)))
                            .font(VivaFont.number(13))
                    }
                    .foregroundStyle(offset == dayOffset ? VivaStyle.textPrimary : VivaStyle.textSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 62)
                    .background(VivaStyle.chip.opacity(offset == dayOffset ? 1 : 0.5), in: Capsule())
                    .overlay(Capsule().strokeBorder(VivaStyle.textPrimary, lineWidth: offset == dayOffset ? 1.5 : 0))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(offset == dayOffset ? .isSelected : [])
            }
        }
    }

    private func row(_ item: JournalCatalogItem) -> some View {
        HStack(spacing: 8) {
            Text(verbatim: item.displayName ?? item.canonical)
                .font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 8)
            if item.kind.isNumeric {
                let current = numeric[item.canonical]
                stepButton("minus") { saveNumeric(item.canonical, max((current ?? 0) - 1, 0)) }
                Text(current.map { $0 == $0.rounded() ? String(Int($0)) : String(format: "%.1f", $0) } ?? "--")
                    .font(VivaFont.number(15)).foregroundStyle(VivaStyle.textPrimary).frame(minWidth: 30)
                stepButton("plus") { saveNumeric(item.canonical, (current ?? 0) + 1) }
            } else {
                answerButton("xmark", item.canonical, value: false)
                answerButton("checkmark", item.canonical, value: true)
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 54)
        .background(VivaStyle.card, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func answerButton(_ symbol: String, _ question: String, value: Bool) -> some View {
        let selected = answers[question] == value
        return Button {
            Task {
                if selected { await repo.clearJournalAnswer(day: dayKey, question: question) }
                else { await repo.saveJournalAnswer(day: dayKey, question: question, answeredYes: value) }
                await load()
            }
        } label: {
            Image(systemName: symbol).font(.system(size: 12, weight: .bold))
                .foregroundStyle(selected ? Color.black : VivaStyle.textPrimary)
                .frame(width: 30, height: 30)
                .background(selected ? (value ? VivaStyle.positive : VivaStyle.textPrimary) : VivaStyle.inset,
                            in: RoundedRectangle(cornerRadius: 5, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(value ? "Yes" : "No"))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func stepButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 12, weight: .bold)).foregroundStyle(VivaStyle.textPrimary)
                .frame(width: 30, height: 30)
                .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func saveNumeric(_ question: String, _ value: Double) {
        Task {
            await repo.saveJournalNumeric(day: dayKey, question: question, value: value)
            await load()
        }
    }

    private func load() async {
        let key = dayKey
        async let dayAnswers = repo.nativeJournalAnswers(day: key)
        async let dayNumeric = repo.nativeJournalNumeric(day: key)
        let entries = await repo.journalEntries()
        let (loadedAnswers, loadedNumeric) = await (dayAnswers, dayNumeric)
        guard !Task.isCancelled, key == dayKey else { return }
        answers = loadedAnswers
        numeric = loadedNumeric
        imported = Array(Set(entries.map(\.question))).sorted()
    }
}

/// Which logged behaviours go with a higher or lower Recovery. All of the maths is the existing
/// `BehaviorInsights.rank`; this screen only lays its results out.
struct VivaBehaviorInsightsView: View {
    @EnvironmentObject private var repo: Repository
    @EnvironmentObject private var router: NavRouter
    @State private var effects: [BehaviorEffect] = []
    @State private var counts: [(question: String, yes: Int, no: Int)] = []
    @State private var loaded = false

    var body: some View {
        VivaDetailScaffold(title: "Behavior Insights") {
            Text("Log your behaviors to reveal personalized insights")
                .font(VivaFont.sectionTitle).foregroundStyle(VivaStyle.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Text("Answer yes and no in your journal on enough days and Viva compares your Recovery on the days you did with the days you did not.")
                .font(VivaFont.caption).foregroundStyle(VivaStyle.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            if loaded && counts.isEmpty {
                VivaCallout(text: String(localized: "Nothing logged yet. Open your journal to start."))
            }
            ForEach(counts, id: \.question) { entry in
                VivaCard(padding: 12) {
                    HStack {
                        Text(verbatim: entry.question).font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
                        Spacer()
                        tally("xmark", entry.no)
                        tally("checkmark", entry.yes)
                    }
                }
            }
            VivaCardButton(String(localized: "Open journal"), icon: "square.and.pencil") { router.openJournal() }
            if !effects.isEmpty {
                VivaCard(title: String(localized: "Impact on Recovery")) {
                    HStack {
                        Label("Hurts", systemImage: "arrowtriangle.down.fill").labelStyle(.titleAndIcon)
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(VivaStyle.warning)
                        Spacer()
                        Text("% impact").vivaLabel()
                        Spacer()
                        Label("Helps", systemImage: "arrowtriangle.up.fill").labelStyle(.titleAndIcon)
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(VivaStyle.positive)
                    }
                    let scale = max(effects.compactMap { $0.pctChange.map(abs) }.max() ?? 1, 1)
                    ForEach(effects, id: \.behavior) { effect in
                        impactRow(effect, scale: scale)
                    }
                }
            } else if loaded && !counts.isEmpty {
                VivaCallout(text: String(localized: "Record at least five yes days and five no days for a behaviour to see how it moves your Recovery."))
            }
        }
        .task(id: repo.refreshSeq) { await load() }
    }

    private func tally(_ symbol: String, _ count: Int) -> some View {
        HStack(spacing: 4) {
            Image(systemName: symbol).font(.system(size: 9, weight: .bold))
                .frame(width: 16, height: 16)
                .overlay(RoundedRectangle(cornerRadius: 3).strokeBorder(VivaStyle.textSecondary, lineWidth: 1))
            Text("\(count)").font(VivaFont.number(12, weight: .medium))
        }
        .foregroundStyle(VivaStyle.textSecondary)
    }

    private func impactRow(_ effect: BehaviorEffect, scale: Double) -> some View {
        let percent = effect.pctChange ?? 0
        let color = percent >= 0 ? VivaStyle.positive : VivaStyle.warning
        return VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: effect.behavior).font(VivaFont.bodyStrong).foregroundStyle(VivaStyle.textPrimary)
            HStack(spacing: 8) {
                GeometryReader { proxy in
                    let half = proxy.size.width / 2
                    let width = half * CGFloat(min(abs(percent) / scale, 1))
                    ZStack(alignment: .leading) {
                        DiagonalHatch(spacing: 6).stroke(VivaStyle.ringTrack, lineWidth: 2)
                            .clipShape(RoundedRectangle(cornerRadius: 2))
                        RoundedRectangle(cornerRadius: 2).fill(color)
                            .frame(width: max(width, 3))
                            .offset(x: percent >= 0 ? half : half - max(width, 3))
                        Circle().fill(VivaStyle.textPrimary).frame(width: 4, height: 4).offset(x: half - 2)
                    }
                }
                .frame(height: 8)
                Text(String(format: "%+.0f%%", percent)).font(VivaFont.number(12)).foregroundStyle(color)
                    .frame(width: 44, alignment: .trailing)
            }
        }
        .padding(12)
        .background(VivaStyle.inset, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private func load() async {
        let entries = await repo.journalEntries()
        guard !Task.isCancelled else { return }
        var yes: [String: Set<String>] = [:]
        var no: [String: Set<String>] = [:]
        for entry in entries {
            if entry.answeredYes { yes[entry.question, default: []].insert(entry.day) }
            else { no[entry.question, default: []].insert(entry.day) }
        }
        let recoveryByDay = Dictionary(repo.days.compactMap { day in day.recovery.map { (day.day, $0) } },
                                       uniquingKeysWith: { _, last in last })
        effects = BehaviorInsights.rank(behaviors: yes, controls: no, outcomeByDay: recoveryByDay,
                                        outcome: "recovery")
            .filter { $0.pctChange != nil }
        counts = Set(yes.keys).union(no.keys).sorted().map { ($0, yes[$0]?.count ?? 0, no[$0]?.count ?? 0) }
        loaded = true
    }
}
#endif
