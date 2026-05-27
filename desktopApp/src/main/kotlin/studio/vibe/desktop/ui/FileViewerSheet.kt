@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@file:Suppress("DEPRECATION")

package studio.vibe.desktop.ui

import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.constants.FileViewerConstants
import java.io.File

// ── Constants ─────────────────────────────────────────────────────────────────

/** Maximum bytes read before the file content is truncated and flagged as too large. */
private const val MAX_FILE_BYTES = 512 * 1024

/** Byte block checked for null-bytes to detect binary files (standard git heuristic). */
private const val BINARY_PROBE_SIZE = 8 * 1024

/** Maximum columns displayed side-by-side. */
private const val MAX_FILE_COLUMNS = 3

/** Pixel height of each code line row. */
private val CODE_LINE_HEIGHT = 20.dp

/** Width of the line-number gutter. */
private val GUTTER_WIDTH = 48.dp

// ── Domain model ──────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy describing every possible display state for a single file column.
 */
private sealed interface FileContentState {
    data object Loading : FileContentState
    data class Loaded(val text: String) : FileContentState
    data object Empty : FileContentState
    data object Binary : FileContentState

    /**
     * @param truncated First [MAX_FILE_BYTES] bytes decoded as UTF-8.
     * @param totalBytes Actual on-disk size reported by [File.length].
     */
    data class TooLarge(val truncated: String, val totalBytes: Long) : FileContentState
    data class Error(val message: String) : FileContentState
}

/**
 * Mutable, observable holder for a single viewed file column.
 *
 * [contentState] is backed by [mutableStateOf] so Compose recomposition is triggered
 * automatically once the async [loadFileContent] coroutine completes.
 *
 * @param path Absolute path to the file on disk.
 */
private class ViewedFileEntry(val path: String) {
    val fileName: String = path.substringAfterLast('/')
    var contentState: FileContentState by mutableStateOf(FileContentState.Loading)
}

// ── Public entry point ────────────────────────────────────────────────────────

/**
 * Resizable [DialogWindow] that renders 1–3 files side-by-side with line numbers.
 *
 * Window width adapts to the file count using constants from [FileViewerConstants].
 * Content is loaded asynchronously; every column handles its own loading / error state.
 *
 * @param filePaths Ordered list of absolute file paths (1–[MAX_FILE_COLUMNS] entries).
 * @param onDismiss Invoked when the user closes the window (Escape key or Close button).
 * @param onAddFile Optional callback for the "Add File" toolbar button.
 *                  Pass `null` to hide the button.
 */
@Composable
fun FileViewerSheet(
    filePaths: List<String>,
    onDismiss: () -> Unit,
    onAddFile: (() -> Unit)? = null,
) {
    require(filePaths.isNotEmpty()) { "FileViewerSheet requires at least one file path" }

    val windowWidth = when (filePaths.size) {
        1    -> FileViewerConstants.SINGLE_FILE_WIDTH
        2    -> FileViewerConstants.TWO_FILE_WIDTH
        else -> FileViewerConstants.THREE_FILE_WIDTH
    }.dp

    val dialogState = rememberDialogState(
        size = DpSize(width = windowWidth, height = FileViewerConstants.SHEET_HEIGHT.dp),
    )

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = buildWindowTitle(filePaths),
        resizable = true,
    ) {
        FileViewerSheetContent(
            filePaths = filePaths,
            onDismiss = onDismiss,
            onAddFile = onAddFile,
        )
    }
}

// ── Sheet content ─────────────────────────────────────────────────────────────

/**
 * Stateful content composable — decoupled from the [DialogWindow] so it can be
 * tested or previewed in isolation.
 */
