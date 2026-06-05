package studio.vibe.shared.service.git.executor

import studio.vibe.shared.contract.AheadBehind
import studio.vibe.shared.contract.GitStatusQuerying
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitCommitInfo
import studio.vibe.shared.model.GitDiffHunk
import studio.vibe.shared.model.GitDiffStat
import studio.vibe.shared.model.GitServiceError
import studio.vibe.shared.model.GitStatus
import studio.vibe.shared.service.git.parser.GitOutputParser
import kotlin.time.Instant

/** Implements [GitStatusQuerying] — status, diff, log, ahead/behind. */
internal class GitStatusExecutor(private val runner: GitProcessRunner) : GitStatusQuerying {

    override suspend fun status(at: FilePath): GitStatus {
        val output = runner.runGit(listOf("status", "--porcelain=v1", "--branch"), at)
        return GitOutputParser.parseStatus(output)
    }

    override suspend fun diff(file: String, staged: Boolean, at: FilePath): List<GitDiffHunk> {
        val args = buildList {
            add("diff")
            if (staged) add("--cached")
            add("--")
            add(file)
        }
        val output = runner.runGit(args, at)
        return GitOutputParser.parseDiff(output)
    }

    override suspend fun fullStagedDiff(at: FilePath): String =
        runner.runGit(listOf("diff", "--staged"), at)

    override suspend fun headDiff(at: FilePath): String {
        return try {
            runner.runGit(listOf("diff", "HEAD"), at)
        } catch (e: GitServiceError.CommandFailed) {
            // No commits yet — return staged diff only.
            runner.runGit(listOf("diff", "--staged"), at)
        }
    }

    override suspend fun diffStats(at: FilePath): Map<String, GitDiffStat> {
        val result = mutableMapOf<String, Pair<Int, Int>>()

        fun merge(output: String) {
            for ((path, stat) in GitOutputParser.parseNumstat(output)) {
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
            merge(runner.runGit(listOf("diff", "--numstat"), at))
        } catch (_: Exception) { /* Ignore — repo may have no unstaged changes */ }

        // Staged diff (index vs HEAD, with fallback for empty repos)
        val staged = try {
            runner.runGit(listOf("diff", "--cached", "--numstat"), at)
        } catch (_: Exception) {
            try {
                runner.runGit(listOf("diff", "--numstat"), at)
            } catch (_: Exception) {
                ""
            }
        }
        merge(staged)

        return result.mapValues { (_, v) -> GitDiffStat(added = v.first, deleted = v.second) }
    }

    override suspend fun log(limit: Int, at: FilePath): List<GitCommitInfo> {
        val output = runner.runGit(
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

    override suspend fun aheadBehind(at: FilePath): AheadBehind {
        return try {
            val output = runner.runGit(
                listOf("rev-list", "--left-right", "--count", "HEAD...@{upstream}"),
                at,
            )
            val parts = output.trim().split("\t")
            if (parts.size == 2) {
                val ahead = parts[0].trim().toIntOrNull() ?: 0
                val behind = parts[1].trim().toIntOrNull() ?: 0
                AheadBehind(ahead, behind)
            } else {
                AheadBehind(0, 0)
            }
        } catch (_: Exception) {
            AheadBehind(0, 0)
        }
    }

    /**
     * Parse an ISO 8601 date string into [Instant].
     * Accepts the `%aI` git log format.
     */
    private fun parseIso8601(value: String): Instant? {
        val s = value.trim()
        if (s.isEmpty()) return null
        return try {
            Instant.parse(s)
        } catch (_: Exception) {
            null
        }
    }
}
