// MARK: - RemoteAPIHandlers+Devices
// Iteration 9 split. Device + server-status endpoints extracted from
// ``RemoteAPIHandlers`` to keep the primary type body under SwiftLint's
// `type_body_length` budget. Behaviour unchanged.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

extension RemoteAPIHandlers {

    func handleListDevices(device: RemoteDevice, channel: Channel, corsOrigin: String?) {
        let deviceResponses = authService.connectedDevices.map { dev in
            let isSelf = dev.id == device.id
            // SEC-M3: never expose peer device IPs — only the caller's own IP
            // is returned, so clients can still display "Connected from <ip>"
            // for themselves without leaking the broader device topology.
            return DeviceResponse(
                deviceId: dev.id.uuidString,
                ip: isSelf ? dev.ipAddress : nil,
                userAgent: dev.displayName,
                connectedSince: dev.connectedAt,
                lastActivity: dev.connectedAt,
                attachedSessions: [],
                isSelf: isSelf
            )
        }
        let resp = DevicesListResponse(
            devices: deviceResponses,
            maxDevices: RemoteAuthService.maxDevices
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleDisconnectDevice(
        deviceId: UUID,
        requestingDevice: RemoteDevice,
        channel: Channel,
        corsOrigin: String?
    ) {
        // SECURITY: a device can only disconnect itself.
        guard deviceId == requestingDevice.id else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "FORBIDDEN", message: "You can only disconnect your own device.")
            )
            writer.sendEncodableResponse(
                resp, status: .forbidden, channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        // SEC-3: fail closed if the server reference is gone (shutdown race).
        // A no-op `serverRef?.disconnect` would still return 204, telling the
        // device it is disconnected while its token stays live server-side.
        guard let serverRef else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "SERVER_UNAVAILABLE",
                    message: "Server is shutting down."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .serviceUnavailable, channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        serverRef.disconnect(deviceId)
        let theWriter = writer
        channel.eventLoop.execute {
            theWriter.sendEmptyResponse(status: .noContent, channel: channel, corsOrigin: corsOrigin)
        }
    }

    func handleStatus(channel: Channel, corsOrigin: String?) {
        // SEC-M1: deliberately minimal payload. `uptime`, `port`, `tls`,
        // and the full terminal color palette are no longer exposed — they
        // were fingerprinting aids for a leaked authenticated token without
        // any client-side use case.
        let resp = StatusResponse(
            version: metadata.appVersion,
            connectedDevices: authService.connectedDevices.count,
            activeWebsockets: serverRef?.activeBridges.count ?? 0
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }
}
