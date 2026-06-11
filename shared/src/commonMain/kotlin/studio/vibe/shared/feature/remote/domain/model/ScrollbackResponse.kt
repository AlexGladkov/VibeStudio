package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScrollbackResponse(
    val content: String,
    @SerialName("total_lines") val totalLines: Int,
    @SerialName("returned_lines") val returnedLines: Int,
)
