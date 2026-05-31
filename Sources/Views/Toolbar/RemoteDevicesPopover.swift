// MARK: - RemoteDevicesPopover
// List of connected Remote Control devices with PIN management.
// macOS 14+, Swift 5.10

import SwiftUI

/// Popover content for the toolbar Remote Control indicator (`iphone` icon).
///
/// Shows:
/// - Current PIN with a "regenerate" button
/// - Connected device list, with per-device "disconnect" action
/// - Server status (port + running indicator)
///
/// The remote server is observed reactively — the popover updates as devices
/// connect / disconnect or the user rotates the PIN.
struct RemoteDevicesPopover: View {

    @Environment(\.remoteControlServer) private var remoteServer

    var body: some View {
        VStack(alignment: .leading, spacing: DSSpacing.md) {
            // PIN section
            HStack {
                Text("PIN")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textSecondary)
                Text(remoteServer.currentPin)
                    .font(DSFont.pinDisplay)
                    .textSelection(.enabled)
                Spacer()
                Button { remoteServer.regeneratePin() } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(DSFont.iconSM)
                }
                .buttonStyle(.plain)
                .help("Обновить PIN")
            }

            Divider()

            if remoteServer.connectedDevices.isEmpty {
                Text("Нет подключённых устройств")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            } else {
                ForEach(remoteServer.connectedDevices) { device in
                    HStack {
                        Image(systemName: "iphone")
                            .font(DSFont.iconSM)
                        VStack(alignment: .leading) {
                            Text(device.displayName)
                                .font(DSFont.sidebarItem)
                            Text(device.ipAddress)
                                .font(DSFont.sidebarItemSmall)
                                .foregroundStyle(DSColor.textMuted)
                        }
                        Spacer()
                        Button { remoteServer.disconnect(device.id) } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(DSColor.textMuted)
                        }
                        .buttonStyle(.plain)
                        .help("Отключить \(device.displayName)")
                    }
                }
            }

            Divider()

            HStack {
                Text("Порт \(remoteServer.port)")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
                Spacer()
                Circle()
                    .fill(remoteServer.isRunning ? DSColor.gitAdded : DSColor.actionStop)
                    .frame(width: 6, height: 6)
                Text(remoteServer.isRunning ? "Активен" : "Остановлен")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }
        }
        .padding(DSSpacing.md)
        .frame(minWidth: DSLayout.remotePopoverMinWidth, maxWidth: DSLayout.remotePopoverMaxWidth)
    }
}
