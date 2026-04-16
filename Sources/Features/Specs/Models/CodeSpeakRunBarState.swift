// MARK: - CodeSpeakRunBarState
// Source of truth for the CodeSpeak toolbar run bar.
// macOS 14+, Swift 5.10

import Foundation
import Observation

/// Encapsulates state for the CodeSpeak toolbar run bar.
///
/// Extracted from `AppNavigationCoordinator` to respect SRP — the navigation
/// coordinator should not own feature-specific business state.
///
/// Observed by both `ToolbarView` (writer: command/taskName/changeMessage)
/// and `CodeSpeakModeView` (writer: isRunning/currentSpecName/isEditorDirty).
@Observable
@MainActor
final class CodeSpeakRunBarState {

    /// Selected command for the toolbar run bar.
    var command: CodeSpeakCommand = .build

    /// Task name input (used when `command == .task`).
    var taskName: String = ""

    /// Change message input (used when `command == .change`).
    var changeMessage: String = ""

    /// Mirrors `SpecBuildPanelViewModel.isRunning`; written by `CodeSpeakModeView`.
    var isRunning: Bool = false

    /// Set to `true` by the toolbar stop button; observed by `CodeSpeakModeView`.
    var stopRequested: Bool = false

    /// Name of the currently selected spec file; written by `CodeSpeakModeView`.
    /// Displayed in the titlebar breadcrumb.
    var currentSpecName: String = ""

    /// True when the spec editor has unsaved changes; written by `CodeSpeakModeView`.
    var isEditorDirty: Bool = false
}
