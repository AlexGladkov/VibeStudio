// MARK: - CodeSpeakEditorView
// Syntax-highlighted NSTextView for CodeSpeak .cs.md editing.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

/// `NSViewRepresentable` wrapping a scrollable `NSTextView` with live syntax
/// highlighting applied by the `Coordinator` after each edit.
///
/// ## Why text load is deferred
///
/// SwiftUI sets the NSScrollView frame AFTER `makeNSView` returns. If we call
/// `textView.string = text` while the scroll view frame is still zero, the
/// text container width is also zero → glyphs laid out in a zero-width column
/// → invisible. `DispatchQueue.main.async` defers the load to the next run-loop
/// cycle by which time the frame and text container width are correct.
struct CodeSpeakEditorView: NSViewRepresentable {

    @Binding var text: String
    var isEditable: Bool = true
    var parserRegistry: SyntaxParserRegistry
    var fileExtension: String = "cs.md"

    // MARK: - NSViewRepresentable

    func makeNSView(context: Context) -> NSScrollView {
        let textView = makeTextView(delegate: context.coordinator)
        let scrollView = makeScrollView(documentView: textView)

        context.coordinator.parser = parserRegistry.parser(for: fileExtension)
        scheduleInitialTextLoad(scrollView: scrollView, coordinator: context.coordinator)

        return scrollView
    }

    /// Create and configure the editing `NSTextView` (TextKit 1, appearance, sizing).
    private func makeTextView(delegate: NSTextViewDelegate) -> NSTextView {
        // ── 1. Text view ───────────────────────────────────────────────────
        let textView = NSTextView(frame: .zero)

        // Force TextKit 1 (accessing layoutManager opts out of TextKit 2).
        _ = textView.layoutManager

        // ── 2. Text container ──────────────────────────────────────────────
        textView.textContainer?.widthTracksTextView = true
        textView.textContainer?.containerSize = NSSize(
            // swiftlint:disable colon
            width:  CGFloat.greatestFiniteMagnitude,
            height: CGFloat.greatestFiniteMagnitude
            // swiftlint:enable colon
        )

        // ── 3. Appearance ──────────────────────────────────────────────────
        textView.isEditable   = isEditable
        textView.isRichText   = true
        textView.allowsUndo   = true
        textView.isAutomaticQuoteSubstitutionEnabled  = false
        textView.isAutomaticDashSubstitutionEnabled   = false
        textView.isAutomaticSpellingCorrectionEnabled = false

        let baseFont  = DSFont.codeEditorNSFont(size: 13)
        let baseColor = NSColor.labelColor
        textView.font      = baseFont
        textView.textColor = baseColor
        textView.typingAttributes = [
            // swiftlint:disable colon
            .font:            baseFont,
            .foregroundColor: baseColor
            // swiftlint:enable colon
        ]
        textView.textContainerInset      = DSLayout.editorContentInset
        textView.drawsBackground         = true
        textView.backgroundColor         = .textBackgroundColor
        textView.isVerticallyResizable   = true
        textView.isHorizontallyResizable = false
        textView.autoresizingMask        = [.width]
        textView.minSize = NSSize(width: 0, height: 0)
        textView.maxSize = NSSize(
            // swiftlint:disable colon
            width:  CGFloat.greatestFiniteMagnitude,
            height: CGFloat.greatestFiniteMagnitude
            // swiftlint:enable colon
        )
        textView.delegate = delegate
        return textView
    }

    /// Wrap the text view in a vertically-scrolling `NSScrollView`.
    private func makeScrollView(documentView textView: NSTextView) -> NSScrollView {
        // ── 4. NSScrollView ────────────────────────────────────────────────
        let scrollView = NSScrollView()
        scrollView.documentView          = textView
        scrollView.hasVerticalScroller   = true
        scrollView.hasHorizontalScroller = false
        scrollView.autohidesScrollers    = true
        scrollView.drawsBackground       = false
        return scrollView
    }

