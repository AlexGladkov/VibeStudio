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

    @State private var vmBox = LazyStateObject<FileDiffViewModel>()

    private var viewModel: FileDiffViewModel {
        vmBox.resolve { FileDiffViewModel(gitService: gitService) }
    }

    // MARK: - Body

    var body: some View {
        let model = viewModel

        return VStack(spacing: 0) {
            headerView
            Divider()
            diffContentView(model)
        }
        .background(DSColor.surfaceBase)
        .task { await model.load(file: file, staged: staged, projectPath: projectPath) }
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
                        DSColor.gitAddedSubtle,
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
    private func diffContentView(_ model: FileDiffViewModel) -> some View {
        if model.isLoading {
            VStack {
                Spacer()
                ProgressView().scaleEffect(DSLayout.progressScaleMedium)
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if model.hunks.isEmpty {
            VStack {
                Spacer()
                Text(model.errorMessage ?? "No changes")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textMuted)
                    .multilineTextAlignment(.center)
                    .padding()
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 0) {
                if let warning = model.sizeWarning {
                    Text(warning)
                        .font(DSFont.iconMD)
                        .foregroundStyle(DSColor.indicatorWaiting)
                        .padding(.horizontal, DSSpacing.sm)
                        .padding(.vertical, DSSpacing.xxs)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Divider()
                }
                DiffView(hunks: model.hunks)
            }
        }
    }
}
