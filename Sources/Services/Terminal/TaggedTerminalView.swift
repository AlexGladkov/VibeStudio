// MARK: - TaggedTerminalView
// Extended SwiftTerm LocalProcessTerminalView with session tracking.
// macOS 14+, Swift 5.10

import AppKit
import SwiftTerm

/// A ``LocalProcessTerminalView`` subclass that carries session/project
/// identifiers and reports activity via a callback.
///
/// Used by ``TerminalService`` to track which terminal view belongs
/// to which session and detect background activity for tab indicators.
final class TaggedTerminalView: LocalProcessTerminalView {

    /// Unique identifier of the terminal session this view belongs to.
    let sessionId: UUID

    /// Identifier of the project this session belongs to.
    let projectId: UUID

    /// Callback invoked when new content appears in the terminal.
    /// Used to detect background activity for tab indicators.
    var onRangeChanged: ((UUID) -> Void)?

    /// Callback invoked when the terminal process exits.
    var onProcessExited: ((UUID, Int32) -> Void)?

    /// Callback invoked when the terminal title changes (xterm escape sequence).
    var onTitleChanged: ((UUID, String) -> Void)?

    /// Callback for incremental terminal output -- provides changed line range.
    /// Used by RemoteSessionBridge to stream output to WebSocket clients.
    /// Parameters: (sessionId, startY, endY)
    var onLinesChanged: ((UUID, Int, Int) -> Void)?

    /// Callback for raw PTY output bytes -- used by RemoteSessionBridge
    /// to relay unprocessed terminal data to the WebSocket client.
    /// This preserves ANSI escape sequences so the remote xterm.js can
    /// correctly render colors, cursor movements, and scrolling.
    /// Parameters: (sessionId, rawBytes)
    var onRawData: ((UUID, ArraySlice<UInt8>) -> Void)?

    /// Second parallel callback for PTY output bytes — used by CostTrackerService
    /// to parse usage/cost lines without interfering with onRawData / RemoteBridgeRegistry.
    /// Called AFTER onRawData, before super.dataReceived.
    /// Parameters: (sessionId, rawBytes)
    var onParsedOutput: ((UUID, ArraySlice<UInt8>) -> Void)?

    // MARK: - Init

