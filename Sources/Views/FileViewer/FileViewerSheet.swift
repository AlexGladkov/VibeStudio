// MARK: - FileViewerSheet
// Sheet for viewing and comparing up to 3 files side-by-side.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit
import UniformTypeIdentifiers

struct FileViewerSheet: View {

    let initialFile: ViewedFile
    let projectPath: URL

    @Environment(\.dismiss) private var dismiss
    @State private var files: [ViewedFile] = []
    @State private var showFilePicker = false

    private var sheetWidth: CGFloat {
        switch files.count {
        case 2: return 1100
        case 3: return 1280
        default: return 600
        }
    }

    private var titleText: String {
        if files.count == 1 {
            return files.first?.fileName ?? initialFile.fileName
        }
        return "Comparing \(files.count) files"
    }

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider().background(DSColor.borderDefault)
            fileColumns
        }
        .frame(width: sheetWidth, height: 600)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            files = [initialFile]
            loadContent(for: initialFile)
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.item],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                let entry = FileEntry(path: url, gitStatus: nil)
                let newFile = ViewedFile(entry: entry)
                files.append(newFile)
                loadContent(for: newFile)
            }
        }
    }

    // MARK: - Toolbar

    private var toolbar: some View {
        HStack(spacing: DSSpacing.sm) {
            Text(titleText)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()

            if files.count < 3 {
                Button {
                    showFilePicker = true
                } label: {
                    HStack(spacing: DSSpacing.xxs) {
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .medium))
                        Text("Add File")
                            .font(DSFont.buttonLabel)
                    }
                    .foregroundStyle(DSColor.accentPrimary)
                }
                .buttonStyle(.plain)
            }

            Button {
                copyAllContent()
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.system(size: 12))
                    .foregroundStyle(DSColor.textSecondary)
            }
            .buttonStyle(.plain)
            .help("Copy content to clipboard")

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.escape, modifiers: [])
        }
        .padding(.horizontal, DSSpacing.lg)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - File Columns

    private var fileColumns: some View {
        HStack(spacing: 0) {
            ForEach(Array(files.enumerated()), id: \.element.id) { index, file in
                if index > 0 {
                    Divider()
                        .background(DSColor.borderDefault)
                }
                FileColumnView(
                    file: file,
                    canClose: files.count > 1,
                    onClose: { removeFile(id: file.id) }
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Actions

    private func loadContent(for file: ViewedFile) {
        let fileId = file.id
        let url = file.entry.path
        Task.detached(priority: .userInitiated) {
            let state = FileLoader.loadContent(at: url)
            await MainActor.run {
                if let idx = files.firstIndex(where: { $0.id == fileId }) {
                    files[idx].contentState = state
                }
            }
        }
    }

    private func removeFile(id: UUID) {
        files.removeAll { $0.id == id }
    }

    private func copyAllContent() {
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
}