@Composable
internal fun FileViewerSheetContent(
    filePaths: List<String>,
    onDismiss: () -> Unit,
    onAddFile: (() -> Unit)?,
) {
    val columns = remember(filePaths) {
        mutableStateListOf(*filePaths.map { ViewedFileEntry(it) }.toTypedArray())
    }

    // Kick off an async read on IO dispatcher for every column in Loading state.
    columns.forEach { entry ->
        LaunchedEffect(entry.path) {
            entry.contentState = withContext(Dispatchers.IO) { loadFileContent(entry.path) }
        }
    }

    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColor.surfaceOverlay)
            .onKeyEvent { event ->
                if (event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        FileViewerToolbar(
            fileCount = columns.size,
            canAddFile = columns.size < MAX_FILE_COLUMNS && onAddFile != null,
            onAddFile = onAddFile,
            onCopyAll = {
                clipboardManager.setText(AnnotatedString(buildCopyAllText(columns)))
            },
            onDismiss = onDismiss,
        )

        HorizontalDivider(color = DSColor.borderDefault, thickness = 1.dp)

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            columns.forEachIndexed { index, entry ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(DSColor.borderDefault),
                    )
                }
                FileColumnView(
                    entry = entry,
                    canClose = columns.size > 1,
                    onClose = { columns.removeAt(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

// ── Toolbar ───────────────────────────────────────────────────────────────────

@Composable
private fun FileViewerToolbar(
    fileCount: Int,
    canAddFile: Boolean,
    onAddFile: (() -> Unit)?,
    onCopyAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(DSColor.surfaceRaised)
            .padding(horizontal = DSSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Text(
            text = if (fileCount == 1) "1 file" else "$fileCount files",
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        if (canAddFile && onAddFile != null) {
            AddFileButton(onClick = onAddFile)
        }

        IconButton(
            onClick = onCopyAll,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy all content to clipboard",
                tint = DSColor.textSecondary,
                modifier = Modifier.size(DSFont.iconLG.value.dp),
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = DSColor.textMuted,
                modifier = Modifier.size(DSFont.iconLG.value.dp),
            )
        }
    }
}

@Composable
private fun AddFileButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = DSSpacing.xs, vertical = DSSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = DSColor.accentPrimary,
            modifier = Modifier.size(DSFont.iconMD.value.dp),
        )
        Text(
            text = "Add File",
            style = DSFont.buttonLabel,
            color = DSColor.accentPrimary,
        )
    }
}

// ── File column ───────────────────────────────────────────────────────────────

@Composable
private fun FileColumnView(
    entry: ViewedFileEntry,
    canClose: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FileColumnHeader(
            fileName = entry.fileName,
            extension = entry.fileName.substringAfterLast('.', "").lowercase(),
            canClose = canClose,
            onClose = onClose,
        )

        HorizontalDivider(color = DSColor.borderDefault, thickness = 1.dp)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val state = entry.contentState) {
                FileContentState.Loading -> ColumnLoadingState()
                FileContentState.Empty   -> ColumnEmptyState()
                FileContentState.Binary  -> ColumnBinaryState()

                is FileContentState.Loaded -> {
                    CodeContentView(
                        content = state.text,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is FileContentState.TooLarge -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SizeWarningBanner(totalBytes = state.totalBytes)
                        if (state.truncated.isNotEmpty()) {
                            CodeContentView(
                                content = state.truncated,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(DSColor.surfaceBase),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "File too large to preview (${formatBytes(state.totalBytes)})",
                                    style = DSFont.sidebarItem,
                                    color = DSColor.textMuted,
                                )
                            }
                        }
                    }
                }

                is FileContentState.Error -> ColumnErrorState(message = state.message)
            }
        }
    }
}

// ── Column header ─────────────────────────────────────────────────────────────

@Composable
private fun FileColumnHeader(
    fileName: String,
    extension: String,
    canClose: Boolean,
    onClose: () -> Unit,
) {
    val (icon, iconColor) = fileIconAndColor(extension)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(DSColor.surfaceRaised)
            .padding(horizontal = DSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(DSFont.iconLG.value.dp),
        )

        Text(
            text = fileName,
            style = DSFont.sidebarItem,
            color = DSColor.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (canClose) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close column",
                    tint = DSColor.textMuted,
                    modifier = Modifier.size(DSFont.iconMD.value.dp),
                )
            }
        }
    }
}

// ── Code content view ─────────────────────────────────────────────────────────

