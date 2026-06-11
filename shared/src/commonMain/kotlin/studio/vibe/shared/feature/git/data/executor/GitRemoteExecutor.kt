package studio.vibe.shared.feature.git.data.executor

import studio.vibe.shared.feature.git.domain.contract.GitRemoteOperating
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import studio.vibe.shared.feature.git.data.parser.GitBranchParser

/** Implements [GitRemoteOperating] — push, pull, fetch, remote management. */
internal class GitRemoteExecutor(private val runner: GitProcessRunner) : GitRemoteOperating {

    override suspend fun push(remote: String, at: FilePath) {
        GitBranchParser.validateBranchName(remote)
        try {
            runner.runGit(listOf("push", remote), at, timeout = runner.networkTimeout)
        } catch (e: GitServiceError.CommandFailed) {
            if (e.stderr.contains("rejected")) throw GitServiceError.PushRejected(reason = e.stderr)
            throw e
        }
    }

    override suspend fun pull(remote: String, at: FilePath) {
        GitBranchParser.validateBranchName(remote)
        try {
            runner.runGit(listOf("pull", remote), at, timeout = runner.networkTimeout)
        } catch (e: GitServiceError.CommandFailed) {
            if (e.stderr.contains("CONFLICT")) {
                val files = e.stderr.split("\n").filter { it.contains("CONFLICT") }
                throw GitServiceError.MergeConflict(files = files)
            }
            throw e
        }
    }

    override suspend fun fetch(remote: String, at: FilePath) {
        GitBranchParser.validateBranchName(remote)
        runner.runGit(listOf("fetch", remote), at, timeout = runner.networkTimeout)
    }

    override suspend fun pushBranch(branch: String, remote: String, at: FilePath) {
        GitBranchParser.validateBranchName(branch)
        GitBranchParser.validateBranchName(remote)
        try {
            runner.runGit(
                listOf("push", "--set-upstream", remote, branch),
                at,
                timeout = runner.networkTimeout,
                suppressCredentials = false,
            )
        } catch (e: GitServiceError.CommandFailed) {
            if (e.stderr.contains("rejected")) throw GitServiceError.PushRejected(reason = e.stderr)
            throw e
        }
    }

    override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) {
        GitBranchParser.validateBranchName(branch)
        GitBranchParser.validateBranchName(remote)
        if (isCurrent) {
            try {
                runner.runGit(
                    listOf("pull", remote),
                    at,
                    timeout = runner.networkTimeout,
                    suppressCredentials = false,
                )
            } catch (e: GitServiceError.CommandFailed) {
                if (e.stderr.contains("CONFLICT")) {
                    val files = e.stderr.split("\n").filter { it.contains("CONFLICT") }
                    throw GitServiceError.MergeConflict(files = files)
                }
                throw e
            }
        } else {
            runner.runGit(
                listOf("fetch", remote, "$branch:$branch"),
                at,
                timeout = runner.networkTimeout,
                suppressCredentials = false,
            )
        }
    }

    override suspend fun addRemote(name: String, url: String, at: FilePath) {
        if (name.isEmpty() || name.startsWith("-") || name.contains(" ") || name.contains("..")) {
            throw GitServiceError.CommandFailed(
                command = "remote",
                exitCode = 1,
                stderr = "Invalid remote name: $name",
            )
        }
        if (url.isEmpty() || url.startsWith("-")) {
            throw GitServiceError.CommandFailed(
                command = "remote",
                exitCode = 1,
                stderr = "Invalid remote URL",
            )
        }
        // Prevent dangerous git transport protocols (ext:: allows arbitrary command execution).
        val forbiddenPrefixes = listOf("ext::", "fd::")
        if (forbiddenPrefixes.any { url.lowercase().startsWith(it) }) {
            throw GitServiceError.CommandFailed(
                command = "remote",
                exitCode = 1,
                stderr = "Unsupported remote URL scheme: only https://, http://, git://, ssh://, git@, and local paths are allowed",
            )
        }
        runner.runGit(listOf("remote", "add", name, url), at)
    }

    override suspend fun remoteURL(name: String, at: FilePath): String? {
        return try {
            val output = runner.runGit(listOf("remote", "get-url", name), at)
            output.trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun defaultRemote(forBranch: String?, at: FilePath): String {
        // 1. Branch-specific remote from git config.
        if (forBranch != null) {
            try {
                val r = runner.runGit(listOf("config", "--get", "branch.$forBranch.remote"), at)
                val trimmed = r.trim()
                if (trimmed.isNotEmpty()) return trimmed
            } catch (_: Exception) { /* Fall through */ }
        }
        // 2. First remote listed in the repo.
        try {
            val remotes = runner.runGit(listOf("remote"), at)
            val first = remotes.split("\n").firstOrNull { it.trim().isNotEmpty() }
            if (first != null) return first.trim()
        } catch (_: Exception) { /* Fall through */ }
        return "origin"
    }
}
