// MARK: - TraceabilityPanelViewModel
// Scans spec files for @file: markers and builds bidirectional traceability map.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

// MARK: - TraceabilityLink

/// A bidirectional link between a spec and a source file.
struct TraceabilityLink: Identifiable, Hashable {
    let id: UUID
    /// The `.cs.md` spec file.
    let specURL: URL
    /// The spec's human-readable name.
    let specName: String
    /// The source file referenced by `@file:` marker.
    let sourceFile: String
}

// MARK: - TraceabilityPanelViewModel

/// ViewModel for `TraceabilityPanelView`.
///
/// Scans all spec files in `spec/*.cs.md` for `@file:` markers of the form:
/// ```
/// @file: Sources/MyModule/MyFile.swift
/// ```
/// and builds a bidirectional index:
/// - `specToFiles`: spec → list of source files it references
/// - `fileToSpecs`: source file → list of specs that reference it
@Observable
@MainActor
final class TraceabilityPanelViewModel {

    // MARK: - State

    /// Maps spec URL → list of source files referenced via `@file:`.
    private(set) var specToFiles: [URL: [String]] = [:]

    /// Maps source file path → list of spec names that reference it.
    private(set) var fileToSpecs: [String: [String]] = [:]

    /// All unique source files referenced by any spec.
    private(set) var referencedFiles: [String] = []

    /// All unique spec names in the map.
    private(set) var allSpecNames: [String] = []

    /// True while scanning.
    private(set) var isLoading = false

    // MARK: - Scan

    /// Scan all `spec/*.cs.md` files for `@file:` markers.
    func scan(at projectRoot: URL) async {
        isLoading = true
        defer { isLoading = false }

        let specDir = projectRoot.appending(path: "spec")
        guard FileManager.default.fileExists(atPath: specDir.path) else {
            specToFiles = [:]
            fileToSpecs = [:]
            referencedFiles = []
            allSpecNames = []
            return
        }

        var newSpecToFiles: [URL: [String]] = [:]
        var newFileToSpecs: [String: [String]] = [:]

        do {
            let contents = try FileManager.default.contentsOfDirectory(
                at: specDir,
                includingPropertiesForKeys: nil,
                options: [.skipsHiddenFiles]
            )
            let specFiles = contents.filter {
                $0.pathExtension == "md" && $0.deletingPathExtension().pathExtension == "cs"
            }

            for specURL in specFiles {
                let specName = specURL.deletingPathExtension().deletingPathExtension().lastPathComponent
                guard let content = try? String(contentsOf: specURL, encoding: .utf8) else { continue }

                let lines = content.components(separatedBy: "\n")
                let refs = lines
                    .filter { $0.lowercased().hasPrefix("@file:") }
                    .map { String($0.dropFirst("@file:".count)).trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }

                if !refs.isEmpty {
                    newSpecToFiles[specURL] = refs
                    for ref in refs {
                        newFileToSpecs[ref, default: []].append(specName)
                    }
                }
            }
        } catch {
            Logger.services.error("TraceabilityPanelViewModel: \(error.localizedDescription, privacy: .public)")
        }

        specToFiles = newSpecToFiles
        fileToSpecs = newFileToSpecs
        referencedFiles = Array(newFileToSpecs.keys).sorted()
        allSpecNames = Array(Set(newSpecToFiles.values.flatMap { _ in [] }
            + newSpecToFiles.keys.map {
                $0.deletingPathExtension().deletingPathExtension().lastPathComponent
            })).sorted()
    }
}