/**
 * Scrollable monospace file viewer with a right-aligned line-number gutter.
 *
 * Lines are rendered via [LazyColumn] for O(1) memory usage on large files.
 * The gutter and code columns share the same [LazyListState] so they scroll in
 * lock-step. Horizontal overflow is handled by a shared [rememberScrollState].
 * Text is wrapped in [SelectionContainer] to allow user copy selections.
 *
 * @param content Raw UTF-8 text content of the file.
 * @param modifier Applied to the outer [Box] container.
 */
@Composable
internal fun CodeContentView(
    content: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(content) { content.split('\n') }
    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()

    Box(modifier = modifier.background(DSColor.surfaceBase)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll),
        ) {
            // Line-number gutter — scrolls in sync with code via shared listState
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(GUTTER_WIDTH)
                    .fillMaxHeight()
                    .background(DSColor.surfaceRaised),
                userScrollEnabled = false,
            ) {
                itemsIndexed(lines, key = { index, _ -> "gutter-$index" }) { index, _ ->
                    Box(
                        modifier = Modifier
                            .width(GUTTER_WIDTH)
                            .height(CODE_LINE_HEIGHT)
                            .padding(end = DSSpacing.sm),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = DSFont.monoSmall,
                            color = DSColor.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Gutter / code divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(DSColor.borderSubtle),
            )

            // Code lines — selectable, single-pass monospace rendering
            SelectionContainer(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                ) {
                    itemsIndexed(lines, key = { index, _ -> "line-$index" }) { _, line ->
                        Text(
                            text = line.ifEmpty { " " },
                            style = DSFont.monoSmall.copy(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = DSColor.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CODE_LINE_HEIGHT)
                                .padding(horizontal = DSSpacing.sm),
                        )
                    }
                }
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

// ── Column placeholder states ─────────────────────────────────────────────────

@Composable
private fun ColumnLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize().background(DSColor.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = DSColor.accentPrimary,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun ColumnEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().background(DSColor.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Empty file",
            style = DSFont.sidebarItem,
            color = DSColor.textMuted,
        )
    }
}

@Composable
private fun ColumnBinaryState() {
    Box(
        modifier = Modifier.fillMaxSize().background(DSColor.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = DSColor.textMuted,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = "Binary file — preview not available",
                style = DSFont.sidebarItem,
                color = DSColor.textMuted,
            )
        }
    }
}

@Composable
private fun ColumnErrorState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(DSColor.surfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DSColor.gitDeleted,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = message,
                style = DSFont.sidebarItem,
                color = DSColor.gitDeleted,
            )
        }
    }
}

@Composable
private fun SizeWarningBanner(totalBytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DSColor.gitModified.copy(alpha = 0.10f))
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = DSColor.gitModified,
            modifier = Modifier.size(DSFont.iconBase.value.dp),
        )
        Text(
            text = "File truncated (${formatBytes(totalBytes)}) — showing first ${MAX_FILE_BYTES / 1024} KB",
            style = DSFont.sidebarItemSmall,
            color = DSColor.gitModified,
        )
    }
}

// ── File loading ──────────────────────────────────────────────────────────────

/**
 * Blocking file read — must be called from a coroutine running on an IO or Default
 * dispatcher (the caller is responsible for the [LaunchedEffect] dispatch context).
 *
 * Binary detection: scans the first [BINARY_PROBE_SIZE] bytes for null (0x00) bytes,
 * the same heuristic used by `git diff` and most text editors.
 *
 * Size limiting: files exceeding [MAX_FILE_BYTES] are truncated at that boundary and
 * returned as [FileContentState.TooLarge]. A non-empty truncated preview is always
 * included so the user can still inspect the beginning of large text files.
 */
