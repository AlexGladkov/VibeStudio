// MARK: - SidebarSectionHeader
// Reusable uppercase header for sidebar/panel sections (FILES, GIT, SPECS, CHANGES).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SidebarSectionHeader

/// Section header used inside the sidebar and right-side panels (Specs,
/// Changes, Files, Git).
///
/// Renders the uppercase section title in the standard sidebar style
/// (`DSFont.sidebarSection`, `DSColor.textSecondary`) and lets callers inject
/// optional badge content (e.g. counters, pass/fail stats) and trailing
/// actions (refresh, add, build…).
///
/// Layout:
/// ```
/// [ title ] [ badge? ]  ──────  [ trailing… ]
/// ```
///
/// All horizontal padding is matched to existing inline headers
/// (`DSSpacing.md`) and the frame height uses `DSLayout.gitSectionHeaderHeight`
/// so it slots in next to the legacy headers without a visual shift.
struct SidebarSectionHeader<Badge: View, Trailing: View>: View {

    // MARK: Properties

    /// Section title; displayed verbatim (no auto-uppercasing — callers pass
    /// `"SPECS"`, `"FILES"`, etc. as they already do today).
    let title: String

    /// Inline badge slot rendered immediately to the right of the title
    /// (typically a counter or pass/fail label).
    let badge: Badge

    /// Trailing slot rendered after the flexible spacer (typically
    /// refresh/add/build buttons).
    let trailing: Trailing

    // MARK: Init

    init(
        title: String,
        @ViewBuilder badge: () -> Badge,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.title = title
        self.badge = badge()
        self.trailing = trailing()
    }

    // MARK: - Body

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            Text(title)
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textSecondary)

            badge

            Spacer()

            trailing
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }
}

// MARK: - Convenience Initialisers

extension SidebarSectionHeader where Badge == EmptyView, Trailing == EmptyView {
    /// Title-only header (no badge, no trailing actions).
    init(title: String) {
        self.init(title: title, badge: { EmptyView() }, trailing: { EmptyView() })
    }
}

extension SidebarSectionHeader where Badge == EmptyView {
    /// Header with trailing actions only — trailing-closure form.
    ///
    /// The trailing closure binds to `trailing` and the `badge` slot is
    /// fixed to `EmptyView`. This keeps the common call site terse:
    /// ```swift
    /// SidebarSectionHeader(title: "GIT") { refreshButton }
    /// ```
    init(title: String, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, badge: { EmptyView() }, trailing: trailing)
    }
}

// NOTE: a `(title:, badge:)` convenience used to live here too, but Swift
// could not disambiguate it from the trailing-actions variant above when
// callers used the bare `{ ... }` trailing-closure form. Callers that need
// a badge-only header must use the full three-parameter initialiser:
// `SidebarSectionHeader(title:, badge: { ... }, trailing: { EmptyView() })`.
