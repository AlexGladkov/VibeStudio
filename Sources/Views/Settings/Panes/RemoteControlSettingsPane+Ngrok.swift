// MARK: - RemoteControlSettingsPane+Ngrok
// ngrok tunnel, PIN and TLS sections for the Remote Control settings pane.
// Split out of RemoteControlSettingsPane.swift to keep the type body under
// the SwiftLint type_body_length limit.
// macOS 14+, Swift 5.10

import SwiftUI

extension RemoteControlSettingsPane {

    // MARK: - ngrok tunnel

    func ngrokTunnelSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("ngrok")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            ngrokTunnelStatus(model: model)

            Spacer()
        }
    }

    @ViewBuilder
    func ngrokTunnelStatus(model: RemoteControlSettingsPaneViewModel) -> some View {
        if model.isNgrokRunning, let url = model.ngrokTunnelURL {
            // Connected — show URL + disconnect button
            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text(url)
                    .font(DSFont.monoSmall)
                    .foregroundStyle(DSColor.accentPrimary)
                    .textSelection(.enabled)
                    .lineLimit(1)
                // SEC-H2: tell the user that the public tunnel only
                // accepts secure WebSocket connections, so client
                // apps connecting manually must use wss:// — server
                // also rejects non-loopback WS upgrades arriving
                // without an HTTPS hop in front.
                HStack(spacing: DSSpacing.xxs) {
                    Image(systemName: "lock.fill")
                        .font(DSFont.iconXS)
                    Text("Connect via wss:// (https:// for web client)")
                }
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
            }

            Button("Disconnect") {
                model.disconnectNgrok()
            }
            .font(DSFont.smallButtonLabel)
            .foregroundStyle(DSColor.indicatorError)

        } else if model.isNgrokRunning {
            // Starting...
            ProgressView()
                .controlSize(.small)
            Text("Connecting...")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

        } else if !model.ngrokAuthtoken.isEmpty && model.isEnabled {
            // Has token, ready to connect
            Button("Connect") {
                model.connectNgrok()
            }
            .font(DSFont.smallButtonLabel)

            if let error = model.ngrokError {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.indicatorError)
                    .lineLimit(2)
            } else {
                Text("Access outside Wi-Fi")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }

        } else {
            // No token — show setup hint
            Text("Access outside Wi-Fi")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
        }
    }

    // MARK: - ngrok authtoken

    func ngrokAuthtokenSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("Authtoken")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            SecureField("ngrok authtoken", text: Binding(
                get: { model.ngrokAuthtoken },
                set: { model.setNgrokAuthtoken($0) }
            ))
            .textFieldStyle(.roundedBorder)
            .frame(maxWidth: 300)
            .disabled(!model.isEnabled || model.isNgrokRunning)

            Link("Get", destination: URL(string: "https://dashboard.ngrok.com/get-started/your-authtoken")!)
                .font(DSFont.sidebarItemSmall)

            Spacer()
        }
    }

    // MARK: - ngrok setup guide

    @ViewBuilder
    func ngrokSetupGuideSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        if model.ngrokAuthtoken.isEmpty && model.isEnabled {
            HStack(spacing: DSSpacing.lg) {
                Color.clear
                    .frame(width: DSLayout.settingsLabelWidth)

                VStack(alignment: .leading, spacing: DSSpacing.xs) {
                    HStack(spacing: 0) {
                        Text("1. ")
                            .foregroundStyle(DSColor.textMuted)
                        Button("brew install ngrok") {
                            ClipboardService.copy("brew install ngrok")
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(DSColor.accentPrimary)
                        .onHover { hovering in
                            if hovering {
                                NSCursor.pointingHand.push()
                            } else {
                                NSCursor.pop()
                            }
                        }
                        Text("  ⌘ copy")
                            .foregroundStyle(DSColor.textGhost)
                    }
                    HStack(spacing: 0) {
                        Text("2. ")
                            .foregroundStyle(DSColor.textMuted)
                        Link("ngrok.com", destination: URL(string: "https://dashboard.ngrok.com/signup")!)
                            .foregroundStyle(DSColor.accentPrimary)
                        Text(" → sign up (free) → copy authtoken")
                            .foregroundStyle(DSColor.textMuted)
                    }
                    HStack(spacing: 0) {
                        Text("3. Paste the token in the field above → Connect")
                            .foregroundStyle(DSColor.textMuted)
                    }
                }
                .font(DSFont.monoSmall)

                Spacer()
            }
        }
    }

    // MARK: - PIN

    func pinSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("PIN")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            Text(model.currentPin)
                .font(DSFont.pinDisplay)
                .textSelection(.enabled)

            Button("Regenerate") {
                model.regeneratePin()
            }
            .font(DSFont.smallButtonLabel)
            .disabled(!model.isEnabled)

            Spacer()
        }
    }

    // MARK: - TLS fingerprint

    func tlsSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("TLS")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            if let fingerprint = TLSCertificateManager.certificateFingerprint() {
                Text(fingerprint)
                    .font(DSFont.monoSmall)
                    .foregroundStyle(DSColor.textMuted)
                    .textSelection(.enabled)
                    .lineLimit(nil)
            } else {
                Text("Certificate not created")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }

            Spacer()
        }
    }
}
