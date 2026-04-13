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
            if i + 1 < length {
                let pair = nsLine.substring(with: NSRange(location: i, length: 2))
                if pair == "**" || pair == "__" {
                    if let endIdx = findClosing(marker: pair, in: nsLine, from: i + 2) {
                        let range = NSRange(
                            location: lineRange.location + i,
                            length: endIdx - i + 2
                        )
                        tokens.append(SyntaxToken(kind: .bold, range: range))
                        i = endIdx + 2
                        continue
                    }
                }
            }

            // Italic *text* or _text_ (single char)
            let ch = nsLine.character(at: i)
            if ch == 0x2A || ch == 0x5F { // * or _
                let marker = String(UnicodeScalar(ch)!)
                if let endIdx = findClosing(marker: marker, in: nsLine, from: i + 1) {
                    let range = NSRange(
                        location: lineRange.location + i,
                        length: endIdx - i + 1
                    )
                    tokens.append(SyntaxToken(kind: .italic, range: range))
                    i = endIdx + 1
                    continue
                }
            }

            // Inline code `text`
            if ch == 0x60 { // `
                if let endIdx = findClosing(marker: "`", in: nsLine, from: i + 1) {
                    let range = NSRange(
                        location: lineRange.location + i,
                        length: endIdx - i + 1
                    )
                    tokens.append(SyntaxToken(kind: .inlineCode, range: range))
                    i = endIdx + 1
                    continue
                }
            }

            // Link [text](url)
            if ch == 0x5B { // [
                if let (linkRange, urlRange) = parseLinkAt(
                    i, in: nsLine, lineRange: lineRange
                ) {
                    tokens.append(SyntaxToken(kind: .link, range: linkRange))
                    tokens.append(SyntaxToken(kind: .linkURL, range: urlRange))
                    i = urlRange.location - lineRange.location + urlRange.length
                    continue
                }
            }

            i += 1
        }

        return tokens
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
