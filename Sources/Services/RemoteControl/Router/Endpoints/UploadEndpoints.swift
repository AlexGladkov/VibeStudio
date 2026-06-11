// MARK: - UploadEndpoints
// /api/v1/uploads/* — temp file uploads for chat-style image attachments.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for binary uploads from the remote web client.
///
/// **Security invariants:**
/// - Authenticated requests only (router enforces).
/// - Single hard size cap (`maxBytes`) — request rejected with `413` otherwise.
/// - Image MIME type whitelist — anything else rejected `415`.
/// - Files written to a process-scoped temp directory under `NSTemporaryDirectory()`;
///   directory auto-created with `0o700`. Cleared by `RemoteUploadStore.purge()`
///   on app exit. Filenames are random UUIDs — no client-supplied path component
///   ever reaches the filesystem.
@MainActor
struct UploadEndpoints {

    let store: RemoteUploadStore
    let builder: HTTPResponseBuilder

    /// Maximum upload size in bytes (10 MB).
    static let maxBytes = 10 * 1024 * 1024

    /// Allowed Content-Type prefixes (MIME whitelist).
    static let allowedTypes: Set<String> = [
        "image/png", "image/jpeg", "image/gif", "image/webp", "image/heic", "image/heif"
    ]

    /// POST /api/v1/uploads/image
    /// Body: raw image bytes. Headers: `Content-Type` (required, whitelisted).
    /// Returns: `{ "path": "/var/folders/.../xxxxxxxx.png" }`.
    func handleImageUpload(
        body: ByteBuffer?,
        contentType: String,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let body, body.readableBytes > 0 else {
            return builder.errorJSONResponse(
                status: .badRequest, code: "UPLOAD_EMPTY",
                message: "Request body is required.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
        guard body.readableBytes <= Self.maxBytes else {
            return builder.errorJSONResponse(
                status: .payloadTooLarge, code: "UPLOAD_TOO_LARGE",
                message: "Upload exceeds the \(Self.maxBytes / 1024 / 1024)MB limit.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
        // Normalize content-type (strip parameters like "; charset=...").
        let normalized = contentType.split(separator: ";").first.map { $0.trimmingCharacters(in: .whitespaces).lowercased() } ?? ""
        guard Self.allowedTypes.contains(normalized) else {
            return builder.errorJSONResponse(
                status: .unsupportedMediaType, code: "UPLOAD_BAD_TYPE",
                message: "Only image/* uploads are accepted.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        let ext = Self.extensionFor(mimeType: normalized)
        do {
            let path = try store.write(
                bytes: body.getBytes(at: body.readerIndex, length: body.readableBytes) ?? [],
                fileExtension: ext
            )
            let resp = UploadImageResponse(path: path)
            return builder.encodableResponse(
                status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
            )
        } catch {
            return builder.errorJSONResponse(
                status: .internalServerError, code: "UPLOAD_FAILED",
                message: "Failed to persist upload.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }
    }

    private static func extensionFor(mimeType: String) -> String {
        switch mimeType {
        case "image/png":  return "png"
        case "image/jpeg": return "jpg"
        case "image/gif":  return "gif"
        case "image/webp": return "webp"
        case "image/heic": return "heic"
        case "image/heif": return "heif"
        default:           return "bin"
        }
    }
}

/// Response DTO for upload endpoints.
struct UploadImageResponse: Codable {
    let path: String
}
