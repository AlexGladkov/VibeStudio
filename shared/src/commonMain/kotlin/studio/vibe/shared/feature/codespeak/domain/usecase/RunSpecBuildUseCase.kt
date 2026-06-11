package studio.vibe.shared.feature.codespeak.domain.usecase

import kotlinx.coroutines.flow.Flow
import studio.vibe.shared.core.common.UseCase
import studio.vibe.shared.core.common.ProcessRunner

/**
 * Use case that streams output lines from a codespeak build command.
 *
 * Isolates [ProcessRunner] from [SpecBuildPanelViewModel] so the ViewModel
 * depends only on the use case contract, not on the infrastructure directly.
 *
 * Returns [Result.success] wrapping a [Flow] of output lines. The flow itself
 * may emit errors; callers should wrap collection in [runCatching].
 */
class RunSpecBuildUseCase(
    private val processRunner: ProcessRunner,
) : UseCase<RunSpecBuildParams, Flow<String>> {

    override suspend fun invoke(params: RunSpecBuildParams): Result<Flow<String>> = runCatching {
        processRunner.stream(
            command = params.command,
            workDir = params.workDir,
        )
    }
}
