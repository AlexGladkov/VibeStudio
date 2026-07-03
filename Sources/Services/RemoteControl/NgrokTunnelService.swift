// MARK: - NgrokTunnelService
// Manages an ngrok tunnel process for remote access outside LAN.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import os
import OSLog

/// Manages an ngrok tunnel subprocess to expose the local Remote Control
/// HTTP server to the internet.
///
/// **Lifecycle:** Owned by ``RemoteControlServer``. Started after the HTTP
/// server binds (when `ngrokEnabled` preference is `true`). Stopped when
/// the server shuts down.
///
/// **Process management:**
/// - Resolves the `ngrok` binary via ``CLIAgentPathResolver`` (trusted dirs only).
/// - Launches `ngrok http <port>` as a child process with an allowlisted environment.
/// - Polls the ngrok local API (`localhost:4040/api/tunnels`) to obtain the public URL.
/// - Terminates the process on `stop()` or when the tunnel exits unexpectedly.
/// - Tracks the PID of our own ngrok process in `/tmp/vibestudio-ngrok.pid` to
///   avoid killing unrelated ngrok instances owned by other applications.
@Observable
@MainActor
final class NgrokTunnelService {

    // MARK: - Observable State

    /// Whether the ngrok process is currently running.
    private(set) var isRunning: Bool = false

    /// The public tunnel URL (e.g. `https://xxxx.ngrok-free.app`).
    /// `nil` while starting or when stopped.
    private(set) var tunnelURL: String?

    /// Human-readable error message if the tunnel failed to start.
    private(set) var error: String?

    // MARK: - Private State

    /// The ngrok child process.
    private var process: Process?

    /// Task that polls the ngrok API for the tunnel URL.
    private var pollTask: Task<Void, Never>?

    /// Background task that kills any stale ngrok process and then launches
    /// the new one. Stored so `stop()` can cancel it — otherwise a `stop()`
    /// during the 500 ms cleanup window would be overridden by a launch that
    /// fires afterwards, re-starting ngrok after an explicit stop.
    private var cleanupTask: Task<Void, Never>?

    /// Background task that escalates SIGTERM to SIGKILL after a timeout.
    /// Stored so rapid stop/start cycles don't accumulate detached tasks each
    /// holding a `Process` reference alive for 5 seconds.
    private var sigkillTask: Task<Void, Never>?

    /// Stderr pipe for capturing error output.
    private var stderrPipe: Pipe?

    /// Accumulated stderr output.
    ///
    /// Held under an `OSAllocatedUnfairLock` (a Sendable `let`, so it is
    /// reachable from the nonisolated pipe/termination callbacks) rather than a
    /// MainActor `var` appended via `Task { @MainActor }`. The previous approach
    /// hopped each chunk onto the MainActor asynchronously, and ordering between
    /// those hops and the termination handler's own hop was not guaranteed — so
    /// ngrok's final diagnostic line could land in `error` too late (or never).
    /// Appending under a lock, plus a synchronous drain in the termination
    /// handler, makes the accumulation order-independent of thread scheduling.
    private let stderrBuffer = OSAllocatedUnfairLock(initialState: "")

    // PID-file management lives in ``NgrokTunnelService+PIDFile`` (Iteration 9 split).

    /// ngrok's local introspection API endpoint.
    ///
    /// L13: cached as a `static let` so the force-unwrapped URL literal is only
    /// constructed once per process, not on every poll attempt.
    /// The URL string is hard-coded and known-valid; force-unwrap is safe.
    private static let ngrokAPIURL = URL(string: "http://localhost:4040/api/tunnels")! // swiftlint:disable:this force_unwrapping

    // MARK: - Start

    /// Start the ngrok tunnel for the given HTTP port.
    ///
    /// - Parameters:
    ///   - httpPort: The local plain-HTTP port to tunnel (loopback only,
    ///     bound by ``RemoteControlServer`` to `127.0.0.1`). We deliberately
    ///     tunnel the plain-HTTP loopback listener (rather than the self-signed
    ///     HTTPS listener) so the ngrok agent → upstream leg of the connection
    ///     does not need to ignore certificate verification. The plain-HTTP
    ///     port is never reachable from the LAN — see ``RemoteControlServer``.
    ///   - authtoken: ngrok authtoken (required since v3). Empty = use system config.
    func start(httpPort: Int, authtoken: String = "") {
        guard !isRunning else { return }

        error = nil
        tunnelURL = nil

        // Resolve ngrok binary from trusted directories before going async,
        // so we can surface the error synchronously on the current call frame.
        guard let ngrokPath = CLIAgentPathResolver.resolve("ngrok") else {
            error = "ngrok не найден. Установите: brew install ngrok"
            Logger.remoteControl.warning("NgrokTunnelService: ngrok binary not found in trusted directories")
            return
        }

        // Build minimal environment. Pass authtoken via env var (not CLI args)
        // to prevent it from being visible in `ps aux` process listings.
        var env = Self.buildEnvironment()
        if !authtoken.isEmpty {
            env["NGROK_AUTHTOKEN"] = authtoken
        }

        // Kill any stale ngrok process from a previous VibeStudio session
        // off the MainActor so `waitUntilExit()` never blocks the UI thread.
        // We then launch ngrok proper once cleanup has had a moment to settle.
        cleanupTask?.cancel()
        cleanupTask = Task.detached(priority: .utility) { [weak self] in
            // Reads the PID file and sends SIGTERM to that specific process only.
            // This avoids disturbing unrelated ngrok instances running on the system.
            await Self.killPreviousOwnedNgrokProcess()

            // Give the killed process a moment to release port 4040 / ngrok's
            // "endpoint already online" lock before we start the new one.
            try? await Task.sleep(for: .milliseconds(500))

            // Bail out if stop() was called during the cleanup window.
            guard !Task.isCancelled else { return }

            // Continue launch on MainActor so we can mutate observable state.
            await MainActor.run {
                self?.launchNgrok(path: ngrokPath, httpPort: httpPort, env: env)
            }
        }
    }

