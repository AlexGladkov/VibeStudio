// MARK: - InstallAgentSheet
// Installation wizard for AI CLI agents.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - InstallAgentSheet

/// Step-by-step installation guide for a specific AI CLI agent.
struct InstallAgentSheet: View {

    let assistant: AIAssistant

    @Environment(\.dismiss) private var dismiss
    @State private var copiedCommand: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            Divider().background(DSColor.borderDefault)

            ScrollView {
                VStack(alignment: .leading, spacing: DSSpacing.xl) {
                    steps
                }
                .padding(DSSpacing.lg)
            }

            Divider().background(DSColor.borderDefault)
            footer
        }
        .frame(minWidth: 400, idealWidth: 480, minHeight: 360, idealHeight: 420)
        .background(DSColor.surfaceOverlay)
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text("Install \(assistant.displayName)")
                .font(DSFont.sheetTitle)
                .foregroundStyle(DSColor.textPrimary)
            Text(assistant.shortDescription)
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(DSSpacing.lg)
    }

    // MARK: - Steps

    @ViewBuilder
    private var steps: some View {
        let hasPrereq = assistant.prerequisite != nil && assistant.prerequisiteCheckCommand != nil
        let installStep = hasPrereq ? 2 : 1
        let setupStep = installStep + 1

        // Step: Prerequisites
        if let prereq = assistant.prerequisite,
           let checkCmd = assistant.prerequisiteCheckCommand {
            stepView(
                number: 1,
                title: "Установи \(prereq)",
                description: "Убедись что установлена нужная версия:",
                command: checkCmd
            )
        }

        // Step: Install
        stepView(
            number: installStep,
            title: "Установи \(assistant.displayName)",
            description: "Выполни команду в терминале:",
            command: assistant.installHint
        )

        // Step: Setup (API key / auth)
        if let setup = assistant.setupInstructions {
            setupStepView(number: setupStep, instructions: setup)
        }
    }

    // MARK: - Step Views

    private func stepView(
        number: Int,
        title: String,
        description: String,
        command: String
    ) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            stepHeader(number: number, title: title)

            Text(description)
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textSecondary)

            commandBlock(command)
        }
    }

    private func setupStepView(number: Int, instructions: String) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            stepHeader(number: number, title: "Настройка")

            Text(instructions)
                .font(DSFont.monoPath)
                .foregroundStyle(DSColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(DSSpacing.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DSColor.surfaceBase, in: RoundedRectangle(cornerRadius: DSRadius.sm))
                .overlay(
                    RoundedRectangle(cornerRadius: DSRadius.sm)
                        .stroke(DSColor.borderDefault, lineWidth: 1)
                )
        }
    }

    private func stepHeader(number: Int, title: String) -> some View {
        HStack(spacing: DSSpacing.sm) {
            Text("\(number)")
                .font(DSFont.smallButtonLabel)
                .foregroundStyle(DSColor.textInverse)
                .frame(width: DSLayout.stepIndicatorSize, height: DSLayout.stepIndicatorSize)
                .background(DSColor.accentPrimary, in: Circle())

            Text(title)
                .font(DSFont.gitBranch)
                .foregroundStyle(DSColor.textPrimary)
        }
    }

    private func commandBlock(_ command: String) -> some View {
        HStack(spacing: DSSpacing.sm) {
            Text(command)
                .font(DSFont.monoPath)
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(2)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(command, forType: .string)
                copiedCommand = command
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    if copiedCommand == command { copiedCommand = nil }
                }
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: copiedCommand == command ? "checkmark" : "doc.on.doc")
                        .font(DSFont.iconMD)
                    Text(copiedCommand == command ? "Copied" : "Copy")
                        .font(DSFont.sidebarItemSmall)
                }
                .foregroundStyle(copiedCommand == command ? DSColor.actionRun : DSColor.textSecondary)
                .padding(.horizontal, DSSpacing.sm)
                .frame(height: 24) // slightly smaller than gitButtonHeight
                .background(DSColor.surfaceRaised, in: RoundedRectangle(cornerRadius: DSRadius.sm))
                .overlay(
                    RoundedRectangle(cornerRadius: DSRadius.sm)
                        .stroke(DSColor.borderDefault, lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
        }
        .padding(DSSpacing.sm)
        .background(DSColor.surfaceBase, in: RoundedRectangle(cornerRadius: DSRadius.sm))
        .overlay(
            RoundedRectangle(cornerRadius: DSRadius.sm)
                .stroke(DSColor.borderDefault, lineWidth: 1)
        )
    }

    // MARK: - Footer

    private var footer: some View {
        HStack {
            Text("После установки перезапусти VibeStudio")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Spacer()

            Button("Done") { dismiss() }
                .buttonStyle(.plain)
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.buttonPrimaryText)
                .frame(width: 72, height: DSLayout.gitButtonHeight)
                .background(DSColor.buttonPrimaryBg, in: RoundedRectangle(cornerRadius: DSRadius.md))
        }
        .padding(DSSpacing.lg)
    }
}
