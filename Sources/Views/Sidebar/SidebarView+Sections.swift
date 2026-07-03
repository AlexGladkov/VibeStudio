// MARK: - SidebarView+Sections
// Extracted sub-views (icon-strip button, empty-state placeholder) to keep the
// SidebarView type body under the length limit.
// macOS 14+, Swift 5.10

import SwiftUI

extension SidebarView {

    // MARK: - Icon Strip Button

    func iconButton(section: SidebarSection, symbol: String) -> some View {
        let isActive = activeSection == section
        return Button {
            activeSection = section
        } label: {
            Image(systemName: symbol)
                .font(DSFont.iconLG)
                .foregroundStyle(isActive ? DSColor.accentPrimary : DSColor.textMuted)
                .frame(width: DSLayout.iconStripButtonSize, height: DSLayout.iconStripButtonSize)
                .background(isActive ? DSColor.surfaceOverlay : Color.clear)
                .cornerRadius(DSRadius.sm)
        }
        .buttonStyle(.plain)
        .sidebarHover(cornerRadius: DSRadius.sm)
    }

    // MARK: - No Project View

    func noProjectView() -> some View {
        VStack {
            Spacer()
            Text("No project selected")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
