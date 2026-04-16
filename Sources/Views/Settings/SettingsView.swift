// MARK: - SettingsView
// Main settings window with sidebar navigation and content pane.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsView

/// Root view for the Settings window.
///
/// Uses a custom `HStack` layout with an enum-driven sidebar
/// (no `NavigationSplitView`). The sidebar lists all ``SettingsSectionGroup``
/// items; the content pane renders the selected ``SettingsItem``.
struct SettingsView: View {

    @Environment(\.dismiss) private var dismiss
    @State private var selectedItem: SettingsItem = .appearance

    var body: some View {
        HStack(spacing: 0) {
            sidebar
            Divider()
            contentPane
        }
        .frame(minWidth: DSLayout.settingsWindowMinWidth, idealWidth: DSLayout.settingsWindowIdealWidth, minHeight: DSLayout.settingsWindowMinHeight, idealHeight: DSLayout.settingsWindowIdealHeight)
        .background(DSColor.surfaceDefault)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Закрыть") {
                    dismiss()
                }
            }
        }
    }

    // MARK: - Sidebar

    private var sidebar: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                ForEach(SettingsSectionGroup.allCases) { group in
                    sectionHeader(group.displayName)

                    ForEach(group.items) { item in
                        sidebarRow(item)
                    }
                }
            }
            .padding(.vertical, DSSpacing.sm)
            .padding(.horizontal, DSSpacing.sm)
        }
        .frame(width: DSLayout.settingsSidebarWidth)
        .background(DSColor.surfaceRaised)
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title.uppercased())
            .font(DSFont.statusBadge)
            .foregroundStyle(DSColor.textMuted)
            .padding(.horizontal, DSSpacing.md)
            .padding(.top, DSSpacing.sm)
            .padding(.bottom, DSSpacing.xxs)
    }

    private func sidebarRow(_ item: SettingsItem) -> some View {
        let isSelected = selectedItem == item

        return Button {
            selectedItem = item
        } label: {
            HStack(spacing: DSSpacing.sm) {
                itemIcon(item, isSelected: isSelected)
                    .frame(width: DSLayout.smallIconButtonSize, height: DSLayout.smallIconButtonSize)

                Text(item.displayName)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(isSelected ? DSColor.textPrimary : DSColor.textSecondary)

                Spacer()
            }
            .padding(.horizontal, DSSpacing.md)
            .padding(.vertical, DSSpacing.xs)
            .background(
                RoundedRectangle(cornerRadius: DSRadius.md)
                    .fill(isSelected ? DSColor.accentPrimarySubtle : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func itemIcon(_ item: SettingsItem, isSelected: Bool) -> some View {
        switch item {
        case .appearance:
            Image(systemName: item.systemImage)
                .font(DSFont.bodySmall)
                .foregroundStyle(isSelected ? DSColor.accentPrimary : DSColor.textSecondary)
        case .llmAssistant(let assistant):
            AIAssistantIconView(assistant: assistant, size: 14) // iconLG point size
                .opacity(isSelected ? 1.0 : 0.6)
        }
    }

    // MARK: - Content Pane

    @ViewBuilder
    private var contentPane: some View {
        Group {
            switch selectedItem {
            case .appearance:
                GeneralSettingsPane()
            case .llmAssistant(let assistant):
                LLMSettingsPane(assistant: assistant)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(DSColor.surfaceDefault)
    }
}

