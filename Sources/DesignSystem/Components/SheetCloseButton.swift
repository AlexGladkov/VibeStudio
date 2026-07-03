// MARK: - SheetCloseButton
// Reusable close ("xmark.circle.fill") button for sheets and viewers.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SheetCloseButton

/// Standard close button shown in the top-right of editor/viewer sheets.
///
/// Wraps the repeated `Button { … } label: { Image("xmark.circle.fill") }`
/// pattern: a plain-styled filled xmark glyph in `DSColor.textMuted`, wired to
/// the Escape key so any sheet can be dismissed with a single keystroke.
///
/// - `action`: invoked on tap and on Escape.
/// - `font`: glyph size (default `DSFont.bodyMedium` = 14pt). Override only when
///   a sheet documents an intentionally different size.
struct SheetCloseButton: View {

    let action: () -> Void
    var font: Font = DSFont.bodyMedium

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark.circle.fill")
                .font(font)
                .foregroundStyle(DSColor.textMuted)
        }
        .buttonStyle(.plain)
        .keyboardShortcut(.escape, modifiers: [])
    }
}