    /// Create a tagged terminal view for a specific session and project.
    ///
    /// - Parameters:
    ///   - sessionId: Session identifier.
    ///   - projectId: Project identifier.
    ///   - frame: Initial frame rectangle.
    init(sessionId: UUID, projectId: UUID, frame: NSRect = .zero) {
        self.sessionId = sessionId
        self.projectId = projectId
        super.init(frame: frame)

        // Stop plain-text URLs from auto-opening in the browser when a window
        // switch / tab activation click lands over a URL. The activating click
        // often carries the Command modifier (Cmd+Tab, Cmd+click), which
        // SwiftTerm's `mouseUp` treats as a deliberate link click.
        //
        // Two independent link paths exist in SwiftTerm and BOTH must be gated:
        //
        // 1. `linkReporting` — controls hover preview + motion link reporting
        //    (`reportLink`). `.none` suppresses the hover URL bubble.
        //
        // 2. `linkHighlightMode` — controls the CLICK-to-open path
        //    (`mouseUp` → `linkForClick` → `requestOpenLink`). This path
        //    ignores `linkReporting` entirely, so `.none` alone does NOT stop
        //    click-open. `linkForClick` gates on `linkVisibleForClick`, which
        //    for `.alwaysWithModifier` returns `match.isExplicit && Command`.
        //    Implicit (plain-text) URLs have `isExplicit == false`, so they can
        //    never be opened by a click — killing the stray-activation-open bug.
        //
        // `requestOpenLink` itself cannot be overridden here: it is satisfied by
        // a protocol-extension default (static witness dispatch), so a subclass
        // override is never called. Gating via these two public knobs is the
        // reliable fix. Only deliberate OSC 8 hyperlinks remain openable, and
        // only with an explicit Command+click.
        linkReporting = .none
        linkHighlightMode = .alwaysWithModifier

        installWheelForwardingMonitor()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    deinit {
        if let monitor = scrollMonitor {
            NSEvent.removeMonitor(monitor)
        }
    }

    // MARK: - Mouse-wheel forwarding

    /// Token for the local scroll-wheel monitor; removed in `deinit`.
    ///
    /// `nonisolated(unsafe)` so the `deinit` (which is non-isolated) can read it.
    /// `NSEvent.removeMonitor` is safe to call from the main thread, where the
    /// view is always deallocated.
    private nonisolated(unsafe) var scrollMonitor: Any?

    /// Make the scroll wheel work inside full-screen TUIs (claude / opencode)
    /// by forwarding wheel ticks to the app as mouse-wheel events — exactly
    /// what a real terminal (Terminal.app / iTerm2) does.
    ///
    /// SwiftTerm's own `scrollWheel` only scrolls its scrollback buffer, which
    /// is empty on the alternate screen, so the wheel feels dead inside an
    /// agent. `scrollWheel` is `public` (not `open`) in SwiftTerm and cannot be
    /// overridden from this module, so we intercept the event with a local
    /// monitor before it reaches the view.
    ///
    /// We only intervene when the app has enabled mouse tracking
    /// (`mouseMode != .off`): then a wheel tick is sent as SGR/normal mouse
    /// button 64 (up) / 65 (down) at the pointer cell, and the app scrolls its
    /// own view. When mouse tracking is off we pass the event through untouched
    /// — SwiftTerm's native scrollback keeps working and nothing is injected
    /// into a keyboard-driven app (an earlier arrow-key approach wrongly walked
    /// the prompt history, so it was removed).
    ///
    /// Known limitation: because the wheel event carries the pointer cell, some
    /// TUIs (e.g. Claude Code) may move their input cursor toward that cell on
    /// scroll. This is an accepted trade-off — scrolling inside the agent is
    /// worth more than the occasional cursor nudge. Revisit if SwiftTerm exposes
    /// the true cell metrics so we can match a real terminal exactly.
    private func installWheelForwardingMonitor() {
        scrollMonitor = NSEvent.addLocalMonitorForEvents(matching: .scrollWheel) { [weak self] event in
            guard let self else { return event }
            return MainActor.assumeIsolated {
                self.forwardWheel(event)
            }
        }
    }

    /// Returns `nil` to consume the event (forwarded to the app as a mouse-wheel
    /// event) or the original `event` to let AppKit / SwiftTerm handle it.
    private func forwardWheel(_ event: NSEvent) -> NSEvent? {
        guard let terminal,
              let window,
              event.window === window,
              event.deltaY != 0,
              // Ignore inertial / momentum scrolling: after the fingers lift, the
              // OS keeps emitting decaying scroll events. Forwarding those makes
              // the app keep scrolling while the user has already moved to the
              // keyboard, which reads as the cursor "jumping" mid-typing.
              event.momentumPhase == [],
              allowMouseReporting,
              terminal.mouseMode != .off,
              // Only on the alternate screen. On the normal buffer we never
              // intervene, so SwiftTerm's native scrollback + draggable scroller
              // keep working even if a tool enables mouse tracking.
              terminal.isCurrentBufferAlternate else {
            return event
        }

        // Only act when the pointer is actually over this terminal view.
        let localPoint = convert(event.locationInWindow, from: nil)
        guard bounds.contains(localPoint) else { return event }

        // Approximate the cell under the pointer. `cellDimension` is internal to
        // SwiftTerm, so derive it from bounds / grid size — for a wheel event the
        // exact cell is not significant, apps scroll the focused pane regardless.
        let cols = max(terminal.cols, 1)
        let rows = max(terminal.rows, 1)
        let cellW = max(bounds.width / CGFloat(cols), 1)
        let cellH = max(bounds.height / CGFloat(rows), 1)
        let col = min(max(0, Int(localPoint.x / cellW)), cols - 1)
        // View is not flipped: terminal row 0 is at the top (high y).
        let row = min(max(0, Int((bounds.height - localPoint.y) / cellH)), rows - 1)

        // Wheel up = button 4 (Cb 64), wheel down = button 5 (Cb 65).
        // Send exactly one wheel event per scroll event — matching real
        // terminals. Multiplying by the delta over-scrolls and reads as jumpy.
        let button = event.deltaY > 0 ? 4 : 5
        let flags = terminal.encodeButton(
            button: button, release: false, shift: false, meta: false, control: false
        )
        terminal.sendEvent(buttonFlags: flags, x: col, y: row)

        return nil
    }

    // MARK: - Overrides

    /// Intercept raw PTY data before terminal emulation.
    ///
    /// Called by `LocalProcess` when bytes arrive from the PTY file descriptor.
    /// We forward raw bytes to `onRawData` before passing them to SwiftTerm's
    /// terminal emulator via `super.dataReceived`. This preserves ANSI escape
    /// sequences for remote xterm.js rendering.
    override func dataReceived(slice: ArraySlice<UInt8>) {
        // ARCH-C1: NSLog removed — was called per PTY chunk (thousands/sec).
        onRawData?(sessionId, slice)
        // Second parallel channel for cost tracking — called AFTER onRawData,
        // before super.dataReceived. Does NOT affect onRawData/RemoteBridgeRegistry.
        onParsedOutput?(sessionId, slice)
        super.dataReceived(slice: slice)
    }

    /// Detect new terminal output for activity tracking.
    ///
    /// This method is called by SwiftTerm when terminal content changes.
    /// The `source` parameter is a `TerminalView`, not a `Terminal`.
    override func rangeChanged(source: TerminalView, startY: Int, endY: Int) {
        super.rangeChanged(source: source, startY: startY, endY: endY)
        onRangeChanged?(sessionId)
        onLinesChanged?(sessionId, startY, endY)
    }

    /// Handle process termination from SwiftTerm's `LocalProcessDelegate`.
    ///
    /// `LocalProcessTerminalView.processTerminated` calls `processDelegate`,
    /// but we also fire our own `onProcessExited` callback so
    /// `TerminalService` can update observable state.
    override func processTerminated(_ source: LocalProcess, exitCode: Int32?) {
        super.processTerminated(source, exitCode: exitCode)
        onProcessExited?(sessionId, exitCode ?? -1)
    }

    // MARK: - Context Menu (right-click copy/paste)

    /// Right-click context menu for copy / paste / select-all.
    ///
    /// This is the primary mouse-driven way to copy terminal output.
    /// Cmd+C also works when the terminal is first responder.
    override func menu(for event: NSEvent) -> NSMenu? {
        let menu = NSMenu(title: "Terminal")

        let hasSelection = selectionActive

        // Copy — enabled only when text is selected.
        let copyItem = NSMenuItem(
            title: "Copy",
            action: #selector(copy(_:)),
            keyEquivalent: "c"
        )
        copyItem.keyEquivalentModifierMask = .command
        copyItem.isEnabled = hasSelection
        copyItem.target = self
        menu.addItem(copyItem)

        // Paste — always available.
        let pasteItem = NSMenuItem(
            title: "Paste",
            action: #selector(paste(_:)),
            keyEquivalent: "v"
        )
        pasteItem.keyEquivalentModifierMask = .command
        pasteItem.target = self
        menu.addItem(pasteItem)

        menu.addItem(.separator())

        // Select All
        let selectAllItem = NSMenuItem(
            title: "Select All",
            action: #selector(selectAll(_:)),
            keyEquivalent: "a"
        )
        selectAllItem.keyEquivalentModifierMask = .command
        selectAllItem.target = self
        menu.addItem(selectAllItem)

        return menu
    }
}

// MARK: - Focus Helper

/// A transparent overlay view that captures mouse clicks and routes
/// focus to the terminal view. Used because `mouseDown` on
/// `TerminalView` is not `open` and cannot be overridden from outside.
final class TerminalFocusHelper: NSView {
    weak var terminalView: NSView?

    override func mouseDown(with event: NSEvent) {
        if let tv = terminalView {
            window?.makeFirstResponder(tv)
        }
        super.mouseDown(with: event)
    }

    override func hitTest(_ point: NSPoint) -> NSView? {
        // Pass through -- let the terminal view handle all events.
        return nil
    }
}
