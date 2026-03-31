// MARK: - TerminalInputSending Protocol
// Programmatic input to terminal sessions.
// macOS 14+, Swift 5.10

import Foundation

/// Programmatic terminal input capability.
///
/// Allows sending text to a running PTY session as if the user typed it.
@MainActor
protocol TerminalInputSending: AnyObject {

    /// Send text input to a terminal session (as if the user typed it).
    ///
    /// Use this to programmatically send commands to the running shell.
    /// The text is written directly to the PTY's stdin.
    ///
    /// - Parameters:
    ///   - text: The text to send, including any newline for command execution.
    ///   - sessionId: Target session ID.
    func sendInput(_ text: String, to sessionId: UUID)
}
