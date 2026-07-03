// MARK: - RemoteAuthMiddleware
// ARCH-H1: Extracted bearer token + auth-error helpers from
// HTTPRequestRouter. Pure functions / static methods — no per-request state.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOHTTP1

/// HTTP response fields derived from an ``AuthError``.
///
/// SEC-L16: ``retryAfterSeconds`` populates the `Retry-After` header for 429
/// responses so honest clients back off without parsing the JSON body.
struct AuthErrorResponse {
    let status: HTTPResponseStatus
    let code: String
    let message: String
    let retryAfterSeconds: Int?
}

/// Stateless helpers for parsing/translating auth state in HTTP requests.
enum RemoteAuthMiddleware {

    /// Extract the bearer token from the `Authorization` header.
    ///
    /// - Returns: The opaque token string, or `nil` when the header is
    ///   missing or does not start with `"Bearer "`.
    static func extractBearerToken(from head: HTTPRequestHead) -> String? {
        guard let auth = head.headers["Authorization"].first,
              auth.hasPrefix("Bearer ") else { return nil }
        return String(auth.dropFirst("Bearer ".count))
    }

    /// Map an ``AuthError`` to the HTTP response fields describing it.
    static func authErrorResponse(_ error: AuthError) -> AuthErrorResponse {
        switch error {
        case .invalidPin:
            return AuthErrorResponse(
                status: .unauthorized, code: "AUTH_PIN_INVALID",
                message: "Incorrect PIN.", retryAfterSeconds: nil)
        case .rateLimited(retryAfterSeconds: let seconds):
            return AuthErrorResponse(
                status: .tooManyRequests, code: "AUTH_LOCKOUT",
                message: "Too many failed attempts. Try again in \(seconds) seconds.",
                retryAfterSeconds: seconds)
        case .globalLockout:
            return AuthErrorResponse(
                status: .tooManyRequests, code: "AUTH_LOCKOUT",
                message: "Server is locked due to excessive failed attempts.",
                retryAfterSeconds: Int(RemoteAuthService.rateLimitWindowSecondsPublic))
        case .invalidToken:
            return AuthErrorResponse(
                status: .unauthorized, code: "AUTH_TOKEN_INVALID",
                message: "Invalid or expired token.", retryAfterSeconds: nil)
        case .tokenExpired:
            return AuthErrorResponse(
                status: .unauthorized, code: "AUTH_TOKEN_EXPIRED",
                message: "Token has expired. Please re-authenticate.", retryAfterSeconds: nil)
        case .ipMismatch:
            return AuthErrorResponse(
                status: .forbidden, code: "AUTH_IP_MISMATCH",
                message: "Token was issued to a different IP address.", retryAfterSeconds: nil)
        case .maxDevicesReached:
            return AuthErrorResponse(
                status: .serviceUnavailable, code: "MAX_DEVICES_REACHED",
                message: "Maximum number of connected devices reached. Try again later.",
                retryAfterSeconds: nil)
        }
    }
}
