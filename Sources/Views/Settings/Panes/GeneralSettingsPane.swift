// MARK: - GeneralSettingsPane
// Appearance settings for the Settings window.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - GeneralSettingsPane

/// Appearance settings pane.
///
/// Reads and writes the selected theme via ``ThemeService``.
/// Changes take effect immediately — `NSApp.appearance` is set synchronously
/// so the whole app (including AppKit views) switches without relaunch.
struct GeneralSettingsPane: View {

    @Environment(\.themeService) private var themeService
    @Environment(\.generalPreferences) private var generalPreferences
    @Environment(\.remoteControlServer) private var remoteServer
    @Environment(\.remoteControlPreferences) private var remotePreferences

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
            Text("Внешний вид")
                .font(DSFont.settingsTitle)
                .foregroundStyle(DSColor.textPrimary)

            Divider().background(DSColor.borderDefault)

            HStack(spacing: DSSpacing.lg) {
                Text("Тема")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Picker("", selection: Binding(
                    get: { themeService.selectedAppearance.rawValue },
                    set: { themeService.setAppearance(AppAppearance(rawValue: $0) ?? .system) }
                )) {
                    Text("System").tag(0)
                    Text("Dark").tag(1)
                    Text("Light").tag(2)
                }
                .pickerStyle(.segmented)
                .frame(width: DSLayout.settingsPickerWidth)
                .labelsHidden()

                Spacer()
            }

            HStack(spacing: DSSpacing.lg) {
                Text("Шрифт терминала")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Stepper(
                    value: Binding(
                        get: { generalPreferences.terminalFontSize },
                        set: { generalPreferences.terminalFontSize = $0 }
                    ),
                    in: 9...24,
                    step: 1
                ) {
                    Text("\(Int(generalPreferences.terminalFontSize)) pt")
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textPrimary)
                        .monospacedDigit()
                }

                Spacer()
            }

            Divider().background(DSColor.borderDefault)

            Text("Вкладки")
                .font(DSFont.settingsTitle)
                .foregroundStyle(DSColor.textPrimary)

            Divider().background(DSColor.borderDefault)

            HStack(spacing: DSSpacing.lg) {
                Text("Подтверждать закрытие")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Toggle("", isOn: Binding(
                    get: { generalPreferences.confirmTabClose },
                    set: { generalPreferences.confirmTabClose = $0 }
                ))
                .toggleStyle(.switch)
                .labelsHidden()

                Spacer()
            }

            Divider().background(DSColor.borderDefault)

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
                .frame(width: 80)
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

            // PIN display
            HStack(spacing: DSSpacing.lg) {
                Text("PIN")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Text(remoteServer.currentPin)
                    .font(.system(size: 18, weight: .bold, design: .monospaced))
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
                    .frame(width: 8, height: 8)

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
