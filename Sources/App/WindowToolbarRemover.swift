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
        /// Cached leading inset so the resize handler uses the same value as
        /// the initial frame calculation — avoids a second button-conversion call.
        private var cachedTrailingInset: CGFloat = 12
        private var cachedLeadingInset: CGFloat = DSLayout.trafficLightsEndFallback

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
        /// `ToolbarView` as a frame-based subview spanning from `trafficLightsEnd`
        /// to `trailingEdge - 12`.
        ///
        /// # Why frame-based layout instead of NSLayoutConstraint
        ///
        /// `NSHostingView` calculates an intrinsic content size from its SwiftUI
        /// content. When both a `leadingAnchor` and a `trailingAnchor` constraint
        /// are active, Auto Layout creates a **circular dependency**:
        ///
        ///   `hosting.width` drives `titlebarContainer.minWidth`
        ///   → drives `NSThemeFrame.minWidth` → drives `NSWindow.minWidth`
        ///
        /// This overrides `.defaultSize(1600, 1000)` and forces the window open
        /// at the hosting view's intrinsic width (~680 pt) regardless of any
        /// priority adjustments on compression/hugging.
        ///
        /// The frame-based approach breaks the cycle entirely: the hosting view's
        /// frame is computed **from** the container's bounds (read-only) and
        /// written as a concrete rect — no constraint feedback loop.
        /// `NSWindow.didResizeNotification` keeps the frame in sync on resize.
        ///
        /// ⚠️ FRAGILE PRIVATE API — uses `NSTitlebarContainerView` class-name lookup.
        /// Verified on macOS 14 Sonoma + macOS 15 Sequoia.
        private func installTrailingToolbar(in window: NSWindow) {
            guard let themeFrame = window.contentView?.superview,
                  let titlebarContainer = titlebarContainerView(in: themeFrame)
            else { return }

            let toolbarView = ToolbarView().injectServices(from: container)
            let hosting = NSHostingView(rootView: toolbarView)
            // Frame-based layout — must NOT mix with Auto Layout constraints.
            hosting.translatesAutoresizingMaskIntoConstraints = true
            hosting.wantsLayer = true

            // Measure where traffic lights end so we can start the breadcrumb just
            // after. standardWindowButton returns buttons in window-flipped coordinates;
            // convert to the titlebarContainer's own coordinate space.
            if let zoomBtn = window.standardWindowButton(.zoomButton),
               let inContainer = zoomBtn.superview?.convert(zoomBtn.frame, to: titlebarContainer) {
                cachedLeadingInset = inContainer.maxX + 8
            }

            titlebarContainer.addSubview(hosting)
            applyFrame(to: hosting, in: titlebarContainer)

            toolbarHostingView = hosting

            // Track future window resizes so the frame stays accurate.
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(windowDidResize(_:)),
                name: NSWindow.didResizeNotification,
                object: window
            )
        }

        /// Computes and assigns the toolbar hosting view's frame from the current
        /// bounds of `titlebarContainer`.
        ///
        /// Frame coordinates inside `NSTitlebarContainerView` are **not** flipped
        /// (origin at bottom-left). The vertical calculation therefore uses the
        /// container height and centres the hosting view by offsetting by half the
        /// height difference.
        private func applyFrame(to hosting: NSView, in container: NSView) {
            let bounds = container.bounds               // not flipped: origin = bottom-left
            let lead   = cachedLeadingInset
            let trail  = cachedTrailingInset
            let width  = max(0, bounds.width - lead - trail)
            let height = bounds.height
            // Center vertically: y = (containerHeight - hostingHeight) / 2.
            // Since we fill the full height the y-offset is 0.
            hosting.frame = NSRect(x: lead, y: 0, width: width, height: height)
        }

        @objc private func windowDidResize(_ note: Notification) {
            guard let hosting = toolbarHostingView,
                  let container = hosting.superview
            else { return }
            applyFrame(to: hosting, in: container)
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
