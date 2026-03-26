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
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    // MARK: - Overrides

    /// Detect new terminal output for activity tracking.
    ///
    /// This method is called by SwiftTerm when terminal content changes.
    /// The `source` parameter is a `TerminalView`, not a `Terminal`.
    override func rangeChanged(source: TerminalView, startY: Int, endY: Int) {
        super.rangeChanged(source: source, startY: startY, endY: endY)
        onRangeChanged?(sessionId)
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
