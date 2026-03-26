// MARK: - CodeContentView
// Monospaced text view with line numbers for file preview.
// macOS 14+, Swift 5.10

import SwiftUI

struct CodeContentView: View {

    let content: String

    private var lines: [String] {
        content.components(separatedBy: "\n")
    }

    private var gutterWidth: CGFloat {
        let digits = max(String(lines.count).count, 3)
        return CGFloat(digits) * 8 + DSSpacing.sm * 2
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView([.horizontal, .vertical], showsIndicators: true) {
                HStack(alignment: .top, spacing: 0) {
                    gutter
                    Divider()
                        .background(DSColor.borderSubtle)
                    codeArea
                        .frame(minWidth: max(proxy.size.width - gutterWidth - 1, 0),
                               alignment: .leading)
                }
            }
        }
        .background(DSColor.surfaceBase)
    }

    // MARK: - Gutter

    private var gutter: some View {
        VStack(alignment: .trailing, spacing: 0) {
            ForEach(Array(lines.enumerated()), id: \.offset) { index, _ in
                Text("\(index + 1)")
                    .font(DSFont.terminal(size: 12))
                    .foregroundStyle(DSColor.textMuted)
                    .frame(height: 18)
            }
        }
        .padding(.horizontal, DSSpacing.sm)
        .padding(.vertical, DSSpacing.xs)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - Code Area

    private var codeArea: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                Text(line.isEmpty ? " " : line)
                    .font(DSFont.terminal(size: 12))
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(height: 18, alignment: .leading)
                    .textSelection(.enabled)
            }
        }
        .padding(.horizontal, DSSpacing.sm)
        .padding(.vertical, DSSpacing.xs)
    }
}
