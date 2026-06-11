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
import studio.vibe.shared.core.common.terminal.TerminalSize
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

    /**
     * Override: do NOT destroy the PTY process when JediTerm closes the widget.
     *
     * Process lifecycle is owned exclusively by [DesktopTerminalService] — it
     * decides when to SIGTERM/SIGKILL via [killSession] / [killAllSessions].
     * Letting JediTerm destroy the process here means switching tabs (which
     * unmounts the widget) would silently kill agent CLIs (Claude, OpenCode,
     * etc.) the user had running in that project, even though they are
     * conceptually still active.
     */
    override fun close() {
        // intentionally no-op — service owns the process
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
        var session: studio.vibe.shared.feature.terminal.domain.model.TerminalSession? = null
    }
    val holder = remember { SessionHolder() }
    var widgetHolder by remember { mutableStateOf<JediTermWidget?>(null) }
    val colors = LocalDSColors.current

    // Persistent cache of JediTermWidget instances keyed by session id. Lets
    // us re-attach the same widget after a tab switch instead of building a
    // fresh one (which loses scrollback and forces a blank terminal until the
    // shell next prints something — the symptom the user reported).
    //
    // Keyed by font size + palette as well so a real change (slider move or
    // theme switch) gets a fresh widget; cosmetic recompositions hit the cache.
    data class WidgetKey(val sessionId: Uuid, val fontSize: Float, val isDark: Boolean)

    val widgetCache = remember { mutableMapOf<WidgetKey, JediTermWidget>() }
    val isDark = colors === DSColor.dark

    // Key on projectId, targetSessionId, fontSize, and palette so we re-create
    // when the theme changes (otherwise the existing widget keeps its old colors).
    LaunchedEffect(projectId, targetSessionId, terminalFontSize, colors) {
        // A session is "live" only when its PTY process is both tracked by the
        // service AND its underlying OS process has not exited. Skipping stale
        // entries is what keeps the terminal from going blank after a tab
        // switch — `_sessions` may still hold a TerminalSession whose pty died.
        fun isLive(s: studio.vibe.shared.feature.terminal.domain.model.TerminalSession?): Boolean {
            if (s == null) return false
            val pty = service.ptyProcessForSession(s.id) ?: return false
            return pty.isAlive
        }

        // 1. Resolve session: target → most recent live project session → create new
        var session: studio.vibe.shared.feature.terminal.domain.model.TerminalSession? = null

        if (targetSessionId != null) {
            val target = service.session(targetSessionId)
            if (isLive(target)) {
                holder.ownsSession = false
                session = target
            }
        }

        if (session == null) {
            // Walk recent-first; pick the first session whose PTY is still alive.
            val live = service.sessions(projectId).reversed().firstOrNull { isLive(it) }
            if (live != null) {
                holder.ownsSession = false
                session = live
            }
        }

        if (session == null) {
            // No live existing session — create a new one; we own its lifecycle.
            holder.ownsSession = true
            session = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                service.createSession(
                    projectId = projectId,
                    shell = initialShell,
                    workingDirectory = workingDirectory?.let {
                        studio.vibe.shared.core.common.FilePath(it)
                    },
                    size = initialSize,
                )
            }
        }
        holder.session = session

        val cacheKey = WidgetKey(session.id, terminalFontSize, isDark)
        val cached = widgetCache[cacheKey]
        if (cached != null) {
            // Re-attach the same widget — scrollback and PTY reader survive.
            widgetHolder = cached
            return@LaunchedEffect
        }

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

        widgetCache[cacheKey] = widget
        widgetHolder = widget
    }

    // Tab switch (projectId change) MUST NOT kill the PTY or dispose the
    // widget — the user expects to come back and find their shell + scrollback
    // intact. We just detach the widget from the current SwingPanel; the
    // cache above keeps the widget alive, and the next composition will
    // re-attach it. Final cleanup happens in the unkeyed DisposableEffect
    // below when the TerminalView itself leaves the composition.
    DisposableEffect(projectId, targetSessionId, terminalFontSize, colors) {
        onDispose {
            widgetHolder = null
            holder.session = null
            holder.ownsSession = false
        }
    }

    // When TerminalView leaves the composition entirely (no active project,
    // toggling CodeSpeak mode, etc.) we own the cleanup — close every cached
    // widget AND detach it from any AWT parent first. JediTermWidget.close()
    // alone tears down its internal state but does not unparent the JComponent,
    // so without the explicit removal the old widget continues to paint on top
    // of whatever surface is mounted next (the symptom the user reported after
    // entering and exiting CodeSpeak mode — ghost terminal stripes).
    DisposableEffect(Unit) {
        onDispose {
            widgetCache.values.forEach { widget ->
                widget.parent?.remove(widget)
                widget.close()
            }
            widgetCache.clear()
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