    // MARK: - Stop

    /// Stop the ngrok tunnel and terminate the subprocess.
    ///
    /// Sends SIGTERM first, then SIGKILL after 5 seconds if the process
    /// has not exited, preventing orphaned ngrok processes.
    func stop() {
        // Cancel any in-flight launch so a stop() during the cleanup window
        // doesn't get overridden by a delayed relaunch.
        cleanupTask?.cancel()
        cleanupTask = nil

        pollTask?.cancel()
        pollTask = nil

        if let proc = process, proc.isRunning {
            let pid = proc.processIdentifier
            proc.terminate()

            // Schedule SIGKILL fallback if SIGTERM is ignored.
            sigkillTask?.cancel()
            sigkillTask = Task.detached { [proc, pid] in
                try? await Task.sleep(for: .seconds(5))
                guard !Task.isCancelled else { return }
                if proc.isRunning {
                    kill(pid, SIGKILL)
                    Logger.remoteControl.warning(
                        "NgrokTunnelService: sent SIGKILL to ngrok (pid=\(pid))"
                    )
                }
            }
        }

        // Remove the PID file so a subsequent start() does not attempt to kill
        // a PID that no longer belongs to us.
        Self.removePIDFile()

        stderrPipe?.fileHandleForReading.readabilityHandler = nil
        process = nil
        isRunning = false
        tunnelURL = nil
        error = nil
        stderrPipe = nil
        stderrBuffer.withLock { $0 = "" }

        Logger.remoteControl.info("NgrokTunnelService: stopped")
    }

    // MARK: - Private: Launch

    /// Perform the actual `Process.run()` call and update observable state.
    ///
    /// Must be called on `MainActor`. Separated from `start()` so it can be
    /// dispatched after the background cleanup task completes.
    private func launchNgrok(path: String, httpPort: Int, env: [String: String]) {
        // Capture stderr for error diagnostics.
        let errPipe = Pipe()
        let proc = Self.makeNgrokProcess(path: path, httpPort: httpPort, env: env, errPipe: errPipe)

        self.stderrPipe = errPipe
        self.stderrBuffer.withLock { $0 = "" }

        installStderrReader(errPipe: errPipe)
        installTerminationHandler(on: proc, errPipe: errPipe)

        do {
            try proc.run()
        } catch {
            self.error = "Не удалось запустить ngrok: \(error.localizedDescription)"
            Logger.remoteControl.error(
                "NgrokTunnelService: failed to launch: \(error.localizedDescription, privacy: .public)"
            )
            return
        }

        // Persist our PID so the next start() can kill precisely this process.
        Self.writePIDFile(pid: proc.processIdentifier)

        self.process = proc
        self.isRunning = true

        Logger.remoteControl.info(
            "NgrokTunnelService: started, tunneling port \(httpPort)"
        )

        // Poll the ngrok API for the public tunnel URL.
        startPollingForURL()
    }

