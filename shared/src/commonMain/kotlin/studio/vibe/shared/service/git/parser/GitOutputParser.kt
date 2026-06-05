package studio.vibe.shared.service.git.parser

import studio.vibe.shared.model.*

/**
 * Pure stateless functions for parsing `git` CLI output.
 *
 * Extracted from [GitCommandExecutor] to keep that class focused on
 * command orchestration. All functions are package-internal and tested
 * via [GitCommandExecutorTest].
 */
internal object GitOutputParser {

    /**
     * Parse `git status --porcelain=v1 --branch` output.
     *
     * Header line format: `## branch...origin/branch [ahead N, behind M]`
     * Status line format: `XY path` where X=index, Y=worktree
     */
    fun parseStatus(output: String): GitStatus {
        var branch = ""
        var ahead = 0
        var behind = 0
        val staged = mutableListOf<GitFile>()
        val unstaged = mutableListOf<GitFile>()
        val untracked = mutableListOf<GitFile>()

        for (line in output.split("\n")) {
            if (line.isEmpty()) continue

            if (line.startsWith("##")) {
                val branchInfo = line.drop(3)
                branch = when {
                    branchInfo.contains("...") -> branchInfo.substringBefore("...")
                    branchInfo.contains(" ") -> branchInfo.substringBefore(" ")
                    else -> branchInfo
                }

                val aheadMatch = Regex("ahead (\\d+)").find(branchInfo)
                if (aheadMatch != null) ahead = aheadMatch.groupValues[1].toIntOrNull() ?: 0

                val behindMatch = Regex("behind (\\d+)").find(branchInfo)
                if (behindMatch != null) behind = behindMatch.groupValues[1].toIntOrNull() ?: 0
                continue
            }

            if (line.length < 4) continue

            val indexChar = line[0]
            val worktreeChar = line[1]
            val filePath = line.substring(3)

            if (indexChar == '?') {
                untracked.add(GitFile(path = filePath, status = GitFileStatus.UNTRACKED))
                continue
            }

            if (indexChar != ' ' && indexChar != '?') {
                parseFileStatus(indexChar)?.let { status ->
                    staged.add(GitFile(path = filePath, status = status))
                }
            }

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
    fun parseFileStatus(char: Char): GitFileStatus? = when (char) {
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
    fun parseDiff(output: String): List<GitDiffHunk> {
        val hunks = mutableListOf<GitDiffHunk>()
        var currentHeader = ""
        var currentLines = mutableListOf<GitDiffLine>()
        var oldLine = 0
        var newLine = 0

        for (line in output.split("\n")) {
            if (line.startsWith("@@")) {
                if (currentHeader.isNotEmpty()) {
                    hunks.add(GitDiffHunk(header = currentHeader, lines = currentLines))
                }
                currentHeader = line
                currentLines = mutableListOf()

                val oldMatch = Regex("-(\\d+)").find(line)
                if (oldMatch != null) oldLine = oldMatch.groupValues[1].toIntOrNull() ?: 0

                val newMatch = Regex("\\+(\\d+)").find(line)
                if (newMatch != null) newLine = newMatch.groupValues[1].toIntOrNull() ?: 0
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
    fun parseNumstat(output: String): Map<String, GitDiffStat> {
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
}
