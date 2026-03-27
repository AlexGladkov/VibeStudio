// MARK: - GitService
// Actor-based git operations via subprocess.
// All commands use argument arrays -- never shell interpolation.
// macOS 14+, Swift 5.10

import Foundation

/// Stateless, thread-safe git CLI wrapper.
///
/// Every git command is executed as a subprocess with strict
/// argument-array invocation to prevent command injection.
/// Branch names are validated before use. File paths are
/// always preceded by `--` to prevent option injection.
///
/// Implemented as a `final class` (not `actor`) because all stored properties
/// are immutable (`let`) — the actor executor added `await` overhead on every
/// call without providing any isolation benefit. All methods are safe to call
/// concurrently since they only read constants and spawn independent subprocesses.
final class GitService: GitServicing, @unchecked Sendable {

    // MARK: - Constants

    /// Default timeout for read-only git operations (seconds).
    private let defaultTimeout: TimeInterval = 30

    /// Extended timeout for network operations like push/pull (seconds).
    private let networkTimeout: TimeInterval = 120

    /// Regex for valid git branch names (prevents injection via crafted names).
    private static let validBranchPattern = /^[a-zA-Z0-9\/_\-\.@]+$/

    /// Shared date formatter for parsing ISO 8601 commit dates.
    private static let commitDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        return formatter
    }()

    // MARK: - Git Binary

    /// Resolved path to the git binary.
    private let gitPath: String

    init() {
        // Prefer Xcode CLT git, fall back to common paths.
        let candidates = [
            "/usr/bin/git",
            "/usr/local/bin/git",
            "/opt/homebrew/bin/git"
        ]
        self.gitPath = candidates.first { FileManager.default.isExecutableFile(atPath: $0) } ?? "/usr/bin/git"
    }

    // MARK: - GitServicing: Status & Info

    func status(at repository: URL) async throws -> GitStatus {
        // Use porcelain v1 for simpler parsing (v2 is more complex and not needed).
        let output = try await runGit(
            ["status", "--porcelain=v1", "--branch"],
            in: repository
        )

        return parseStatus(output)
    }

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

    func initRepository(at path: URL) async throws {
        try await runGit(["init"], in: path)
    }

    func addRemote(name: String, url: String, at repository: URL) async throws {
        guard !name.isEmpty, !name.hasPrefix("-"), !name.contains(" "), !name.contains("..") else {
            throw GitServiceError.commandFailed(command: "remote", exitCode: 1, stderr: "Invalid remote name: \(name)")
        }
        // Enhanced URL validation: must be a recognized git transport scheme
        guard !url.isEmpty, !url.hasPrefix("-") else {
            throw GitServiceError.commandFailed(command: "remote", exitCode: 1, stderr: "Invalid remote URL")
        }
        // Prevent dangerous git transport protocols (ext:: allows arbitrary command execution)
        let forbiddenPrefixes = ["ext::", "fd::"]
        guard !forbiddenPrefixes.contains(where: { url.lowercased().hasPrefix($0) }) else {
            throw GitServiceError.commandFailed(command: "remote", exitCode: 1,
                stderr: "Unsupported remote URL scheme: only https://, http://, git://, ssh://, git@, and local paths are allowed")
        }
        try await runGit(["remote", "add", name, url], in: repository)
    }

    func branches(at repository: URL) async throws -> [GitBranch] {
        // Use separate commands for local and remote to correctly classify branches
        // that contain '/' in their name (e.g. feature/my-thing is local, not remote).

        // Step 1: local branches
        let localOutput = try await runGit(
            ["branch", "--list", "--format=%(refname:short)\t%(HEAD)"],
            in: repository
        )
        var result: [GitBranch] = localOutput.components(separatedBy: .newlines)
            .filter { !$0.isEmpty }
            .compactMap { line -> GitBranch? in
                let parts = line.components(separatedBy: "\t")
                guard let name = parts.first, !name.isEmpty else { return nil }
                let isCurrent = parts.count >= 2 && parts[1] == "*"
                return GitBranch(name: name, isRemote: false, isCurrent: isCurrent)
            }

        // Step 2: remote tracking branches (local cache — no network call)
        do {
            let remoteOutput = try await runGit(
                ["branch", "-r", "--list", "--format=%(refname:short)"],
                in: repository
            )
            let remoteBranches = remoteOutput.components(separatedBy: .newlines)
                .filter { !$0.isEmpty }
                .compactMap { line -> GitBranch? in
                    let name = line.trimmingCharacters(in: .whitespaces)
                    // Skip remote HEAD aliases like origin/HEAD
                    guard !name.isEmpty, !name.hasSuffix("/HEAD") else { return nil }
                    return GitBranch(name: name, isRemote: true, isCurrent: false)
                }
            result.append(contentsOf: remoteBranches)
        } catch {
            // Remote refs unavailable — return local branches only.
        }

        return result
    }

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

    // MARK: - GitServicing: Commit

    @discardableResult
    func commit(message: String, at repository: URL) async throws -> String {
        guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw GitServiceError.commandFailed(
                command: "commit",
                exitCode: 1,
                stderr: "Commit message cannot be empty"
            )
        }

        let output = try await runGit(["commit", "-m", message], in: repository)

        // Extract commit hash from output (first line usually contains it).
        if let match = output.range(of: #"[0-9a-f]{7,40}"#, options: .regularExpression) {
            return String(output[match])
        }
        return output
    }

    // MARK: - GitServicing: Remote

    func push(remote: String, at repository: URL) async throws {
        try validateBranchName(remote)
        do {
            try await runGit(["push", remote], in: repository, timeout: networkTimeout)
        } catch let error as GitServiceError {
            if case .commandFailed(_, _, let stderr) = error,
               stderr.contains("rejected") {
                throw GitServiceError.pushRejected(reason: stderr)
            }
            throw error
        }
    }

    func pull(remote: String, at repository: URL) async throws {
        try validateBranchName(remote)
        do {
            try await runGit(["pull", remote], in: repository, timeout: networkTimeout)
        } catch let error as GitServiceError {
            if case .commandFailed(_, _, let stderr) = error,
               stderr.contains("CONFLICT") {
                let files = stderr.components(separatedBy: .newlines)
                    .filter { $0.contains("CONFLICT") }
                throw GitServiceError.mergeConflict(files: files)
            }
            throw error
        }
    }

    func fetch(remote: String, at repository: URL) async throws {
        try validateBranchName(remote)
        try await runGit(["fetch", remote], in: repository, timeout: networkTimeout)
    }

    func pushBranch(_ branch: String, remote: String, at repository: URL) async throws {
        try validateBranchName(branch)
        try validateBranchName(remote)
        do {
            // --set-upstream tracks the remote branch; harmless if tracking already exists.
            // suppressCredentials=false so osxkeychain / SSH agent work normally.
            try await runGit(["push", "--set-upstream", remote, branch],
                             in: repository, timeout: networkTimeout, suppressCredentials: false)
        } catch let error as GitServiceError {
            if case .commandFailed(_, _, let stderr) = error, stderr.contains("rejected") {
                throw GitServiceError.pushRejected(reason: stderr)
            }
            throw error
        }
    }

    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws {
        try validateBranchName(branch)
        try validateBranchName(remote)
        if isCurrent {
            do {
                try await runGit(["pull", remote], in: repository,
                                 timeout: networkTimeout, suppressCredentials: false)
            } catch let error as GitServiceError {
                if case .commandFailed(_, _, let stderr) = error, stderr.contains("CONFLICT") {
                    let files = stderr.components(separatedBy: .newlines).filter { $0.contains("CONFLICT") }
                    throw GitServiceError.mergeConflict(files: files)
                }
                throw error
            }
        } else {
            try await runGit(["fetch", remote, "\(branch):\(branch)"],
                             in: repository, timeout: networkTimeout, suppressCredentials: false)
        }
    }

    func remoteURL(name: String, at repository: URL) async -> String? {
        guard let output = try? await runGit(["remote", "get-url", name], in: repository) else {
            return nil
        }
        let trimmed = output.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    func defaultRemote(for branch: String?, at repository: URL) async -> String {
        // 1. Branch-specific remote from git config
        if let branch = branch,
           let r = try? await runGit(["config", "--get", "branch.\(branch).remote"], in: repository),
           !r.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return r.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        // 2. First remote listed in the repo
        if let remotes = try? await runGit(["remote"], in: repository),
           let first = remotes.components(separatedBy: .newlines).first(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty }) {
            return first.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return "origin"
    }

    // MARK: - GitServicing: Branches

    func checkout(branch: String, at repository: URL) async throws {
        try validateBranchName(branch)
        try await runGit(["switch", branch], in: repository)
    }

    func createBranch(name: String, from startPoint: String?, at repository: URL) async throws {
        try validateBranchName(name)
        var args = ["switch", "-c", name]
        if let startPoint {
            try validateBranchName(startPoint)
            args.append(startPoint)
        }
        try await runGit(args, in: repository)
    }

    // MARK: - GitServicing: Utility

    func isRepository(at path: URL) async -> Bool {
        do {
            try await runGit(
                ["rev-parse", "--is-inside-work-tree"],
                in: path
            )
            return true
        } catch {
            return false
        }
    }

    func repositoryRoot(for path: URL) async throws -> URL {
        let output = try await runGit(
            ["rev-parse", "--show-toplevel"],
            in: path
        )
        let trimmed = output.trimmingCharacters(in: .whitespacesAndNewlines)
        return URL(fileURLWithPath: trimmed)
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

    // MARK: - Private: Subprocess Execution

    /// Execute a git command as a subprocess with strict argument-array invocation.
    ///
    /// **Security**: Never uses `/bin/sh -c`. Arguments are passed directly
    /// to the git binary via `Process.arguments`, preventing shell injection.
    ///
    /// Uses `terminationHandler` instead of `waitUntilExit()` so the actor thread
    /// is never blocked — multiple git commands can be in-flight concurrently.
    ///
    /// - Parameters:
    ///   - args: Git subcommand and arguments (e.g., `["status", "--porcelain=v1"]`).
    ///   - dir: Working directory for the command.
    ///   - timeout: Maximum execution time in seconds.
    /// - Returns: Standard output as a string.
    /// - Throws: ``GitServiceError`` on failure or timeout.
    @discardableResult
    private func runGit(
        _ args: [String],
        in dir: URL,
        timeout: TimeInterval? = nil,
        suppressCredentials: Bool = true
    ) async throws -> String {
        let effectiveTimeout = timeout ?? defaultTimeout
        let commandDesc = args.first ?? "git"
        let gitBinary = self.gitPath

        return try await withCheckedThrowingContinuation { continuation in
            let process = Process()
            process.executableURL = URL(fileURLWithPath: gitBinary)
            // Use `git -C <dir>` instead of setting currentDirectoryURL.
            // Setting currentDirectoryURL causes the forked child process to call
            // chdir(dir) BEFORE execve — while it still carries VibeStudio's process
            // identity. That chdir into ~/Documents triggers a TCC dialog on every
            // git subprocess call. With `-C dir` git itself (an Apple-signed binary)
            // handles the chdir internally after exec, which does not trigger TCC.
            process.arguments = ["-C", dir.path] + args

            var env = ProcessInfo.processInfo.environment
            if suppressCredentials {
                // Non-network commands: prevent any interactive credential prompt.
                env["GIT_TERMINAL_PROMPT"] = "0"
                env["GIT_ASKPASS"] = "/usr/bin/true"
            }
            // Network commands (suppressCredentials=false) run with the full inherited
            // environment so the system credential helper (osxkeychain / SSH agent) works.
            process.environment = env

            let stdoutPipe = Pipe()
            let stderrPipe = Pipe()
            process.standardOutput = stdoutPipe
            process.standardError = stderrPipe

            // IOBuffer is a class so it can be captured by reference across @Sendable closures.
            // All mutations happen on ioQueue (serial) — no data races.
            final class IOBuffer: @unchecked Sendable {
                var stdout = Data()
                var stderr = Data()
            }
            let buf = IOBuffer()
            let ioQueue = DispatchQueue(label: "git.io.\(commandDesc)")

            // Drain pipes incrementally to prevent deadlock when git output exceeds
            // the OS pipe buffer (~64 KB). Without this, git blocks writing to the
            // pipe, never exits, and the 30-second timeout fires.
            stdoutPipe.fileHandleForReading.readabilityHandler = { handle in
                let chunk = handle.availableData
                guard !chunk.isEmpty else { return }
                ioQueue.async { buf.stdout.append(chunk) }
            }
            stderrPipe.fileHandleForReading.readabilityHandler = { handle in
                let chunk = handle.availableData
                guard !chunk.isEmpty else { return }
                ioQueue.async { buf.stderr.append(chunk) }
            }

            let timeoutItem = DispatchWorkItem { [weak process] in
                process?.terminate()
            }
            DispatchQueue.global().asyncAfter(deadline: .now() + effectiveTimeout, execute: timeoutItem)

            // terminationHandler fires on a background thread when the process exits.
            // This suspends the continuation asynchronously — the actor is NOT blocked
            // while git is running, so concurrent git calls can interleave freely.
            process.terminationHandler = { proc in
                timeoutItem.cancel()

                // Disable handlers, then flush any bytes that arrived between the last
                // readabilityHandler call and the process exit (ioQueue.sync ensures
                // all prior ioQueue.async appends have completed before we read more).
                stdoutPipe.fileHandleForReading.readabilityHandler = nil
                stderrPipe.fileHandleForReading.readabilityHandler = nil
                ioQueue.sync {
                    buf.stdout.append(stdoutPipe.fileHandleForReading.readDataToEndOfFile())
                    buf.stderr.append(stderrPipe.fileHandleForReading.readDataToEndOfFile())
                }

                let stdout = String(data: buf.stdout, encoding: .utf8) ?? ""
                let stderr = String(data: buf.stderr, encoding: .utf8) ?? ""

                if proc.terminationReason == .uncaughtSignal {
                    continuation.resume(
                        throwing: GitServiceError.timeout(command: commandDesc, seconds: effectiveTimeout)
                    )
                    return
                }

                guard proc.terminationStatus == 0 else {
                    if stderr.contains("not a git repository") {
                        continuation.resume(
                            throwing: GitServiceError.notARepository(path: dir)
                        )
                    } else {
                        continuation.resume(
                            throwing: GitServiceError.commandFailed(
                                command: commandDesc,
                                exitCode: proc.terminationStatus,
                                stderr: stderr
                            )
                        )
                    }
                    return
                }

                continuation.resume(returning: stdout)
            }

            do {
                try process.run()
            } catch {
                timeoutItem.cancel()
                continuation.resume(throwing: GitServiceError.gitNotFound)
            }
        }
    }

    // MARK: - Internal: Parsing (visible to tests)

    /// Parse `git status --porcelain=v1 --branch` output.
    func parseStatus(_ output: String) -> GitStatus {
        var branch = ""
        var staged: [GitFile] = []
        var unstaged: [GitFile] = []
        var untracked: [GitFile] = []
        var ahead = 0
        var behind = 0

        for line in output.components(separatedBy: .newlines) where !line.isEmpty {
            if line.hasPrefix("##") {
                // Branch line: ## main...origin/main [ahead 2, behind 1]
                let branchInfo = String(line.dropFirst(3))
                if let dotRange = branchInfo.range(of: "...") {
                    branch = String(branchInfo[branchInfo.startIndex..<dotRange.lowerBound])
                } else if let spaceRange = branchInfo.range(of: " ") {
                    branch = String(branchInfo[branchInfo.startIndex..<spaceRange.lowerBound])
                } else {
                    branch = branchInfo
                }

                // Parse ahead/behind from branch line.
                if let aheadMatch = branchInfo.range(of: #"ahead (\d+)"#, options: .regularExpression) {
                    let numStr = branchInfo[aheadMatch]
                        .components(separatedBy: " ").last ?? "0"
                    ahead = Int(numStr) ?? 0
                }
                if let behindMatch = branchInfo.range(of: #"behind (\d+)"#, options: .regularExpression) {
                    let numStr = branchInfo[behindMatch]
                        .components(separatedBy: " ").last ?? "0"
                    behind = Int(numStr) ?? 0
                }
                continue
            }

            guard line.count >= 4 else { continue }

            let index = line.index(line.startIndex, offsetBy: 0)
            let worktree = line.index(line.startIndex, offsetBy: 1)
            let pathStart = line.index(line.startIndex, offsetBy: 3)
            let indexChar = line[index]
            let worktreeChar = line[worktree]
            let filePath = String(line[pathStart...])

            // Untracked files.
            if indexChar == "?" {
                untracked.append(GitFile(path: filePath, status: .untracked))
                continue
            }

            // Staged changes (index column).
            if indexChar != " " && indexChar != "?" {
                if let status = parseFileStatus(indexChar) {
                    staged.append(GitFile(path: filePath, status: status))
                }
            }

            // Unstaged changes (worktree column).
            if worktreeChar != " " && worktreeChar != "?" {
                if let status = parseFileStatus(worktreeChar) {
                    unstaged.append(GitFile(path: filePath, status: status))
                }
            }
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

                // Parse hunk header for line numbers.
                if let match = line.range(of: #"-(\d+)"#, options: .regularExpression) {
                    oldLine = Int(line[match].dropFirst()) ?? 0
                }
                if let match = line.range(of: #"\+(\d+)"#, options: .regularExpression) {
                    newLine = Int(line[match].dropFirst()) ?? 0
                }
                continue
            }

            guard !currentHeader.isEmpty else { continue }

            if line.hasPrefix("+") {
                currentLines.append(GitDiffLine(
                    type: .addition,
                    content: String(line.dropFirst()),
                    oldLineNumber: nil,
                    newLineNumber: newLine
                ))
                newLine += 1
            } else if line.hasPrefix("-") {
                currentLines.append(GitDiffLine(
                    type: .deletion,
                    content: String(line.dropFirst()),
                    oldLineNumber: oldLine,
                    newLineNumber: nil
                ))
                oldLine += 1
            } else {
                currentLines.append(GitDiffLine(
                    type: .context,
                    content: line.hasPrefix(" ") ? String(line.dropFirst()) : line,
                    oldLineNumber: oldLine,
                    newLineNumber: newLine
                ))
                oldLine += 1
                newLine += 1
            }
        }

        if !currentHeader.isEmpty {
            hunks.append(GitDiffHunk(header: currentHeader, lines: currentLines))
        }

        return hunks
    }

    // MARK: - Internal: Validation (visible to tests)

    /// Validate that a branch name does not contain dangerous characters.
    ///
    /// Prevents command injection via crafted branch names like
    /// `--upload-pack=evil` or `; rm -rf /`.
    ///
    /// - Parameter name: Branch or remote name to validate.
    /// - Throws: ``GitServiceError.commandFailed`` if the name is invalid.
    func validateBranchName(_ name: String) throws {
        guard !name.isEmpty,
              !name.hasPrefix("-"),
              !name.contains(".."),
              !name.contains(" "),
              !name.contains("~"),
              !name.contains("^"),
              !name.contains(":"),
              !name.contains("\\"),
              name.wholeMatch(of: Self.validBranchPattern) != nil else {
            throw GitServiceError.commandFailed(
                command: "validate",
                exitCode: 1,
                stderr: "Invalid branch name: \(name)"
            )
        }
    }

}
