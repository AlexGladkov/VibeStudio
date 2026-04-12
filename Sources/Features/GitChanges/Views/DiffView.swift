// MARK: - DiffView
// Side-by-side (split) diff view — old file on the left, new on the right.
// macOS 14+, Swift 5.10

import SwiftUI

/// Side-by-side split diff view.
///
/// Renders git diff hunks in two equal-width columns:
/// - **Left** — old file: context lines + deleted lines (red background).
/// - **Right** — new file: context lines + added lines (green background).
///
/// Deletion/addition pairs in the same change block are aligned row-by-row
/// for easy visual comparison. Hunk headers span the full width.
struct DiffView: View {

    let hunks: [GitDiffHunk]

    var body: some View {
        let rows = buildRows(from: hunks)
        return ScrollView(.vertical) {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(rows) { row in
                    rowView(row)
                }
            }
        }
        .background(DSColor.surfaceBase)
    }

    // MARK: - Row Views

    @ViewBuilder
    private func rowView(_ row: SplitRow) -> some View {
        switch row.kind {
        case .hunkHeader(let text):
            Text(text)
                .font(DSFont.terminal(size: 11))
                .foregroundStyle(DSColor.textMuted)
                .padding(.horizontal, DSSpacing.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: DSLayout.diffLineHeight)
                .background(DSColor.surfaceOverlay)

        case .content(let left, let right):
            HStack(spacing: 0) {
                sideCell(left)
                    .frame(maxWidth: .infinity)
                    .clipped()
                Rectangle()
                    .fill(DSColor.diffGutter.opacity(0.25))
                    .frame(width: 1)
                sideCell(right)
                    .frame(maxWidth: .infinity)
                    .clipped()
            }
            .frame(height: DSLayout.diffLineHeight)
        }
    }

    // MARK: - Side Cell

    private func sideCell(_ cell: SplitDiffCell?) -> some View {
        HStack(spacing: 0) {
            // Line number gutter
            Group {
                if let num = cell?.lineNumber {
                    Text("\(num)")
                } else {
                    Text("")
                }
            }
            .font(DSFont.terminal(size: 11))
            .foregroundStyle(DSColor.diffGutter)
            .frame(width: 32, alignment: .trailing)
            .padding(.trailing, 3)

            // +/- symbol
            Text(prefix(for: cell?.type))
                .font(DSFont.terminal(size: 12))
                .foregroundStyle(prefixColor(for: cell?.type))
                .frame(width: 12)

            // Line content
            Text(cell?.content ?? "")
                .font(DSFont.terminal(size: 12))
                .foregroundStyle(DSColor.textPrimary)
                .lineLimit(1)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)

            Spacer(minLength: 4)
        }
        .frame(maxWidth: .infinity)
        .background(cellBackground(for: cell))
    }

    // MARK: - Helpers

    private func prefix(for type: DiffLineType?) -> String {
        switch type {
        case .addition: "+"
        case .deletion: "-"
        case .context, .none: " "
        }
    }

    private func prefixColor(for type: DiffLineType?) -> Color {
        switch type {
        case .addition: DSColor.gitAdded
        case .deletion: DSColor.gitDeleted
        case .context, .none: DSColor.textMuted
        }
    }

    private func cellBackground(for cell: SplitDiffCell?) -> Color {
        guard let cell else {
            // Empty placeholder — subtle tint so the gutter/content area
            // is visually distinct from context lines.
            return DSColor.diffGutter.opacity(0.06)
        }
        switch cell.type {
        case .addition: return DSColor.diffAddedBg
        case .deletion: return DSColor.diffDeletedBg
        case .context:  return .clear
        }
    }

    // MARK: - Row Builder

    /// Converts raw diff hunks into flat `SplitRow` array for rendering.
    ///
    /// Change blocks (consecutive deletions + additions) are aligned:
    /// each deletion is paired with the corresponding addition on the same row.
    /// Extra deletions get an empty right cell; extra additions get an empty left cell.
    private func buildRows(from hunks: [GitDiffHunk]) -> [SplitRow] {
        var rows: [SplitRow] = []
        var nextId = 0

        for hunk in hunks {
            rows.append(SplitRow(id: nextId, kind: .hunkHeader(hunk.header)))
            nextId += 1

            var i = 0
            let lines = hunk.lines

            while i < lines.count {
                switch lines[i].type {

                case .context:
                    let line = lines[i]
                    rows.append(SplitRow(id: nextId, kind: .content(
                        left:  SplitDiffCell(lineNumber: line.oldLineNumber, content: line.content, type: .context),
                        right: SplitDiffCell(lineNumber: line.newLineNumber, content: line.content, type: .context)
                    )))
                    nextId += 1
                    i += 1

                case .deletion:
                    // Collect consecutive deletions…
                    var deletions: [GitDiffLine] = []
                    while i < lines.count && lines[i].type == .deletion {
                        deletions.append(lines[i])
                        i += 1
                    }
                    // …then any additions that immediately follow (change block).
                    var additions: [GitDiffLine] = []
                    while i < lines.count && lines[i].type == .addition {
                        additions.append(lines[i])
                        i += 1
                    }
                    // Pair them row-by-row; extras get a nil on the opposite side.
                    for j in 0..<max(deletions.count, additions.count) {
                        let del = j < deletions.count ? deletions[j] : nil
                        let add = j < additions.count ? additions[j] : nil
                        rows.append(SplitRow(id: nextId, kind: .content(
                            left:  del.map { SplitDiffCell(lineNumber: $0.oldLineNumber, content: $0.content, type: .deletion) },
                            right: add.map { SplitDiffCell(lineNumber: $0.newLineNumber, content: $0.content, type: .addition) }
                        )))
                        nextId += 1
                    }

                case .addition:
                    // Standalone addition (not immediately preceded by a deletion block).
                    let line = lines[i]
                    rows.append(SplitRow(id: nextId, kind: .content(
                        left:  nil,
                        right: SplitDiffCell(lineNumber: line.newLineNumber, content: line.content, type: .addition)
                    )))
                    nextId += 1
                    i += 1
                }
            }
        }

        return rows
    }
}

// MARK: - Private Models

private struct SplitRow: Identifiable {
    let id: Int
    enum Kind {
        case hunkHeader(String)
        case content(left: SplitDiffCell?, right: SplitDiffCell?)
    }
    let kind: Kind
}

private struct SplitDiffCell {
    let lineNumber: Int?
    let content: String
    let type: DiffLineType
}
