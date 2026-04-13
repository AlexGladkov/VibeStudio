// MARK: - SpecWizardSheet
// Simple sheet for creating a new .cs.md spec file by name.
// macOS 14+, Swift 5.10

import SwiftUI

/// Sheet that asks for a spec file name and creates an empty `.cs.md` file.
///
/// Sheet size: 400 × 220.
struct SpecWizardSheet: View {

    let projectPath: URL
    var onCreated: (() -> Void)?

    @Environment(\.dismiss) private var dismiss

    @State private var specName: String = ""
    @State private var errorMessage: String?
    @State private var isSaving = false

    // MARK: - Derived

    private var sanitizedName: String {
        specName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: " ", with: "-")
            .filter { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }
    }

    private var canCreate: Bool {
        !sanitizedName.isEmpty
    }

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                Image(systemName: "doc.badge.plus")
                    .foregroundStyle(DSColor.agentCodeSpeak)
                Text("New Spec")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)
                Spacer()
            }
            .padding(.horizontal, DSSpacing.lg)
            .padding(.vertical, DSSpacing.md)

            Divider()

            // Content
            VStack(alignment: .leading, spacing: DSSpacing.sm) {
                Text("File name")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)

                TextField("e.g. user-auth", text: $specName)
                    .styledInput()
                    .onSubmit { createSpec() }

                if !sanitizedName.isEmpty {
                    Text("spec/\(sanitizedName).cs.md")
                        .font(.system(size: 10))
                        .foregroundStyle(DSColor.textMuted)
                }

                if let error = errorMessage {
                    Text(error)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.gitDeleted)
                }
            }
            .padding(DSSpacing.lg)

            Spacer()

            Divider()

            // Footer
            HStack {
                Button("Cancel") { dismiss() }
                    .buttonStyle(.plain)
                    .foregroundStyle(DSColor.textSecondary)
                    .padding(.horizontal, DSSpacing.md)
                    .padding(.vertical, DSSpacing.xs)
                    .background(DSColor.surfaceOverlay, in: RoundedRectangle(cornerRadius: DSRadius.md))
                    .overlay(
                        RoundedRectangle(cornerRadius: DSRadius.md)
                            .stroke(DSColor.borderDefault, lineWidth: 1)
                    )

                Spacer()

                Button("Create") {
                    createSpec()
                }
                .buttonStyle(.plain)
                .foregroundStyle(canCreate ? DSColor.buttonPrimaryText : DSColor.textMuted)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.xs)
                .background(
                    canCreate ? DSColor.agentCodeSpeak : DSColor.surfaceOverlay,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
                .disabled(!canCreate || isSaving)
            }
            .padding(.horizontal, DSSpacing.lg)
            .padding(.vertical, DSSpacing.md)
        }
        .background(DSColor.surfaceBase)
        .frame(width: 400, height: 220)
    }

    // MARK: - Action

    private func createSpec() {
        guard canCreate, !isSaving else { return }
        isSaving = true
        errorMessage = nil

        let specDir = projectPath.appending(path: "spec")
        let fileURL = specDir.appending(path: "\(sanitizedName).cs.md")

        let template = "# \(specName.trimmingCharacters(in: .whitespacesAndNewlines))\n\n"

        do {
            try FileManager.default.createDirectory(at: specDir, withIntermediateDirectories: true)
            try Data(template.utf8).write(to: fileURL, options: .atomic)
            onCreated?()
            dismiss()
        } catch {
            errorMessage = "Failed to create file: \(error.localizedDescription)"
            isSaving = false
        }
    }
}