private fun loadFileContent(path: String): FileContentState = try {
    val file = File(path)

    if (!file.exists() || !file.isFile) {
        return FileContentState.Error("File not found: ${file.name}")
    }

    val totalBytes = file.length()

    // Binary probe
    val probeSize = minOf(totalBytes, BINARY_PROBE_SIZE.toLong()).toInt()
    if (probeSize > 0) {
        val probe = ByteArray(probeSize)
        file.inputStream().use { it.read(probe) }
        if (probe.any { it == 0.toByte() }) {
            return FileContentState.Binary
        }
    }

    if (totalBytes == 0L) return FileContentState.Empty

    if (totalBytes > MAX_FILE_BYTES) {
        val buffer = ByteArray(MAX_FILE_BYTES)
        val bytesRead = file.inputStream().use { stream ->
            var offset = 0
            while (offset < MAX_FILE_BYTES) {
                val read = stream.read(buffer, offset, MAX_FILE_BYTES - offset)
                if (read == -1) break
                offset += read
            }
            offset
        }
        val truncated = String(buffer, 0, bytesRead, Charsets.UTF_8)
        return FileContentState.TooLarge(truncated = truncated, totalBytes = totalBytes)
    }

    val text = file.readText(Charsets.UTF_8)
    if (text.isEmpty()) FileContentState.Empty else FileContentState.Loaded(text)
} catch (e: Exception) {
    FileContentState.Error(e.message ?: "Failed to read file")
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Produces an aggregated plain-text block from all columns, suitable for the system clipboard.
 * Loaded and truncated columns include their content; all other states emit a placeholder line.
 */
private fun buildCopyAllText(columns: List<ViewedFileEntry>): String =
    columns.joinToString("\n\n${"-".repeat(60)}\n\n") { entry ->
        when (val state = entry.contentState) {
            is FileContentState.Loaded   -> "// ${entry.fileName}\n${state.text}"
            is FileContentState.TooLarge -> "// ${entry.fileName} (truncated)\n${state.truncated}"
            else                          -> "// ${entry.fileName} — not available"
        }
    }

/**
 * Human-readable byte count, matching the Swift [formatBytes] output:
 * values >= 1 MB show one decimal place; everything else is shown in KB.
 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    else                -> "%.0f KB".format(bytes / 1_000.0)
}

/** Single-line window title derived from the displayed file names. */
private fun buildWindowTitle(filePaths: List<String>): String {
    val names = filePaths.map { it.substringAfterLast('/') }
    return when (names.size) {
        1    -> names[0]
        2    -> "${names[0]}  vs  ${names[1]}"
        else -> names.joinToString("  |  ")
    }
}

/**
 * Maps a lowercase file extension to a (icon, tint) pair.
 *
 * Intentionally mirrors the mapping in [SidebarView.fileIconAndColor] for visual
 * consistency between the file tree and the viewer header, with additional
 * language entries (JS/TS, Python, Rust, Go, C/C++, Ruby) that are relevant in
 * a full-file preview context.
 */
private fun fileIconAndColor(ext: String): Pair<ImageVector, Color> = when (ext) {
    "kt", "kts"                                -> Icons.Default.Code to DSColor.accentPrimary
    "swift"                                    -> Icons.Default.Code to Color(0xFFF05138)
    "java"                                     -> Icons.Default.Code to Color(0xFFB07219)
    "js", "ts", "tsx", "jsx", "mjs", "cjs"     -> Icons.Default.Code to Color(0xFFF7DF1E)
    "py", "pyi"                                -> Icons.Default.Code to Color(0xFF3572A5)
    "rs"                                       -> Icons.Default.Code to Color(0xFFDEA584)
    "go"                                       -> Icons.Default.Code to Color(0xFF00ADD8)
    "c", "cpp", "cc", "h", "hpp", "hxx"        -> Icons.Default.Code to Color(0xFF555555)
    "rb"                                       -> Icons.Default.Code to Color(0xFF701516)
    "json", "yaml", "yml", "toml"              -> Icons.Default.Settings to DSColor.textSecondary
    "md", "txt", "rst", "adoc"                 -> Icons.Default.Description to DSColor.textSecondary
    "xml", "html", "htm", "css", "scss", "less" -> Icons.Default.Code to DSColor.gitModified
    "gradle", "gradle.kts"                     -> Icons.Default.Settings to DSColor.textSecondary
    "sh", "bash", "zsh", "fish"                -> Icons.Default.Code to DSColor.textSecondary
    "png", "jpg", "jpeg", "svg", "gif", "webp" -> Icons.Default.Image to DSColor.textSecondary
    else -> Icons.AutoMirrored.Filled.InsertDriveFile to DSColor.textSecondary
}
