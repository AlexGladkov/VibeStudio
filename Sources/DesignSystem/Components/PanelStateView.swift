// MARK: - PanelStateView
// Universal empty / loading / error state for side panels.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - PanelStateView

/// Centred placeholder shown inside a panel when the underlying content is
/// empty, loading or failed to load.
///
/// All three states share the same vertical layout:
///   `Spacer · {icon|ProgressView} · title · subtitle? · Spacer`
///
/// The component owns its outer `frame(maxWidth: .infinity, maxHeight: .infinity)`
/// so callers can drop it straight into a panel without extra wrappers.
///
/// Use ``Kind/loading`` to render a `ProgressView` in place of the SF Symbol —
/// the supplied `icon` is still kept for callers that pass one but is ignored
/// for the spinner case.
///
/// Visual tokens:
/// - icon: `DSFont.emptyStateIcon` (24pt)
/// - title: `DSFont.bodyMedium` (14pt) in `DSColor.textPrimary`
/// - subtitle: `DSFont.bodySmall` (12pt) in `DSColor.textMuted`
struct PanelStateView: View {

    /// Visual variant of the state.
    enum Kind {
        /// Empty data (e.g. "no specs"). Shows the supplied icon.
        case empty
        /// In-flight load. Shows a `ProgressView` spinner instead of the icon.
        case loading
        /// Error state. Shows the supplied icon (caller usually picks
        /// `exclamationmark.triangle` plus a danger colour).
        case error
    }

    // MARK: Properties

    /// State variant — drives whether the icon or a spinner is shown.
    let kind: Kind
    /// SF Symbol name for the empty/error variants. Ignored when `kind == .loading`.
    let icon: String
    /// Primary message (always present).
    let title: String
    /// Optional secondary message rendered below the title.
    let subtitle: String?
    /// Foreground colour for the icon (defaults to muted text).
    let iconColor: Color

    // MARK: Init

    init(
        kind: Kind = .empty,
        icon: String,
        title: String,
        subtitle: String? = nil,
        iconColor: Color = DSColor.textMuted
    ) {
        self.kind = kind
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.iconColor = iconColor
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: DSSpacing.sm) {
            Spacer()

            if kind == .loading {
                ProgressView()
                    .scaleEffect(DSLayout.progressScaleMedium)
            } else {
                Image(systemName: icon)
                    .font(DSFont.emptyStateIcon)
                    .foregroundStyle(iconColor)
            }

            Text(title)
                .font(DSFont.bodyMedium)
                .foregroundStyle(DSColor.textPrimary)

            if let subtitle {
                Text(subtitle)
                    .font(DSFont.bodySmall)
                    .foregroundStyle(DSColor.textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, DSSpacing.md)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
