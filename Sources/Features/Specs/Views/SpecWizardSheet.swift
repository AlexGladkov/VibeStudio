// MARK: - SpecWizardSheet
// Multi-step wizard for creating a new spec with AI generation.
// macOS 14+, Swift 5.10

import SwiftUI

/// Multi-step sheet that guides the user through creating a new `.cs.md` spec.
///
/// Step 1: Enter spec name + description.
/// Step 2: AI generation in progress (spinner).
/// Step 3: Preview generated content + save.
///
/// Sheet size: 500 × 500.
struct SpecWizardSheet: View {

    let projectPath: URL
    var onCreated: (() -> Void)?

    @Environment(\.dismiss) private var dismiss

    @State private var vm: SpecWizardViewModel?

    private var viewModel: SpecWizardViewModel {
        if let existing = vm { return existing }
        let created = SpecWizardViewModel(projectPath: projectPath)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel

        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                Image(systemName: "doc.badge.plus")
                    .foregroundStyle(DSColor.agentCodeSpeak)
                Text("New Spec")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)
                Spacer()
                stepIndicator(model: model)
            }
            .padding(.horizontal, DSSpacing.lg)
            .padding(.vertical, DSSpacing.md)

            Divider()

            // Step content
            Group {
                switch model.currentStep {
                case .nameAndDescription:
                    stepOneView(model: model)
                case .generating:
                    generatingView
                case .previewAndSave:
                    stepThreeView(model: model)
                }
            }
            .frame(maxHeight: .infinity)

            Divider()

            // Footer
            footerView(model: model)
        }
        .background(DSColor.surfaceBase)
        .frame(width: 500, height: 500)
        .onAppear {
            if vm == nil { vm = SpecWizardViewModel(projectPath: projectPath) }
        }
    }

    // MARK: - Step Indicator

    private func stepIndicator(model: SpecWizardViewModel) -> some View {
        HStack(spacing: DSSpacing.xs) {
            ForEach(WizardStep.allCases, id: \.rawValue) { step in
                if step != .generating {
                    Circle()
                        .fill(model.currentStep == step ? DSColor.agentCodeSpeak : DSColor.textMuted.opacity(0.3))
                        .frame(width: 6, height: 6)
                }
            }
        }
    }

    // MARK: - Step 1: Name & Description

    private func stepOneView(model: SpecWizardViewModel) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.lg) {
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Spec name")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField("e.g. user-auth", text: Binding(
                    get: { model.specName },
                    set: { model.specName = $0 }
                ))
                .styledInput()

                if !model.sanitizedName.isEmpty {
                    Text("spec/\(model.sanitizedName).cs.md")
                        .font(.system(size: 10))
                        .foregroundStyle(DSColor.textMuted)
                }
            }

            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Description")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField(
                    "Describe what this spec should cover...",
                    text: Binding(
                        get: { model.description },
                        set: { model.description = $0 }
                    ),
                    axis: .vertical
                )
                .lineLimit(4...6)
                .styledInput()
            }

            if let error = model.errorMessage {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
            }

            Spacer()
        }
        .padding(DSSpacing.lg)
    }

    // MARK: - Step 2: Generating

    private var generatingView: some View {
        VStack(spacing: DSSpacing.md) {
            Spacer()
            ProgressView()
                .scaleEffect(1.2)
            Text("Generating spec with AI…")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Step 3: Preview & Edit

    private func stepThreeView(model: SpecWizardViewModel) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("PREVIEW — edit before saving")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textMuted)
                .padding(.horizontal, DSSpacing.lg)
                .padding(.vertical, DSSpacing.xs)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DSColor.surfaceOverlay)

            ScrollView {
                TextEditor(text: Binding(
                    get: { model.generatedContent },
                    set: { model.generatedContent = $0 }
                ))
                .font(DSFont.terminal(size: 12))
                .foregroundStyle(DSColor.textPrimary)
                .scrollContentBackground(.hidden)
                .background(Color.clear)
                .padding(DSSpacing.md)
            }
            .background(DSColor.surfaceBase)

            if let error = model.errorMessage {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .padding(.horizontal, DSSpacing.lg)
                    .padding(.vertical, DSSpacing.xs)
            }
        }
    }

    // MARK: - Footer

    private func footerView(model: SpecWizardViewModel) -> some View {
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

            switch model.currentStep {
            case .nameAndDescription:
                Button("Generate with AI") {
                    Task { await model.generateSpec() }
                }
                .buttonStyle(.plain)
                .foregroundStyle(model.canGenerate ? DSColor.buttonPrimaryText : DSColor.textMuted)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.xs)
                .background(
                    model.canGenerate ? DSColor.agentCodeSpeak : DSColor.surfaceOverlay,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
                .disabled(!model.canGenerate)

            case .generating:
                EmptyView()

            case .previewAndSave:
                Button("Back") {
                    model.currentStep = .nameAndDescription
                }
                .buttonStyle(.plain)
                .foregroundStyle(DSColor.textSecondary)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.xs)
                .background(DSColor.surfaceOverlay, in: RoundedRectangle(cornerRadius: DSRadius.md))
                .overlay(
                    RoundedRectangle(cornerRadius: DSRadius.md)
                        .stroke(DSColor.borderDefault, lineWidth: 1)
                )

                Button("Save Spec") {
                    Task {
                        if await model.save() {
                            onCreated?()
                            dismiss()
                        }
                    }
                }
                .buttonStyle(.plain)
                .foregroundStyle(model.canSave ? DSColor.buttonPrimaryText : DSColor.textMuted)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.xs)
                .background(
                    model.canSave ? DSColor.buttonPrimaryBg : DSColor.surfaceOverlay,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
                .disabled(!model.canSave || model.isSaving)
            }
        }
        .padding(.horizontal, DSSpacing.lg)
        .padding(.vertical, DSSpacing.md)
    }
}
