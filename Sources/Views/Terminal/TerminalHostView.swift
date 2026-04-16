// MARK: - TerminalHostView
// NSViewRepresentable bridge for SwiftTerm terminal view.
// PTY lifecycle is managed by TerminalService, not this view.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit
import SwiftTerm

/// Bridges a SwiftTerm ``TaggedTerminalView`` into SwiftUI.
///
/// Important lifecycle rules:
/// - `makeNSView` calls `terminalService.attachView(to:)` to get the view.
/// - `dismantleNSView` calls `detachView(from:)` -- does NOT kill the PTY.
/// - The PTY process continues running when the view is off-screen.
/// - Focus is captured on click via `mouseDown` override in ``TaggedTerminalView``.
struct TerminalHostView: NSViewRepresentable {

    let sessionId: UUID

    @Environment(\.terminalSessionManager) private var terminalManager
    /// Observed so SwiftUI calls `updateNSView` whenever the color scheme changes.
    @Environment(\.colorScheme) private var colorScheme

    // MARK: - NSViewRepresentable

    func makeNSView(context: Context) -> NSView {
        let container = NSView()
        container.wantsLayer = true
        // `NSColor.cgColor` must be resolved inside a drawing-appearance context;
        // outside one it returns a concrete (non-dynamic) CGColor locked to whatever
        // appearance was current at call time — causing wrong colours on theme switch.
        NSApp.effectiveAppearance.performAsCurrentDrawingAppearance {
            container.layer?.backgroundColor = DSColor.surfaceBaseNS.cgColor
        }

        do {
            let terminalView = try terminalManager.attachView(to: sessionId)
            terminalView.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(terminalView)

            NSLayoutConstraint.activate([
                terminalView.topAnchor.constraint(
                    equalTo: container.topAnchor,
                    constant: DSLayout.terminalPadding.top
                ),
                terminalView.leadingAnchor.constraint(
                    equalTo: container.leadingAnchor,
                    constant: DSLayout.terminalPadding.leading
                ),
                terminalView.trailingAnchor.constraint(
                    equalTo: container.trailingAnchor,
                    constant: -DSLayout.terminalPadding.trailing
                ),
                terminalView.bottomAnchor.constraint(
                    equalTo: container.bottomAnchor,
                    constant: -DSLayout.terminalPadding.bottom
                ),
            ])

            // Request focus.
            DispatchQueue.main.async {
                terminalView.window?.makeFirstResponder(terminalView)
            }
        } catch {
            // Show error state if terminal view cannot be attached.
            let label = NSTextField(labelWithString: "Terminal unavailable")
            label.textColor = .secondaryLabelColor // AppKit semantic color, equivalent to DSColor.textSecondary
            label.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(label)
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: container.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            ])
        }

        return container
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        // Re-resolve the container background on every appearance change.
        // `colorScheme` is observed above, so this is called whenever the theme
        // switches. Without this the padding area stays the wrong colour because
        // a plain CGColor captured outside a drawing context is never dynamic.
        NSApp.effectiveAppearance.performAsCurrentDrawingAppearance {
            nsView.layer?.backgroundColor = DSColor.surfaceBaseNS.cgColor
        }
    }

    static func dismantleNSView(_ nsView: NSView, coordinator: ()) {
        // IMPORTANT: Do NOT kill the PTY here.
        // Only detach the view -- the PTY continues running in the background.
        // The TerminalService manages PTY lifecycle independently.
        //
        // Deactivate constraints before removing to prevent AutoLayout from
        // retaining stale references to the old container, which would cause
        // unsatisfiable constraint warnings and potential layout loops when the
        // same terminal view is re-attached to a new container.
        for subview in nsView.subviews {
            NSLayoutConstraint.deactivate(subview.constraints)
            subview.removeFromSuperview()
        }
    }
}
