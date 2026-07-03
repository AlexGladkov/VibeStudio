// MARK: - RemoteAPIHandlers+Auth
// Iteration 9 split. Authentication endpoints (`/auth/token`, `/auth/validate`)
// extracted from ``RemoteAPIHandlers`` to keep the primary type body under
// SwiftLint's `type_body_length` budget. Behaviour unchanged.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1
import OSLog

extension RemoteAPIHandlers {

    // MARK: - Token issuance (auth-free)

    func handleAuthToken(
        head: HTTPRequestHead,
        body: ByteBuffer?,
        context: RouteRequestContext,
        decoder: JSONDecoder
    ) {
        let channel = context.channel
        let corsOrigin = context.corsOrigin
        let clientIP = context.clientIP
        let remoteAddress = context.remoteAddress

        guard let request = Self.decodeAuthTokenRequest(body: body, decoder: decoder) else {
            writer.sendErrorJSON(
                status: .badRequest,
                code: "INVALID_REQUEST",
                message: "Request body must be JSON with a 'pin' field.",
                channel: channel,
                corsOrigin: corsOrigin
            )
            return
        }

        let userAgent = head.headers["User-Agent"].first ?? ""
        let authSvc = authService
        let theWriter = writer
        // ARCH-C1 / SEC-L1: replaced NSLog with privacy-aware os_log.
        Logger.remoteControl.debug(
            "handleAuthToken: clientIP=\(clientIP, privacy: .public) remoteAddr=\(remoteAddress, privacy: .public)"
        )

        Task { @MainActor in
            let result = authSvc.validatePin(request.pin, clientIP: clientIP, userAgent: userAgent)
            RemoteAuditLog.authAttempt(ip: clientIP, success: result.isSuccess)
            Logger.remoteControl.debug(
                "validatePin result=\(result.isSuccess ? "OK" : "FAIL", privacy: .public) ip=\(clientIP, privacy: .public)"
            )

            switch result {
            case .success(let tokenResponse):
                // SECURITY: never log token material.
                Logger.remoteControl.debug("auth success ip=\(clientIP, privacy: .public)")
                let resp = AuthTokenResponseDTO(
                    token: tokenResponse.token,
                    expiresAt: Date().addingTimeInterval(RemoteAuthService.tokenTTL),
                    deviceId: tokenResponse.device.id.uuidString
                )
                theWriter.sendEncodableResponse(
                    resp, status: .ok, channel: channel, corsOrigin: corsOrigin
                )

            case .failure(let error):
                Self.sendAuthTokenFailure(
                    error, channel: channel, corsOrigin: corsOrigin, writer: theWriter
                )
            }
        }
    }

    /// Decode the `{ "pin": ... }` body of a token request, or `nil` when the
    /// body is missing / malformed.
    private static func decodeAuthTokenRequest(
        body: ByteBuffer?,
        decoder: JSONDecoder
    ) -> AuthTokenRequest? {
        guard let body,
              let bodyData = body.getBytes(
                at: body.readerIndex, length: body.readableBytes
              ).map({ Data($0) }) else {
            return nil
        }
        return try? decoder.decode(AuthTokenRequest.self, from: bodyData)
    }

    /// Translate an ``AuthError`` into its wire response and write it on the
    /// event loop (mirrors the `.failure` arm inline previously).
    private static func sendAuthTokenFailure(
        _ error: AuthError,
        channel: Channel,
        corsOrigin: String?,
        writer: HTTPResponseWriter
    ) {
        let authResp = RemoteAuthMiddleware.authErrorResponse(error)
        let resp = ErrorResponse(error: ErrorDetail(code: authResp.code, message: authResp.message))
        guard let data = try? writer.encoder.encode(resp) else { return }
        channel.eventLoop.execute {
            writer.sendRawJSON(
                status: authResp.status,
                data: data,
                channel: channel,
                corsOrigin: corsOrigin,
                retryAfterSeconds: authResp.retryAfterSeconds
            )
        }
    }

    // MARK: - Token validation (authenticated)

    func handleAuthValidate(device: RemoteDevice, channel: Channel, corsOrigin: String?) {
        let resp = AuthValidateResponse(
            valid: true,
            deviceId: device.id.uuidString,
            expiresAt: device.connectedAt.addingTimeInterval(RemoteAuthService.tokenTTL)
        )
        writer.sendEncodable(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }
}
