package studio.vibe.shared.feature.git.data.executor

import studio.vibe.shared.core.common.ProcessRunner
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.git.domain.model.GitServiceError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Shared process-execution helper for all git executor classes.
 *
 * Wraps [ProcessRunner] and maps exit codes / stderr patterns to [GitServiceError].
 * Security: commands are always passed as argument arrays — never shell strings —
 * preventing command injection.
 */
internal class GitProcessRunner(
    private val processRunner: ProcessRunner,
    private val gitPath: String,
) {
    val defaultTimeout: Duration = 30.seconds
    val networkTimeout: Duration = 120.seconds

    /**
     * Execute a git command.
     *
     * @param args Git subcommand and arguments (e.g. ["status", "--porcelain=v1"]).
     * @param dir Working directory for the command.
     * @param timeout Maximum execution time (defaults to [defaultTimeout]).
     * @param suppressCredentials When true, sets GIT_TERMINAL_PROMPT=0 and
     *   GIT_ASKPASS=/usr/bin/true to prevent interactive credential prompts.
     * @return Standard output as a string.
     * @throws GitServiceError on failure or timeout.
     */
    suspend fun runGit(
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
}
