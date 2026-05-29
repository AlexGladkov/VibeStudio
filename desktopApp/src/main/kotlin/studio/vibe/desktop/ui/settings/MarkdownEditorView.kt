package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSSpacing

/**
 * Monospaced plain-text editor for markdown files used across all Settings sheets.
 *
 * Mirrors MarkdownEditorView.swift: NSTextView wrapped for SwiftUI → BasicTextField.
 *
 * @param text Current text content.
 * @param onTextChange Called whenever the user edits the content.
 * @param isEditable When `false` the field renders as a read-only viewer.
 * @param modifier Modifier applied to the root composable.
 */
@Composable
public fun MarkdownEditorView(
    text: String,
    onTextChange: (String) -> Unit,
    isEditable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val editorStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = if (isEditable) LocalDSColors.current.textPrimary else LocalDSColors.current.textSecondary,
        lineHeight = 18.sp,
    )

    BasicTextField(
        value = text,
        onValueChange = if (isEditable) onTextChange else { _ -> },
        readOnly = !isEditable,
        textStyle = editorStyle,
        cursorBrush = SolidColor(if (isEditable) LocalDSColors.current.accentPrimary else LocalDSColors.current.textMuted),
        modifier = modifier
            .fillMaxSize()
            .background(LocalDSColors.current.surfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DSSpacing.md, vertical = DSSpacing.md),
    )
}
