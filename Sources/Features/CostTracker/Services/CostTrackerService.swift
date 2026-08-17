// MARK: - CostTrackerService
// Real-time token/cost accumulator for agent terminal sessions.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Hard limits for the cost tracker buffers and values.
enum CostTrackerLimits {
    /// Maximum per-session line buffer size in bytes.
    /// Protects against memory exhaustion when a PTY stream emits continuous
    /// bytes without a newline (e.g. `cat` of a large file).
    static let maxLineBufferBytes = 4096
}

/// Accumulates token usage and estimated cost for active agent sessions.
///
/// **Architecture:**
/// - Cost data lives here, NOT in `TerminalSession`, so that frequent
///   token updates do NOT trigger `updateSessionState` / `sessionsChanged`
///   broadcasts (which would re-render the entire session list on each chunk).
/// - Receives raw PTY bytes via `TaggedTerminalView.onParsedOutput` — a second
///   parallel callback that does NOT interfere with `onRawData` (used by
///   `RemoteBridgeRegistry` for WebSocket streaming).
/// - Parses only when a complete `\n`-terminated line has been assembled from
///   potentially-fragmented PTY chunks (E1 — multichunk usage).
/// - Only parses sessions explicitly registered as agent sessions to avoid
///   false positives from shell output (DoD business rule).
///
/// **Security:**
/// - Raw PTY text is NEVER logged (may contain API key fragments from env).
/// - Only numeric counters are logged with `.public` privacy.
@Observable
@MainActor
final class CostTrackerService {

    // MARK: - Public State

    /// Accumulated cost snapshots keyed by agent session UUID.
    ///
    /// Updated on every successful usage parse. Survives tab-switch
    /// (callback detaches but snapshot remains).
    private(set) var sessionCosts: [UUID: SessionCostSnapshot] = [:]

    // MARK: - Private: Line Buffers

    /// Per-session accumulation buffer for incomplete lines.
    /// Bytes are appended as PTY chunks arrive; flushed on `\n`.
    private var lineBuffers: [UUID: [UInt8]] = [:]

    // MARK: - Init

    init() {
        // Load any operator price override on startup (fail-open).
        ModelPricingTable.reloadOverride()
    }

    // MARK: - Feed

    /// Feed raw PTY bytes for an agent session.
    ///
    /// Called from `TaggedTerminalView.onParsedOutput` on the MainActor.
    /// Assembles complete lines and calls ``parseLine(_:sessionId:)`` on
    /// each `\n`-terminated chunk.
    ///
    /// - Parameters:
    ///   - sessionId: Session UUID this data belongs to.
    ///   - projectId: Project UUID (reserved for future per-project rollup).
    ///   - slice: Raw bytes from the PTY file descriptor.
    func feed(sessionId: UUID, projectId: UUID, slice: ArraySlice<UInt8>) {
        // Fast pre-check: skip chunks with no likely usage bytes
        guard TokenUsageParser.mightContainUsage(slice) else { return }

        var buf = lineBuffers[sessionId] ?? []

        for byte in slice {
            if byte == UInt8(ascii: "\n") {
                if !buf.isEmpty {
                    if let line = String(bytes: buf, encoding: .utf8) {
                        parseLine(line, sessionId: sessionId)
                    }
                    buf.removeAll(keepingCapacity: true)
                }
            } else {
                buf.append(byte)
                // E2 — buffer overflow guard: drop if no newline in 4KB
                if buf.count >= CostTrackerLimits.maxLineBufferBytes {
                    Logger.costTracker.debug(
                        "CostTracker: buffer overflow for session \(sessionId, privacy: .public), dropping"
                    )
                    buf.removeAll(keepingCapacity: false)
                }
            }
        }
        lineBuffers[sessionId] = buf
    }

    // MARK: - Reset

    /// Reset the cost snapshot for a session (called on process exit).
    ///
    /// Clears both the snapshot and the line buffer so a restarted agent
    /// begins with fresh counters (E7).
    func reset(for sessionId: UUID) {
        sessionCosts.removeValue(forKey: sessionId)
        lineBuffers.removeValue(forKey: sessionId)
        Logger.costTracker.info(
            "CostTracker: reset session \(sessionId, privacy: .public)"
        )
    }

    /// Remove all data for a session (called on session removal).
    func removeSession(_ sessionId: UUID) {
        sessionCosts.removeValue(forKey: sessionId)
        lineBuffers.removeValue(forKey: sessionId)
    }

    // MARK: - Snapshot Access

    /// Returns the current snapshot for a session, or nil if no data yet.
    func snapshot(for sessionId: UUID) -> SessionCostSnapshot? {
        sessionCosts[sessionId]
    }

    /// Returns the combined snapshot for all sessions that have cost data.
    /// Used when the caller doesn't have a specific sessionId (e.g. CodeSpeak toolbar).
    func anyActiveSnapshot() -> SessionCostSnapshot? {
        sessionCosts.values.first(where: { $0.totalTokens > 0 })
    }

    // MARK: - Private: Parsing

    private func parseLine(_ line: String, sessionId: UUID) {
        let stripped = ANSIStripper.strip(line)
        guard let usage = TokenUsageParser.parse(from: stripped) else { return }

        // Accumulate
        var snap = sessionCosts[sessionId] ?? SessionCostSnapshot()
        snap.accumulate(usage)
        sessionCosts[sessionId] = snap

        // Log only numeric counters — never the raw line (security)
        Logger.costTracker.info(
            "CostTracker: session=\(sessionId, privacy: .public) prompt=\(snap.promptTokens, privacy: .public) completion=\(snap.completionTokens, privacy: .public)"
        )
    }
}
