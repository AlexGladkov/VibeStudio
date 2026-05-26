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

    // MARK: - Start

    /// Start the ngrok tunnel for the given HTTP port.
    ///
    /// - Parameters:
    ///   - httpPort: The local HTTP port to tunnel (e.g. 7843).
    ///   - authtoken: ngrok authtoken (required since v3). Empty = use system config.
    func start(httpPort: Int, authtoken: String = "") {
        guard !isRunning else { return }

        error = nil
        tunnelURL = nil

        // Resolve ngrok binary from trusted directories.
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

        let proc = Process()
        proc.executableURL = URL(fileURLWithPath: ngrokPath)

        proc.arguments = ["http", "\(httpPort)"]
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

        self.process = proc
        self.isRunning = true

        Logger.remoteControl.info(
            "NgrokTunnelService: started, tunneling port \(httpPort)"
        )

        // Poll the ngrok API for the public tunnel URL.
        startPollingForURL()
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
        stderrPipe?.fileHandleForReading.readabilityHandler = nil
        process = nil
        isRunning = false
        tunnelURL = nil
        error = nil
        stderrPipe = nil
        accumulatedStderr = ""

        Logger.remoteControl.info("NgrokTunnelService: stopped")
    }

    // MARK: - Private: URL Polling

    /// Poll `http://localhost:4040/api/tunnels` until a public URL is found.
    ///
    /// ngrok starts its local inspection API on port 4040. We query it every
    /// 2 seconds for up to 30 seconds.
    private func startPollingForURL() {
        pollTask = Task { @MainActor [weak self] in
            let maxAttempts = 15
            let url = URL(string: "http://localhost:4040/api/tunnels")!

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
            "NGROK_AUTHTOKEN",
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
