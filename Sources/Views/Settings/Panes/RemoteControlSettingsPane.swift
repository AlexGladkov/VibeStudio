// MARK: - RemoteControlSettingsPane
// Remote Control server settings pane.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - RemoteControlSettingsPane

/// Settings pane for the embedded Remote Control HTTP/WS server.
struct RemoteControlSettingsPane: View {

    @Environment(\.remoteControlServer) private var remoteServer
    @Environment(\.remoteControlPreferences) private var remotePreferences

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
            Text("Remote Control")
                .font(DSFont.settingsTitle)
                .foregroundStyle(DSColor.textPrimary)

            Divider().background(DSColor.borderDefault)

            // Enable toggle
            HStack(spacing: DSSpacing.lg) {
                Text("Включить")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Toggle("", isOn: Binding(
                    get: { remotePreferences.remoteControlEnabled },
                    set: { newValue in
                        remotePreferences.remoteControlEnabled = newValue
                        if newValue {
                            remoteServer.start()
                        } else {
                            remoteServer.stop()
                        }
                    }
                ))
                .toggleStyle(.switch)
                .labelsHidden()

                Spacer()
            }

            // Port
            HStack(spacing: DSSpacing.lg) {
                Text("Порт")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                TextField("", value: Binding(
                    get: { remotePreferences.remoteControlPort },
                    set: { newValue in
                        let old = remotePreferences.remoteControlPort
                        remotePreferences.remoteControlPort = newValue
                        if remoteServer.isRunning && newValue != old {
                            remoteServer.stop()
                            remoteServer.start()
                        }
                    }
                ), format: .number)
                .textFieldStyle(.roundedBorder)
                .frame(width: DSLayout.portInputWidth)
                .disabled(!remotePreferences.remoteControlEnabled)

                Text("(1024-65535)")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)

