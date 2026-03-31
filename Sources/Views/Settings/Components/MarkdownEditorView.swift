// MARK: - MarkdownEditorView
// Shared NSTextView-based editor for markdown files in Settings sheets.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

// MARK: - MarkdownEditorView

/// Monospaced `NSTextView` wrapped for SwiftUI.
///
/// Supports read-only mode via `isEditable`. Used across all Settings editor sheets
/// (Claude config, agents, commands, skills) to eliminate NSViewRepresentable duplication.
struct MarkdownEditorView: NSViewRepresentable {

    @Binding var text: String

    /// When `false` the text view is non-editable (read-only viewer).
    var isEditable: Bool = true

    func makeNSView(context: Context) -> NSScrollView {
        let scrollView = NSTextView.scrollableTextView()
        guard let textView = scrollView.documentView as? NSTextView else { return scrollView }

        textView.isEditable = isEditable
        textView.isRichText = false
        textView.allowsUndo = true
        textView.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        textView.textContainerInset = NSSize(width: 12, height: 12)
        textView.isAutomaticQuoteSubstitutionEnabled = false
        textView.isAutomaticDashSubstitutionEnabled = false
        textView.isAutomaticSpellingCorrectionEnabled = false
        textView.drawsBackground = false
        scrollView.drawsBackground = false
        textView.delegate = context.coordinator

        return scrollView
    }

    func updateNSView(_ scrollView: NSScrollView, context: Context) {
        guard let textView = scrollView.documentView as? NSTextView else { return }
        textView.isEditable = isEditable
        if textView.string != text {
            let sel = textView.selectedRanges
            textView.string = text
            textView.selectedRanges = sel
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(text: $text) }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var text: Binding<String>
        init(text: Binding<String>) { self.text = text }
        func textDidChange(_ notification: Notification) {
            guard let tv = notification.object as? NSTextView else { return }
            text.wrappedValue = tv.string
        }
    }
}
