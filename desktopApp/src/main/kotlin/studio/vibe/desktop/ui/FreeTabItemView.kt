@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
 * Additions:
 * - Drag-to-reorder: drag the tab horizontally. The parent is responsible for
 *   tracking [dragOffsetX] and calling the [onDrag] / [onDragEnd] callbacks to
 *   update list order and reset the offset.
 * - Close confirmation: when [requiresCloseConfirmation] is `true`, tapping × shows
 *   an [AlertDialog] before invoking [onClose].
 *
 * @param title                    Current tab title.
 * @param isActive                 Whether this tab is the currently selected one.
 * @param onClick                  Called when the tab is clicked to select it.
 * @param onClose                  Called when the × close button is confirmed.
 * @param onRename                 Called with the new name after an inline rename.
 * @param isDragging               Whether this tab is currently being dragged.
 * @param dragOffsetX              Current horizontal drag displacement in pixels.
 * @param requiresCloseConfirmation When `true`, closing this tab shows a dialog.
 * @param onDragStart              Called when the drag gesture begins.
 * @param onDrag                   Called with the horizontal drag delta each frame.
 * @param onDragEnd                Called when the drag gesture ends or is cancelled.
 * @param onWidthMeasured          Called once with the measured tab width in pixels;
 *                                  used by the parent to calculate swap thresholds.
 */
@Composable
fun FreeTabItemView(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragOffsetX: Float = 0f,
    requiresCloseConfirmation: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (delta: Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onWidthMeasured: (Float) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(title) { mutableStateOf(title) }
    val focusRequester = remember { FocusRequester() }
    var showCloseConfirm by remember { mutableStateOf(false) }

    val bgColor = when {
        isDragging -> DSColor.surfaceTabActive
        isActive   -> DSColor.surfaceTabActive
        isHovered  -> DSColor.surfaceTabHover
        else       -> DSColor.surfaceTabInactive
    }

    Box(
        modifier = modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .onGloballyPositioned { coords -> onWidthMeasured(coords.size.width.toFloat()) }
            .then(if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(DSRadius.md)) else Modifier)
            .pointerInput(title) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    ) {
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
                FreeTabCloseButton(
                    onClick = {
                        if (requiresCloseConfirmation) {
                            showCloseConfirm = true
                        } else {
                            onClose()
                        }
                    },
                )
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

    // ── Close confirmation dialog ─────────────────────────────────────────────
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Close \"$title\"?") },
            text = {
                Text("This terminal has unsaved or active content. Are you sure you want to close it?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirm = false
                        onClose()
                    },
                ) {
                    Text("Close Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
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
