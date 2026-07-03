// MARK: - FileSystemWatcher
// FSEventStream wrapper with AsyncStream delivery and debouncing.
// macOS 14+, Swift 5.10

import Foundation
import CoreServices

/// File system change observer using FSEventStream.
///
/// Wraps CoreServices FSEventStream into an ``AsyncStream<FileChangeEvent>``.
/// Events are debounced (300ms default) and filtered against ignored patterns
/// (node_modules, .git/objects, .build, DerivedData, etc.).
///
/// Thread safety: internal state is protected by a dedicated DispatchQueue.
/// All public methods can be called from any thread.
final class FileSystemWatcher: FileSystemWatching, @unchecked Sendable {

    // MARK: - Types

    private struct WatchEntry {
        let info: WatchInfo
        var streamRef: FSEventStreamRef?
    }

    /// Context object passed through FSEventStreamContext.info pointer.
    /// Holds a weak reference to the watcher and the per-watch ignored patterns
    /// so the C callback can filter events without accessing `self` directly.
    private final class CallbackContext {
        weak var watcher: FileSystemWatcher?
        let ignoredPatterns: [String]

        init(watcher: FileSystemWatcher, ignoredPatterns: [String]) {
            self.watcher = watcher
            self.ignoredPatterns = ignoredPatterns
        }
    }

    // MARK: - State

    private let queue = DispatchQueue(label: "tech.mobiledeveloper.vibestudio.fswatcher", qos: .utility)
    private var watches: [WatchToken: WatchEntry] = [:]
    private let continuation: AsyncStream<FileChangeEvent>.Continuation
    private let _events: AsyncStream<FileChangeEvent>

    /// Directories that are always excluded from watch events.
    private static let systemExclusions: Set<String> = PathConstants.excludedDirectoryNames

    /// Paths that must not be watched (safety check).
    private static let forbiddenRoots: Set<String> = PathConstants.forbiddenRootPaths

    // MARK: - Init

    init() {
        let (stream, continuation) = AsyncStream<FileChangeEvent>.makeStream()
        _events = stream
        self.continuation = continuation
    }

    deinit {
        // Must stop all FSEventStreams synchronously before deallocation.
        // unwatchAll() uses async barrier which would execute after deinit completes,
        // leaving streams running. Instead, perform cleanup inline with sync barrier.
        queue.sync(flags: .barrier) {
            for (_, entry) in self.watches {
                guard let stream = entry.streamRef else { continue }
                FSEventStreamStop(stream)
                FSEventStreamInvalidate(stream)
                FSEventStreamRelease(stream)
            }
            self.watches.removeAll()
        }
        continuation.finish()
    }

    // MARK: - FileSystemWatching

    var events: AsyncStream<FileChangeEvent> { _events }

    var activeWatches: [WatchInfo] {
        queue.sync {
            watches.values.map(\.info)
        }
    }

    @discardableResult
    func watch(directory: URL, options: WatchOptions) throws -> WatchToken {
        let path = directory.path

        // Validate: must not be a system directory.
        guard !Self.forbiddenRoots.contains(path) else {
            throw FileSystemWatcherError.pathNotFound(directory)
        }

        // Validate: must exist and be a directory.
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: path, isDirectory: &isDir),
              isDir.boolValue else {
            throw FileSystemWatcherError.pathNotFound(directory)
        }

        // Create FSEventStream outside the lock (C API call).
        let streamRef = createStream(
            for: path,
            debounce: options.debounceInterval,
            ignoredPatterns: options.ignoredPatterns
        )

        guard let streamRef else {
            throw FileSystemWatcherError.streamCreationFailed(path: directory)
        }

        // Atomic check-and-insert + stream start: all inside a single barrier block
        // so a concurrent unwatch() barrier cannot interleave between dictionary
        // mutation and FSEventStreamStart, which would call Stop on an unstarted stream.
        let token = try queue.sync(flags: .barrier) { () throws -> WatchToken in
            let isDuplicate = watches.values.contains { $0.info.directory == directory }
            if isDuplicate {
                // Clean up the stream we already created.
                FSEventStreamInvalidate(streamRef)
                FSEventStreamRelease(streamRef)
                throw FileSystemWatcherError.alreadyWatching(path: directory)
            }

            let token = WatchToken()
            let info = WatchInfo(
                token: token,
                directory: directory,
                options: options,
                startedAt: .now
            )
            watches[token] = WatchEntry(info: info, streamRef: streamRef)

            // Start the stream inside the barrier so that any concurrent unwatch()
            // barriers that follow will see an already-started (or already-stopped)
            // stream, never an in-between partially-configured one.
            FSEventStreamSetDispatchQueue(streamRef, queue)
            FSEventStreamStart(streamRef)

            return token
        }

