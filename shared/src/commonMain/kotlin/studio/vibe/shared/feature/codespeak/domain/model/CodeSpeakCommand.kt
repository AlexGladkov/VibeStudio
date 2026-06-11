package studio.vibe.shared.feature.codespeak.domain.model

public enum class CodeSpeakCommand(public val id: String) {
    BUILD("build"),
    RUN("run"),
    IMPL("impl"),
    TEST("test"),
    TASK("task"),
    CHANGE("change");

    public val displayName: String
        get() = when (this) {
            BUILD -> "Build"
            RUN -> "Run"
            IMPL -> "Impl"
            TEST -> "Test"
            TASK -> "Task"
            CHANGE -> "Change"
        }

    public val requiresInput: Boolean
        get() = this == TASK || this == CHANGE

    public val inputLabel: String
        get() = when (this) {
            TASK -> "Task"
            CHANGE -> "Message"
            else -> ""
        }

    public val inputPlaceholder: String
        get() = when (this) {
            TASK -> "Task name..."
            CHANGE -> "Describe the change..."
            else -> ""
        }

    public val supportsStatsParsing: Boolean
        get() = this == BUILD

    public fun cliArguments(
        specPath: String? = null,
        taskName: String = "",
        changeMessage: String = "",
    ): List<String> = when (this) {
        TASK -> {
            val sanitizedTask = taskName.take(256)
            buildList {
                add("task")
                add("--no-interactive")
                add("--")
                add(sanitizedTask)
                specPath?.let { addAll(listOf("--spec", it)) }
            }
        }
        CHANGE -> {
            val sanitizedMessage = changeMessage.take(2000)
            buildList {
                add("change")
                add("--no-interactive")
                specPath?.let { add(it) }
                addAll(listOf("-m", sanitizedMessage))
            }
        }
        else -> buildList {
            add(id)
            add("--no-interactive")
            specPath?.let { add(it) }
        }
    }
}
