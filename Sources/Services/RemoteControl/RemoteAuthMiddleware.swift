// MARK: - RemoteAuthMiddleware
// ARCH-H1: Extracted bearer token + auth-error helpers from
// HTTPRequestRouter. Pure functions / static methods — no per-request state.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOHTTP1

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

    /// Map an ``AuthError`` to (HTTP status, error code, human-readable
    /// message, optional Retry-After seconds).
    ///
    /// SEC-L16: 429 responses must include a `Retry-After` header so honest
    /// clients honour back-off without parsing the JSON body.
    static func authErrorResponse(_ error: AuthError) -> (HTTPResponseStatus, String, String, Int?) {
        switch error {
        case .invalidPin:
            return (.unauthorized, "AUTH_PIN_INVALID", "Incorrect PIN.", nil)
        case .rateLimited(retryAfterSeconds: let seconds):
            return (.tooManyRequests, "AUTH_LOCKOUT",
                    "Too many failed attempts. Try again in \(seconds) seconds.",
                    seconds)
        case .globalLockout:
            return (.tooManyRequests, "AUTH_LOCKOUT",
                    "Server is locked due to excessive failed attempts.",
                    Int(RemoteAuthService.rateLimitWindowSecondsPublic))
        case .invalidToken:
            return (.unauthorized, "AUTH_TOKEN_INVALID", "Invalid or expired token.", nil)
        case .tokenExpired:
            return (.unauthorized, "AUTH_TOKEN_EXPIRED", "Token has expired. Please re-authenticate.", nil)
        case .ipMismatch:
            return (.forbidden, "AUTH_IP_MISMATCH", "Token was issued to a different IP address.", nil)
        case .maxDevicesReached:
            return (.serviceUnavailable, "MAX_DEVICES_REACHED",
                    "Maximum number of connected devices reached. Try again later.",
                    nil)
        }
    }
}