        return token
    }

    func unwatch(_ token: WatchToken) {
        // R-06: Use async barrier instead of sync to prevent deadlock when
        // called from our own queue (e.g. deinit triggered on queue thread).
        queue.async(flags: .barrier) { [weak self] in
            guard let self else { return }
            guard let entry = self.watches.removeValue(forKey: token),
                  let stream = entry.streamRef else { return }
            FSEventStreamStop(stream)
            FSEventStreamInvalidate(stream)
            FSEventStreamRelease(stream)
        }
    }

    func unwatchAll() {
        // R-06: Use async barrier instead of sync to prevent deadlock when
        // called from our own queue (e.g. deinit triggered on queue thread).
        queue.async(flags: .barrier) { [weak self] in
            guard let self else { return }
            for (_, entry) in self.watches {
                guard let stream = entry.streamRef else { continue }
                FSEventStreamStop(stream)
                FSEventStreamInvalidate(stream)
                FSEventStreamRelease(stream)
            }
            self.watches.removeAll()
            // Finish the AsyncStream so consumers (startFileEventForwarding Task)
            // terminate cleanly when called during app shutdown. Safe to call
            // multiple times — AsyncStream.Continuation.finish() is idempotent.
            self.continuation.finish()
        }
    }

    // MARK: - Private: Stream Creation

    private func createStream(
        for path: String,
        debounce: TimeInterval,
        ignoredPatterns: [String]
    ) -> FSEventStreamRef? {
        let pathsToWatch = [path] as CFArray
        var context = makeStreamContext(ignoredPatterns: ignoredPatterns)

        let flags: FSEventStreamCreateFlags = UInt32(
            kFSEventStreamCreateFlagUseCFTypes |
            kFSEventStreamCreateFlagFileEvents |
            kFSEventStreamCreateFlagNoDefer
        )

        return FSEventStreamCreate(
            nil, { _, info, numEvents, eventPaths, eventFlags, _ in
                guard let info else { return }
                let ctx = Unmanaged<CallbackContext>.fromOpaque(info).takeUnretainedValue()
                FileSystemWatcher.handleFSEvents(
                    ctx: ctx,
                    numEvents: numEvents,
                    eventPaths: eventPaths,
                    eventFlags: eventFlags
                )
            },
            &context,
            pathsToWatch,
            FSEventStreamEventId(kFSEventStreamEventIdSinceNow),
            debounce,
            flags
        )
    }

    /// Build the `FSEventStreamContext` whose `info` retains a `CallbackContext`.
    ///
    /// R-05: `passRetained` prevents use-after-free. R-22: `CallbackContext`
    /// carries `ignoredPatterns` for per-watch filtering. The retain cycle
    /// (self <- stream <- context -> CallbackContext.watcher -> self) is broken
    /// because `CallbackContext` holds a weak ref to `watcher`, and the context
    /// release callback balances the `passRetained` call.
    private func makeStreamContext(ignoredPatterns: [String]) -> FSEventStreamContext {
        let callbackCtx = CallbackContext(watcher: self, ignoredPatterns: ignoredPatterns)
        return FSEventStreamContext(
            version: 0,
            info: Unmanaged.passRetained(callbackCtx).toOpaque(),
            retain: nil,
            release: { info in
                guard let info else { return }
                Unmanaged<CallbackContext>.fromOpaque(info).release()
            },
            copyDescription: nil
        )
    }

    /// Decode raw FSEvents, filter them, and forward `FileChangeEvent`s.
    ///
    /// Runs on the FSEvents callback thread; keeps no captured state (all inputs
    /// arrive via `ctx` and the C parameters).
    private static func handleFSEvents(
        ctx: CallbackContext,
        numEvents: Int,
        eventPaths: UnsafeMutableRawPointer,
        eventFlags: UnsafePointer<FSEventStreamEventFlags>
    ) {
        guard let watcher = ctx.watcher else { return }
        guard let paths = unsafeBitCast(eventPaths, to: NSArray.self) as? [String] else { return }
        let flags = Array(UnsafeBufferPointer(start: eventFlags, count: numEvents))

        for i in 0..<numEvents {
            let eventPath = paths[i]
            if shouldSkipEvent(path: eventPath, ignoredPatterns: ctx.ignoredPatterns) { continue }

            let event = FileChangeEvent(
                path: URL(fileURLWithPath: eventPath),
                kind: fileChangeKind(for: flags[i]),
                timestamp: .now
            )
            watcher.continuation.yield(event)
        }
    }

    /// Whether an event path is filtered out by system exclusions or per-watch patterns.
    private static func shouldSkipEvent(path eventPath: String, ignoredPatterns: [String]) -> Bool {
        // Filter excluded directories (system-wide).
        let pathComponents = eventPath.components(separatedBy: "/")
        if pathComponents.contains(where: { systemExclusions.contains($0) }) { return true }
        // R-22: Filter per-watch ignored patterns.
        return ignoredPatterns.contains { eventPath.contains($0) }
    }

    /// Map an FSEvent flag bitmask to a ``FileChangeKind`` (created > deleted > renamed > modified).
    private static func fileChangeKind(for flag: FSEventStreamEventFlags) -> FileChangeKind {
        if flag & UInt32(kFSEventStreamEventFlagItemCreated) != 0 { return .created }
        if flag & UInt32(kFSEventStreamEventFlagItemRemoved) != 0 { return .deleted }
        if flag & UInt32(kFSEventStreamEventFlagItemRenamed) != 0 { return .renamed }
        return .modified
    }
}
