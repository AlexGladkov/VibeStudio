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
import com.jediterm.terminal.emulator.ColorPaletteImpl
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.DSColors
import studio.vibe.desktop.ui.theme.LocalDSColors
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

private class VibeTerminalSettingsProvider(
    private val colors: DSColors,
    private val fontSize: Float = 13f,
) : DefaultSettingsProvider() {

    override fun getDefaultBackground(): TerminalColor =
        TerminalColor(colors.surfaceBase.toJColor())

    override fun getDefaultForeground(): TerminalColor =
        TerminalColor(colors.textPrimary.toJColor())

    override fun getSelectionColor(): TextStyle =
        TextStyle(
            TerminalColor(colors.textPrimary.toJColor()),
            TerminalColor(colors.surfaceSelection.toJColor()),
        )

    override fun getTerminalColorPalette() = ColorPaletteImpl.XTERM_PALETTE

    override fun getTerminalFont(): Font =
        Font(Font.MONOSPACED, Font.PLAIN, fontSize.toInt())

    override fun getTerminalFontSize(): Float = fontSize

    override fun getLineSpacing(): Float = 1.1f

    override fun scrollToBottomOnTyping(): Boolean = true

    override fun copyOnSelect(): Boolean = false

    override fun pasteOnMiddleMouseClick(): Boolean = false

    override fun useAntialiasing(): Boolean = true
}

// ── PTY connector ────────────────────────────────────────────────────────────

/**
 * Bridges pty4j to JediTerm **and** feeds captured output back to
 * [DesktopTerminalService.receiveOutput] for scrollback / activity tracking.
 *
 * This connector is the **sole reader** of the PTY [java.io.InputStream].
 * The service no longer spawns its own reader coroutine, which eliminates
 * the data-race where two threads competed for the same stream and JediTerm
 * received partial or no output.
 */
private class Pty4JConnector(
    private val ptyProcess: com.pty4j.PtyProcess,
    private val service: DesktopTerminalService,
    private val sessionId: Uuid,
    charset: Charset = Charsets.UTF_8,
) : ProcessTtyConnector(ptyProcess, charset) {

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val bytesRead = super.read(buf, offset, length)
        if (bytesRead > 0) {
            val chunk = String(buf, offset, bytesRead)
            service.receiveOutput(sessionId, chunk)
        }
        return bytesRead
    }

    override fun resize(termSize: TermSize) {
        service.resize(
            sessionId = sessionId,
            size = TerminalSize(columns = termSize.columns, rows = termSize.rows),
        )
    }

    override fun getName(): String = "pty4j"
}

// ── TerminalView ─────────────────────────────────────────────────────────────

/**
 * Compose wrapper for a JediTerm terminal backed by pty4j.
 *
 * @param service         The desktop terminal service that owns PTY sessions.
 * @param projectId       Owner project — used to look up / create sessions.
 * @param targetSessionId Optional specific session ID to display (e.g. an agent session
 *                        created by [ToolbarViewModel.launchAgent]).  When `null` the view
 *                        reuses the most recent existing session for [projectId] or creates
 *                        a new one.
 * @param terminalFontSize Font size for the terminal (from GeneralPreferences).
 * @param initialShell    Shell executable.  `null` → default shell.
 * @param workingDirectory Working directory for the shell.
 * @param initialSize     Initial PTY dimensions.
 */
@Composable
fun TerminalView(
    service: DesktopTerminalService,
    projectId: Uuid,
    targetSessionId: Uuid? = null,
    terminalFontSize: Float = 13f,
    initialShell: String? = null,
    workingDirectory: String? = null,
    initialSize: TerminalSize = TerminalSize(columns = 220, rows = 50),
    modifier: Modifier = Modifier,
) {
    // Track session ownership and current session outside of LaunchedEffect
    // to avoid race conditions between LaunchedEffect and DisposableEffect.
    // Using a holder object ensures both effects see the same reference.
    class SessionHolder {
        var ownsSession: Boolean = false
        var session: studio.vibe.shared.model.TerminalSession? = null
    }
    val holder = remember { SessionHolder() }
    var widgetHolder by remember { mutableStateOf<JediTermWidget?>(null) }
    val colors = LocalDSColors.current

    // Key on projectId, targetSessionId, fontSize, and palette so we re-create
    // when the theme changes (otherwise the existing widget keeps its old colors).
    LaunchedEffect(projectId, targetSessionId, terminalFontSize, colors) {
        // 1. Resolve session: target → existing for project → create new
        var session = if (targetSessionId != null) {
            // Agent session created externally — we do NOT own it.
            holder.ownsSession = false
            service.session(targetSessionId)
        } else {
            service.sessions(projectId).lastOrNull()
        }

        // Fallback: if targetSessionId was given but session is gone (agent exited),
        // try most recent project session before creating a new one.
        if (session == null && targetSessionId != null) {
            holder.ownsSession = false
            session = service.sessions(projectId).lastOrNull()
        }

        if (session == null) {
            // No existing session — create a new one; we own its lifecycle.
            holder.ownsSession = true
            session = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                service.createSession(
                    projectId = projectId,
                    shell = initialShell,
                    workingDirectory = workingDirectory?.let {
                        studio.vibe.shared.model.FilePath(it)
                    },
                    size = initialSize,
                )
            }
        }
        holder.session = session

        val ptyProcess = service.ptyProcessForSession(session.id) ?: return@LaunchedEffect

        // JediTerm widget must be created on EDT — LaunchedEffect on Desktop dispatches on
        // Dispatchers.Main which *is* the Swing EDT (via kotlinx-coroutines-swing).
        val settings = VibeTerminalSettingsProvider(colors = colors, fontSize = terminalFontSize)
        val widget = JediTermWidget(settings)

        val connector = Pty4JConnector(
            ptyProcess = ptyProcess,
            service = service,
            sessionId = session.id,
        )

        widget.createTerminalSession(connector)
        widget.start()

        widget.background = colors.surfaceBase.toAwtColor()
        widget.isOpaque = true

        widgetHolder = widget
    }

    DisposableEffect(projectId, targetSessionId, terminalFontSize) {
        onDispose {
            // Only kill the session if *we* created it — don't kill agent sessions owned by toolbar.
            if (holder.ownsSession) {
                holder.session?.id?.let { id -> service.killSession(id, force = false) }
            }
            widgetHolder?.close()
            widgetHolder = null
            holder.ownsSession = false
            holder.session = null
        }
    }

    val widget = widgetHolder
    if (widget != null) {
        SwingPanel(
            modifier = modifier,
            factory = {
                JPanel(java.awt.BorderLayout()).apply {
                    background = colors.surfaceBase.toAwtColor()
                    add(widget, java.awt.BorderLayout.CENTER)
                }
            },
            update = { panel ->
                // When the widget changes (project/session/fontSize changed), swap it
                val current = widgetHolder ?: return@SwingPanel
                if (panel.componentCount == 0 || panel.getComponent(0) !== current) {
                    panel.removeAll()
                    panel.add(current, java.awt.BorderLayout.CENTER)
                    panel.revalidate()
                    panel.repaint()
                }
            },
        )
    }
}
