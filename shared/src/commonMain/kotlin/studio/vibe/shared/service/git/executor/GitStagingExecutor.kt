package studio.vibe.shared.service.git.executor

import studio.vibe.shared.contract.GitStaging
import studio.vibe.shared.model.FilePath

/** Implements [GitStaging] — stage and unstage files. */
internal class GitStagingExecutor(private val runner: GitProcessRunner) : GitStaging {

    override suspend fun stage(files: List<String>, at: FilePath) {
        if (files.isEmpty()) {
            runner.runGit(listOf("add", "-A"), at)
        } else {
            val args = buildList {
                add("add")
                add("--")
                addAll(files)
            }
            runner.runGit(args, at)
        }
    }

    override suspend fun unstage(files: List<String>, at: FilePath) {
        if (files.isEmpty()) {
            runner.runGit(listOf("restore", "--staged", "."), at)
        } else {
            val args = buildList {
                add("restore")
                add("--staged")
                add("--")
                addAll(files)
            }
            runner.runGit(args, at)
        }
    }
}
