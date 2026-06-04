package studio.vibe.desktop.ui.codespeak

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.shared.model.SpecFile

@Composable
internal fun EditorColumn(
    selectedSpec: SpecFile?,
    editorContent: String,
    isEditorDirty: Boolean,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDSColors.current
    Column(modifier = modifier.background(colors.surfaceBase)) {
        if (selectedSpec != null) {
            EditorBreadcrumb(spec = selectedSpec, isEditorDirty = isEditorDirty)
            HorizontalDivider(color = LocalDSColors.current.borderSubtle, thickness = 1.dp)

            val scrollState = rememberScrollState()
            BasicTextField(
                value = editorContent,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = LocalDSColors.current.textPrimary,
                    lineHeight = 18.sp,
                ),
                cursorBrush = SolidColor(LocalDSColors.current.accentPrimary),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
                visualTransformation = { text ->
                    val annotated = highlightCodeSpeak(text.text, colors)
                    TransformedText(annotated, OffsetMapping.Identity)
                },
            )
        } else {
            EditorEmptyState()
        }
    }
}

/**
 * "Projects > parentDir > specName" breadcrumb at top of editor column.
 * Placement: ВЕРХ ЦЕНТРАЛЬНОЙ ПАНЕЛИ, по ЛЕВОМУ КРАЮ — per CLAUDE.md invariant.
 */
@Composable
private fun EditorBreadcrumb(spec: SpecFile, isEditorDirty: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(DSLayout.gitSectionHeaderHeight)
            .padding(horizontal = DSSpacing.md),
    ) {
        Text(text = "Projects", style = DSFont.sidebarItemSmall, color = LocalDSColors.current.textMuted)
        BreadcrumbSeparator()
        Text(
            text = spec.path.parent.name.ifEmpty { "project" },
            style = DSFont.sidebarItemSmall,
            color = LocalDSColors.current.textMuted,
        )
        BreadcrumbSeparator()
        Text(text = spec.name, style = DSFont.sidebarItemSmall, color = LocalDSColors.current.textPrimary)
        if (isEditorDirty) {
            Spacer(Modifier.width(DSSpacing.xs))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(LocalDSColors.current.gitModified, CircleShape),
            )
        }
    }
}

@Composable
private fun BreadcrumbSeparator() {
    Text(text = " › ", style = DSFont.sidebarItemSmall, color = LocalDSColors.current.textMuted)
}

@Composable
private fun EditorEmptyState() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.TextSnippet,
                contentDescription = null,
                tint = LocalDSColors.current.textMuted,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(DSSpacing.sm))
            Text(
                text = "Select a spec to view",
                style = DSFont.sidebarItem,
                color = LocalDSColors.current.textMuted,
            )
        }
    }
}
