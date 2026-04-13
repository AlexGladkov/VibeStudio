// MARK: - CodeSpeakModeViewModel
// ViewModel for the 3-column CodeSpeak mode layout.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// ViewModel for `CodeSpeakModeView`.
///
/// Manages the selected spec, its editor content, and the build panel state.
/// Owns `SpecsViewModel` and `SpecBuildPanelViewModel` as sub-view-models so
/// the build output and spec list share the same lifecycle as the mode view.
@Observable
@MainActor
final class CodeSpeakModeViewModel {

    // MARK: - State

    /// Currently selected spec file for editing.
    var selectedSpec: SpecFile? = nil

    /// Raw markdown content of the selected spec (bound to the editor).
    var editorContent: String = ""

    /// True when `editorContent` has unsaved changes.
    var isEditorDirty: Bool = false

    // MARK: - Sub-ViewModels

    let specsVM: SpecsViewModel
    let buildVM: SpecBuildPanelViewModel

    // MARK: - Init

    init(codeSpeak: CodeSpeakService, projectManager: any ProjectManaging) {
        specsVM = SpecsViewModel()
        buildVM = SpecBuildPanelViewModel(codeSpeak: codeSpeak, projectManager: projectManager)
    }

    // MARK: - Spec Selection

    /// Load the given spec into the editor.
    func selectSpec(_ spec: SpecFile) {
        if isEditorDirty {
            Task { await saveCurrentSpec() }
        }
        selectedSpec = spec
        editorContent = (try? String(contentsOf: spec.url, encoding: .utf8)) ?? ""
        isEditorDirty = false
    }

    // MARK: - Save

    /// Persist `editorContent` back to disk (no-op if not dirty or no spec selected).
    func saveCurrentSpec() async {
        guard let spec = selectedSpec, isEditorDirty else { return }
        do {
            try Data(editorContent.utf8).write(to: spec.url)
            isEditorDirty = false
        } catch {
            Logger.services.error("CodeSpeakModeViewModel: failed to save spec: \(error.localizedDescription, privacy: .public)")
        }
    }
}
