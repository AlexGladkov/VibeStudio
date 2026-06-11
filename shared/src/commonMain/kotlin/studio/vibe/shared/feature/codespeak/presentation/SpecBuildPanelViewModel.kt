package studio.vibe.shared.feature.codespeak.presentation

import kotlinx.coroutines.CoroutineScope
import studio.vibe.shared.core.common.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.vibe.shared.core.common.PersistenceStore
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.feature.codespeak.domain.usecase.RunSpecBuildParams
import studio.vibe.shared.feature.codespeak.domain.usecase.RunSpecBuildUseCase
import studio.vibe.shared.core.util.AtomicInt
import studio.vibe.shared.feature.codespeak.domain.model.CodeSpeakCommand
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.feature.codespeak.domain.model.SpecFile
import studio.vibe.shared.feature.codespeak.domain.model.SpecStats
import studio.vibe.shared.feature.codespeak.presentation.SpecBuildPanelState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
class SpecBuildPanelViewModel(
    private val runSpecBuildUseCase: RunSpecBuildUseCase,
    private val projectManaging: ProjectManaging,
    parentScope: CoroutineScope,
    /**
     * Clock used to stamp [SpecStats.buildDate].
     * Default: [Clock.System] for production. Override with a fake in tests.
     */
    private val clock: Clock = Clock.System,
) : BaseViewModel(parentScope) {

    private val _state = MutableStateFlow(SpecBuildPanelState())
    val state: StateFlow<SpecBuildPanelState> = _state.asStateFlow()

    private var runJob: Job? = null

    // Generation counter — incremented on every runCommand() call.
    // Each launched coroutine captures its own generation snapshot and bails out
    // if a newer run has started by the time a process output line is processed.
    // This matches the Swift CodeSpeakProcessRunner actor + generation guard pattern.
    // AtomicInt ensures visibility across threads without a full mutex on hot path.
    private val generation = AtomicInt(0)

    // Tracks the project this panel currently belongs to. Switching tabs
    // must wipe the per-project transient state (output buffer, task name,
    // commit message, stats) so the panel doesn't leak between projects.
    private var currentProjectId: Uuid? = null

    /**
     * Resets all per-project transient state when [projectId] differs from the
     * previously tracked project. Cancels any in-flight build job too —
     * letting it keep running against the old project would be misleading.
     */
    fun resetForProject(projectId: Uuid?) {
        if (currentProjectId == projectId) return
        runJob?.cancel()
        runJob = null
        generation.incrementAndGet()
        _state.value = SpecBuildPanelState()
        currentProjectId = projectId
    }

    fun selectCommand(command: CodeSpeakCommand) {
        _state.update { it.copy(selectedCommand = command) }
    }

    fun updateTaskName(name: String) {
        _state.update { it.copy(taskName = name) }
    }

    fun updateChangeMessage(message: String) {
        _state.update { it.copy(changeMessage = message) }
    }

    fun runCommand(projectId: Uuid, specFile: SpecFile? = null) {
        val project = projectManaging.project(projectId) ?: return
        val command = _state.value.selectedCommand

        val cliArgs = command.cliArguments(
            specPath = specFile?.path?.path,
            taskName = _state.value.taskName,
            changeMessage = _state.value.changeMessage,
        )
        val fullCommand = listOf("codespeak") + cliArgs

        _state.update { it.copy(isRunning = true, output = "", errorMessage = null, stopRequested = false) }

        // Cancel any previous run before starting a new one (stop+start guard).
        runJob?.cancel()
        val myGeneration = generation.incrementAndGet()
        runJob = scope.launch {
            val buildResult = runSpecBuildUseCase(RunSpecBuildParams(fullCommand, project.path))
            buildResult.fold(
                onSuccess = { outputFlow ->
                    runCatching {
                        outputFlow.collect { line ->
                            // Guard: discard output from a superseded run.
                            if (generation.value != myGeneration) return@collect
                            appendOutput(line)
                            if (command.supportsStatsParsing) {
                                tryParseStats(line)
                            }
                        }
                    }.onFailure { e ->
                        if (generation.value != myGeneration) return@onFailure
                        _state.update { s ->
                            s.copy(errorMessage = e.message, output = s.output + "\nError: ${e.message}")
                        }
                    }
                },
                onFailure = { e ->
                    if (generation.value == myGeneration) {
                        _state.update { s ->
                            s.copy(errorMessage = e.message, output = s.output + "\nError: ${e.message}")
                        }
                    }
                },
            )
            if (generation.value == myGeneration) {
                _state.update { it.copy(isRunning = false, stopRequested = false) }
            }
        }
    }

    fun stopCommand() {
        _state.update { it.copy(stopRequested = true) }
        generation.incrementAndGet() // invalidate any in-flight collect callbacks
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false, stopRequested = false) }
    }

    fun clearOutput() {
        _state.update { it.copy(output = "", errorMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun dispose() {
        runJob?.cancel()
        runJob = null
        super.dispose()
    }

    private fun appendOutput(line: String) {
        _state.update { s ->
            val newOutput = if (s.output.isEmpty()) line else "${s.output}\n$line"
            s.copy(output = newOutput)
        }
    }

    private fun tryParseStats(line: String) {
        // Parse patterns like "Passed: 5/8", "5 passing", "5 passed, 3 failed"
        val passingPattern = Regex("""(\d+)\s+passing""", RegexOption.IGNORE_CASE)
        val failingPattern = Regex("""(\d+)\s+failing""", RegexOption.IGNORE_CASE)
        val ratioPattern = Regex("""[Pp]assed:\s*(\d+)/(\d+)""")

        ratioPattern.find(line)?.let { match ->
            val passing = match.groupValues[1].toIntOrNull() ?: return
            val total = match.groupValues[2].toIntOrNull() ?: return
            _state.update { s ->
                s.copy(
                    lastStats = SpecStats(
                        passing = passing,
                        total = total,
                        buildDate = clock.now(),
                    )
                )
            }
            return
        }

        val passing = passingPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val failing = failingPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (passing != null) {
            val total = passing + (failing ?: 0)
            _state.update { s ->
                s.copy(
                    lastStats = SpecStats(
                        passing = passing,
                        total = total,
                        buildDate = clock.now(),
                    )
                )
            }
        }
    }
}
