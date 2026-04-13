// MARK: - WelcomeView
// Landing screen shown when no project is open.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI
import UniformTypeIdentifiers

// MARK: - WelcomeView

/// Welcome screen displayed when no project is active.
struct WelcomeView: View {

    @Environment(\.projectManager) private var projectManager
    @State private var showFileImporter = false
    @State private var showCreateNewSheet = false
    @State private var vm: AddProjectViewModel?

    private var viewModel: AddProjectViewModel {
        if let existing = vm { return existing }
        let created = AddProjectViewModel(projectManager: projectManager)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        VStack(spacing: 0) {
            Spacer()

            // App icon + title
            VStack(spacing: DSSpacing.sm) {
                Image(systemName: "terminal.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(DSColor.accentPrimary)

                Text("VibeStudio")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)

                Text("Open a folder to get started")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textSecondary)
            }

            Spacer().frame(height: DSSpacing.xl)

            // Action buttons
            HStack(spacing: DSSpacing.sm) {
                // Create New
                Button {
                    showCreateNewSheet = true
                } label: {
                    HStack(spacing: DSSpacing.xs) {
                        Image(systemName: "plus.square")
                        Text("Create New")
                    }
                    .font(DSFont.buttonLabel)
                    .foregroundStyle(DSColor.buttonPrimaryText)
                    .padding(.horizontal, DSSpacing.lg)
                    .padding(.vertical, DSSpacing.sm)
                    .background(DSColor.buttonPrimaryBg, in: RoundedRectangle(cornerRadius: DSRadius.md))
                }
                .buttonStyle(.plain)

                // Open Folder
                Button {
                    showFileImporter = true
                } label: {
                    HStack(spacing: DSSpacing.xs) {
                        Image(systemName: "folder.badge.plus")
                        Text("Open Folder")
                    }
                    .font(DSFont.buttonLabel)
                    .foregroundStyle(DSColor.textPrimary)
                    .padding(.horizontal, DSSpacing.lg)
                    .padding(.vertical, DSSpacing.sm)
                    .background(DSColor.surfaceOverlay, in: RoundedRectangle(cornerRadius: DSRadius.md))
                    .overlay(
                        RoundedRectangle(cornerRadius: DSRadius.md)
                            .stroke(DSColor.borderDefault, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .keyboardShortcut("o", modifiers: .command)
            }

            // Open projects (shown when returning from CodeSpeak mode — projects exist but none active)
            if !projectManager.projects.isEmpty {
                Spacer().frame(height: DSSpacing.xl)

                VStack(alignment: .leading, spacing: DSSpacing.xs) {
                    Text("PROJECTS")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)
                        .padding(.bottom, 2)

                    ForEach(projectManager.projects.sorted { $0.lastOpened > $1.lastOpened }) { project in
                        RecentProjectRow(project: project) {
                            projectManager.activeProjectId = project.id
                        }
                    }
                }
                .frame(maxWidth: 420)
            }

            // Recent projects (previously added but since removed)
            if !projectManager.recentProjects.isEmpty {
                Spacer().frame(height: DSSpacing.xl)

                VStack(alignment: .leading, spacing: DSSpacing.xs) {
                    Text("RECENT")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)
                        .padding(.bottom, 2)

                    ForEach(projectManager.recentProjects) { project in
                        RecentProjectRow(project: project) {
                            _ = model.openRecentProject(project)
                        }
                    }
                }
                .frame(maxWidth: 420)
            }

            // Inline error (e.g. path no longer exists)
            if let err = model.openError {
                Spacer().frame(height: DSSpacing.sm)
                Text(err)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 420)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DSColor.surfaceBase)
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            model.openProject(at: url)
        }
        .sheet(isPresented: $showCreateNewSheet) {
            CreateNewProjectSheet()
        }
        .onAppear {
            if vm == nil {
                vm = AddProjectViewModel(projectManager: projectManager)
            }
        }
    }
}

// MARK: - RecentProjectRow

private struct RecentProjectRow: View {
    let project: Project
    let onTap: () -> Void

    @State private var isHovering = false

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "folder.fill")
                    .foregroundStyle(DSColor.gitModified)
                    .font(.system(size: 14))

                VStack(alignment: .leading, spacing: 2) {
                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textPrimary)

                    Text(project.path.path)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }

                Spacer()
            }
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
            .background(
                RoundedRectangle(cornerRadius: DSRadius.sm)
                    .fill(isHovering ? DSColor.textPrimary.opacity(0.07) : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { isHovering = $0 }
    }
}

// MARK: - CreateNewProjectSheet

struct CreateNewProjectSheet: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(\.projectManager) private var projectManager

    @State private var projectName: String = ""
    @State private var parentFolder: URL? = nil
    @State private var showFolderPicker = false
    @State private var errorMessage: String? = nil

    private var trimmedName: String { projectName.trimmingCharacters(in: .whitespaces) }
    private var canCreate: Bool { !trimmedName.isEmpty && parentFolder != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            Text("Create New Project")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(DSColor.textPrimary)
                .padding(.bottom, DSSpacing.lg)

            // Project name field
            fieldLabel("Project Name")
            TextField("my-project", text: $projectName)
                .textFieldStyle(.roundedBorder)
                .padding(.bottom, DSSpacing.md)

            // Location picker
            fieldLabel("Location")
            HStack(spacing: DSSpacing.sm) {
                Text(parentFolder?.abbreviatingWithTilde ?? "Choose a folder…")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(parentFolder != nil ? DSColor.textPrimary : DSColor.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button("Browse…") { showFolderPicker = true }
                    .buttonStyle(.bordered)
            }
            .padding(DSSpacing.sm)
            .background(DSColor.surfaceInput)
            .clipShape(RoundedRectangle(cornerRadius: DSRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: DSRadius.sm)
                    .stroke(DSColor.borderDefault, lineWidth: 1)
            )
            .padding(.bottom, DSSpacing.xs)

            // Preview path
            if let parent = parentFolder, !trimmedName.isEmpty {
                Text(parent.appendingPathComponent(trimmedName).path)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }

            Spacer()

            // Error
            if let err = errorMessage {
                Text(err)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .padding(.bottom, DSSpacing.sm)
            }

            // Buttons
            HStack {
                Button("Cancel") { dismiss() }
                    .buttonStyle(.bordered)
                Spacer()
                Button("Create") { createProject() }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canCreate)
                    .keyboardShortcut(.return, modifiers: .command)
            }
        }
        .padding(DSSpacing.xl)
        .frame(width: 480, height: 280)
        .background(DSColor.surfaceBase)
        .fileImporter(
            isPresented: $showFolderPicker,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                parentFolder = url
            }
        }
    }

    private func fieldLabel(_ text: String) -> some View {
        Text(text)
            .font(DSFont.sidebarItemSmall)
            .foregroundStyle(DSColor.textSecondary)
            .padding(.bottom, 4)
    }

    private func createProject() {
        guard canCreate, let parent = parentFolder else { return }
        errorMessage = nil

        let newURL = parent.appendingPathComponent(trimmedName)
        do {
            try FileManager.default.createDirectory(
                at: newURL,
                withIntermediateDirectories: true,
                attributes: nil
            )
            let project = try projectManager.addProject(at: newURL)
            projectManager.activeProjectId = project.id
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

// MARK: - URL convenience

private extension URL {
    /// ~/path/to/dir → ~/path/to/dir (shorter display).
    var abbreviatingWithTilde: String {
        path.replacingOccurrences(
            of: NSHomeDirectory(),
            with: "~"
        )
    }
}
