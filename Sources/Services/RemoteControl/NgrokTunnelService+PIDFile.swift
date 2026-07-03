// MARK: - NgrokTunnelService+PIDFile
// Iteration 9 split. PID-file management extracted from ``NgrokTunnelService``
// to keep the primary file under SwiftLint's `file_length` budget. All methods
// are `nonisolated static` — they carry no instance state and are safe to call
// from `Task.detached` cleanup blocks. The methods invoked from the main type
// (`writePIDFile`, `removePIDFile`, `killPreviousOwnedNgrokProcess`) are
// `internal`; the file-local helpers stay `private`.
//
// macOS 14+, Swift 5.10

import Foundation
import OSLog

extension NgrokTunnelService {

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

    /// Write the PID of the ngrok subprocess we own to the Application
    /// Support PID file.
    ///
    /// The file is read on the next `start()` to kill only *our* previous
    /// process, leaving any unrelated ngrok instances untouched.
    static func writePIDFile(pid: Int32) {
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
    nonisolated static func removePIDFile() {
        guard let url = pidFileURL() else { return }
        do {
            try FileManager.default.removeItem(at: url)
        } catch {
            // Don't escalate — the only sane recovery is "try again next
            // shutdown". But the silent swallow hid genuine permission /
            // path-corruption issues during debugging.
            Logger.remoteControl.warning(
                "Failed to remove ngrok PID file: \(error.localizedDescription, privacy: .public)"
            )
        }
    }

    /// Read the PID file and send SIGTERM to that specific process if it exists.
    ///
    /// This method is `nonisolated` and `static` so it can be called safely
    /// from a `Task.detached` block without crossing actor boundaries. It is
    /// `async` and suspends via `Task.sleep` between liveness probes rather
    /// than blocking with `Thread.sleep`: the caller runs on a cooperative
    /// Swift Concurrency executor, so a blocking sleep would stall a shared
    /// pool thread. `try?` is used on the sleep so cancellation simply ends
    /// the wait early (the SIGKILL escalation below still runs).
    nonisolated static func killPreviousOwnedNgrokProcess() async {
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
            try? await Task.sleep(for: .milliseconds(100))
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
