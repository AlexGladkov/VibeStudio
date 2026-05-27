@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.jediterm.core.Color as JColor
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.ColorPaletteImpl
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.shared.model.TerminalSize
import java.awt.Font
import java.nio.charset.Charset
import javax.swing.JPanel
import kotlin.uuid.Uuid

// ── Compose Color → JediTerm Color ──────────────────────────────────────────

private fun androidx.compose.ui.graphics.Color.toJColor(): JColor =
    JColor((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun androidx.compose.ui.graphics.Color.toAwtColor(): java.awt.Color =
    java.awt.Color(red, green, blue, alpha)

// ── JediTerm settings provider ──────────────────────────────────────────────

private class VibeTerminalSettingsProvider : DefaultSettingsProvider() {

    override fun getDefaultBackground(): TerminalColor =
        TerminalColor(DSColor.surfaceBase.toJColor())

    override fun getDefaultForeground(): TerminalColor =
        TerminalColor(DSColor.textPrimary.toJColor())

    override fun getSelectionColor(): TextStyle =
        TextStyle(
            TerminalColor(DSColor.textPrimary.toJColor()),
            TerminalColor(DSColor.surfaceSelection.toJColor()),
        )

    override fun getTerminalColorPalette() = ColorPaletteImpl.XTERM_PALETTE

    override fun getTerminalFont(): Font =
        Font(Font.MONOSPACED, Font.PLAIN, 13)

    override fun getTerminalFontSize(): Float = 13f

    override fun getLineSpacing(): Float = 1.1f

    override fun scrollToBottomOnTyping(): Boolean = true

    override fun copyOnSelect(): Boolean = false

    override fun pasteOnMiddleMouseClick(): Boolean = false

    override fun useAntialiasing(): Boolean = true
}

// ── PTY connector ────────────────────────────────────────────────────────────

private class Pty4JConnector(
    private val ptyProcess: com.pty4j.PtyProcess,
    private val service: DesktopTerminalService,
    private val sessionId: Uuid,
    charset: Charset = Charsets.UTF_8,
) : ProcessTtyConnector(ptyProcess, charset) {

    override fun resize(termSize: TermSize) {
        service.resize(
            sessionId = sessionId,
            size = TerminalSize(columns = termSize.columns, rows = termSize.rows),
        )
    }

    override fun getName(): String = "pty4j"
}

// ── TerminalView ─────────────────────────────────────────────────────────────

@Composable
fun TerminalView(
    service: DesktopTerminalService,
    projectId: Uuid,
    initialShell: String? = null,
    workingDirectory: String? = null,
    initialSize: TerminalSize = TerminalSize(columns = 220, rows = 50),
    modifier: Modifier = Modifier,
) {
    var sessionRef by remember { mutableStateOf<studio.vibe.shared.model.TerminalSession?>(null) }
    val widgetHolder = remember { mutableStateOf<JediTermWidget?>(null) }

    LaunchedEffect(projectId) {
        val session = service.createSession(
            projectId = projectId,
            shell = initialShell,
            workingDirectory = workingDirectory?.let {
                studio.vibe.shared.model.FilePath(it)
            },
            size = initialSize,
        )
        sessionRef = session

        val ptyProcess = service.ptyProcessForSession(session.id) ?: return@LaunchedEffect

        val settings = VibeTerminalSettingsProvider()
        val widget = JediTermWidget(settings)

        val connector = Pty4JConnector(
            ptyProcess = ptyProcess,
            service = service,
            sessionId = session.id,
        )

        widget.createTerminalSession(connector)
        widget.start()

        widget.background = DSColor.surfaceBase.toAwtColor()
        widget.isOpaque = true

        widgetHolder.value = widget
    }

    DisposableEffect(projectId) {
        onDispose {
            sessionRef?.id?.let { id -> service.killSession(id, force = false) }
            widgetHolder.value?.close()
            widgetHolder.value = null
        }
    }

    val widget = widgetHolder.value
    if (widget != null) {
        SwingPanel(
            modifier = modifier,
            factory = {
                JPanel(java.awt.BorderLayout()).apply {
                    background = DSColor.surfaceBase.toAwtColor()
                    add(widget, java.awt.BorderLayout.CENTER)
                }
            },
            update = { },
        )
    }
}
