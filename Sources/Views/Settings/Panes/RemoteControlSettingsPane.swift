// MARK: - RemoteControlSettingsPane
// Remote Control server settings pane.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - RemoteControlSettingsPane

/// Settings pane for the embedded Remote Control HTTP/WS server.
struct RemoteControlSettingsPane: View {

    @Environment(\.remoteControlServer) private var remoteServer
    @Environment(\.remoteControlPreferences) private var remotePreferences

    // MARK: ViewModel (lazy init)

    @State private var vmBox = LazyStateObject<RemoteControlSettingsPaneViewModel>()
    private var viewModel: RemoteControlSettingsPaneViewModel {
        vmBox.resolve {
            RemoteControlSettingsPaneViewModel(
                server: remoteServer,
                preferences: remotePreferences
            )
        }
    }

    var body: some View {
        let model = viewModel
        return content(model: model)
    }

    private func content(model: RemoteControlSettingsPaneViewModel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                headerSection()
                enableSection(model: model)
                portSection(model: model)
                localhostSection(model: model)
                bonjourSection(model: model)
                ngrokTunnelSection(model: model)
                ngrokAuthtokenSection(model: model)
                ngrokSetupGuideSection(model: model)
                pinSection(model: model)
                tlsSection(model: model)
                statusSection(model: model)
                connectedDevicesSection(model: model)
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Header

    @ViewBuilder
    private func headerSection() -> some View {
        Text("Remote Control")
            .font(DSFont.settingsTitle)
            .foregroundStyle(DSColor.textPrimary)

        Divider().background(DSColor.borderDefault)
    }

    // MARK: - Enable toggle

    private func enableSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("Включить")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            Toggle("", isOn: Binding(
                get: { model.isEnabled },
                set: { model.setEnabled($0) }
            ))
            .toggleStyle(.switch)
            .labelsHidden()

            Spacer()
        }
    }

    // MARK: - Port

    private func portSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("Порт")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            TextField("", value: Binding(
                get: { model.port },
                set: { model.setPort($0) }
            ), format: .number)
            .textFieldStyle(.roundedBorder)
            .frame(width: DSLayout.portInputWidth)
            .disabled(!model.isEnabled)

            Text("(1024-65535)")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Spacer()
        }
    }

    // MARK: - Bind to localhost

    private func localhostSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("Localhost")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            Toggle("", isOn: Binding(
                get: { model.bindToLocalhost },
                set: { model.setBindToLocalhost($0) }
            ))
            .toggleStyle(.switch)
            .labelsHidden()
            .disabled(!model.isEnabled)

            Text("Только локальные подключения")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Spacer()
        }
    }

    // MARK: - Bonjour

    private func bonjourSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("Bonjour")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            Toggle("", isOn: Binding(
                get: { model.bonjourEnabled },
                set: { model.setBonjourEnabled($0) }
            ))
            .toggleStyle(.switch)
            .labelsHidden()
            .disabled(!model.isEnabled || model.bindToLocalhost)

            Text("Обнаружение в сети")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Spacer()
        }
    }

    // MARK: - Server status

    private func statusSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        HStack(spacing: DSSpacing.lg) {
            Text("Статус")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

            Circle()
                .fill(model.isRunning ? DSColor.gitAdded : DSColor.actionStop)
                .frame(width: DSLayout.statusDotSize, height: DSLayout.statusDotSize)

            Text(model.isRunning ? "Активен (порт \(model.serverPort))" : "Остановлен")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textSecondary)

            Spacer()
        }
    }

    // MARK: - Connected devices

    @ViewBuilder
    private func connectedDevicesSection(model: RemoteControlSettingsPaneViewModel) -> some View {
        if !model.connectedDevices.isEmpty {
            VStack(alignment: .leading, spacing: DSSpacing.sm) {
                Text("Подключённые устройства")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)

                ForEach(model.connectedDevices) { device in
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
                            model.disconnect(deviceId: device.id)
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
}
