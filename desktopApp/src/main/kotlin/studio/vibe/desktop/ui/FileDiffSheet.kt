@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import studio.vibe.desktop.DesktopServiceContainer
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.model.FilePath
import studio.vibe.shared.model.GitFile
import studio.vibe.shared.model.GitFileStatus

// ── Binary extensions that cannot produce a text diff ────────────────────────

private val BINARY_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "ico", "icns", "pdf",
    "zip", "tar", "gz", "bz2", "xz", "dmg", "exe",
    "dylib", "so", "a", "o", "class", "jar", "war",
    "ear", "bin", "dat", "db", "sqlite",
)

private const val MAX_DIFF_BYTES = 512 * 1024 // 512 KB guard

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * A standalone [DialogWindow] that shows a side-by-side diff for [file].
 *
 * Fetches the diff asynchronously via [DesktopServiceContainer.gitService] and
 * renders it inside [DiffView].  Handles loading, error, binary, and empty states.
 *
 * @param container Service container providing [GitServicing].
 * @param file The git file whose diff should be displayed.
 * @param projectPath Absolute path to the git repository root.
 * @param staged Whether to show the staged diff (`git diff --cached`) or the
 *               working-tree diff (`git diff`).
 * @param onDismiss Called when the user requests the window to close.
 */
@Composable
fun FileDiffSheet(
    container: DesktopServiceContainer,
    file: GitFile,
    projectPath: String,
    staged: Boolean = false,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(
        size = DpSize(width = 1000.dp, height = 640.dp),
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = file.path.substringAfterLast('/'),
        resizable = true,
    ) {
        FileDiffSheetContent(
            container = container,
            file = file,
            projectPath = projectPath,
            staged = staged,
            onDismiss = onDismiss,
        )
    }
}

// ── Sheet content (separated so it can be tested without a window) ────────────

@Composable
internal fun FileDiffSheetContent(
    container: DesktopServiceContainer,
    file: GitFile,
    projectPath: String,
    staged: Boolean,
    onDismiss: () -> Unit,
) {
    // UI state
    var diffText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sizeWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.path, staged) {
        isLoading = true
        errorMessage = null
        sizeWarning = null
        diffText = ""

        val ext = file.path.substringAfterLast('.', "").lowercase()
        if (ext in BINARY_EXTENSIONS) {
            errorMessage = "Binary file — diff not available"
            isLoading = false
            return@LaunchedEffect
        }

        runCatching {
            val hunks = container.gitService.diff(
                file = file.path,
                staged = staged,
                at = FilePath(projectPath),
            )

            // Reconstruct unified diff text from hunks for the DiffView parser
            val sb = StringBuilder()
            var totalBytes = 0
            for (hunk in hunks) {
                sb.appendLine(hunk.header)
                for (line in hunk.lines) {
                    val prefix = when (line.type) {
                        studio.vibe.shared.model.DiffLineType.ADDITION -> "+"
                        studio.vibe.shared.model.DiffLineType.DELETION -> "-"
                        studio.vibe.shared.model.DiffLineType.CONTEXT -> " "
                    }
                    val row = "$prefix${line.content}"
                    totalBytes += row.encodeToByteArray().size
                    sb.appendLine(row)
                }
                if (totalBytes > MAX_DIFF_BYTES) {
                    sizeWarning = "Diff truncated — file too large (${totalBytes / 1024} KB shown)"
                    break
                }
            }
            diffText = sb.toString()
        }.onFailure { e ->
            errorMessage = e.message ?: "Failed to load diff"
        }

        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(LocalDSColors.current.surfaceBase)) {
        DiffSheetHeader(
            file = file,
            staged = staged,
            onDismiss = onDismiss,
        )

        HorizontalDivider(color = LocalDSColors.current.borderDefault, thickness = 1.dp)

        sizeWarning?.let { warning ->
            Text(
                text = warning,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.indicatorWaiting,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalDSColors.current.surfaceOverlay)
                    .padding(horizontal = DSSpacing.sm, vertical = DSSpacing.xxs),
            )
            HorizontalDivider(color = LocalDSColors.current.borderDefault, thickness = 1.dp)
        }

        when {
            isLoading -> LoadingState()
            errorMessage != null -> ErrorState(errorMessage!!)
            diffText.isBlank() -> ErrorState("No changes")
            else -> DiffView(
                diffText = diffText,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun DiffSheetHeader(
    file: GitFile,
    staged: Boolean,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(LocalDSColors.current.surfaceRaised)
            .padding(horizontal = DSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        // Status letter badge
        val statusLetter = statusLetterFor(file.status)
        val statusColor = statusColorFor(file.status, LocalDSColors.current)
        Text(
            text = statusLetter,
            style = DSFont.gitStatus,
            color = statusColor,
            modifier = Modifier.width(16.dp),
        )

        // Filename
        val fileName = file.path.substringAfterLast('/')
        Text(
            text = fileName,
            style = DSFont.gitBranch,
            color = LocalDSColors.current.textPrimary,
            maxLines = 1,
        )

        // Directory path (dimmed, truncated in the middle)
        val dir = file.path.substringBeforeLast('/', "")
        if (dir.isNotEmpty()) {
            Text(
                text = dir,
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        // "staged" badge
        if (staged) {
            Text(
                text = "staged",
                style = DSFont.statusBadge,
                color = LocalDSColors.current.gitAdded,
                modifier = Modifier
                    .background(
                        color = LocalDSColors.current.gitAdded.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(DSRadius.sm),
                    )
                    .padding(horizontal = DSSpacing.xs, vertical = 2.dp),
            )
        }

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close diff",
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Loading / error states ────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = LocalDSColors.current.accentPrimary,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = DSFont.sidebarItem,
            color = LocalDSColors.current.textMuted,
        )
    }
}

// ── Status helpers (package-private for reuse in GitPanel) ───────────────────

internal fun statusLetterFor(status: GitFileStatus): String = when (status) {
    GitFileStatus.MODIFIED -> "M"
    GitFileStatus.ADDED -> "A"
    GitFileStatus.DELETED -> "D"
    GitFileStatus.RENAMED -> "R"
    GitFileStatus.COPIED -> "C"
    GitFileStatus.UNTRACKED -> "?"
}

internal fun statusColorFor(status: GitFileStatus, colors: DSColors): Color = when (status) {
    GitFileStatus.MODIFIED -> colors.gitModified
    GitFileStatus.ADDED -> colors.gitAdded
    GitFileStatus.DELETED -> colors.gitDeleted
    GitFileStatus.RENAMED -> colors.gitRenamed
    GitFileStatus.COPIED -> colors.gitAdded
    GitFileStatus.UNTRACKED -> colors.gitUntracked
}
