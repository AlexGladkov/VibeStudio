// MARK: - CodeContentView
// Monospaced text view with line numbers for file preview.
// macOS 14+, Swift 5.10

import SwiftUI

struct CodeContentView: View {

    let lines: [String]

    init(content: String) {
        self.lines = content.components(separatedBy: "\n")
    }

    private var gutterWidth: CGFloat {
        let digits = max(String(lines.count).count, 3)
        return CGFloat(digits) * DSLayout.codeDigitWidth + DSSpacing.sm * 2
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
                    .frame(height: DSLayout.diffLineHeight)
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
                    .frame(height: DSLayout.diffLineHeight, alignment: .leading)
                    .textSelection(.enabled)
            }
        }
        .padding(.horizontal, DSSpacing.sm)
        .padding(.vertical, DSSpacing.xs)
    }
}