    /// Build the ngrok child `Process` with args, environment and pipes set.
    private static func makeNgrokProcess(
        path: String,
        httpPort: Int,
        env: [String: String],
        errPipe: Pipe
    ) -> Process {
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: path)
        // SECURITY (H1): tunnel the loopback plain-HTTP listener instead of the
        // self-signed HTTPS listener. Using `http://localhost:<port>` here
        // removes the need for `--verify-upstream-tls=false`, which previously
        // silently disabled TLS chain verification on the agent → upstream leg.
        // The plain-HTTP port is bound to `127.0.0.1` only by
        // ``RemoteControlServer`` (see start() in that file), so it remains
        // unreachable from the LAN — only the local ngrok agent talks to it.
        proc.arguments = ["http", "http://localhost:\(httpPort)"]
        proc.environment = env
        proc.standardError = errPipe
        // Suppress stdout (ngrok prints banner to stdout).
        proc.standardOutput = Pipe()
        return proc
    }

    /// Read stderr asynchronously during process lifetime to avoid
    /// blocking readDataToEndOfFile in the termination handler.
    private func installStderrReader(errPipe: Pipe) {
        errPipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let data = handle.availableData
            guard !data.isEmpty,
                  let text = String(data: data, encoding: .utf8) else { return }
            // Append synchronously under the lock — no MainActor hop — so the
            // accumulation order matches the delivery order regardless of thread.
            self?.stderrBuffer.withLock { $0 += text }
        }
    }

    /// Install the termination handler that resets observable state on the
    /// MainActor when the ngrok process exits.
    private func installTerminationHandler(on proc: Process, errPipe: Pipe) {
        proc.terminationHandler = { [weak self] terminatedProcess in
            let exitCode = terminatedProcess.terminationStatus
            // Stop async reading — process is done.
            errPipe.fileHandleForReading.readabilityHandler = nil

            // Drain any bytes still buffered in the pipe synchronously on this
            // termination thread. `readabilityHandler` is detached above and may
            // have been detached before ngrok's final diagnostic line was
            // delivered; without this drain that last chunk would be lost.
            let tail = errPipe.fileHandleForReading.readDataToEndOfFile()
            if !tail.isEmpty, let tailText = String(data: tail, encoding: .utf8) {
                self?.stderrBuffer.withLock { $0 += tailText }
            }
            // Snapshot the fully-accumulated stderr synchronously so the value
            // carried into the MainActor hop below is complete and final.
            let collectedStderr = self?.stderrBuffer.withLock { $0 } ?? ""

            Task { @MainActor [weak self] in
                guard let self, self.process === terminatedProcess else { return }
                self.process = nil
                self.isRunning = false
                self.pollTask?.cancel()
                self.pollTask = nil

                if exitCode != 0 && self.error == nil {
                    let stderrText = collectedStderr.trimmingCharacters(in: .whitespacesAndNewlines)
                    self.error = stderrText.isEmpty
                        ? "ngrok завершился с кодом \(exitCode)"
                        : "ngrok: \(stderrText)"
                }

                Logger.remoteControl.info(
                    "NgrokTunnelService: process exited code=\(exitCode)"
                )
            }
        }
    }

    // MARK: - Private: URL Polling

    /// Poll `http://localhost:4040/api/tunnels` until a public URL is found.
    ///
    /// ngrok starts its local inspection API on port 4040. We query it every
    /// 2 seconds for up to 30 seconds.
    private func startPollingForURL() {
        pollTask = Task { @MainActor [weak self] in
            let maxAttempts = 15
            let url = Self.ngrokAPIURL

            for attempt in 1...maxAttempts {
                guard !Task.isCancelled, let self, self.isRunning else { return }

                // Wait before polling (ngrok needs time to initialize).
                try? await Task.sleep(for: .seconds(2))
                guard !Task.isCancelled else { return }

                do {
                    let (data, _) = try await URLSession.shared.data(from: url)
                    if let publicURL = Self.parsePublicURL(from: data) {
                        self.tunnelURL = publicURL
                        Logger.remoteControl.info(
                            "NgrokTunnelService: tunnel URL = \(publicURL, privacy: .public)"
                        )
                        return
                    }
                } catch {
                    // ngrok API not ready yet — retry.
                    Logger.remoteControl.debug(
                        "NgrokTunnelService: poll attempt \(attempt)/\(maxAttempts) failed: \(error.localizedDescription)"
                    )
                }
            }

            // Exhausted all attempts.
            guard let self, self.isRunning, self.tunnelURL == nil else { return }
            self.error = "Не удалось получить URL туннеля"
            Logger.remoteControl.warning("NgrokTunnelService: failed to obtain tunnel URL after \(maxAttempts) attempts")
        }
    }

    // MARK: - Private: JSON Parsing

    /// Parse the public URL from ngrok's `/api/tunnels` JSON response.
    ///
    /// Expected structure:
    /// ```json
    /// { "tunnels": [{ "public_url": "https://xxxx.ngrok-free.app", ... }] }
    /// ```
    private static func parsePublicURL(from data: Data) -> String? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tunnels = json["tunnels"] as? [[String: Any]],
              let first = tunnels.first,
              let publicURL = first["public_url"] as? String else {
            return nil
        }
        return publicURL
    }

    // MARK: - Private: Environment

    /// Build a minimal environment for the ngrok subprocess.
    ///
    /// Inherits only safe variables from the parent process (HOME, PATH, etc.)
    /// to prevent accidental secret leakage.
    private static func buildEnvironment() -> [String: String] {
        let allowed: Set<String> = [
            "HOME", "USER", "LOGNAME",
            "LANG", "LC_ALL", "LC_CTYPE",
            "TERM", "PATH", "TMPDIR",
            "NGROK_AUTHTOKEN"
        ]

        let processEnv = ProcessInfo.processInfo.environment
        var result: [String: String] = [:]

        for key in allowed {
            if let value = processEnv[key] {
                result[key] = value
            }
        }

        // Ensure PATH includes trusted directories for ngrok to find its dependencies.
        let trustedBins = SecurityConstants.trustedBinDirectories
        let currentPath = result["PATH"] ?? "/usr/bin:/bin:/usr/sbin:/sbin"
        let existingParts = currentPath.split(separator: ":").map(String.init)
        let missingBins = trustedBins.filter { !existingParts.contains($0) }
        if !missingBins.isEmpty {
            result["PATH"] = (missingBins + existingParts).joined(separator: ":")
        }

        return result
    }
}
