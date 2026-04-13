// MARK: - SyntaxHighlightTextStorage
// NSTextStorage subclass with live syntax highlighting (TextKit 1).
// macOS 14+, Swift 5.10

import AppKit

/// `NSTextStorage` subclass that applies syntax highlighting via a
/// ``SyntaxParsing`` implementation on every text mutation.
///
/// ## Architecture
///
/// - **Proxy pattern:** wraps `NSMutableAttributedString` as backing store.
/// - **Primitives** (`replaceCharacters`, `setAttributes`) do NOT call
///   `beginEditing/endEditing` — that is the caller's responsibility per
///   NSTextStorage contract. They only mutate `backingStore` and call `edited()`.
/// - **On `processEditing()`:** applies base attrs to `editedRange` synchronously
///   (so text is immediately visible), then spawns `Task.detached` for tokenization.
/// - **Version guard** (`editVersion: UInt64`) ensures stale results are discarded.
/// - **`NSRange.clamped`** before every attribute mutation (crash safety).
///
/// ## Why editedRange.length can be 0
///
/// `NSTextStorage.editedRange` after character insertion carries the length of
/// the NEW characters (changeInLength). For a single typed character this is 1,
/// not 0. However `NSTextStorage` expands `editedRange` to paragraph boundaries
/// before calling `processEditing()`, so in practice length is always >= 1
/// for character edits. We still guard against `NSNotFound` for attribute-only
/// edits where range could theoretically be zero-length.
@MainActor
final class SyntaxHighlightTextStorage: NSTextStorage {

    // MARK: - Backing Store

    private let backingStore = NSMutableAttributedString()

    // MARK: - Configuration

    private var parser: (any SyntaxParsing)?
    private var baseFont: NSFont = DSFont.codeEditorNSFont(size: 13)
    private var baseTextColor: NSColor = .labelColor

    // MARK: - Version Guard

    /// Monotonically increasing edit counter.
    ///
    /// Tokenization `Task` captures this value; the apply step checks equality
    /// so that results from outdated tokenization passes are silently discarded.
    private var editVersion: UInt64 = 0

    /// Set to `true` while `applyTokens` is executing so `processEditing()`
    /// does not re-schedule a redundant highlight pass.
    private var isApplyingHighlight = false

    // MARK: - NSTextStorage Required Overrides
    //
    // Apple documentation: primitive methods MUST NOT call beginEditing/endEditing.
    // Only callers that want to batch multiple changes should use that pair.
    // `edited()` immediately triggers `processEditing()` when there is no active
    // editing session (i.e., when called outside beginEditing/endEditing).

    override var string: String { backingStore.string }

    override func attributes(
        at location: Int,
        effectiveRange range: NSRangePointer?
    ) -> [NSAttributedString.Key: Any] {
        backingStore.attributes(at: location, effectiveRange: range)
    }

    override func replaceCharacters(in range: NSRange, with str: String) {
        backingStore.replaceCharacters(in: range, with: str)
        edited(.editedCharacters, range: range, changeInLength: (str as NSString).length - range.length)
    }

    override func setAttributes(
        _ attrs: [NSAttributedString.Key: Any]?,
        range: NSRange
    ) {
        backingStore.setAttributes(attrs, range: range)
        edited(.editedAttributes, range: range, changeInLength: 0)
    }

    // MARK: - Highlighting

    override func processEditing() {
        // Apply base font/color to the edited range synchronously, BEFORE
        // notifying layout managers via super.processEditing(). This guarantees
        // that when the layout manager calls attributes(at:effectiveRange:) during
        // its invalidation sweep, it sees valid foreground color and font, so the
        // glyphs are drawn immediately (not after the async highlight pass).
        //
        // We write directly into backingStore (bypassing our own primitive API)
        // to avoid re-triggering edited() and creating a re-entrant edit session.
        // This is the documented proxy-subclass pattern from Apple's TextEdit example.
        if !isApplyingHighlight {
            let currentLength = backingStore.length
            let eRange = editedRange
            // editedRange.location == NSNotFound means no character/attribute edit
            // was recorded (e.g., processEditing called spuriously). Skip in that case.
            if currentLength > 0, eRange.location != NSNotFound {
                // Clamp to actual content — editedRange can extend past end in edge cases.
                let safeEnd = min(NSMaxRange(eRange), currentLength)
                if safeEnd > eRange.location {
                    let safeRange = NSRange(location: eRange.location,
                                           length: safeEnd - eRange.location)
                    backingStore.addAttributes(
                        [.font: baseFont, .foregroundColor: baseTextColor],
                        range: safeRange
                    )
                }
            }
        }

        super.processEditing()
        guard !isApplyingHighlight else { return }
        scheduleHighlighting()
    }

