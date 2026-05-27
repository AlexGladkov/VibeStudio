@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSLayout
import studio.vibe.desktop.ui.theme.DSRadius
import studio.vibe.desktop.ui.theme.DSSpacing

/**
 * A standalone "free" tab — a terminal session not tied to any project.
 *
 * Port of Swift `FreeTabItemView`. Shows a terminal icon and an editable title.
 * Double-clicking the title enters rename mode; pressing Enter or clicking
 * outside commits the new name. The close button appears on hover or when active.
 *
 * @param title        Current tab title.
 * @param isActive     Whether this tab is the currently selected one.
 * @param onClick      Called when the tab is clicked to select it.
 * @param onClose      Called when the × close button is clicked.
 * @param onRename     Called with the new name after an inline rename.
 */
@Composable
fun FreeTabItemView(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(title) { mutableStateOf(title) }
    val focusRequester = remember { FocusRequester() }

    val bgColor = when {
        isActive  -> DSColor.surfaceTabActive
        isHovered -> DSColor.surfaceTabHover
        else      -> DSColor.surfaceTabInactive
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .widthIn(min = DSLayout.tabMinWidth, max = DSLayout.tabMaxWidth)
                .height(DSLayout.tabHeight)
                .clip(RoundedCornerShape(topStart = DSRadius.md, topEnd = DSRadius.md))
                .background(bgColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = DSLayout.tabHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = if (isActive || isHovered) DSColor.textPrimary else DSColor.textSecondary,
                modifier = Modifier.size(DSFont.iconMD.value.dp),
            )

            if (isEditing) {
                // Inline rename text field
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    singleLine = true,
                    textStyle = DSFont.tabTitle.copy(color = DSColor.textPrimary),
                    cursorBrush = SolidColor(DSColor.accentPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (!it.isFocused) commitRename(editText, title, onRename) { isEditing = false } }
                        .onKeyEvent { event ->
                            when {
                                event.type == KeyEventType.KeyUp && event.key == Key.Enter -> {
                                    commitRename(editText, title, onRename) { isEditing = false }
                                    true
                                }
                                event.type == KeyEventType.KeyUp && event.key == Key.Escape -> {
                                    editText = title
                                    isEditing = false
                                    true
                                }
                                else -> false
                            }
                        },
                )
            } else {
                Text(
                    text = title,
                    style = DSFont.tabTitle,
                    color = if (isActive || isHovered) DSColor.textPrimary else DSColor.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }

            if ((isActive || isHovered) && !isEditing) {
                FreeTabCloseButton(onClick = onClose)
            }
        }

        // Active bottom underline
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(DSLayout.tabMinWidth)
                    .height(DSLayout.tabActiveIndicatorHeight)
                    .background(DSColor.accentPrimary),
            )
        }
    }
}

private fun commitRename(
    newName: String,
    fallback: String,
    onRename: (String) -> Unit,
    setEditing: (Boolean) -> Unit,
) {
    val trimmed = newName.trim()
    onRename(if (trimmed.isNotEmpty()) trimmed else fallback)
    setEditing(false)
}

@Composable
private fun FreeTabCloseButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(DSLayout.tabCloseSize)
            .clip(RoundedCornerShape(DSRadius.sm))
            .background(if (isHovered) DSColor.hoverOverlay else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Close tab",
            tint = DSColor.textSecondary,
            modifier = Modifier.size(DSLayout.tabCloseIconSize.value.dp),
        )
    }
}
