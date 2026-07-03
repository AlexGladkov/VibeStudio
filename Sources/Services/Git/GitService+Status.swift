// MARK: - GitService+Status
// Working-tree status, ahead/behind counters, staging, and porcelain parsing.

import Foundation

extension GitService {

    // MARK: - GitServicing: Status & Info

    func status(at repository: URL) async throws -> GitStatus {
        // Use porcelain v1 for simpler parsing (v2 is more complex and not needed).
        let output = try await runGit(
            ["status", "--porcelain=v1", "--branch"],
            in: repository
        )

        return parseStatus(output)
    }

    // MARK: - Ahead/Behind (used by GitStatusPoller)

    /// Get ahead/behind count relative to upstream.
    ///
    /// - Parameter repository: Repository root path.
    /// - Returns: Tuple of (ahead, behind) counts.
    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int) {
        let output = try await runGit(
            ["rev-list", "--left-right", "--count", "HEAD...@{upstream}"],
            in: repository
        )
        let parts = output.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: "\t")
        guard parts.count == 2,
              let ahead = Int(parts[0]),
              let behind = Int(parts[1]) else {
            return (0, 0)
        }
        return (ahead, behind)
    }

    // MARK: - GitServicing: Staging

    func stage(files: [String], at repository: URL) async throws {
        if files.isEmpty {
            try await runGit(["add", "-A"], in: repository)
        } else {
            var args = ["add", "--"]
            args.append(contentsOf: files)
            try await runGit(args, in: repository)
        }
    }

    func unstage(files: [String], at repository: URL) async throws {
        if files.isEmpty {
            try await runGit(["restore", "--staged", "."], in: repository)
        } else {
            var args = ["restore", "--staged", "--"]
            args.append(contentsOf: files)
            try await runGit(args, in: repository)
        }
    }

    // MARK: - Internal: Parsing (visible to tests)

    /// Parse `git status --porcelain=v1 --branch` output.
    ///
    /// Dispatches each line either to ``parseBranchLine(_:)`` (the leading
    /// `## main...origin/main [ahead 2, behind 1]` line) or to
    /// ``parseFileLine(_:staged:unstaged:untracked:)``. ARCH-M4: each helper
    /// stays under 30 LOC.
    func parseStatus(_ output: String) -> GitStatus {
        var branch = ""
        var staged: [GitFile] = []
        var unstaged: [GitFile] = []
        var untracked: [GitFile] = []
        var ahead = 0
        var behind = 0

        for line in output.components(separatedBy: .newlines) where !line.isEmpty {
            if line.hasPrefix("##") {
                let parsed = parseBranchLine(line)
                branch = parsed.branch
                ahead = parsed.ahead
                behind = parsed.behind
                continue
            }

            parseFileLine(line, staged: &staged, unstaged: &unstaged, untracked: &untracked)
        }

        return GitStatus(
            branch: branch,
            aheadCount: ahead,
            behindCount: behind,
            stagedFiles: staged,
            unstagedFiles: unstaged,
            untrackedFiles: untracked
        )
    }

    /// Result of parsing the `## <branch>...` porcelain header line.
    struct BranchLineInfo {
        let branch: String
        let ahead: Int
        let behind: Int
    }

    /// Parse the `## <branch>[...upstream] [ahead N, behind M]` header line.
    ///
    /// - Parameter line: The full header line including the leading `##`.
    /// - Returns: The local branch name plus the ahead / behind counters
    ///   (zero when absent).
    func parseBranchLine(_ line: String) -> BranchLineInfo {
        let info = String(line.dropFirst(3))
        let branch: String
        if let dotRange = info.range(of: "...") {
            branch = String(info[info.startIndex..<dotRange.lowerBound])
        } else if let spaceRange = info.range(of: " ") {
            branch = String(info[info.startIndex..<spaceRange.lowerBound])
        } else {
            branch = info
        }

        let ahead = parseTrailingNumber(in: info, keyword: "ahead")
        let behind = parseTrailingNumber(in: info, keyword: "behind")
        return BranchLineInfo(branch: branch, ahead: ahead, behind: behind)
    }

    /// Helper: read the integer that follows `keyword` in the porcelain
    /// branch-header (e.g. `keyword = "ahead"` in `[ahead 2, behind 1]`).
    ///
    /// PERF: called on every status poll — hand-rolled instead of
    /// `.regularExpression` (which recompiles the pattern each call). Mirrors
    /// the old regex `keyword (\d+)`: exactly one space between the keyword and
    /// one or more digits; anything else yields `0`.
    ///
    /// Scans *every* occurrence of `keyword` — not just the first — because a
    /// branch name can contain the word as a substring (e.g.
    /// `feature/ahead-payment...origin/... [ahead 2]`). Only an occurrence
    /// followed by a space + digits is the real `[ahead N] / [behind M]` token.
    private func parseTrailingNumber(in haystack: String, keyword: String) -> Int {
        var searchStart = haystack.startIndex
        while let range = haystack.range(of: keyword, range: searchStart..<haystack.endIndex) {
            let rest = haystack[range.upperBound...]
            if rest.hasPrefix(" ") {
                let digits = rest.dropFirst().prefix { $0.isNumber }
                if !digits.isEmpty { return Int(digits) ?? 0 }
            }
            searchStart = range.upperBound
        }
        return 0
    }

    /// Parse a single porcelain status line (non-header) and append the
    /// resulting `GitFile` to the relevant bucket.
    func parseFileLine(
        _ line: String,
        staged: inout [GitFile],
        unstaged: inout [GitFile],
        untracked: inout [GitFile]
    ) {
        guard line.count >= 4 else { return }

        let indexChar = line[line.startIndex]
        let worktreeChar = line[line.index(after: line.startIndex)]
        let filePath = String(line[line.index(line.startIndex, offsetBy: 3)...])

        if indexChar == "?" {
            untracked.append(GitFile(path: filePath, status: .untracked))
            return
        }

        // Staged changes (index column).
        if indexChar != " ", let status = parseFileStatus(indexChar) {
            staged.append(GitFile(path: filePath, status: status))
        }
        // Unstaged changes (worktree column).
        if worktreeChar != " ", worktreeChar != "?", let status = parseFileStatus(worktreeChar) {
            unstaged.append(GitFile(path: filePath, status: status))
        }
    }

    /// Map a single porcelain status character to ``GitFileStatus``.
    func parseFileStatus(_ char: Character) -> GitFileStatus? {
        switch char {
        case "M": return .modified
        case "A": return .added
        case "D": return .deleted
        case "R": return .renamed
        case "C": return .copied
        default: return nil
        }
    }
}
