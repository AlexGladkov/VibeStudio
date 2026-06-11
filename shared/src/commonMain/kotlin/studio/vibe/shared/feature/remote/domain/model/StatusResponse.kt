package studio.vibe.shared.feature.remote.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.vibe.shared.core.common.terminal.TerminalColorsResponse

@Serializable
data class StatusResponse(
    val server: ServerInfoResponse,
    val connections: ConnectionsInfoResponse,
    val theme: ThemeInfoResponse,
) {
    @Serializable
    data class ServerInfoResponse(
        val version: String,
        @SerialName("api_version") val apiVersion: String,
        @SerialName("uptime_seconds") val uptimeSeconds: Int,
        val port: Int,
        val tls: String,
        @SerialName("bonjour_published") val bonjourPublished: Boolean,
    )

    @Serializable
    data class ConnectionsInfoResponse(
        @SerialName("connected_devices") val connectedDevices: Int,
        @SerialName("max_devices") val maxDevices: Int,
        @SerialName("active_websockets") val activeWebsockets: Int,
    )

    @Serializable
    data class ThemeInfoResponse(
        val appearance: String,
        @SerialName("terminal_colors") val terminalColors: TerminalColorsResponse,
    )
}