    /// Defer the initial text load until SwiftUI has sized the scroll view.
    ///
    /// ARCH-M5: replaced `DispatchQueue.main.async` with a structured Task
    /// that yields once and bails out when the scroll view is no longer
    /// attached to a window (the user dismissed the sheet before the deferred
    /// work ran).
    private func scheduleInitialTextLoad(scrollView: NSScrollView, coordinator: Coordinator) {
        let initialText = text
        Task { @MainActor [weak scrollView] in
            await Task.yield()
            guard let scrollView,
                  scrollView.window != nil,
                  let tv = scrollView.documentView as? NSTextView,
                  !initialText.isEmpty else { return }
            // Pin text view width to clip view bounds width.
            let cv = scrollView.contentView
            let w = cv.bounds.width
            if w > 0 && tv.frame.width != w {
                tv.frame = NSRect(x: 0, y: 0,
                                  width: w,
                                  height: max(tv.frame.height, cv.bounds.height))
            }
            tv.string = initialText
            coordinator.scheduleHighlighting(for: tv, text: initialText)
        }
    }

    func updateNSView(_ scrollView: NSScrollView, context: Context) {
        guard let textView = scrollView.documentView as? NSTextView else { return }
        textView.isEditable = isEditable

        // Sync text (skip when equal — user is actively editing).
        guard textView.string != text else { return }

        textView.string = text
        // Reset cursor to start — avoids NSSelectionArray crash when new text
        // is shorter than current selection (selectedRanges = [] is illegal).
        textView.setSelectedRange(NSRange(location: 0, length: 0))
        context.coordinator.scheduleHighlighting(for: textView, text: text)
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        nsView: NSScrollView,
        context: Context
    ) -> CGSize? {
        CGSize(width: proposal.width ?? 400, height: proposal.height ?? 400)
    }

    func makeCoordinator() -> Coordinator { Coordinator(text: $text) }

    // MARK: - Coordinator

    final class Coordinator: NSObject, NSTextViewDelegate {

        var text: Binding<String>
        var parser: (any SyntaxParsing)?
        private var highlightVersion: UInt64 = 0

        init(text: Binding<String>) { self.text = text }

        func textDidChange(_ notification: Notification) {
            guard let tv = notification.object as? NSTextView else { return }
            text.wrappedValue = tv.string
            scheduleHighlighting(for: tv, text: tv.string)
        }

        func scheduleHighlighting(for textView: NSTextView, text: String) {
            highlightVersion &+= 1
            let version        = highlightVersion
            let capturedParser = parser
            let capturedFont   = DSFont.codeEditorNSFont(size: 13)
            let capturedBase   = NSColor.labelColor

            Task.detached(priority: .userInitiated) { [weak self, weak textView] in
                guard let self else { return }
                var tokens: [SyntaxToken] = []
                if let p = capturedParser {
                    tokens = SyntaxTokenizer.tokenizeAll(text: text, parser: p)
                }
                await MainActor.run { [weak self, weak textView] in
                    guard let self,
                          self.highlightVersion == version,
                          let textView,
                          let storage = textView.textStorage else { return }
                    let currentLength = storage.length
                    guard currentLength > 0 else { return }
                    storage.beginEditing()
                    storage.setAttributes(
                        [.font: capturedFont, .foregroundColor: capturedBase],
                        range: NSRange(location: 0, length: currentLength)
                    )
                    for token in tokens {
                        guard let color = DSColor.syntaxNSColor(for: token.kind) else { continue }
                        let loc = token.range.location
                        let len = token.range.length
                        guard loc >= 0, loc < currentLength else { continue }
                        let safeLen   = min(len, currentLength - loc)
                        guard safeLen > 0 else { continue }
                        let safeRange = NSRange(location: loc, length: safeLen)
                        var attrs: [NSAttributedString.Key: Any] = [.foregroundColor: color]
                        if token.kind == .heading {
                            attrs[.font] = NSFont.monospacedSystemFont(
                                ofSize: capturedFont.pointSize, weight: .bold)
                        }
                        storage.addAttributes(attrs, range: safeRange)
                    }
                    storage.endEditing()
                }
            }
        }
    }
}
