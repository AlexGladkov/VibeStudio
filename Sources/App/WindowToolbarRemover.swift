// MARK: - WindowToolbarRemover
// Configures the NSWindow titlebar so our custom ToolbarView content
// sits flush with the traffic lights row.
// macOS 14+, Swift 5.10

import AppKit
import SwiftUI

/// Zero-size NSViewRepresentable that removes NSToolbar reserved space
/// and configures the window titlebar for full-bleed content layout.
///
/// Insert via `.background(WindowToolbarRemover())` on the root view.
struct WindowToolbarRemover: NSViewRepresentable {

    func makeNSView(context: Context) -> ConfiguringView {
        ConfiguringView()
    }

    func updateNSView(_ nsView: ConfiguringView, context: Context) {
        nsView.configure()
    }

    final class ConfiguringView: NSView {
        private var didConfigureWindow = false

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            configure()
        }

        func configure() {
            guard let window else { return }

            // One-time window chrome setup.
            if !didConfigureWindow {
                didConfigureWindow = true

                // Full-size content view so SwiftUI fills behind the toolbar.
                window.styleMask.insert(.fullSizeContentView)
                window.titlebarAppearsTransparent = true
                window.titleVisibility = .hidden

                // Remove the separator line between toolbar and content.
                window.titlebarSeparatorStyle = .none

                // Paint the window background to match surfaceBase (#1A1B1E)
                // so the transparent toolbar inherits the correct dark color.
                window.backgroundColor = DSColor.surfaceBaseNS
            }

            // Re-apply toolbar layout on every update so items added later
            // (e.g. when transitioning from Welcome → project open) also get
            // the correct position and no bordered-capsule background.
            fixToolbarItems(in: window)
        }

        /// Ensures flexible-space sits at index 0 (pushing items to trailing
        /// edge) and strips the macOS bordered-capsule style from every item.
        /// Deferred one runloop so SwiftUI finishes registering its items first.
        private func fixToolbarItems(in window: NSWindow) {
            DispatchQueue.main.async { [weak window] in
                guard let toolbar = window?.toolbar else { return }

                // Guarantee flexible space is at position 0.
                // If it drifted (SwiftUI inserted items before it), move it back.
                if let existingIdx = toolbar.items.firstIndex(where: {
                    $0.itemIdentifier == .flexibleSpace
                }) {
                    if existingIdx != 0 {
                        toolbar.removeItem(at: existingIdx)
                        toolbar.insertItem(withItemIdentifier: .flexibleSpace, at: 0)
                    }
                } else {
                    toolbar.insertItem(withItemIdentifier: .flexibleSpace, at: 0)
                }

                // Strip bordered-capsule background from all items.
                for item in toolbar.items {
                    item.isBordered = false
                }
            }
        }
    }
}
