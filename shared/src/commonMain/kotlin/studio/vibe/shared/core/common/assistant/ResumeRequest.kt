package studio.vibe.shared.core.common.assistant

/**
 * Encapsulates the information required to resume an existing native agent session.
 */
data class ResumeRequest(val nativeSessionId: String)
