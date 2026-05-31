// MARK: - SettingsToggleRow
// Switch-style preference row with a title and optional description.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsToggleRow

/// A horizontal switch-style preference row used inside settings panes.
///
/// Layout (matches the previous local `preferenceRow` helper in
/// `CodeSpeakSettingsPane`):
/// ```
/// title                                       (.switch)
/// description?
/// ```
///
/// Visual tokens (intentionally identical to the prior inline implementation
/// — do **not** swap to `bodyMedium`/`bodySmall`; those are larger and would
/// break the existing settings card density):
/// - title: `DSFont.buttonLabel` (12pt medium), `DSColor.textPrimary`
/// - description: `DSFont.sidebarItemSmall` (11pt), `DSColor.textSecondary`
/// - Toggle: `.switch` style, `.controlSize(.small)`, labels hidden
///
/// Caller is responsible for wrapping the row in `.settingsCard()` and
/// inserting `Divider().padding(.leading, DSSpacing.md)` between consecutive
/// rows — the existing CodeSpeak pane already does this and we keep its
/// structure.
struct SettingsToggleRow: View {

    // MARK: Properties

    /// Primary title (always present).
    let title: String

    /// Optional secondary description rendered beneath the title.
    let description: String?

    /// Two-way binding driving the underlying switch.
    @Binding var isOn: Bool

    // MARK: Init

    init(title: String, description: String? = nil, isOn: Binding<Bool>) {
        self.title = title
        self.description = description
        self._isOn = isOn
    }

    // MARK: - Body

    var body: some View {
        HStack(spacing: DSSpacing.sm) {
            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text(title)
                    .font(DSFont.buttonLabel)
                    .foregroundStyle(DSColor.textPrimary)

                if let description {
                    Text(description)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Spacer()

            Toggle("", isOn: $isOn)
                .toggleStyle(.switch)
                .labelsHidden()
                .controlSize(.small)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }
}
