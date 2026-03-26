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
    @State private var vm: FileViewerViewModel?
    @State private var showFilePicker = false

    private var viewModel: FileViewerViewModel {
        if let existing = vm { return existing }
        let created = FileViewerViewModel(initialFile: initialFile)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        VStack(spacing: 0) {
            toolbar(model: model)
            Divider().background(DSColor.borderDefault)
            fileColumns(model: model)
        }
        .frame(width: model.sheetWidth, height: FileViewerConstants.sheetHeight)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            if vm == nil {
                vm = FileViewerViewModel(initialFile: initialFile)
            }
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.item],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                model.addFile(at: url)
            }
        }
    }

    // MARK: - Toolbar

    private func toolbar(model: FileViewerViewModel) -> some View {
        HStack(spacing: DSSpacing.sm) {
            Text(model.titleText)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)

            Spacer()

            if model.canAddMoreFiles {
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
                model.copyAllContent()
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

    private func fileColumns(model: FileViewerViewModel) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(model.files.enumerated()), id: \.element.id) { index, file in
                if index > 0 {
                    Divider()
                        .background(DSColor.borderDefault)
                }
                FileColumnView(
                    file: file,
                    canClose: model.files.count > 1,
                    onClose: { model.removeFile(id: file.id) }
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
