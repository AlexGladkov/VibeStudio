package studio.vibe.shared.feature.git.data.executor

import studio.vibe.shared.feature.git.domain.contract.GitRepositoryInspection
import studio.vibe.shared.core.common.FilePath

/** Implements [GitRepositoryInspection] — repository detection and initialization. */
internal class GitInspectExecutor(private val runner: GitProcessRunner) : GitRepositoryInspection {

    override suspend fun isRepository(at: FilePath): Boolean {
        return try {
            runner.runGit(listOf("rev-parse", "--is-inside-work-tree"), at)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun repositoryRoot(forPath: FilePath): FilePath {
        val output = runner.runGit(listOf("rev-parse", "--show-toplevel"), forPath)
        return FilePath(output.trim())
    }

    override suspend fun initRepository(at: FilePath) {
        runner.runGit(listOf("init"), at)
    }
}
