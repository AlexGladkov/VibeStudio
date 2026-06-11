// MARK: - AuthEndpoints
// /api/v1/auth/* and /api/v1/devices/* handlers.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for the auth + device endpoints. All handlers run on
/// `@MainActor` since they touch `RemoteAuthService` state. Each method
/// returns the response head + buffer for the caller to write.
@MainActor
struct AuthEndpoints {

    let authService: RemoteAuthService
    let builder: HTTPResponseBuilder
    let decoder: JSONDecoder

    // MARK: - Token Issuance

    /// POST /api/v1/auth/token — exchange a PIN for a bearer token.
    /// Returns `nil` when the request body is malformed (caller emits 400).
    func handlePinValidate(
        body: ByteBuffer?,
        clientIP: String,
        userAgent: String,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let body,
              let bodyData = body.getBytes(at: body.readerIndex, length: body.readableBytes).map({ Data($0) }),
              let request = try? decoder.decode(AuthTokenRequest.self, from: bodyData) else {
            return builder.errorJSONResponse(
                status: .badRequest,
                code: "INVALID_REQUEST",
                message: "Request body must be JSON with a 'pin' field.",
                corsOrigin: corsOrigin,
                allocator: allocator
            )
        }

        let result = authService.validatePin(request.pin, clientIP: clientIP, userAgent: userAgent)
        let success: Bool
        if case .success = result { success = true } else { success = false }
        RemoteAuditLog.authAttempt(ip: clientIP, success: success)
        #if DEBUG
        NSLog("[RC-AUTH] validatePin result: \(success ? "OK" : "FAIL") ip=\(clientIP)")
        #endif

        switch result {
        case .success(let tokenResponse):
            #if DEBUG
            NSLog("[RC-AUTH] issued token=\(String(tokenResponse.token.prefix(8)))...")
            #endif
            let expiresAt = authService.expiry(of: tokenResponse.token)?.expiresAt
                ?? Date().addingTimeInterval(authService.tokenTTLSeconds)
            let resp = AuthTokenResponseDTO(
                token: tokenResponse.token,
                expiresAt: expiresAt,
                deviceId: tokenResponse.device.id.uuidString
            )
            return builder.encodableResponse(
                status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
            )

        case .failure(let error):
            let (status, code, message) = Self.authErrorResponse(error)
            return builder.errorJSONResponse(
                status: status, code: code, message: message,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
    }

    // MARK: - Token Validation Response

    /// GET /api/v1/auth/validate — confirm a bearer token is still valid.
    func handleTokenValidate(
        device: RemoteDevice,
        token: String,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let expiresAt = authService.expiry(of: token)?.expiresAt
            ?? device.connectedAt.addingTimeInterval(authService.tokenTTLSeconds)
        let resp = AuthValidateResponse(
            valid: true,
            deviceId: device.id.uuidString,
            expiresAt: expiresAt
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    // MARK: - Token Refresh

    /// POST /api/v1/auth/refresh — exchange a still-valid bearer token for a
    /// new one with `expiresAt = now + tokenTTL`. Used by clients to keep a
    /// session alive without re-prompting the user for the PIN.
    /// The old token is invalidated on success (rotation prevents replay).
    func handleTokenRefresh(
        token: String,
        clientIP: String,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        switch authService.refreshToken(token, clientIP: clientIP) {
        case .success(let tokenResponse):
            let expiresAt = authService.expiry(of: tokenResponse.token)?.expiresAt
                ?? Date().addingTimeInterval(authService.tokenTTLSeconds)
            let resp = AuthTokenResponseDTO(
                token: tokenResponse.token,
                expiresAt: expiresAt,
                deviceId: tokenResponse.device.id.uuidString
            )
            return builder.encodableResponse(
                status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
            )
        case .failure(let error):
            let (status, code, message) = Self.authErrorResponse(error)
            return builder.errorJSONResponse(
                status: status, code: code, message: message,
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
    }

    // MARK: - Devices

    /// GET /api/v1/devices — list the currently connected devices.
    func handleListDevices(
        requestingDevice: RemoteDevice,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let deviceResponses = authService.connectedDevices.map { dev in
            DeviceResponse(
                deviceId: dev.id.uuidString,
                ip: dev.ipAddress,
                userAgent: dev.displayName,
                connectedSince: dev.connectedAt,
                lastActivity: dev.connectedAt,
                attachedSessions: [],
                isSelf: dev.id == requestingDevice.id
            )
        }
        let resp = DevicesListResponse(
            devices: deviceResponses,
            maxDevices: RemoteAuthService.maxDevices
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// DELETE /api/v1/devices/:id — disconnect a device.
    /// SECURITY: a device can only disconnect itself.
    func handleRevokeDevice(
        deviceId: UUID,
        requestingDevice: RemoteDevice,
        serverRef: RemoteControlServer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard deviceId == requestingDevice.id else {
            return builder.errorJSONResponse(
                status: .forbidden,
                code: "FORBIDDEN",
                message: "You can only disconnect your own device.",
                corsOrigin: corsOrigin,
                allocator: allocator
            )
        }
        serverRef?.disconnect(deviceId)
        return builder.emptyResponse(status: .noContent, corsOrigin: corsOrigin, allocator: allocator)
    }

    // MARK: - Error Mapping

    /// Map an `AuthError` to the standard `(status, code, message)` triple.
    static func authErrorResponse(_ error: AuthError) -> (HTTPResponseStatus, String, String) {
        switch error {
        case .invalidPin:
            return (.unauthorized, "AUTH_PIN_INVALID", "Incorrect PIN.")
        case .rateLimited(retryAfterSeconds: let seconds):
            return (.tooManyRequests, "AUTH_LOCKOUT",
                    "Too many failed attempts. Try again in \(seconds) seconds.")
        case .globalLockout:
            return (.tooManyRequests, "AUTH_LOCKOUT",
                    "Server is locked due to excessive failed attempts.")
        case .invalidToken:
            return (.unauthorized, "AUTH_TOKEN_INVALID", "Invalid or expired token.")
        case .tokenExpired:
            return (.unauthorized, "AUTH_TOKEN_EXPIRED", "Token has expired. Please re-authenticate.")
        case .ipMismatch:
            return (.forbidden, "AUTH_IP_MISMATCH", "Token was issued to a different IP address.")
        case .maxDevicesReached:
            return (.serviceUnavailable, "MAX_DEVICES_REACHED",
                    "Maximum number of connected devices reached. Try again later.")
        case .secureRandomFailure:
            return (.internalServerError, "AUTH_RNG_FAILURE",
                    "Secure random generator failed. Please try again.")
        }
    }
}
