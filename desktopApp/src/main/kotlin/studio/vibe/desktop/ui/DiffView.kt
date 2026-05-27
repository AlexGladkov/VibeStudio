@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@file:Suppress("DEPRECATION")

package studio.vibe.desktop.ui

import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing

// ── Constants ─────────────────────────────────────────────────────────────────

private val LINE_NUMBER_WIDTH = 48.dp
private val PREFIX_WIDTH = 16.dp
private val DIFF_LINE_HEIGHT = 20.dp
private val GUTTER_DIVIDER_WIDTH = 1.dp

// ── Public entry point ────────────────────────────────────────────────────────

/**
 * Side-by-side split diff viewer.
 *
 * Accepts a raw unified diff string (output of `git diff`) and renders it in
 * two equal-width columns — old file on the left, new file on the right.
 * Deletion/addition pairs within the same change block are row-aligned so the
 * reader can compare them at a glance.  Hunk headers span the full width.
 *
 * @param diffText Raw unified diff text (may be empty or a "binary file" stub).
 * @param modifier Optional layout modifier applied to the outer container.
 */
@Composable
fun DiffView(
    diffText: String,
    modifier: Modifier = Modifier,
) {
    val rows = remember(diffText) { parseDiff(diffText) }
    val listState = rememberLazyListState()

    Box(modifier = modifier.background(DSColor.surfaceBase)) {
        if (rows.isEmpty()) {
            EmptyDiffPlaceholder()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                    DiffRowView(row)
                }
            }
            VerticalScrollbar(
                adapter = ScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp),
            )
        }
    }
}

// ── Row rendering ─────────────────────────────────────────────────────────────

@Composable
private fun DiffRowView(row: SplitRow) {
    when (val kind = row.kind) {
        is SplitRow.Kind.HunkHeader -> HunkHeaderRow(kind.text)
        is SplitRow.Kind.Content -> ContentRow(kind.left, kind.right)
    }
}

@Composable
private fun HunkHeaderRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DIFF_LINE_HEIGHT)
            .background(DSColor.surfaceOverlay)
            .padding(horizontal = DSSpacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = DSFont.monoSmall,
            color = DSColor.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContentRow(left: SplitDiffCell?, right: SplitDiffCell?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DIFF_LINE_HEIGHT),
    ) {
        SideCell(
            cell = left,
            modifier = Modifier.weight(1f),
        )
        // Centre divider
        Box(
            Modifier
                .width(GUTTER_DIVIDER_WIDTH)
                .fillMaxHeight()
                .background(DSColor.diffGutter.copy(alpha = 0.25f)),
        )
        SideCell(
            cell = right,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SideCell(cell: SplitDiffCell?, modifier: Modifier = Modifier) {
    val bgColor = when (cell?.type) {
        DiffCellType.ADDITION -> DSColor.diffAddedBg
        DiffCellType.DELETION -> DSColor.diffDeletedBg
        DiffCellType.CONTEXT, null -> Color.Transparent
    }
    val emptyBg = if (cell == null) DSColor.diffGutter.copy(alpha = 0.06f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(if (cell != null) bgColor else emptyBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Line number
        Text(
            text = cell?.lineNumber?.toString() ?: "",
            style = DSFont.monoSmall.copy(fontSize = 11.sp),
            color = DSColor.diffGutter,
            modifier = Modifier
                .width(LINE_NUMBER_WIDTH)
                .padding(end = DSSpacing.xxs),
            maxLines = 1,
        )

        // +/- prefix symbol
        val prefixText = when (cell?.type) {
            DiffCellType.ADDITION -> "+"
            DiffCellType.DELETION -> "-"
            DiffCellType.CONTEXT, null -> " "
        }
        val prefixColor = when (cell?.type) {
            DiffCellType.ADDITION -> DSColor.gitAdded
            DiffCellType.DELETION -> DSColor.gitDeleted
            DiffCellType.CONTEXT, null -> DSColor.textMuted
        }
        Text(
            text = prefixText,
            style = DSFont.monoSmall.copy(fontSize = 12.sp),
            color = prefixColor,
            modifier = Modifier.width(PREFIX_WIDTH),
            maxLines = 1,
        )

        // Line content
        val contentColor = when (cell?.type) {
            DiffCellType.ADDITION -> DSColor.gitAdded
            DiffCellType.DELETION -> DSColor.gitDeleted
            DiffCellType.CONTEXT -> DSColor.textSecondary
            null -> Color.Transparent
        }
        Text(
            text = cell?.content ?: "",
            style = DSFont.monoSmall.copy(fontSize = 12.sp),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .padding(end = DSSpacing.xs),
        )
    }
}

@Composable
private fun EmptyDiffPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No changes",
            style = DSFont.sidebarItem,
            color = DSColor.textMuted,
        )
    }
}

// ── Unified diff parser ───────────────────────────────────────────────────────

/**
 * Parses a raw unified diff string into a flat list of [SplitRow] values
 * suitable for direct rendering in a [LazyColumn].
 *
 * Algorithm:
 * 1. Skip file header lines (`---` / `+++`).
 * 2. Hunk header lines (`@@…`) become full-width [SplitRow.Kind.HunkHeader] rows.
 * 3. Inside a hunk, consecutive deletions followed by consecutive additions are
 *    paired row-by-row so both sides align visually.  Unpaired deletions get a
 *    null right cell; unpaired additions get a null left cell.
 * 4. Context lines (space-prefixed) appear on both sides with matching line numbers.
 */
