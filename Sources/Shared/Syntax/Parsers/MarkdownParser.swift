// MARK: - MarkdownParser
// Syntax parser for standard Markdown.
// macOS 14+, Swift 5.10

import Foundation

/// Line-by-line Markdown parser.
///
/// Handles headings, bold, italic, inline code, code fences, links,
/// list markers, and blockquotes. Multiline state (code blocks) is
/// carried via ``LineContext``.
struct MarkdownParser: SyntaxParsing, Sendable {

    let supportedExtensions: [String] = ["md"]

    func parseLine(
        _ line: String,
        lineRange: NSRange,
        context: LineContext
    ) -> (tokens: [SyntaxToken], nextContext: LineContext) {
        var ctx = context
        let trimmed = line.trimmingCharacters(in: .whitespaces)

        // Code fence toggle
        if trimmed.hasPrefix("```") || trimmed.hasPrefix("~~~") {
            let fence = String(trimmed.prefix(3))
            if ctx.inCodeBlock && ctx.codeBlockFence == fence {
                ctx.inCodeBlock = false
                ctx.codeBlockFence = nil
                ctx.codeBlockLanguage = nil
            } else if !ctx.inCodeBlock {
                ctx.inCodeBlock = true
                ctx.codeBlockFence = fence
                ctx.codeBlockLanguage = String(trimmed.dropFirst(3))
                    .trimmingCharacters(in: .whitespaces)
            }
            return ([SyntaxToken(kind: .codeBlockFence, range: lineRange)], ctx)
        }

        // Inside code block -- plain
        if ctx.inCodeBlock {
            return ([SyntaxToken(kind: .codeBlockBody, range: lineRange)], ctx)
        }

        // Heading: # through ######
        if let headingRange = headingRange(in: line, lineRange: lineRange) {
            return ([SyntaxToken(kind: .heading, range: headingRange)], ctx)
        }

        // Blockquote
        if trimmed.hasPrefix(">") {
            return ([SyntaxToken(kind: .blockquote, range: lineRange)], ctx)
        }

        // List marker
        if let markerToken = listMarkerToken(in: line, lineRange: lineRange) {
            return ([markerToken], ctx)
        }

        // Inline tokens (bold, italic, inline code, links)
        let inlineTokens = parseInline(line: line, lineRange: lineRange)
        return (inlineTokens, ctx)
    }

    // MARK: - Private Helpers

    private func headingRange(in line: String, lineRange: NSRange) -> NSRange? {
        guard line.hasPrefix("#") else { return nil }
        var count = 0
        for ch in line {
            if ch == "#" { count += 1 } else { break }
        }
        guard count <= 6 else { return nil }
        let afterHashes = line.dropFirst(count)
        guard afterHashes.hasPrefix(" ") else { return nil }
        return lineRange
    }

    private func listMarkerToken(in line: String, lineRange: NSRange) -> SyntaxToken? {
        let trimmed = line.trimmingCharacters(in: CharacterSet(charactersIn: " \t"))
        let isUnordered = trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") || trimmed.hasPrefix("+ ")
        let isOrdered = trimmed.first?.isNumber == true && trimmed.contains(". ")

        guard isUnordered || isOrdered else { return nil }

        let leadingSpaces = line.prefix(while: { $0 == " " || $0 == "\t" }).count

        let markerLen: Int
        if isUnordered {
            markerLen = 2
        } else {
            guard let dotIndex = trimmed.firstIndex(of: ".") else { return nil }
            markerLen = trimmed.distance(from: trimmed.startIndex, to: dotIndex) + 2
        }

        let available = lineRange.length - leadingSpaces
        guard available > 0 else { return nil }

        let markerRange = NSRange(
            location: lineRange.location + leadingSpaces,
            length: min(markerLen, available)
        )
        return SyntaxToken(kind: .listMarker, range: markerRange)
    }

    private func parseInline(line: String, lineRange: NSRange) -> [SyntaxToken] {
        var tokens: [SyntaxToken] = []
        let nsLine = line as NSString
        let length = nsLine.length

        var i = 0
        while i < length {
            // Bold **text** or __text__
            if let (token, next) = matchBold(at: i, in: nsLine, length: length, lineRange: lineRange) {
                tokens.append(token)
                i = next
                continue
            }

            let ch = nsLine.character(at: i)

            // Italic *text* or _text_ (single char)
            if let (token, next) = matchItalic(at: i, char: ch, in: nsLine, lineRange: lineRange) {
                tokens.append(token)
                i = next
                continue
            }

            // Inline code `text`
            if let (token, next) = matchInlineCode(at: i, char: ch, in: nsLine, lineRange: lineRange) {
                tokens.append(token)
                i = next
                continue
            }

            // Link [text](url)
            if let (linkTokens, next) = matchLink(at: i, char: ch, in: nsLine, lineRange: lineRange) {
                tokens.append(contentsOf: linkTokens)
                i = next
                continue
            }

            i += 1
        }

        return tokens
    }

