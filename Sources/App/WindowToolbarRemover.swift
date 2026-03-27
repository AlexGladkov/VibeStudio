// MARK: - WindowToolbarRemover
// Configures the NSWindow titlebar and mounts ToolbarView inside the
// NSTitlebarContainerView — the system-managed strip that always sits
// at the visual top of the window regardless of coordinate system quirks.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftUI

struct WindowToolbarRemover: NSViewRepresentable {

    let container: ServiceContainer

    func makeNSView(context: Context) -> ConfiguringView {
        ConfiguringView(container: container)
    }

    func updateNSView(_ nsView: ConfiguringView, context: Context) {
        nsView.configure()
    }

    // MARK: - ConfiguringView

    final class ConfiguringView: NSView {

        private var didConfigureWindow = false
        private let container: ServiceContainer
        private var toolbarHostingView: NSView?

        init(container: ServiceContainer) {
            self.container = container
            super.init(frame: .zero)
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) not used") }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            configure()
        }

        func configure() {
            guard let window, !didConfigureWindow else { return }
            didConfigureWindow = true

            // ── Window chrome ────────────────────────────────────────────
            window.styleMask.insert(.fullSizeContentView)
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.titlebarSeparatorStyle = .none
            window.backgroundColor = DSColor.surfaceBaseNS

            // ── Trailing toolbar ─────────────────────────────────────────
            // Deferred one runloop so SwiftUI finishes building its toolbar
            // view hierarchy before we traverse it.
            DispatchQueue.main.async { [weak self, weak window] in
                guard let self, let window else { return }
                self.installTrailingToolbar(in: window)
            }
        }

        /// Finds `NSTitlebarContainerView` — the system view that occupies the
        /// visual top strip of the window (titlebar + toolbar row) — and adds
        /// `ToolbarView` as a trailing `NSHostingView` anchored to its center.
        ///
        /// Using `NSTitlebarContainerView` as the anchor parent eliminates all
        /// coordinate-system confusion: its coordinate origin is always at the
        /// top of the window chrome, so `centerYAnchor` and `trailingAnchor`
        /// behave predictably regardless of whether the parent is flipped.
        private func installTrailingToolbar(in window: NSWindow) {
            guard let themeFrame = window.contentView?.superview,
                  let titlebarContainer = titlebarContainerView(in: themeFrame)
            else { return }

            let toolbarView = ToolbarView().injectServices(from: container)
            let hosting = NSHostingView(rootView: toolbarView)
            hosting.translatesAutoresizingMaskIntoConstraints = false
            hosting.wantsLayer = true

            titlebarContainer.addSubview(hosting)
            NSLayoutConstraint.activate([
                hosting.trailingAnchor.constraint(
                    equalTo: titlebarContainer.trailingAnchor,
                    constant: -12
                ),
                hosting.centerYAnchor.constraint(
                    equalTo: titlebarContainer.centerYAnchor
                ),
            ])

            toolbarHostingView = hosting
        }

        /// Walks `NSThemeFrame`'s direct subviews to find the view whose class
        /// name contains "TitlebarContainer". This is the private system view
        /// that wraps the traffic lights + toolbar strip.
        ///
        /// ⚠️ FRAGILE PRIVATE API — depends on Apple's internal AppKit view hierarchy.
        /// Class name search is the only viable approach; no public API exposes this view.
        /// Verified on macOS 14 Sonoma + macOS 15 Sequoia.
        /// If this returns `nil` on a future macOS version, the toolbar items will not
        /// appear (the app remains functional but the toolbar area will be empty).
        /// Monitor: `Logger.ui.error("titlebarContainerView: NSTitlebarContainerView not found")`
        private func titlebarContainerView(in themeFrame: NSView) -> NSView? {
            let result = themeFrame.subviews.first {
                String(describing: type(of: $0)).contains("TitlebarContainer")
            }
            if result == nil {
                Logger.ui.error("WindowToolbarRemover: NSTitlebarContainerView not found — private AppKit API may have changed")
            }
            return result
        }
    }
}
