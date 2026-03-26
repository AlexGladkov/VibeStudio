// MARK: - ViewedFile
// Model and loader for file preview in FileViewerSheet.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - FileContentState

enum FileContentState: Sendable {
    case loading
    case loaded(String)
    case empty
    case binary
    case tooLarge(truncated: String, originalSize: Int)
    case error(String)
}

// MARK: - ViewedFile

struct ViewedFile: Identifiable, Sendable {
    let id: UUID
    let entry: FileEntry
    var contentState: FileContentState

    init(entry: FileEntry, contentState: FileContentState = .loading) {
        self.id = UUID()
        self.entry = entry
        self.contentState = contentState
    }

    var fileName: String { entry.path.lastPathComponent }
    var filePath: String { entry.path.path }
}

// MARK: - FileLoader

enum FileLoader {
    static let warningSizeBytes = 100_000
    static let truncateSizeBytes = 1_000_000
    static let maxLineCount = 5_000
    static let refuseSizeBytes = 5_000_000

    static func loadContent(at url: URL) -> FileContentState {
        let fm = FileManager.default
        guard fm.isReadableFile(atPath: url.path) else {
            return .error("File not readable")
        }
        guard let attributes = try? fm.attributesOfItem(atPath: url.path),
              let fileSize = attributes[.size] as? Int else {
            return .error("Cannot read file attributes")
        }
        if fileSize == 0 { return .empty }
        if fileSize > refuseSizeBytes {
            return .tooLarge(truncated: "", originalSize: fileSize)
        }
        guard let data = fm.contents(atPath: url.path),
              let content = String(data: data, encoding: .utf8) else {
            return .binary
        }
        if fileSize > truncateSizeBytes {
            let lines = content.components(separatedBy: "\n")
            let truncated = lines.prefix(maxLineCount).joined(separator: "\n")
            return .tooLarge(truncated: truncated, originalSize: fileSize)
        }
        return .loaded(content)
    }
}
