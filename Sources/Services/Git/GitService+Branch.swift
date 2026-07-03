// MARK: - GitService+Branch
// Branch listing/switching, repository checks, and branch-name validation.

import Foundation

extension GitService {

    /// Regex matching the subset of branch name characters we accept.
    ///
    /// A `static let` on the extension is legal (only *instance* stored
    /// properties are disallowed in extensions). Kept beside its sole
    /// consumer, ``validateBranchName(_:)``.
    ///
    /// **Scope (ARCH-L4): ASCII-only by design.** A branch name containing
    /// unicode (e.g. Cyrillic or emoji) will be rejected even though `git
    /// check-ref-format --branch <name>` would consider it valid. The
    /// trade-off favours injection-safety — every accepted character is
    /// safe to splice into a shell-free `Process.arguments` array without
    /// further escaping. If/when wider unicode is needed, the right
    /// migration is to call `git check-ref-format --branch <name>` via a
    /// strictly argument-array subprocess (no shell) and treat exit-code 0
    /// as the allow-list.
    ///
    /// Disallowed characters of note: `;`, `&`, `|`, ` `, `~`, `^`, `:`,
    /// `\`, `*`, `?`, `[`, the leading dash form `-...` (covered by the
    /// explicit prefix check in ``validateBranchName(_:)``), and any
    /// non-ASCII codepoint.
    static let validBranchPattern = /^[a-zA-Z0-9\/_\-\.@]+$/

    // MARK: - GitServicing: Branches

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

    // MARK: - Internal: Validation (visible to tests)

    /// Validate that a branch name does not contain dangerous characters.
    ///
    /// Prevents command injection via crafted branch names like
    /// `--upload-pack=evil` or `; rm -rf /`.
    ///
    /// ARCH-L4: the explicit `contains(":")` / `contains("..")` /
    /// `contains(" ")` / `contains("~")` / `contains("^")` /
    /// `contains("\\")` checks that used to live here are removed —
    /// ``validBranchPattern`` already rejects every one of those bytes
    /// (the character class only permits `[a-zA-Z0-9 / _ - . @]`). We
    /// keep:
    ///   * `isEmpty` — pattern matches one-or-more
    ///   * `hasPrefix("-")` — argv leading dash is interpreted as a flag
    ///     even when the bytes are otherwise safe
    ///   * `contains("..")` — `..` is two safe chars but the *sequence*
    ///     creates a parent-dir traversal in ref names.
    ///
    /// `internal` (not `private`): the remote command group in
    /// `GitService+Remote.swift` calls it across files.
    ///
    /// - Parameter name: Branch or remote name to validate.
    /// - Throws: ``GitServiceError.commandFailed`` if the name is invalid.
    func validateBranchName(_ name: String) throws {
        guard !name.isEmpty,
              !name.hasPrefix("-"),
              !name.contains(".."),
              name.wholeMatch(of: Self.validBranchPattern) != nil else {
            throw GitServiceError.commandFailed(
                command: "validate",
                exitCode: 1,
                stderr: "Invalid branch name: \(name)"
            )
        }
    }
}