                Spacer()
            }

            // Bind to localhost
            HStack(spacing: DSSpacing.lg) {
                Text("Localhost")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Toggle("", isOn: Binding(
                    get: { remotePreferences.bindToLocalhost },
                    set: { remotePreferences.bindToLocalhost = $0 }
                ))
                .toggleStyle(.switch)
                .labelsHidden()
                .disabled(!remotePreferences.remoteControlEnabled)

                Text("Только локальные подключения")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)

                Spacer()
            }

            // Bonjour
            HStack(spacing: DSSpacing.lg) {
                Text("Bonjour")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Toggle("", isOn: Binding(
                    get: { remotePreferences.bonjourEnabled },
                    set: { remotePreferences.bonjourEnabled = $0 }
                ))
                .toggleStyle(.switch)
                .labelsHidden()
                .disabled(!remotePreferences.remoteControlEnabled || remotePreferences.bindToLocalhost)

                Text("Обнаружение в сети")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)

                Spacer()
            }

            // ngrok tunnel
            HStack(spacing: DSSpacing.lg) {
                Text("ngrok")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                if remoteServer.isNgrokRunning, let url = remoteServer.ngrokTunnelURL {
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
                            Text("Подключайтесь по wss:// (https:// для веб-клиента)")
                        }
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                    }

                    Button("Отключить") {
                        remotePreferences.ngrokEnabled = false
                        remoteServer.stopNgrok()
                    }
                    .font(DSFont.smallButtonLabel)
                    .foregroundStyle(DSColor.indicatorError)

                } else if remoteServer.isNgrokRunning {
                    // Starting...
                    ProgressView()
                        .controlSize(.small)
                    Text("Подключение...")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)

                } else if !remotePreferences.ngrokAuthtoken.isEmpty && remotePreferences.remoteControlEnabled {
                    // Has token, ready to connect
                    Button("Подключить") {
                        remotePreferences.ngrokEnabled = true
                        remoteServer.startNgrok()
                    }
                    .font(DSFont.smallButtonLabel)

                    if let error = remoteServer.ngrokError {
                        Text(error)
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.indicatorError)
                            .lineLimit(2)
                    } else {
                        Text("Доступ вне Wi-Fi")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                    }

                } else {
                    // No token — show setup hint
                    Text("Доступ вне Wi-Fi")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                }

                Spacer()
            }

            // ngrok authtoken + setup guide
            HStack(spacing: DSSpacing.lg) {
                Text("Authtoken")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                SecureField("ngrok authtoken", text: Binding(
                    get: { remotePreferences.ngrokAuthtoken },
                    set: { remotePreferences.ngrokAuthtoken = $0 }
                ))
                .textFieldStyle(.roundedBorder)
                .frame(maxWidth: 300)
                .disabled(!remotePreferences.remoteControlEnabled || remoteServer.isNgrokRunning)

                Link("Получить", destination: URL(string: "https://dashboard.ngrok.com/get-started/your-authtoken")!)
                    .font(DSFont.sidebarItemSmall)

                Spacer()
            }

            // Setup guide when no token
            if remotePreferences.ngrokAuthtoken.isEmpty && remotePreferences.remoteControlEnabled {
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
                            Text("  ⌘ скопировать")
                                .foregroundStyle(DSColor.textGhost)
                        }
                        HStack(spacing: 0) {
                            Text("2. ")
                                .foregroundStyle(DSColor.textMuted)
                            Link("ngrok.com", destination: URL(string: "https://dashboard.ngrok.com/signup")!)
                                .foregroundStyle(DSColor.accentPrimary)
                            Text(" → регистрация (бесплатно) → скопировать authtoken")
                                .foregroundStyle(DSColor.textMuted)
                        }
                        HStack(spacing: 0) {
                            Text("3. Вставить токен в поле выше → Подключить")
                                .foregroundStyle(DSColor.textMuted)
                        }
                    }
                    .font(DSFont.monoSmall)

                    Spacer()
                }
            }

            // PIN display
            HStack(spacing: DSSpacing.lg) {
                Text("PIN")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Text(remoteServer.currentPin)
                    .font(DSFont.pinDisplay)
                    .textSelection(.enabled)

                Button("Обновить") {
                    remoteServer.regeneratePin()
                }
                .font(DSFont.smallButtonLabel)
                .disabled(!remotePreferences.remoteControlEnabled)

                Spacer()
            }

            // TLS fingerprint
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
                    Text("Сертификат не создан")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                }

                Spacer()
            }

            // Server status + connected devices
            HStack(spacing: DSSpacing.lg) {
                Text("Статус")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Circle()
                    .fill(remoteServer.isRunning ? DSColor.gitAdded : DSColor.actionStop)
                    .frame(width: DSLayout.statusDotSize, height: DSLayout.statusDotSize)

                Text(remoteServer.isRunning ? "Активен (порт \(remoteServer.port))" : "Остановлен")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textSecondary)

                Spacer()
            }

            // Connected devices list
            if !remoteServer.connectedDevices.isEmpty {
                VStack(alignment: .leading, spacing: DSSpacing.sm) {
                    Text("Подключённые устройства")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textSecondary)

                    ForEach(remoteServer.connectedDevices) { device in
                        HStack(spacing: DSSpacing.sm) {
                            Image(systemName: "iphone")
                                .font(DSFont.iconSM)
                                .foregroundStyle(DSColor.textSecondary)
                            Text(device.displayName)
                                .font(DSFont.sidebarItem)
                            Text(device.ipAddress)
                                .font(DSFont.sidebarItemSmall)
                                .foregroundStyle(DSColor.textMuted)
                            Spacer()
                            Button {
                                remoteServer.disconnect(device.id)
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundStyle(DSColor.textMuted)
                            }
                            .buttonStyle(.plain)
                            .help("Отключить \(device.displayName)")
                        }
                    }
                }
                .padding(.leading, DSLayout.settingsLabelWidth + DSSpacing.lg)
            }

            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
