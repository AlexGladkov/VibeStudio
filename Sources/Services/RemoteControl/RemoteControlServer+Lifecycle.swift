// MARK: - RemoteControlServer+Lifecycle
// Iteration 9 split. Server teardown extracted from ``RemoteControlServer`` to
// keep the primary type body under SwiftLint's `type_body_length` budget. The
// state-mutating commit (`finishStartup`) and the observable-state teardown
// snapshot (`beginStopCleanup`) stay on the main type so its `private(set)`
// observable properties keep their tight setter visibility; only the detached
// NIO shutdown plumbing lives here.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOPosix
import OSLog

extension RemoteControlServer {

    /// Gracefully stop the server and disconnect all devices (fire-and-forget).
    func stop() {
        guard isRunning, !isTransitioning else { return }

        isTransitioning = true
        let handles = beginStopCleanup()
        weak var weakSelf = self

        Task.detached {
            await Self.shutdownNIO(handles)
            Logger.remoteControl.info("RemoteControlServer stopped")
            await MainActor.run {
                weakSelf?.isTransitioning = false
            }
        }
    }

    /// Gracefully stop the server and await full NIO shutdown.
    func stopAsync() async {
        guard isRunning, !isTransitioning else { return }

        isTransitioning = true
        let handles = beginStopCleanup()

        await Self.shutdownNIO(handles)
        Logger.remoteControl.info("RemoteControlServer stopped (async)")
        isTransitioning = false
    }

    /// NIO resources detached from the server during teardown, awaited by
    /// ``shutdownNIO(_:)`` off the MainActor.
    struct NIOTeardownHandles {
        let serverChannel: Channel?
        let httpChannel: Channel?
        let group: MultiThreadedEventLoopGroup?
    }

    /// Await closure of NIO channels and event loop group shutdown.
    static func shutdownNIO(_ handles: NIOTeardownHandles) async {
        if let channel = handles.serverChannel {
            try? await channel.close().get()
        }
        if let httpChannel = handles.httpChannel {
            try? await httpChannel.close().get()
        }
        if let group = handles.group {
            try? await group.shutdownGracefully()
        }
    }
}
