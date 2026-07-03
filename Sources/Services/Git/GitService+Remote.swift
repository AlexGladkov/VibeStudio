// MARK: - GitService+Remote
// Repository init, remote configuration, and network operations (push/pull/fetch).

import Foundation

extension GitService {

    // MARK: - GitServicing: Repository / Remote setup

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

    // MARK: - GitServicing: Remote (network)

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
}
