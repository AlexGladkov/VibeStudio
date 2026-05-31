// MARK: - NgrokTunnelService
// Manages an ngrok tunnel process for remote access outside LAN.
// macOS 14+, Swift 5.10

import Foundation
import Observation
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

    /// Stderr pipe for capturing error output.
    private var stderrPipe: Pipe?

    /// Accumulated stderr output (read asynchronously during process lifetime).
    private var accumulatedStderr: String = ""

    // MARK: - PID File

    /// File used to track the PID of our ngrok subprocess across app launches.
    ///
    /// SEC-H3: stored under the user's Application Support directory rather
    /// than `/tmp`. `/tmp` is world-writable, which exposes the PID file to a
    /// TOCTOU / symlink attack — a local attacker could swap the file
    /// contents between read and `kill(2)`, redirecting our SIGTERM at an
    /// unrelated process. Application Support is per-user (mode `0700` by
    /// default for user-created subdirectories), eliminating that vector.
    ///
    /// Resolved lazily so a missing Application Support directory falls back
    /// to a `nil` URL (the kill / write code already tolerates that — `try?`
    /// on every I/O call).
    private nonisolated static func pidFileURL() -> URL? {
        guard let appSupport = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first else {
            return nil
        }
        let dir = appSupport.appendingPathComponent("VibeStudio", isDirectory: true)
        // Ensure the directory exists. `withIntermediateDirectories: true`
        // makes this idempotent and creates a `0700` directory on the first
        // call (FileManager honours the umask for non-existing parents).
        try? FileManager.default.createDirectory(
            at: dir, withIntermediateDirectories: true
        )
        return dir.appendingPathComponent("ngrok.pid")
    }

    /// ngrok's local introspection API endpoint.
    ///
    /// L13: cached as a `static let` so the force-unwrapped URL literal is only
    /// constructed once per process, not on every poll attempt.
    /// The URL string is hard-coded and known-valid; force-unwrap is safe.
    // swiftlint:disable:next force_unwrapping
    private static let ngrokAPIURL = URL(string: "http://localhost:4040/api/tunnels")!

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
        Task.detached(priority: .utility) {
            // Reads the PID file and sends SIGTERM to that specific process only.
            // This avoids disturbing unrelated ngrok instances running on the system.
            Self.killPreviousOwnedNgrokProcess()

            // Give the killed process a moment to release port 4040 / ngrok's
            // "endpoint already online" lock before we start the new one.
            try? await Task.sleep(for: .milliseconds(500))

            // Continue launch on MainActor so we can mutate observable state.
            await MainActor.run {
                self.launchNgrok(path: ngrokPath, httpPort: httpPort, env: env)
            }
        }
    }

    // MARK: - Stop

    /// Stop the ngrok tunnel and terminate the subprocess.
    ///
    /// Sends SIGTERM first, then SIGKILL after 5 seconds if the process
    /// has not exited, preventing orphaned ngrok processes.
    func stop() {
        pollTask?.cancel()
        pollTask = nil

        if let proc = process, proc.isRunning {
            let pid = proc.processIdentifier
            proc.terminate()

            // Schedule SIGKILL fallback if SIGTERM is ignored.
            Task.detached {
                try? await Task.sleep(for: .seconds(5))
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
        accumulatedStderr = ""

        Logger.remoteControl.info("NgrokTunnelService: stopped")
    }

    // MARK: - Private: Launch

    /// Perform the actual `Process.run()` call and update observable state.
    ///
    /// Must be called on `MainActor`. Separated from `start()` so it can be
    /// dispatched after the background cleanup task completes.
    private func launchNgrok(path: String, httpPort: Int, env: [String: String]) {
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

        // Capture stderr for error diagnostics.
        let errPipe = Pipe()
        proc.standardError = errPipe
        // Suppress stdout (ngrok prints banner to stdout).
        proc.standardOutput = Pipe()

        self.stderrPipe = errPipe
        self.accumulatedStderr = ""

        // Read stderr asynchronously during process lifetime to avoid
        // blocking readDataToEndOfFile in the termination handler.
        errPipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let data = handle.availableData
            guard !data.isEmpty,
                  let text = String(data: data, encoding: .utf8) else { return }
            Task { @MainActor [weak self] in
                self?.accumulatedStderr += text
            }
        }

        // Termination handler — reset state on MainActor.
        proc.terminationHandler = { [weak self] terminatedProcess in
            let exitCode = terminatedProcess.terminationStatus
            // Stop async reading — process is done.
            errPipe.fileHandleForReading.readabilityHandler = nil

            Task { @MainActor [weak self] in
                guard let self, self.process === terminatedProcess else { return }
                self.process = nil
                self.isRunning = false
                self.pollTask?.cancel()
                self.pollTask = nil

                if exitCode != 0 && self.error == nil {
                    let stderrText = self.accumulatedStderr.trimmingCharacters(in: .whitespacesAndNewlines)
                    self.error = stderrText.isEmpty
                        ? "ngrok завершился с кодом \(exitCode)"
                        : "ngrok: \(stderrText)"
                }

                Logger.remoteControl.info(
                    "NgrokTunnelService: process exited code=\(exitCode)"
                )
            }
        }

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

    // MARK: - Private: PID File Management

    /// Write the PID of the ngrok subprocess we own to the Application
    /// Support PID file.
    ///
    /// The file is read on the next `start()` to kill only *our* previous
    /// process, leaving any unrelated ngrok instances untouched.
    private static func writePIDFile(pid: Int32) {
        guard let url = pidFileURL() else {
            Logger.remoteControl.warning(
                "NgrokTunnelService: cannot resolve Application Support — skipping PID file write"
            )
            return
        }
        let content = "\(pid)\n"
        try? content.write(to: url, atomically: true, encoding: .utf8)
        Logger.remoteControl.debug("NgrokTunnelService: wrote PID file pid=\(pid)")
    }

    /// Delete the PID file, called from `stop()` after the process is gone.
    private nonisolated static func removePIDFile() {
        guard let url = pidFileURL() else { return }
        try? FileManager.default.removeItem(at: url)
    }

    /// Read the PID file and send SIGTERM to that specific process if it exists.
    ///
    /// This method is `nonisolated` and `static` so it can be called safely
    /// from a `Task.detached` block without crossing actor boundaries.
    /// `waitUntilExit()` is used here deliberately: this runs on a background
    /// executor, so blocking is acceptable and keeps the cleanup synchronous
    /// before we release the lock.
    private nonisolated static func killPreviousOwnedNgrokProcess() {
        guard let url = pidFileURL(),
              let content = try? String(contentsOf: url, encoding: .utf8),
              let pid = Int32(content.trimmingCharacters(in: .whitespacesAndNewlines)),
              pid > 1 else {
            return
        }

        // Verify the PID actually corresponds to an ngrok process before killing,
        // because PIDs are recycled by the OS and we must not kill a reused PID
        // that now belongs to a different application.
        guard Self.processNameMatches(pid: pid, expectedName: "ngrok") else {
            Logger.remoteControl.debug(
                "NgrokTunnelService: PID \(pid) from pid file is no longer an ngrok process — skipping kill"
            )
            removePIDFile()
            return
        }

        kill(pid, SIGTERM)
        Logger.remoteControl.info("NgrokTunnelService: sent SIGTERM to previous owned ngrok pid=\(pid)")

        // Wait briefly for the process to exit so port 4040 is released.
        // We use `ps` in a subprocess rather than `waitpid` because the process
        // is not our direct child in this launch (it was started in a prior session).
        var waited = 0
        while waited < 10 {
            Thread.sleep(forTimeInterval: 0.1)
            waited += 1
            if kill(pid, 0) != 0 { break } // process gone (errno == ESRCH)
        }

        if kill(pid, 0) == 0 {
            // Process still alive after 1 second — escalate to SIGKILL.
            kill(pid, SIGKILL)
            Logger.remoteControl.warning("NgrokTunnelService: escalated to SIGKILL for previous owned ngrok pid=\(pid)")
        }

        removePIDFile()
    }

    /// Verify that the process with `pid` has `expectedName` as its executable name.
    ///
    /// Uses `/bin/ps` to avoid depending on `/usr/bin/pgrep` flag differences
    /// across macOS versions.
    private nonisolated static func processNameMatches(pid: Int32, expectedName: String) -> Bool {
        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: "/bin/ps")
        proc.arguments = ["-p", "\(pid)", "-o", "comm="]

        let pipe = Pipe()
        proc.standardOutput = pipe
        proc.standardError = Pipe()

        guard (try? proc.run()) != nil else { return false }
        proc.waitUntilExit()

        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        guard let output = String(data: data, encoding: .utf8) else { return false }

        // `comm=` emits the executable base name, e.g. "ngrok"
        let name = output.trimmingCharacters(in: .whitespacesAndNewlines)
        return name == expectedName
    }
}
