package studio.vibe.shared.contract

interface AICommitServicing {
    suspend fun generateCommitMessage(diff: String): String
}
