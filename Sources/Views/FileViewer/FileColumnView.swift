// MARK: - FileColumnView
// Single column in the side-by-side file comparison layout.
// macOS 14+, Swift 5.10

import SwiftUI

struct FileColumnView: View {

    let file: ViewedFile
    let canClose: Bool
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            Divider().background(DSColor.borderDefault)
            contentArea
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: DSSpacing.sm) {
            Image(systemName: "doc.text")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)

            Text(file.fileName)
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer()

            if canClose {
                Button {
                    onClose()
                } label: {
                    Image(systemName: "xmark")
                        .font(DSFont.iconMD)
                        .foregroundStyle(DSColor.textMuted)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - Content

    @ViewBuilder
    private var contentArea: some View {
        switch file.contentState {
        case .loading:
            centeredPlaceholder {
                ProgressView()
                    .scaleEffect(0.8)
            }
        case .loaded(let text):
            CodeContentView(content: text)
        case .empty:
            centeredPlaceholder {
                Text("Empty file")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textMuted)
            }
        case .binary:
            centeredPlaceholder {
                VStack(spacing: DSSpacing.sm) {
                    Image(systemName: "doc.zipper")
                        .font(DSFont.emptyStateIcon)
                        .foregroundStyle(DSColor.textMuted)
                    Text("Binary file -- preview not available")
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textMuted)
                }
            }
        case .tooLarge(let truncated, let size):
            if truncated.isEmpty {
                centeredPlaceholder {
                    VStack(spacing: DSSpacing.sm) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(DSFont.emptyStateIcon)
                            .foregroundStyle(DSColor.gitModified)
                        Text("File too large to preview (\(formatBytes(size)))")
                            .font(DSFont.sidebarItem)
                            .foregroundStyle(DSColor.textMuted)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    sizeWarningBanner(size: size)
                    CodeContentView(content: truncated)
                }
            }
        case .error(let message):
            centeredPlaceholder {
                VStack(spacing: DSSpacing.sm) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(DSFont.emptyStateIcon)
                        .foregroundStyle(DSColor.gitDeleted)
                    Text(message)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.gitDeleted)
                        .multilineTextAlignment(.center)
                }
            }
        }
    }

    // MARK: - Helpers

    private func centeredPlaceholder<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack {
            Spacer()
            content()
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DSColor.surfaceBase)
    }

    private func sizeWarningBanner(size: Int) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.gitModified)
            Text("File truncated (\(formatBytes(size))) -- showing first \(FileLoader.maxLineCount) lines")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.gitModified)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.xs)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DSColor.gitModified.opacity(0.1))
    }

    private func formatBytes(_ bytes: Int) -> String {
        if bytes >= 1_000_000 {
            return String(format: "%.1f MB", Double(bytes) / 1_000_000)
        }
        return String(format: "%.0f KB", Double(bytes) / 1_000)
    }
}