    // MARK: - Public API

    /// Attach or replace the syntax parser. Triggers a full re-highlight.
    func setParser(_ newParser: (any SyntaxParsing)?) {
        parser = newParser
        invalidateAll()
    }

    /// Update base font and text color, then re-highlight.
    func updateAppearance(font: NSFont, textColor: NSColor) {
        baseFont = font
        baseTextColor = textColor
        invalidateAll()
    }

    // MARK: - Private

    private func invalidateAll() {
        let fullRange = NSRange(location: 0, length: backingStore.length)
        guard fullRange.length > 0 else { return }
        scheduleHighlighting(range: fullRange)
    }

    private func scheduleHighlighting(range: NSRange? = nil) {
        editVersion &+= 1
        let capturedVersion = editVersion
        let text = backingStore.string
        let capturedParser = parser
        let capturedFont = baseFont
        let capturedColor = baseTextColor
        let fullLength = (text as NSString).length

        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self else { return }

            var tokens: [SyntaxToken] = []
            if let capturedParser {
                tokens = Self.tokenizeAll(text: text, parser: capturedParser)
            }

            await MainActor.run { [weak self] in
                guard let self, self.editVersion == capturedVersion else { return }
                self.applyTokens(
                    tokens,
                    fullLength: fullLength,
                    font: capturedFont,
                    textColor: capturedColor
                )
            }
        }
    }

    /// Tokenize the entire document line by line (runs on background thread).
    private nonisolated static func tokenizeAll(
        text: String,
        parser: any SyntaxParsing
    ) -> [SyntaxToken] {
        let nsText = text as NSString
        let fullLength = nsText.length
        var context = LineContext.initial
        var allTokens: [SyntaxToken] = []
        var pos = 0

        while pos < fullLength {
            let lineRange = nsText.lineRange(for: NSRange(location: pos, length: 0))

            var contentRange = lineRange
            if contentRange.length > 0 {
                let lastChar = nsText.character(at: NSMaxRange(contentRange) - 1)
                if lastChar == 0x0A || lastChar == 0x0D {
                    contentRange.length -= 1
                }
            }
            let lineContent = nsText.substring(with: contentRange)

            let (tokens, nextContext) = parser.parseLine(
                lineContent,
                lineRange: lineRange,
                context: context
            )
            allTokens.append(contentsOf: tokens)
            context = nextContext
            pos = NSMaxRange(lineRange)

            if pos == 0 { break }
        }

        return allTokens
    }

    /// Apply tokens to the backing store on the MainActor.
    ///
    /// Uses `isApplyingHighlight` to prevent `processEditing()` from scheduling
    /// a new tokenization pass while we are mid-apply.
    private func applyTokens(
        _ tokens: [SyntaxToken],
        fullLength: Int,
        font: NSFont,
        textColor: NSColor
    ) {
        let currentLength = backingStore.length
        guard currentLength > 0 else { return }

        let fullRange = NSRange(location: 0, length: currentLength)

        isApplyingHighlight = true
        defer { isApplyingHighlight = false }

        // Batch all attribute mutations. beginEditing/endEditing groups them so
        // layout managers receive a single consolidated notification.
        beginEditing()

        backingStore.setAttributes([.font: font, .foregroundColor: textColor], range: fullRange)

        for token in tokens {
            guard let color = DSColor.syntaxNSColor(for: token.kind) else { continue }
            guard let safeRange = token.range.clamped(to: currentLength) else { continue }

            var attrs: [NSAttributedString.Key: Any] = [.foregroundColor: color]
            if token.kind == .heading {
                attrs[.font] = NSFont.monospacedSystemFont(ofSize: font.pointSize, weight: .bold)
            }
            backingStore.addAttributes(attrs, range: safeRange)
        }

        edited(.editedAttributes, range: fullRange, changeInLength: 0)
        endEditing()
    }
}

// MARK: - NSRange Clamping Helper

private extension NSRange {

    func clamped(to length: Int) -> NSRange? {
        guard location >= 0, location < length else { return nil }
        let safeLength = min(self.length, length - location)
        guard safeLength > 0 else { return nil }
        return NSRange(location: location, length: safeLength)
    }
}
