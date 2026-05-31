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
    var selectedSpec: SpecFile?

    /// Currently selected generated file (read-only viewer).
    var selectedGenerated: GeneratedFile?

    /// Raw markdown content of the selected spec (bound to the editor).
    var editorContent: String = ""

    /// True when `editorContent` has unsaved changes.
    var isEditorDirty: Bool = false

    /// Generated files discovered in the active project root.
    var generatedFiles: [GeneratedFile] = []

    /// Timestamp of the last successful spec save. Observed by `CodeSpeakModeView`
    /// to trigger auto-build when `csPreferences.autoBuildOnSave` is enabled.
    private(set) var savedAt: Date?

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
        selectedGenerated = nil
        editorContent = (try? String(contentsOf: spec.url, encoding: .utf8)) ?? ""
        isEditorDirty = false
    }

    /// Open a generated file in the read-only viewer.
    func selectGeneratedFile(_ file: GeneratedFile) {
        if isEditorDirty {
            Task { await saveCurrentSpec() }
        }
        selectedSpec = nil
        selectedGenerated = file
        editorContent = (try? String(contentsOf: file.url, encoding: .utf8)) ?? ""
        isEditorDirty = false
    }

    // MARK: - Generated File Scanner

    /// Directories and file names to skip when scanning for generated files.
    private static let skipDirectories: Set<String> = [
        "spec", ".git", "node_modules", ".build", ".swiftpm",
        "__pycache__", ".venv", "venv", "vendor", "dist", "build"
    ]
    private static let skipFilenames: Set<String> = [
        "codespeak.json"
    ]

    /// Scans the project root (one level deep) for files that contain a
    /// CodeSpeak marker in their first 3 lines. Results are stored in
    /// `generatedFiles` sorted by file name.
    func scanGenerated(at projectRoot: URL) async {
        let fm = FileManager.default
        guard let entries = try? fm.contentsOfDirectory(
            at: projectRoot,
            includingPropertiesForKeys: [.isDirectoryKey, .isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else { return }

        var found: [GeneratedFile] = []

        for entry in entries {
            let name = entry.lastPathComponent

            // Skip known directories and special files
            guard !Self.skipFilenames.contains(name) else { continue }

            let isDir = (try? entry.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) ?? false
            if isDir {
                guard !Self.skipDirectories.contains(name) else { continue }
                // One level deeper inside non-skipped directories
                if let subEntries = try? fm.contentsOfDirectory(
                    at: entry,
                    includingPropertiesForKeys: [.isRegularFileKey],
                    options: [.skipsHiddenFiles]
                ) {
                    for sub in subEntries {
                        if let file = await makeGeneratedFile(at: sub) {
                            found.append(file)
                        }
                    }
                }
            } else {
                // Skip .cs.md spec files
                guard !name.hasSuffix(".cs.md") else { continue }
                if let file = await makeGeneratedFile(at: entry) {
                    found.append(file)
                }
            }
        }

        generatedFiles = found.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func makeGeneratedFile(at url: URL) async -> GeneratedFile? {
        guard let contents = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        guard GeneratedFile.isGenerated(contents) else { return nil }
        let specName = GeneratedFile.parseSpecName(from: contents)
        return GeneratedFile(id: UUID(), url: url, specName: specName)
    }

    // MARK: - Save

    /// Persist `editorContent` back to disk (no-op if not dirty or no spec selected).
    func saveCurrentSpec() async {
        guard let spec = selectedSpec, isEditorDirty else { return }
        do {
            try Data(editorContent.utf8).write(to: spec.url)
            isEditorDirty = false
            savedAt = Date()
        } catch {
            Logger.services.error("CodeSpeakModeViewModel: failed to save spec: \(error.localizedDescription, privacy: .public)")
        }
    }

    // MARK: - Build Coordination (ARCH-M3)

    /// Reset editor state when the active project changes. Called from the
    /// view's `.onChange(of: projectManager.activeProjectId)` handler so the
    /// previous spec's content doesn't leak into the new project's editor.
    func resetForProjectSwitch() {
        selectedSpec = nil
        selectedGenerated = nil
        editorContent = ""
        isEditorDirty = false
    }

    /// Reload spec and generated-file trees for `projectPath`. Called after
    /// a project switch.
    func reload(projectPath: URL) async {
        await specsVM.loadSpecs(at: projectPath)
        await scanGenerated(at: projectPath)
    }

    /// Mirror the toolbar-owned `CodeSpeakRunBarState` command/parameters
    /// into ``buildVM`` and execute the build for the supplied project /
    /// spec.
    ///
    /// Centralises the "play button pressed" coordination that previously
    /// lived in the view's `.onChange(of: codeSpeakBuildRequested)` block.
    ///
    /// - Parameters:
    ///   - projectPath: Active project root.
    ///   - specURL: URL of the currently selected spec (passed through to
    ///     enable spec-aware commands).
    ///   - runBar: Snapshot of the toolbar run-bar state at the moment the
    ///     run was triggered.
    func runBuild(at projectPath: URL, specURL: URL?, runBar: CodeSpeakRunBarState) async {
        buildVM.selectedCommand = runBar.command
        buildVM.taskName = runBar.taskName
        buildVM.changeMessage = runBar.changeMessage
        await buildVM.run(at: projectPath, specPath: specURL)
    }

    /// Run the default `build` command — used by the auto-build-on-save
    /// hook and by `task(id:)` "build on project open".
    func runDefaultBuild(at projectPath: URL, specURL: URL? = nil) async {
        buildVM.selectedCommand = .build
        await buildVM.run(at: projectPath, specPath: specURL)
    }
}
