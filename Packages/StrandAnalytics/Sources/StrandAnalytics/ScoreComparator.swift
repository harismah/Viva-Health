import Foundation

// ScoreComparator.swift — agreement between a NOOP-computed daily series and a reference series.
//
// Pure, deterministic, DB-free. Used to validate on-device scores against the user's OWN WHOOP
// data export (recovery, HRV, RHR, strain, sleep): both sides are "yyyy-MM-dd"-keyed daily values,
// inner-joined on the day key. Reports
//
//   bias  = mean(noop − ref)                         (signed; + means NOOP reads high)
//   MAE   = mean|noop − ref|
//   RMSE  = sqrt(mean((noop − ref)²))
//   LoA   = bias ± 1.96·SD(noop − ref)               (Bland–Altman 95% limits, sample SD)
//   r     = Pearson r via CorrelationEngine.pearson  (nil below 3 pairs or zero variance)
//
// Non-finite values on either side are dropped before the join. Nothing here knows about units:
// the caller maps both series onto the same scale first (e.g. WHOOP Day Strain 0–21 ⇄ Effort 0–100).

/// One day present in both series.
public struct ScorePair: Equatable, Sendable {
    public let day: String
    public let noop: Double
    public let reference: Double
    /// noop − reference.
    public var diff: Double { noop - reference }
}

/// Agreement statistics for one metric.
public struct ScoreAgreement: Equatable, Sendable {
    public let n: Int
    public let bias: Double
    public let mae: Double
    public let rmse: Double
    public let limitsOfAgreement: (lower: Double, upper: Double)
    public let r: Double?
    /// Day-sorted joined pairs.
    public let pairs: [ScorePair]
    /// Largest |noop − ref| first; ties broken by day ascending.
    public let worstDays: [ScorePair]

    public static func == (a: ScoreAgreement, b: ScoreAgreement) -> Bool {
        a.n == b.n && a.bias == b.bias && a.mae == b.mae && a.rmse == b.rmse
            && a.limitsOfAgreement.lower == b.limitsOfAgreement.lower
            && a.limitsOfAgreement.upper == b.limitsOfAgreement.upper
            && a.r == b.r && a.pairs == b.pairs && a.worstDays == b.worstDays
    }
}

public enum ScoreComparator {

    /// Compare two day-keyed series. Returns nil when no day has a finite value on both sides.
    public static func compare(noop: [String: Double],
                               reference: [String: Double],
                               worstCount: Int = 5) -> ScoreAgreement? {
        let pairs: [ScorePair] = noop.keys.sorted().compactMap { day in
            guard let a = noop[day], let b = reference[day], a.isFinite, b.isFinite else { return nil }
            return ScorePair(day: day, noop: a, reference: b)
        }
        guard !pairs.isEmpty else { return nil }

        let n = Double(pairs.count)
        let diffs = pairs.map(\.diff)
        let bias = diffs.reduce(0, +) / n
        let mae = diffs.reduce(0) { $0 + abs($1) } / n
        let rmse = (diffs.reduce(0) { $0 + $1 * $1 } / n).squareRoot()
        let sd = pairs.count > 1
            ? (diffs.reduce(0) { $0 + ($1 - bias) * ($1 - bias) } / (n - 1)).squareRoot()
            : 0
        let r = pairs.count >= 3 ? CorrelationEngine.pearson(pairs.map { ($0.noop, $0.reference) })?.r : nil

        let worst = pairs.sorted {
            abs($0.diff) != abs($1.diff) ? abs($0.diff) > abs($1.diff) : $0.day < $1.day
        }
        return ScoreAgreement(n: pairs.count, bias: bias, mae: mae, rmse: rmse,
                              limitsOfAgreement: (bias - 1.96 * sd, bias + 1.96 * sd),
                              r: r, pairs: pairs, worstDays: Array(worst.prefix(max(0, worstCount))))
    }
}
