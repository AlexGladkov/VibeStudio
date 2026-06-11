package studio.vibe.shared.feature.codespeak.presentation

data class TraceabilityPanelState(
    val entries: List<TraceabilityEntry> = emptyList(),
    val fileEntries: List<FileTraceabilityEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
