package studio.vibe.shared.feature.codespeak.domain.model

import kotlin.time.Instant

public data class SpecStats(
    val passing: Int,
    val total: Int,
    val buildDate: Instant,
) {
    public val allPassing: Boolean get() = total > 0 && passing == total
    public val failing: Int get() = total - passing
}
