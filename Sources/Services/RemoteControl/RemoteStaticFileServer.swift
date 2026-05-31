// MARK: - RemoteStaticFileServer
// ARCH-H1 + ARCH-H8: Serves static assets for the Remote Control web UI.
// Holds the pre-loaded ``RemoteStaticFileCache`` and a writer. NIO event
// loop never calls `Bundle.main` itself — `baseURL` was captured at
// MainActor init time and is reused for the disk fallback path.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Serves files from the `RemoteControlWeb` bundle directory.
///
/// Fast path is the in-memory cache (`RemoteStaticFileCache.files`). The
/// disk fallback uses the pre-resolved `baseURL` so we never touch
/// `Bundle.main` from NIO threads.
struct RemoteStaticFileServer: Sendable {

    private let cache: RemoteStaticFileCache
    private let writer: HTTPResponseWriter

    init(cache: RemoteStaticFileCache, writer: HTTPResponseWriter) {
        self.cache = cache
        self.writer = writer
    }

    /// Serve `fileName` (relative path inside `RemoteControlWeb/`) with the
    /// given declared content type.
    ///
    /// Sends 404 when the file is missing from both cache and disk; 403 if
    /// the resolved path escapes the base directory (defense in depth even
    /// though all paths are constructed from a constant prefix today).
    func serveStaticFile(
        _ fileName: String,
        contentType: String,
        channel: Channel,
        corsOrigin: String?
    ) {
        // Fast path: in-memory cache.
        if let (cachedData, cachedContentType) = cache.files[fileName] {
            sendFile(
                data: cachedData,
                contentType: cachedContentType,
                fileName: fileName,
                channel: channel
            )
            return
        }

        // Fallback: read from disk using the cached baseURL (resolved on
        // MainActor at server start time, never touches Bundle.main here).
        guard let baseURL = cache.baseURL else {
            writer.sendErrorJSON(
                status: .notFound, code: "NOT_FOUND",
                message: "Static file not found.",
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        let resolved = baseURL.appendingPathComponent(fileName).standardized
        guard resolved.path.hasPrefix(baseURL.standardized.path) else {
            writer.sendErrorJSON(
                status: .forbidden, code: "FORBIDDEN",
                message: "Access denied.",
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        guard let data = try? Data(contentsOf: resolved) else {
            writer.sendErrorJSON(
                status: .notFound, code: "NOT_FOUND",
                message: "Static file not found.",
                channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        sendFile(data: data, contentType: contentType, fileName: fileName, channel: channel)
    }

    // MARK: - Private

    private func sendFile(data: Data, contentType: String, fileName: String, channel: Channel) {
        let isVendor = fileName.hasPrefix("vendor/")
        let isHTML = contentType.hasPrefix("text/html")
        writer.sendStaticFileData(
            data: data,
            contentType: contentType,
            isVendor: isVendor,
            isHTML: isHTML,
            channel: channel
        )
    }
}
