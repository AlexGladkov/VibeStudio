package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.remote.TlsCertificateManager
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing

/**
 * Remote Control settings pane.
 *
 * Wired to the live [RemoteControlServer] from [DesktopServiceContainer].
 * Provides: enable toggle, port, localhost binding, Bonjour, ngrok tunnel,
 * ngrok authtoken, PIN display/regenerate, TLS fingerprint, server status,
 * and connected devices list.
 *
 * Mirrors Swift RemoteControlSettingsPane.swift behavior exactly.
 */
@Composable
fun RemoteControlSettingsPane(
    container: DesktopServiceContainer,
    modifier: Modifier = Modifier,
) {
    val prefs = container.remoteControlPreferences
    val server = container.remoteControlServer
    val scope = rememberCoroutineScope()

    // Observe live server state via StateFlow.
    val isRunning by server.isRunning.collectAsState()
    val isNgrokRunning by server.ngrok.isRunning.collectAsState()
    val ngrokTunnelURL by server.ngrok.tunnelURL.collectAsState()
    val ngrokError by server.ngrok.error.collectAsState()
    val connectedDevices by server.authService.connectedDevices.collectAsState()
    val currentPin by server.authService.currentPin.collectAsState()

    // Local UI state.
    var enabled by remember { mutableStateOf(prefs.remoteControlEnabled) }
    var port by remember { mutableStateOf(prefs.remoteControlPort.toString()) }
    var bindLocalhost by remember { mutableStateOf(prefs.bindToLocalhost) }
    var bonjourEnabled by remember { mutableStateOf(prefs.bonjourEnabled) }
    var ngrokToken by remember { mutableStateOf("") }
    var ngrokTokenVisible by remember { mutableStateOf(false) }
    var tlsFingerprint by remember { mutableStateOf<String?>(null) }

    // Load ngrok token and TLS fingerprint from secure/disk storage.
    LaunchedEffect(Unit) {
        ngrokToken = prefs.loadNgrokAuthtoken()
        tlsFingerprint = TlsCertificateManager.certificateFingerprint()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = "Remote Control")

        // ── Server toggle ──────────────────────────────────────────────────

        SettingsCard {
            Column {
                // Enable / Disable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Remote Control",
                            style = DSFont.sidebarItem,
                            color = DSColor.textPrimary,
                        )
                        Text(
                            text = "Start the HTTP/WS server for remote device access",
                            style = DSFont.sidebarItemSmall,
                            color = DSColor.textMuted,
                        )
                    }

                    Spacer(Modifier.width(DSSpacing.md))

                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            prefs.remoteControlEnabled = value
                            if (value) {
                                server.start()
                            } else {
                                server.stop()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DSColor.accentPrimary,
                            uncheckedThumbColor = DSColor.textMuted,
                            uncheckedTrackColor = DSColor.surfaceOverlay,
                        ),
                    )
                }

                SettingsListDivider()

                // Port
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Port",
                        style = DSFont.sidebarItem,
                        color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                        modifier = Modifier.width(140.dp),
                    )

                    BasicTextField(
                        value = port,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                                port = newValue
                                newValue.toIntOrNull()?.let { parsed ->
                                    if (parsed in 1024..65535) {
                                        val old = prefs.remoteControlPort
                                        prefs.remoteControlPort = parsed
                                        // Restart server if running and port changed.
                                        if (isRunning && parsed != old) {
                                            server.stop()
                                            server.start()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = enabled,
                        textStyle = DSFont.monoPath.copy(
                            color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                        ),
                        cursorBrush = SolidColor(DSColor.accentPrimary),
                        modifier = Modifier.width(70.dp),
                        singleLine = true,
                    )

                    Spacer(Modifier.width(DSSpacing.sm))

                    Text(
                        text = "(1024–65535)",
                        style = DSFont.sidebarItemSmall,
                        color = DSColor.textMuted,
                    )
                }

                SettingsListDivider()

                // Bind to localhost
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bind to localhost",
                            style = DSFont.sidebarItem,
                            color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                        )
                        Text(
                            text = "Local connections only (disables LAN access)",
                            style = DSFont.sidebarItemSmall,
                            color = DSColor.textMuted,
                        )
                    }

                    Switch(
                        checked = bindLocalhost,
                        onCheckedChange = { value ->
                            bindLocalhost = value
                            prefs.bindToLocalhost = value
                        },
                        enabled = enabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DSColor.accentPrimary,
                            uncheckedThumbColor = DSColor.textMuted,
                            uncheckedTrackColor = DSColor.surfaceOverlay,
                        ),
                    )
                }

                SettingsListDivider()

                // Bonjour
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bonjour",
                            style = DSFont.sidebarItem,
                            color = if (enabled && !bindLocalhost) DSColor.textPrimary else DSColor.textDisabled,
                        )
                        Text(
                            text = "Advertise server on local network (macOS only)",
                            style = DSFont.sidebarItemSmall,
                            color = DSColor.textMuted,
                        )
                    }

                    Switch(
                        checked = bonjourEnabled,
                        onCheckedChange = { value ->
                            bonjourEnabled = value
                            prefs.bonjourEnabled = value
                        },
                        enabled = enabled && !bindLocalhost,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DSColor.accentPrimary,
                            uncheckedThumbColor = DSColor.textMuted,
                            uncheckedTrackColor = DSColor.surfaceOverlay,
                        ),
                    )
                }
            }
        }

        // ── ngrok tunnel ───────────────────────────────────────────────────

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "ngrok Tunnel",
                style = DSFont.buttonLabel,
                color = DSColor.textSecondary,
            )

            SettingsCard {
                Column {
                    // ngrok status row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
                    ) {
                        Text(
                            text = "Tunnel",
                            style = DSFont.sidebarItem,
                            color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                            modifier = Modifier.width(100.dp),
                        )

                        when {
                            isNgrokRunning && ngrokTunnelURL != null -> {
                                // Connected — show URL + disconnect button.
                                Text(
                                    text = ngrokTunnelURL!!,
                                    style = DSFont.monoSmall,
                                    color = DSColor.accentPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                TextButton(
                                    onClick = {
                                        prefs.ngrokEnabled = false
                                        server.stopNgrok()
                                    },
                                ) {
                                    Text(
                                        text = "Disconnect",
                                        style = DSFont.sidebarItemSmall,
                                        color = DSColor.indicatorError,
                                    )
                                }
                            }

                            isNgrokRunning -> {
                                // Starting — show spinner.
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                    color = DSColor.accentPrimary,
                                )
                                Text(
                                    text = "Connecting...",
                                    style = DSFont.sidebarItemSmall,
                                    color = DSColor.textMuted,
                                )
                            }

                            ngrokToken.isNotBlank() && enabled -> {
                                // Has token, ready to connect.
                                TextButton(
                                    onClick = {
                                        prefs.ngrokEnabled = true
                                        server.startNgrok()
                                    },
                                ) {
                                    Text(
                                        text = "Connect",
                                        style = DSFont.sidebarItem,
                                        color = DSColor.accentPrimary,
                                    )
                                }
                                if (ngrokError != null) {
                                    Text(
                                        text = ngrokError!!,
                                        style = DSFont.sidebarItemSmall,
                                        color = DSColor.indicatorError,
                                        maxLines = 2,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Text(
                                        text = "Access outside Wi-Fi",
                                        style = DSFont.sidebarItemSmall,
                                        color = DSColor.textMuted,
                                    )
                                }
                            }

                            else -> {
                                Text(
                                    text = "Access outside Wi-Fi",
                                    style = DSFont.sidebarItemSmall,
                                    color = DSColor.textMuted,
                                )
                            }
                        }
                    }

                    SettingsListDivider()

                    // Authtoken field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Authtoken",
                            style = DSFont.sidebarItem,
                            color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                            modifier = Modifier.width(100.dp),
                        )

                        BasicTextField(
                            value = ngrokToken,
                            onValueChange = { newValue ->
                                ngrokToken = newValue
                                scope.launch {
                                    prefs.saveNgrokAuthtoken(newValue)
                                }
                            },
                            enabled = enabled && !isNgrokRunning,
                            textStyle = DSFont.monoPath.copy(
                                color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                            ),
                            visualTransformation = if (ngrokTokenVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            cursorBrush = SolidColor(DSColor.accentPrimary),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )

                        TextButton(onClick = { ngrokTokenVisible = !ngrokTokenVisible }) {
                            Text(
                                text = if (ngrokTokenVisible) "Hide" else "Show",
                                style = DSFont.sidebarItemSmall,
                                color = DSColor.accentPrimary,
                            )
                        }
                    }

                    // Setup hint when no token and server enabled.
                    if (ngrokToken.isBlank() && enabled) {
                        SettingsListDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                        ) {
                            Text(
                                text = "Setup (one-time):",
                                style = DSFont.sidebarItemSmall,
                                color = DSColor.textSecondary,
                            )
                            Text(text = "1. brew install ngrok", style = DSFont.monoSmall, color = DSColor.textMuted)
                            Text(text = "2. Sign up at ngrok.com → copy authtoken above", style = DSFont.monoSmall, color = DSColor.textMuted)
                            Text(text = "3. Enable Remote Control + Connect", style = DSFont.monoSmall, color = DSColor.textMuted)
                        }
                    }
                }
            }
        }

        // ── PIN ────────────────────────────────────────────────────────────

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Authentication",
                style = DSFont.buttonLabel,
                color = DSColor.textSecondary,
            )

            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
                    ) {
                        Text(
                            text = "PIN",
                            style = DSFont.sidebarItem,
                            color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                            modifier = Modifier.width(100.dp),
                        )

                        Text(
                            text = currentPin,
                            style = DSFont.sidebarItem.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            ),
                            color = if (enabled) DSColor.textPrimary else DSColor.textDisabled,
                        )

                        Spacer(Modifier.weight(1f))

                        TextButton(
                            onClick = { server.regeneratePin() },
                            enabled = enabled,
                        ) {
                            Text(
                                text = "Regenerate",
                                style = DSFont.sidebarItemSmall,
                                color = if (enabled) DSColor.accentPrimary else DSColor.textDisabled,
                            )
                        }
                    }

                    // TLS fingerprint
                    if (tlsFingerprint != null) {
                        SettingsListDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "TLS",
                                style = DSFont.sidebarItem,
                                color = DSColor.textPrimary,
                                modifier = Modifier.width(100.dp),
                            )
                            Text(
                                text = tlsFingerprint!!,
                                style = DSFont.monoSmall,
                                color = DSColor.textMuted,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // ── Server status ──────────────────────────────────────────────────

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Text(
                text = "Status",
                style = DSFont.buttonLabel,
                color = DSColor.textSecondary,
            )

            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isRunning) DSColor.indicatorRunning else DSColor.actionStop,
                                shape = CircleShape,
                            ),
                    )

                    Spacer(Modifier.width(DSSpacing.sm))

                    Text(
                        text = if (isRunning) "Active (port ${prefs.remoteControlPort})" else "Stopped",
                        style = DSFont.sidebarItem,
                        color = DSColor.textSecondary,
                    )
                }

                // Connected devices
                if (connectedDevices.isNotEmpty()) {
                    SettingsListDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                    ) {
                        Text(
                            text = "Connected Devices",
                            style = DSFont.sidebarItemSmall,
                            color = DSColor.textSecondary,
                        )
                        connectedDevices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
                            ) {
                                Text(
                                    text = device.displayName,
                                    style = DSFont.sidebarItem,
                                    color = DSColor.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = device.ipAddress,
                                    style = DSFont.sidebarItemSmall,
                                    color = DSColor.textMuted,
                                )
                                TextButton(
                                    onClick = { server.disconnect(device.id) },
                                ) {
                                    Text(
                                        text = "Kick",
                                        style = DSFont.sidebarItemSmall,
                                        color = DSColor.indicatorError,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(DSSpacing.xl))
    }
}
