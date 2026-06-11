package studio.vibe.shared.feature.git.domain.model

/** Result of comparing local and upstream branch commit counts. */
data class AheadBehind(val ahead: Int, val behind: Int)
