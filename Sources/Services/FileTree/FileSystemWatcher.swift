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

    private let queue = DispatchQueue(label: "com.vibestudio.fswatcher", qos: .utility)
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

        // Atomic check-and-insert: duplicate check + dictionary write
        // in a single barrier block to prevent TOCTOU race.
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
            return token
        }

        FSEventStreamSetDispatchQueue(streamRef, queue)
        FSEventStreamStart(streamRef)

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
        }
    }

    // MARK: - Private: Stream Creation

    private func createStream(
        for path: String,
        debounce: TimeInterval,
        ignoredPatterns: [String]
    ) -> FSEventStreamRef? {
        let pathsToWatch = [path] as CFArray

        // R-05: Use passRetained via CallbackContext to prevent use-after-free.
        // R-22: CallbackContext carries ignoredPatterns for per-watch filtering.
        // The retain cycle (self <- stream <- context -> CallbackContext.watcher -> self)
        // is broken because CallbackContext holds a weak ref to watcher, and the
        // context release callback balances the passRetained call.
        let callbackCtx = CallbackContext(watcher: self, ignoredPatterns: ignoredPatterns)
        var context = FSEventStreamContext(
            version: 0,
            info: Unmanaged.passRetained(callbackCtx).toOpaque(),
            retain: nil,
            release: { info in
                guard let info else { return }
                Unmanaged<CallbackContext>.fromOpaque(info).release()
            },
            copyDescription: nil
        )

        let flags: FSEventStreamCreateFlags = UInt32(
            kFSEventStreamCreateFlagUseCFTypes |
            kFSEventStreamCreateFlagFileEvents |
            kFSEventStreamCreateFlagNoDefer
        )

        return FSEventStreamCreate(
            nil,
            { _, info, numEvents, eventPaths, eventFlags, _ in
                guard let info else { return }
                let ctx = Unmanaged<CallbackContext>.fromOpaque(info).takeUnretainedValue()
                guard let watcher = ctx.watcher else { return }
                guard let paths = unsafeBitCast(eventPaths, to: NSArray.self) as? [String] else { return }

                let flags = Array(UnsafeBufferPointer(start: eventFlags, count: numEvents))

                for i in 0..<numEvents {
                    let eventPath = paths[i]
                    let eventFlag = flags[i]

                    // Filter excluded directories (system-wide).
                    let pathComponents = eventPath.components(separatedBy: "/")
                    let shouldExclude = pathComponents.contains { component in
                        FileSystemWatcher.systemExclusions.contains(component)
                    }
                    if shouldExclude { continue }

                    // R-22: Filter per-watch ignored patterns.
                    let shouldIgnore = ctx.ignoredPatterns.contains { pattern in
                        eventPath.contains(pattern)
                    }
                    if shouldIgnore { continue }

                    let url = URL(fileURLWithPath: eventPath)
                    let kind: FileChangeKind

                    if eventFlag & UInt32(kFSEventStreamEventFlagItemCreated) != 0 {
                        kind = .created
                    } else if eventFlag & UInt32(kFSEventStreamEventFlagItemRemoved) != 0 {
                        kind = .deleted
                    } else if eventFlag & UInt32(kFSEventStreamEventFlagItemRenamed) != 0 {
                        kind = .renamed
                    } else {
                        kind = .modified
                    }

                    let event = FileChangeEvent(
                        path: url,
                        kind: kind,
                        timestamp: .now
                    )
                    watcher.continuation.yield(event)
                }
            },
            &context,
            pathsToWatch,
            FSEventStreamEventId(kFSEventStreamEventIdSinceNow),
            debounce,
            flags
        )
    }
}
