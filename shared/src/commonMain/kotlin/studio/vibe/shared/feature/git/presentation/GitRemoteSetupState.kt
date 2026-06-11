package studio.vibe.shared.feature.git.presentation

data class GitRemoteSetupState(
    val remoteName: String = "origin",
    val remoteURL: String = "",
    val existingRemoteURL: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)
