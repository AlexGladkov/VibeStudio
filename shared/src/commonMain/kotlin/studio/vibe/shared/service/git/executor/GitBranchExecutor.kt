package studio.vibe.shared.service.git.executor

import studio.vibe.shared.contract.GitBranching
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitBranch
import studio.vibe.shared.service.git.parser.GitBranchParser

/** Implements [GitBranching] — list, checkout, create, delete branches. */
internal class GitBranchExecutor(private val runner: GitProcessRunner) : GitBranching {

    override suspend fun branches(at: FilePath): List<GitBranch> {
        // Step 1: local branches.
        val localOutput = runner.runGit(
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
            val remoteOutput = runner.runGit(
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
        GitBranchParser.validateBranchName(branch)
        runner.runGit(listOf("switch", branch), at)
    }

    override suspend fun createBranch(name: String, from: String?, at: FilePath) {
        GitBranchParser.validateBranchName(name)
        val args = buildList {
            add("switch")
            add("-c")
            add(name)
            if (from != null) {
                GitBranchParser.validateBranchName(from)
                add(from)
            }
        }
        runner.runGit(args, at)
    }

    override suspend fun deleteBranch(name: String, force: Boolean, at: FilePath) {
        GitBranchParser.validateBranchName(name)
        // -d = safe delete (refuses if unmerged); -D = force delete.
        val flag = if (force) "-D" else "-d"
        runner.runGit(listOf("branch", flag, name), at)
    }
}