private fun parseDiff(diffText: String): List<SplitRow> {
    if (diffText.isBlank()) return emptyList()

    val rawLines = diffText.lines()
    // Convert raw text into typed DiffInputLine values
    val inputLines = mutableListOf<DiffInputLine>()
    var oldLine = 0
    var newLine = 0

    for (raw in rawLines) {
        when {
            raw.startsWith("--- ") || raw.startsWith("+++ ") -> {
                // File header — skip entirely (not rendered)
                continue
            }

            raw.startsWith("@@ ") -> {
                // Parse hunk header to extract start line numbers
                // Format: @@ -<old_start>[,<count>] +<new_start>[,<count>] @@
                val match = Regex("""^@@\s+-(\d+)(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@""").find(raw)
                if (match != null) {
                    oldLine = match.groupValues[1].toIntOrNull() ?: 1
                    newLine = match.groupValues[2].toIntOrNull() ?: 1
                }
                inputLines += DiffInputLine.HunkHeader(raw)
            }

            raw.startsWith("-") -> {
                inputLines += DiffInputLine.Deletion(raw.drop(1), oldLine)
                oldLine++
            }

            raw.startsWith("+") -> {
                inputLines += DiffInputLine.Addition(raw.drop(1), newLine)
                newLine++
            }

            raw.startsWith(" ") || raw.isEmpty() -> {
                // Context line (space prefix or empty — some diffs omit the space on blank lines)
                inputLines += DiffInputLine.Context(
                    content = if (raw.startsWith(" ")) raw.drop(1) else raw,
                    oldLineNumber = oldLine,
                    newLineNumber = newLine,
                )
                oldLine++
                newLine++
            }

            else -> {
                // Unknown — ignore (diff meta lines like "diff --git", "index …", "new file mode")
                continue
            }
        }
    }

    return buildSplitRows(inputLines)
}

private fun buildSplitRows(inputLines: List<DiffInputLine>): List<SplitRow> {
    val rows = mutableListOf<SplitRow>()
    var i = 0

    while (i < inputLines.size) {
        when (val line = inputLines[i]) {
            is DiffInputLine.HunkHeader -> {
                rows += SplitRow(SplitRow.Kind.HunkHeader(line.text))
                i++
            }

            is DiffInputLine.Context -> {
                rows += SplitRow(
                    SplitRow.Kind.Content(
                        left = SplitDiffCell(line.oldLineNumber, line.content, DiffCellType.CONTEXT),
                        right = SplitDiffCell(line.newLineNumber, line.content, DiffCellType.CONTEXT),
                    ),
                )
                i++
            }

            is DiffInputLine.Deletion -> {
                // Collect all consecutive deletions…
                val deletions = mutableListOf<DiffInputLine.Deletion>()
                while (i < inputLines.size && inputLines[i] is DiffInputLine.Deletion) {
                    deletions += inputLines[i] as DiffInputLine.Deletion
                    i++
                }
                // …then any additions that immediately follow (same change block)
                val additions = mutableListOf<DiffInputLine.Addition>()
                while (i < inputLines.size && inputLines[i] is DiffInputLine.Addition) {
                    additions += inputLines[i] as DiffInputLine.Addition
                    i++
                }
                // Pair them row-by-row; extras get a null on the opposite side
                val pairCount = maxOf(deletions.size, additions.size)
                for (j in 0 until pairCount) {
                    val del = deletions.getOrNull(j)
                    val add = additions.getOrNull(j)
                    rows += SplitRow(
                        SplitRow.Kind.Content(
                            left = del?.let { SplitDiffCell(it.lineNumber, it.content, DiffCellType.DELETION) },
                            right = add?.let { SplitDiffCell(it.lineNumber, it.content, DiffCellType.ADDITION) },
                        ),
                    )
                }
            }

            is DiffInputLine.Addition -> {
                // Standalone addition (not preceded by deletions in the same block)
                rows += SplitRow(
                    SplitRow.Kind.Content(
                        left = null,
                        right = SplitDiffCell(line.lineNumber, line.content, DiffCellType.ADDITION),
                    ),
                )
                i++
            }
        }
    }

    return rows
}

// ── Private models ────────────────────────────────────────────────────────────

/** Intermediate representation of a single line from the raw diff text. */
private sealed interface DiffInputLine {
    data class HunkHeader(val text: String) : DiffInputLine
    data class Context(val content: String, val oldLineNumber: Int, val newLineNumber: Int) : DiffInputLine
    data class Deletion(val content: String, val lineNumber: Int) : DiffInputLine
    data class Addition(val content: String, val lineNumber: Int) : DiffInputLine
}

/** A single cell on one side of the split view. */
private data class SplitDiffCell(
    val lineNumber: Int?,
    val content: String,
    val type: DiffCellType,
)

private enum class DiffCellType { CONTEXT, ADDITION, DELETION }

/** One rendered row in the diff — either a full-width hunk header or a paired content row. */
private data class SplitRow(val kind: Kind) {
    sealed interface Kind {
        data class HunkHeader(val text: String) : Kind
        data class Content(val left: SplitDiffCell?, val right: SplitDiffCell?) : Kind
    }
}
