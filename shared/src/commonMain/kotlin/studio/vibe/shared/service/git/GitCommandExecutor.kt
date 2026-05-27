package studio.vibe.shared.service.git

import studio.vibe.shared.contract.GitServicing
import studio.vibe.shared.contract.ProcessRunner
import studio.vibe.shared.model.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Stateless git CLI wrapper that delegates subprocess execution to [ProcessRunner].
 *
 * All git commands are executed via argument arrays — never shell string interpolation —
 * preventing command injection. Branch names are validated before use. File paths are
 * always preceded by `--` to prevent option injection.
 *
 * @param processRunner Platform-specific process execution abstraction.
 * @param gitPath Path to the git binary (default: "git", resolved from PATH).
 */
class GitCommandExecutor(
    private val processRunner: ProcessRunner,
    private val gitPath: String = "git",
) : GitServicing {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private val defaultTimeout: Duration = 30.seconds
    private val networkTimeout: Duration = 120.seconds

    // Regex for valid git branch/remote names — prevents injection via crafted names.
    private val validBranchRegex = Regex("^[a-zA-Z0-9/_\\-.@]+$")

    // -------------------------------------------------------------------------
    // GitStatusQuerying
    // -------------------------------------------------------------------------

    override suspend fun status(at: FilePath): GitStatus {
        val output = runGit(listOf("status", "--porcelain=v1", "--branch"), at)
        return parseStatus(output)
    }

    override suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk> {
        val args = buildList {
            add("diff")
            if (staged) add("--cached")
            add("--")
            add(file)
        }
        val output = runGit(args, at)
        return parseDiff(output)
    }

    override suspend fun fullStagedDiff(at: FilePath): String =
        runGit(listOf("diff", "--staged"), at)

    override suspend fun headDiff(at: FilePath): String {
        return try {
            runGit(listOf("diff", "HEAD"), at)
        } catch (e: GitServiceError.CommandFailed) {
            // No commits yet — return staged diff only.
            runGit(listOf("diff", "--staged"), at)
        }
    }

    override suspend fun diffStats(at: FilePath): Map<String, GitDiffStat> {
        val result = mutableMapOf<String, Pair<Int, Int>>()

        fun merge(output: String) {
            for ((path, stat) in parseNumstat(output)) {
                val existing = result[path]
                result[path] = if (existing != null) {
                    Pair(existing.first + stat.added, existing.second + stat.deleted)
                } else {
                    Pair(stat.added, stat.deleted)
                }
            }
        }

        // Unstaged diff (vs index)
        try {
            merge(runGit(listOf("diff", "--numstat"), at))
        } catch (_: Exception) { /* Ignore — repo may have no unstaged changes */ }

        // Staged diff (index vs HEAD, with fallback for empty repos)
        val staged = try {
            runGit(listOf("diff", "--cached", "--numstat"), at)
        } catch (_: Exception) {
            try {
                runGit(listOf("diff", "--numstat"), at)
            } catch (_: Exception) {
                ""
            }
        }
        merge(staged)

        return result.mapValues { (_, v) -> GitDiffStat(added = v.first, deleted = v.second) }
    }

    override suspend fun log(limit: Int, at: FilePath): List<GitCommitInfo> {
        val output = runGit(
            listOf("log", "--format=%H\t%h\t%s\t%an\t%aI", "-n", "$limit"),
            at,
        )
        return output.split("\n")
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size < 5) return@mapNotNull null
                val date = parseIso8601(parts[4]) ?: return@mapNotNull null
                GitCommitInfo(
                    hash = parts[0],
                    shortHash = parts[1],
                    message = parts[2],
                    author = parts[3],
                    date = date,
                )
            }
    }

    override suspend fun aheadBehind(at: FilePath): Pair<Int, Int> {
        return try {
            val output = runGit(
                listOf("rev-list", "--left-right", "--count", "HEAD...@{upstream}"),
                at,
            )
            val parts = output.trim().split("\t")
            if (parts.size == 2) {
                val ahead = parts[0].trim().toIntOrNull() ?: 0
                val behind = parts[1].trim().toIntOrNull() ?: 0
                Pair(ahead, behind)
            } else {
                Pair(0, 0)
            }
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    // -------------------------------------------------------------------------
    // GitStaging
    // -------------------------------------------------------------------------

    override suspend fun stage(files: List<String>, at: FilePath) {
        if (files.isEmpty()) {
            runGit(listOf("add", "-A"), at)
        } else {
            val args = buildList {
                add("add")
                add("--")
                addAll(files)
            }
            runGit(args, at)
        }
    }

    override suspend fun unstage(files: List<String>, at: FilePath) {
        if (files.isEmpty()) {
            runGit(listOf("restore", "--staged", "."), at)
        } else {
            val args = buildList {
                add("restore")
                add("--staged")
                add("--")
                addAll(files)
            }
            runGit(args, at)
        }
    }

    // -------------------------------------------------------------------------
    // GitCommitting
    // -------------------------------------------------------------------------

    override suspend fun commit(message: String, at: FilePath): String {
        if (message.trim().isEmpty()) {
            throw GitServiceError.CommandFailed(
                command = "commit",
                exitCode = 1,
                stderr = "Commit message cannot be empty",
            )
        }
        val output = runGit(listOf("commit", "-m", message), at)
        // Extract commit hash from output (first 7–40 hex chars).
        val match = Regex("[0-9a-f]{7,40}").find(output)
        return match?.value ?: output
    }

    // -------------------------------------------------------------------------
    // GitRemoteOperating
    // -------------------------------------------------------------------------

    override suspend fun push(remote: String, at: FilePath) {
        validateBranchName(remote)
        try {
            runGit(listOf("push", remote), at, timeout = networkTimeout)
        } catch (e: GitServiceError.CommandFailed) {
            if (e.stderr.contains("rejected")) throw GitServiceError.PushRejected(reason = e.stderr)
            throw e
        }
    }

    override suspend fun pull(remote: String, at: FilePath) {
        validateBranchName(remote)
        try {
            runGit(listOf("pull", remote), at, timeout = networkTimeout)
        } catch (e: GitServiceError.CommandFailed) {
            if (e.stderr.contains("CONFLICT")) {
                val files = e.stderr.split("\n").filter { it.contains("CONFLICT") }
                throw GitServiceError.MergeConflict(files = files)
            }
            throw e
        }
    }

    override suspend fun fetch(remote: String, at: FilePath) {
        validateBranchName(remote)
        runGit(listOf("fetch", remote), at, timeout = networkTimeout)
    }

    override suspend fun pushBranch(branch: String, remote: String, at: FilePath) {
        validateBranchName(branch)
        validateBranchName(remote)
        try {
            runGit(
                listOf("push", "--set-upstream", remote, branch),
                at,
                timeout = networkTimeout,
                suppressCredentials = false,
            )
        } catch (e: GitServiceError.CommandFailed) {
            if (e.stderr.contains("rejected")) throw GitServiceError.PushRejected(reason = e.stderr)
            throw e
        }
    }

    override suspend fun pullBranch(branch: String, isCurrent: Boolean, remote: String, at: FilePath) {
        validateBranchName(branch)
        validateBranchName(remote)
        if (isCurrent) {
            try {
                runGit(listOf("pull", remote), at, timeout = networkTimeout, suppressCredentials = false)
            } catch (e: GitServiceError.CommandFailed) {
                if (e.stderr.contains("CONFLICT")) {
                    val files = e.stderr.split("\n").filter { it.contains("CONFLICT") }
                    throw GitServiceError.MergeConflict(files = files)
                }
                throw e
            }
        } else {
            runGit(
                listOf("fetch", remote, "$branch:$branch"),
                at,
                timeout = networkTimeout,
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
        runGit(listOf("remote", "add", name, url), at)
    }

    override suspend fun remoteURL(name: String, at: FilePath): String? {
        return try {
            val output = runGit(listOf("remote", "get-url", name), at)
            output.trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun defaultRemote(forBranch: String?, at: FilePath): String {
        // 1. Branch-specific remote from git config.
        if (forBranch != null) {
            try {
                val r = runGit(listOf("config", "--get", "branch.$forBranch.remote"), at)
                val trimmed = r.trim()
                if (trimmed.isNotEmpty()) return trimmed
            } catch (_: Exception) { /* Fall through */ }
        }
        // 2. First remote listed in the repo.
        try {
            val remotes = runGit(listOf("remote"), at)
            val first = remotes.split("\n").firstOrNull { it.trim().isNotEmpty() }
            if (first != null) return first.trim()
        } catch (_: Exception) { /* Fall through */ }
        return "origin"
    }

    // -------------------------------------------------------------------------
    // GitBranching
    // -------------------------------------------------------------------------

    override suspend fun branches(at: FilePath): List<GitBranch> {
        // Step 1: local branches.
        val localOutput = runGit(
            listOf("branch", "--list", "--format=%(refname:short)\t%(HEAD)"),
            at,
        )
        val result = mutableListOf<GitBranch>()
        result += localOutput.split("\n")
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                val name = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val isCurrent = parts.size >= 2 && parts[1] == "*"
                GitBranch(name = name, isRemote = false, isCurrent = isCurrent)
            }

        // Step 2: remote tracking branches (local cache — no network call).
        try {
            val remoteOutput = runGit(
                listOf("branch", "-r", "--list", "--format=%(refname:short)"),
                at,
            )
            result += remoteOutput.split("\n")
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val name = line.trim()
                    // Skip remote HEAD aliases like origin/HEAD.
                    if (name.isEmpty() || name.endsWith("/HEAD")) return@mapNotNull null
                    GitBranch(name = name, isRemote = true, isCurrent = false)
                }
        } catch (_: Exception) {
            // Remote refs unavailable — return local branches only.
        }

        return result
    }

    override suspend fun checkout(branch: String, at: FilePath) {
        validateBranchName(branch)
        runGit(listOf("switch", branch), at)
    }

    override suspend fun createBranch(name: String, from: String?, at: FilePath) {
        validateBranchName(name)
        val args = buildList {
            add("switch")
            add("-c")
            add(name)
            if (from != null) {
                validateBranchName(from)
                add(from)
            }
        }
        runGit(args, at)
    }

    // -------------------------------------------------------------------------
    // GitRepositoryInspection
    // -------------------------------------------------------------------------

    override suspend fun isRepository(at: FilePath): Boolean {
        return try {
            runGit(listOf("rev-parse", "--is-inside-work-tree"), at)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun repositoryRoot(forPath: FilePath): FilePath {
        val output = runGit(listOf("rev-parse", "--show-toplevel"), forPath)
        return FilePath(output.trim())
    }

    override suspend fun initRepository(at: FilePath) {
        runGit(listOf("init"), at)
    }

    // -------------------------------------------------------------------------
    // Private: subprocess execution
    // -------------------------------------------------------------------------

    /**
     * Execute a git command as a subprocess via [processRunner].
     *
     * Security: never uses a shell. Arguments are passed directly to the git
     * binary, preventing shell injection. The working directory is the repository
     * root — identical to using `git -C <dir>` semantics.
     *
     * @param args Git subcommand and arguments (e.g. ["status", "--porcelain=v1"]).
     * @param dir Working directory for the command.
     * @param timeout Maximum execution time (defaults to [defaultTimeout]).
     * @param suppressCredentials When true, sets GIT_TERMINAL_PROMPT=0 and
     *   GIT_ASKPASS=/usr/bin/true to prevent interactive credential prompts.
     * @return Standard output as a string.
     * @throws GitServiceError on failure or timeout.
     */
    private suspend fun runGit(
        args: List<String>,
        dir: FilePath,
        timeout: Duration = defaultTimeout,
        suppressCredentials: Boolean = true,
    ): String {
        val command = buildList {
            add(gitPath)
            addAll(args)
        }

        val env = buildMap<String, String> {
            if (suppressCredentials) {
                put("GIT_TERMINAL_PROMPT", "0")
                put("GIT_ASKPASS", "/usr/bin/true")
            }
        }

        val commandDesc = args.firstOrNull() ?: "git"
        val result = try {
            processRunner.run(
                command = command,
                workDir = dir,
                env = env,
                timeout = timeout,
            )
        } catch (e: Exception) {
            throw GitServiceError.GitNotFound
        }

        return when {
            result.exitCode == 0 -> result.stdout
            result.stderr.contains("not a git repository") ->
                throw GitServiceError.NotARepository(path = dir)
            else -> throw GitServiceError.CommandFailed(
                command = commandDesc,
                exitCode = result.exitCode,
                stderr = result.stderr,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal: Parsing
    // -------------------------------------------------------------------------

    /**
     * Parse `git status --porcelain=v1 --branch` output.
     *
     * Header line format: `## branch...origin/branch [ahead N, behind M]`
     * Status line format: `XY path` where X=index, Y=worktree
     */
    internal fun parseStatus(output: String): GitStatus {
        var branch = ""
        var ahead = 0
        var behind = 0
        val staged = mutableListOf<GitFile>()
        val unstaged = mutableListOf<GitFile>()
        val untracked = mutableListOf<GitFile>()

        for (line in output.split("\n")) {
            if (line.isEmpty()) continue

            if (line.startsWith("##")) {
                // Branch line: ## main...origin/main [ahead 2, behind 1]
                val branchInfo = line.drop(3)
                branch = when {
                    branchInfo.contains("...") ->
                        branchInfo.substringBefore("...")
                    branchInfo.contains(" ") ->
                        branchInfo.substringBefore(" ")
                    else -> branchInfo
                }

                // Parse ahead/behind counts.
                val aheadMatch = Regex("ahead (\\d+)").find(branchInfo)
                if (aheadMatch != null) {
                    ahead = aheadMatch.groupValues[1].toIntOrNull() ?: 0
                }
                val behindMatch = Regex("behind (\\d+)").find(branchInfo)
                if (behindMatch != null) {
                    behind = behindMatch.groupValues[1].toIntOrNull() ?: 0
                }
                continue
            }

            if (line.length < 4) continue

            val indexChar = line[0]
            val worktreeChar = line[1]
            val filePath = line.substring(3)

            // Untracked files (both columns are '?').
            if (indexChar == '?') {
                untracked.add(GitFile(path = filePath, status = GitFileStatus.UNTRACKED))
                continue
            }

            // Staged changes (index column).
            if (indexChar != ' ' && indexChar != '?') {
                parseFileStatus(indexChar)?.let { status ->
                    staged.add(GitFile(path = filePath, status = status))
                }
            }

            // Unstaged changes (worktree column).
            if (worktreeChar != ' ' && worktreeChar != '?') {
                parseFileStatus(worktreeChar)?.let { status ->
                    unstaged.add(GitFile(path = filePath, status = status))
                }
            }
        }

        return GitStatus(
            branch = branch,
            aheadCount = ahead,
            behindCount = behind,
            stagedFiles = staged,
            unstagedFiles = unstaged,
            untrackedFiles = untracked,
        )
    }

    /** Map a single porcelain status character to [GitFileStatus]. */
    internal fun parseFileStatus(char: Char): GitFileStatus? = when (char) {
        'M' -> GitFileStatus.MODIFIED
        'A' -> GitFileStatus.ADDED
        'D' -> GitFileStatus.DELETED
        'R' -> GitFileStatus.RENAMED
        'C' -> GitFileStatus.COPIED
        else -> null
    }

    /**
     * Parse unified diff output into hunks.
     *
     * Hunk headers start with `@@` and contain old/new line number offsets.
     * Lines prefixed with `+` are additions, `-` deletions, ` ` context.
     */
    internal fun parseDiff(output: String): List<GitDiffHunk> {
        val hunks = mutableListOf<GitDiffHunk>()
        var currentHeader = ""
        var currentLines = mutableListOf<GitDiffLine>()
        var oldLine = 0
        var newLine = 0

        for (line in output.split("\n")) {
            if (line.startsWith("@@")) {
                // Save the previous hunk if one is in progress.
                if (currentHeader.isNotEmpty()) {
                    hunks.add(GitDiffHunk(header = currentHeader, lines = currentLines))
                }
                currentHeader = line
                currentLines = mutableListOf()

                // Parse hunk header for starting line numbers.
                val oldMatch = Regex("-(\\d+)").find(line)
                if (oldMatch != null) {
                    oldLine = oldMatch.groupValues[1].toIntOrNull() ?: 0
                }
                val newMatch = Regex("\\+(\\d+)").find(line)
                if (newMatch != null) {
                    newLine = newMatch.groupValues[1].toIntOrNull() ?: 0
                }
                continue
            }

            if (currentHeader.isEmpty()) continue

            when {
                line.startsWith("+") -> {
                    currentLines.add(
                        GitDiffLine(
                            type = DiffLineType.ADDITION,
                            content = line.drop(1),
                            oldLineNumber = null,
                            newLineNumber = newLine,
                        )
                    )
                    newLine++
                }
                line.startsWith("-") -> {
                    currentLines.add(
                        GitDiffLine(
                            type = DiffLineType.DELETION,
                            content = line.drop(1),
                            oldLineNumber = oldLine,
                            newLineNumber = null,
                        )
                    )
                    oldLine++
                }
                else -> {
                    currentLines.add(
                        GitDiffLine(
                            type = DiffLineType.CONTEXT,
                            content = if (line.startsWith(" ")) line.drop(1) else line,
                            oldLineNumber = oldLine,
                            newLineNumber = newLine,
                        )
                    )
                    oldLine++
                    newLine++
                }
            }
        }

        if (currentHeader.isNotEmpty()) {
            hunks.add(GitDiffHunk(header = currentHeader, lines = currentLines))
        }

        return hunks
    }

    /**
     * Parse `git diff --numstat` output into a path → (added, deleted) map.
     *
     * Each line: `<added>\t<deleted>\t<path>`. Binary files show `-` for counts
     * and are skipped.
     */
    internal fun parseNumstat(output: String): Map<String, GitDiffStat> {
        val dict = mutableMapOf<String, GitDiffStat>()
        for (line in output.split("\n")) {
            if (line.isEmpty()) continue
            val parts = line.split("\t", limit = 3)
            if (parts.size != 3) continue
            val added = parts[0].toIntOrNull() ?: continue
            val deleted = parts[1].toIntOrNull() ?: continue
            dict[parts[2]] = GitDiffStat(added = added, deleted = deleted)
        }
        return dict
    }

    // -------------------------------------------------------------------------
    // Internal: Validation
    // -------------------------------------------------------------------------

    /**
     * Validate that a branch or remote name does not contain dangerous characters.
     *
     * Prevents command injection via crafted names such as `--upload-pack=evil`
     * or `; rm -rf /`. Mirrors the rules enforced by git itself.
     *
     * @throws [GitServiceError.CommandFailed] if the name is invalid.
     */
    internal fun validateBranchName(name: String) {
        val valid = name.isNotEmpty() &&
            !name.startsWith("-") &&
            !name.contains("..") &&
            !name.contains(" ") &&
            !name.contains("~") &&
            !name.contains("^") &&
            !name.contains(":") &&
            !name.contains("\\") &&
            validBranchRegex.matches(name)

        if (!valid) {
            throw GitServiceError.CommandFailed(
                command = "validate",
                exitCode = 1,
                stderr = "Invalid branch name: $name",
            )
        }
    }

    // -------------------------------------------------------------------------
    // Private: ISO 8601 date parsing (commonMain — no java.time / Foundation)
    // -------------------------------------------------------------------------

    /**
     * Parse an ISO 8601 date string into [Instant].
     *
     * Accepts the `%aI` git log format, which produces strings like:
     * `2024-01-15T10:30:00+02:00` or `2024-01-15T10:30:00Z`.
     *
     * Uses [Instant.parse] from kotlin.time which supports a strict ISO 8601
     * subset (RFC 3339). Offset-qualified timestamps are normalized to UTC
     * by re-encoding as UTC before parsing.
     */
    private fun parseIso8601(value: String): Instant? {
        val s = value.trim()
        if (s.isEmpty()) return null
        return try {
            // kotlin.time.Instant.parse accepts ISO-8601 / RFC-3339 strings.
            Instant.parse(s)
        } catch (_: Exception) {
            // Try normalizing +HH:MM offset to Z by stripping and adjusting.
            // e.g. 2024-01-15T10:30:00+02:00 → parse as-is via Instant.parse
            // (already handled above, this is a fallback for malformed dates).
            null
        }
    }
}
