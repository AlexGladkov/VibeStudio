// MARK: - LineNumberRulerView
// NSRulerView subclass for line number gutter in syntax editor.
// macOS 14+, Swift 5.10

import AppKit

/// Draws line numbers in the gutter of an `NSScrollView` hosting an `NSTextView`.
///
/// Automatically redraws on text changes and scroll position changes by
/// observing `NSTextStorage.didProcessEditingNotification` and
/// `NSView.boundsDidChangeNotification`.
final class LineNumberRulerView: NSRulerView {

    // MARK: - Properties

    private weak var textView: NSTextView?

    private var font: NSFont {
        DSFont.codeEditorNSFont(size: 10)
    }

    private var textColor: NSColor {
        NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(
                from: [.darkAqua, .accessibilityHighContrastDarkAqua]
            ) != nil
            // Muted gray matching diffGutter palette
            return isDark ? NSColor(white: 0.45, alpha: 1) : NSColor(white: 0.55, alpha: 1)
        }
    }

    private var backgroundColor: NSColor {
        NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(
                from: [.darkAqua, .accessibilityHighContrastDarkAqua]
            ) != nil
            // surfaceRaised equivalent
            return isDark ? NSColor(white: 0.13, alpha: 1) : NSColor(white: 0.96, alpha: 1)
        }
    }

    // MARK: - Init

    init(textView: NSTextView) {
        self.textView = textView
        super.init(
            scrollView: textView.enclosingScrollView,
            orientation: .verticalRuler
        )
        clientView = textView
        ruleThickness = DSLayout.lineNumberGutterWidth
    }

    @available(*, unavailable)
    required init(coder: NSCoder) {
        fatalError("init(coder:) not used")
    }

    // MARK: - Lifecycle

    override func viewDidMoveToSuperview() {
        super.viewDidMoveToSuperview()
        guard superview != nil else {
            stopObserving()
            return
        }
        startObserving()
    }

    private var observations: [NSObjectProtocol] = []

    private func startObserving() {
        let center = NotificationCenter.default

        observations = [
            center.addObserver(
                forName: NSTextStorage.didProcessEditingNotification,
                object: textView?.textStorage,
                queue: .main
            ) { [weak self] _ in self?.needsDisplay = true },

            center.addObserver(
                forName: NSView.boundsDidChangeNotification,
                object: textView?.enclosingScrollView?.contentView,
                queue: .main
            ) { [weak self] _ in self?.needsDisplay = true }
        ]
    }

    private func stopObserving() {
        observations.forEach { NotificationCenter.default.removeObserver($0) }
        observations = []
    }

    // MARK: - Drawing

    override func drawHashMarksAndLabels(in rect: NSRect) {
        backgroundColor.setFill()
        rect.fill()

        guard let textView,
              let layoutManager = textView.layoutManager,
              let textContainer = textView.textContainer else { return }

        let visibleRect = textView.visibleRect
        let glyphRange = layoutManager.glyphRange(
            forBoundingRect: visibleRect,
            in: textContainer
        )
        let charRange = layoutManager.characterRange(
            forGlyphRange: glyphRange,
            actualGlyphRange: nil
        )

        let nsText = textView.string as NSString
        let fullLength = nsText.length
        guard fullLength > 0 else { return }

        // Count lines up to charRange.location to find starting line number
        var lineNumber = 1
        var pos = 0
        while pos < charRange.location && pos < fullLength {
            let lineRange = nsText.lineRange(for: NSRange(location: pos, length: 0))
            lineNumber += 1
            pos = NSMaxRange(lineRange)
        }

        // Draw line numbers for visible glyph range
        var glyphIndex = glyphRange.location
        while glyphIndex < NSMaxRange(glyphRange) {
            var lineGlyphRange = NSRange()
            let lineRect = layoutManager.lineFragmentRect(
                forGlyphAt: glyphIndex,
                effectiveRange: &lineGlyphRange
            )

            let yInRuler = lineRect.minY - visibleRect.minY + convert(.zero, from: textView).y
            let numberString = "\(lineNumber)" as NSString
            let attrs: [NSAttributedString.Key: Any] = [
                .font: font,
                .foregroundColor: textColor
            ]
            let size = numberString.size(withAttributes: attrs)
            let drawRect = NSRect(
                x: ruleThickness - size.width - 6,
                y: yInRuler + (lineRect.height - size.height) / 2,
                width: size.width,
                height: size.height
            )
            numberString.draw(in: drawRect, withAttributes: attrs)

            lineNumber += 1
            glyphIndex = NSMaxRange(lineGlyphRange)
        }
    }

    override var requiredThickness: CGFloat {
        let lineCount = max(textView?.string.components(separatedBy: "\n").count ?? 1, 1)
        let digits = max(String(lineCount).count, 2)
        let charWidth = DSFont.codeEditorNSFont(size: 10).maximumAdvancement.width
        return CGFloat(digits) * charWidth + 20
    }
}
