// MARK: - RemoteUploadStore
// Filesystem-backed temp store for remote chat attachments.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Manages temporary upload files written by the remote web client.
///
/// Files live under a single per-process directory created with `0o700`
/// permissions inside `NSTemporaryDirectory()`. Filenames are random UUIDs
/// suffixed with the negotiated file extension — never derived from
/// client-supplied data. Call ``purge()`` at app shutdown to remove the
/// directory tree.
@MainActor
final class RemoteUploadStore {

    private let directoryURL: URL
    private let fm = FileManager.default

    init() {
        let base = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
        self.directoryURL = base.appendingPathComponent("vibestudio-remote-uploads", isDirectory: true)
        try? createDirectoryIfNeeded()
    }

    /// Returns the absolute filesystem path of the newly written file.
    func write(bytes: [UInt8], fileExtension: String) throws -> String {
        try createDirectoryIfNeeded()
        let name = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            + "." + fileExtension
        let url = directoryURL.appendingPathComponent(name, isDirectory: false)
        try Data(bytes).write(to: url, options: [.atomic])
        // Tighten: owner read/write only.
        try? fm.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
        Logger.remoteControl.info("Remote upload written: \(name, privacy: .public) bytes=\(bytes.count)")
        return url.path
    }

    /// Remove all stored uploads. Safe to call multiple times.
    func purge() {
        guard fm.fileExists(atPath: directoryURL.path) else { return }
        try? fm.removeItem(at: directoryURL)
        Logger.remoteControl.info("Remote upload store purged")
    }

    // MARK: - Private

    private func createDirectoryIfNeeded() throws {
        if !fm.fileExists(atPath: directoryURL.path) {
            try fm.createDirectory(
                at: directoryURL,
                withIntermediateDirectories: true,
                attributes: [.posixPermissions: 0o700]
            )
        }
    }
}
