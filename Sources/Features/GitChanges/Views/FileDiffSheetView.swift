// MARK: - FileDiffSheetView
// Diff view opened as a standalone resizable NSWindow.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

/// Content view for the standalone diff window.
///
/// Instantiated via ``DiffWindowStore/open(file:staged:projectPath:gitService:)``
/// which hosts it in a resizable `NSWindow`. The `gitService` is injected
/// directly (not via `@Environment`) so the window doesn't need to be part of
/// the main SwiftUI view hierarchy.
struct FileDiffSheetView: View {

    let file: GitFile
    let staged: Bool
    let projectPath: URL?
    let gitService: any GitServicing

    @State private var hunks: [GitDiffHunk] = []
    @State private var isLoading = true
    @State private var sizeWarning: String?
    @State private var errorMessage: String?

    // MARK: - Constants

    private static let maxDiffSizeBytes = 512 * 1024

    private static let binaryExtensions: Set<String> = [
        "png", "jpg", "jpeg", "gif", "ico", "icns", "pdf",
        "zip", "tar", "gz", "dmg", "exe", "dylib", "so", "a", "o",
        "class", "jar", "war", "ear", "bin", "dat"
    ]

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            headerView
            Divider()
            diffContentView
        }
        .background(DSColor.surfaceBase)
        .task { await loadDiff() }
    }

    // MARK: - Header

    private var headerView: some View {
        HStack(spacing: DSSpacing.sm) {
            Text(file.status.rawValue)
                .font(DSFont.gitStatus)
                .foregroundStyle(file.status.color)
                .frame(width: DSLayout.statusLetterWidth, alignment: .center)

            Text((file.path as NSString).lastPathComponent)
                .font(DSFont.gitBranch)
                .foregroundStyle(DSColor.textPrimary)

            let dir = (file.path as NSString).deletingLastPathComponent
            if !dir.isEmpty && dir != "." {
                Text(dir)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }

            if staged {
                Text("staged")
                    .font(DSFont.iconMD)
                    .foregroundStyle(DSColor.gitAdded)
                    .padding(.horizontal, DSSpacing.xs)
                    .padding(.vertical, 1) // sub-grid vertical padding for badge
                    .background(
                        DSColor.gitAdded.opacity(0.1),
                        in: RoundedRectangle(cornerRadius: DSRadius.sm)
                    )
            }

            Spacer()

            Button {
                NSApp.keyWindow?.close()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16)) // close icon, intentionally larger than iconLG
                    .foregroundStyle(DSColor.textMuted)
                    .frame(width: DSLayout.closeButtonSize, height: DSLayout.closeButtonSize)
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.escape, modifiers: [])
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - Diff Content

    @ViewBuilder
    private var diffContentView: some View {
        if isLoading {
            VStack {
                Spacer()
                ProgressView().scaleEffect(0.7)
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if hunks.isEmpty {
            VStack {
                Spacer()
                Text(errorMessage ?? "No changes")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textMuted)
                    .multilineTextAlignment(.center)
                    .padding()
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 0) {
                if let warning = sizeWarning {
                    Text(warning)
                        .font(DSFont.iconMD)
                        .foregroundStyle(DSColor.indicatorWaiting)
                        .padding(.horizontal, DSSpacing.sm)
                        .padding(.vertical, DSSpacing.xxs)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Divider()
                }
                DiffView(hunks: hunks)
            }
        }
    }

    // MARK: - Diff Loading

    private func loadDiff() async {
        guard let path = projectPath else {
            errorMessage = "No active project"
            isLoading = false
            return
        }

        let ext = (file.path as NSString).pathExtension.lowercased()
        guard !Self.binaryExtensions.contains(ext) else {
            errorMessage = "Binary file — diff not available"
            isLoading = false
            return
        }

        do {
            let loaded = try await gitService.diff(file: file.path, staged: staged, at: path)

            let totalSize = loaded.reduce(0) { total, hunk in
                total + hunk.lines.reduce(0) { $0 + $1.content.utf8.count }
            }

            if totalSize > Self.maxDiffSizeBytes {
                sizeWarning = "Diff truncated — file too large (\(totalSize / 1024) KB)"
                var accumulated = 0
                hunks = Array(loaded.prefix(while: { hunk in
                    let size = hunk.lines.reduce(0) { $0 + $1.content.utf8.count }
                    guard accumulated + size <= Self.maxDiffSizeBytes else { return false }
                    accumulated += size
                    return true
                }))
            } else {
                hunks = loaded
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}
