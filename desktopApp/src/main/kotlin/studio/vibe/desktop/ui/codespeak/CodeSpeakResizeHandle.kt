package studio.vibe.desktop.ui.codespeak

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import studio.vibe.desktop.ui.theme.LocalDSColors

/**
 * Draggable 5dp-wide vertical resize divider.
 */
@Composable
internal fun CodeSpeakResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onDrag(dragAmount.x)
                }
            }
            .background(LocalDSColors.current.borderDefault),
    )
}
