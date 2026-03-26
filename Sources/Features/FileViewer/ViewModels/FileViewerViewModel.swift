// MARK: - FileViewerViewModel
// Presentation logic for the file viewer sheet.
// macOS 14+, Swift 5.10

import AppKit
import Foundation
import Observation

/// Manages state and business logic for viewing and comparing files.
@Observable
@MainActor
final class FileViewerViewModel {

    // MARK: - Observable State

    private(set) var files: [ViewedFile] = []

    // MARK: - Computed Properties

    var canAddMoreFiles: Bool { files.count < 3 }

    var titleText: String {
        if files.count == 1 {
            return files.first?.fileName ?? ""
        }
        return "Comparing \(files.count) files"
    }

    var sheetWidth: CGFloat {
        switch files.count {
        case 2: return FileViewerConstants.twoFileWidth
        case 3: return FileViewerConstants.threeFileWidth
        default: return FileViewerConstants.singleFileWidth
        }
    }

    // MARK: - Init

    init(initialFile: ViewedFile) {
        files = [initialFile]
        loadContent(for: initialFile)
    }

    // MARK: - Actions

    func addFile(at url: URL) {
        let entry = FileEntry(path: url, gitStatus: nil)
        let newFile = ViewedFile(entry: entry)
        files.append(newFile)
        loadContent(for: newFile)
    }

    func removeFile(id: UUID) {
        files.removeAll { $0.id == id }
    }

    func copyAllContent() {
        var parts: [String] = []
        for file in files {
            var section = "// === \(file.fileName) ===\n"
            switch file.contentState {
            case .loaded(let text):
                section += text
            case .tooLarge(let truncated, _) where !truncated.isEmpty:
                section += truncated
            default:
                section += "// (no text content)"
            }
            parts.append(section)
        }
        let combined = parts.joined(separator: "\n\n")
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(combined, forType: .string)
    }

    // MARK: - Private

    private func loadContent(for file: ViewedFile) {
        let fileId = file.id
        let url = file.entry.path
        Task.detached(priority: .userInitiated) { [weak self] in
            let state = FileLoader.loadContent(at: url)
            await MainActor.run { [weak self] in
                guard let self else { return }
                if let idx = self.files.firstIndex(where: { $0.id == fileId }) {
                    self.files[idx].contentState = state
                }
            }
        }
    }
}