    /// Match `**bold**` / `__bold__` at `i`; returns the token and next index.
    private func matchBold(
        at i: Int, in nsLine: NSString, length: Int, lineRange: NSRange
    ) -> (SyntaxToken, Int)? {
        guard i + 1 < length else { return nil }
        let pair = nsLine.substring(with: NSRange(location: i, length: 2))
        guard pair == "**" || pair == "__" else { return nil }
        guard let endIdx = findClosing(marker: pair, in: nsLine, from: i + 2) else { return nil }
        let range = NSRange(location: lineRange.location + i, length: endIdx - i + 2)
        return (SyntaxToken(kind: .bold, range: range), endIdx + 2)
    }

    /// Match `*italic*` / `_italic_` at `i`; returns the token and next index.
    private func matchItalic(
        at i: Int, char ch: unichar, in nsLine: NSString, lineRange: NSRange
    ) -> (SyntaxToken, Int)? {
        guard ch == 0x2A || ch == 0x5F else { return nil } // * or _
        let marker = String(UnicodeScalar(ch)!)
        guard let endIdx = findClosing(marker: marker, in: nsLine, from: i + 1) else { return nil }
        let range = NSRange(location: lineRange.location + i, length: endIdx - i + 1)
        return (SyntaxToken(kind: .italic, range: range), endIdx + 1)
    }

    /// Match `` `code` `` at `i`; returns the token and next index.
    private func matchInlineCode(
        at i: Int, char ch: unichar, in nsLine: NSString, lineRange: NSRange
    ) -> (SyntaxToken, Int)? {
        guard ch == 0x60 else { return nil } // `
        guard let endIdx = findClosing(marker: "`", in: nsLine, from: i + 1) else { return nil }
        let range = NSRange(location: lineRange.location + i, length: endIdx - i + 1)
        return (SyntaxToken(kind: .inlineCode, range: range), endIdx + 1)
    }

    /// Match `[text](url)` at `i`; returns the link + URL tokens and next index.
    private func matchLink(
        at i: Int, char ch: unichar, in nsLine: NSString, lineRange: NSRange
    ) -> ([SyntaxToken], Int)? {
        guard ch == 0x5B else { return nil } // [
        guard let (linkRange, urlRange) = parseLinkAt(i, in: nsLine, lineRange: lineRange) else { return nil }
        let next = urlRange.location - lineRange.location + urlRange.length
        return (
            [
                SyntaxToken(kind: .link, range: linkRange),
                SyntaxToken(kind: .linkURL, range: urlRange)
            ],
            next
        )
    }

    private func findClosing(
        marker: String,
        in nsLine: NSString,
        from start: Int
    ) -> Int? {
        let length = nsLine.length
        let markerLen = (marker as NSString).length
        var i = start
        while i + markerLen - 1 < length {
            let candidate = nsLine.substring(with: NSRange(location: i, length: markerLen))
            if candidate == marker { return i }
            i += 1
        }
        return nil
    }

    private func parseLinkAt(
        _ start: Int,
        in nsLine: NSString,
        lineRange: NSRange
    ) -> (linkRange: NSRange, urlRange: NSRange)? {
        let length = nsLine.length

        // Find ]
        guard let closeBracket = (start + 1..<length).first(where: {
            nsLine.character(at: $0) == 0x5D // ]
        }) else { return nil }

        // Check for (
        guard closeBracket + 1 < length,
              nsLine.character(at: closeBracket + 1) == 0x28 else { return nil } // (

        // Find )
        guard let closeParen = (closeBracket + 2..<length).first(where: {
            nsLine.character(at: $0) == 0x29 // )
        }) else { return nil }

        let linkRange = NSRange(
            location: lineRange.location + start,
            length: closeBracket - start + 1
        )
        let urlRange = NSRange(
            location: lineRange.location + closeBracket + 1,
            length: closeParen - closeBracket
        )
        return (linkRange, urlRange)
    }
}
