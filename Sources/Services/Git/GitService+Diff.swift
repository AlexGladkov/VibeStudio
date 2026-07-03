// MARK: - GitService+Diff
// Diff family (file/staged/head/stats), commit log, and unified-diff parsing.

import Foundation

extension GitService {

    /// Shared date formatter for parsing ISO 8601 commit dates.
    ///
    /// A `static let` on the extension is legal (only *instance* stored
    /// properties are disallowed in extensions). Kept here beside its sole
    /// consumer, ``log(limit:at:)``.
    static let commitDateFormatter = ISO8601DateFormatter()

    // MARK: - GitServicing: Diff

    func diff(file: String, staged: Bool, at repository: URL) async throws -> [GitDiffHunk] {
        var args = ["diff"]
        if staged { args.append("--cached") }
        args.append("--")
        args.append(file)

        let output = try await runGit(args, in: repository)
        return parseDiff(output)
    }

    func fullStagedDiff(at repository: URL) async throws -> String {
        try await runGit(["diff", "--staged"], in: repository)
    }

    func headDiff(at repository: URL) async throws -> String {
        // git diff HEAD shows all uncommitted changes (staged + unstaged) vs last commit.
        // Falls back to staged-only for repos without any commits yet.
        do {
            return try await runGit(["diff", "HEAD"], in: repository)
        } catch let error as GitServiceError {
            if case .commandFailed = error {
                // No commits yet — return staged diff only
                return try await runGit(["diff", "--staged"], in: repository)
            }
            throw error
        }
    }

    func diffStats(at repository: URL) async throws -> [String: GitDiffStat] {
        // Merge unstaged and staged numstat results.
        var result: [String: (added: Int, deleted: Int)] = [:]

        func merge(_ output: String) {
            for (path, stat) in parseNumstat(output) {
                if let existing = result[path] {
                    result[path] = (existing.added + stat.added, existing.deleted + stat.deleted)
                } else {
                    result[path] = stat
                }
            }
        }

        // Unstaged diff (vs index)
        if let unstaged = try? await runGit(["diff", "--numstat"], in: repository) {
            merge(unstaged)
        }
        // Staged diff (index vs HEAD, with fallback for empty repos)
        let staged: String
        do {
            staged = try await runGit(["diff", "--cached", "--numstat"], in: repository)
        } catch {
            staged = (try? await runGit(["diff", "--numstat"], in: repository)) ?? ""
        }
        merge(staged)

        return result.mapValues { GitDiffStat(added: $0.added, deleted: $0.deleted) }
    }

    /// Parse `git diff --numstat` output into a path→(added, deleted) dictionary.
    ///
    /// Each line has the form: `<added>\t<deleted>\t<path>`
    /// Binary files show `-` for both counts and are skipped.
    ///
    /// `internal` (not `private`) so unit tests can exercise the parser in
    /// isolation, matching the other `parse*` diff helpers.
    func parseNumstat(_ output: String) -> [String: (added: Int, deleted: Int)] {
        var dict: [String: (added: Int, deleted: Int)] = [:]
        for line in output.split(separator: "\n", omittingEmptySubsequences: true) {
            let parts = line.split(separator: "\t", maxSplits: 2)
            guard parts.count == 3 else { continue }
            guard let added = Int(parts[0]), let deleted = Int(parts[1]) else { continue }
            let path = String(parts[2])
            dict[path] = (added, deleted)
        }
        return dict
    }

    // MARK: - GitServicing: Log

    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo] {
        let output = try await runGit(
            ["log", "--format=%H\t%h\t%s\t%an\t%aI", "-n", "\(limit)"],
            in: repository
        )

        return output.components(separatedBy: .newlines)
            .filter { !$0.isEmpty }
            .compactMap { line -> GitCommitInfo? in
                let parts = line.components(separatedBy: "\t")
                guard parts.count >= 5 else { return nil }
                return GitCommitInfo(
                    hash: parts[0],
                    shortHash: parts[1],
                    message: parts[2],
                    author: parts[3],
                    date: Self.commitDateFormatter.date(from: parts[4]) ?? .now
                )
            }
    }

    // MARK: - Internal: Diff Parsing (visible to tests)

    /// Parse unified diff output into hunks.
    func parseDiff(_ output: String) -> [GitDiffHunk] {
        var hunks: [GitDiffHunk] = []
        var currentHeader = ""
        var currentLines: [GitDiffLine] = []
        var oldLine = 0
        var newLine = 0

        for line in output.components(separatedBy: .newlines) {
            if line.hasPrefix("@@") {
                // Save previous hunk if exists.
                if !currentHeader.isEmpty {
                    hunks.append(GitDiffHunk(header: currentHeader, lines: currentLines))
                }
                currentHeader = line
                currentLines = []
                (oldLine, newLine) = parseHunkLineNumbers(line)
                continue
            }

            guard !currentHeader.isEmpty else { continue }

            // Git's "\ No newline at end of file" marker is diff metadata, not a
            // content line. It must be ignored entirely: emitting it as a
            // `.context` line advances BOTH the old and new counters, shifting
            // the line numbers of every subsequent line in the hunk.
            if line.hasPrefix("\\") { continue }

            currentLines.append(makeDiffLine(from: line, oldLine: &oldLine, newLine: &newLine))
        }

        if !currentHeader.isEmpty {
            hunks.append(GitDiffHunk(header: currentHeader, lines: currentLines))
        }

        return hunks
    }

    /// Extract the starting old/new line numbers from a `@@ -a,b +c,d @@` header.
    private func parseHunkLineNumbers(_ line: String) -> (old: Int, new: Int) {
        var old = 0
        var new = 0
        if let match = line.range(of: #"-(\d+)"#, options: .regularExpression) {
            old = Int(line[match].dropFirst()) ?? 0
        }
        if let match = line.range(of: #"\+(\d+)"#, options: .regularExpression) {
            new = Int(line[match].dropFirst()) ?? 0
        }
        return (old, new)
    }

    /// Build a ``GitDiffLine`` from a diff body line, advancing the line counters.
    private func makeDiffLine(
        from line: String,
        oldLine: inout Int,
        newLine: inout Int
    ) -> GitDiffLine {
        if line.hasPrefix("+") {
            defer { newLine += 1 }
            return GitDiffLine(
                type: .addition,
                content: String(line.dropFirst()),
                oldLineNumber: nil,
                newLineNumber: newLine
            )
        }
        if line.hasPrefix("-") {
            defer { oldLine += 1 }
            return GitDiffLine(
                type: .deletion,
                content: String(line.dropFirst()),
                oldLineNumber: oldLine,
                newLineNumber: nil
            )
        }
        defer {
            oldLine += 1
            newLine += 1
        }
        return GitDiffLine(
            type: .context,
            content: line.hasPrefix(" ") ? String(line.dropFirst()) : line,
            oldLineNumber: oldLine,
            newLineNumber: newLine
        )
    }
}
