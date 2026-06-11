// MARK: - ClaudePermissionsResolving
// Small contract that owns the decision to launch Claude with
// `--dangerously-skip-permissions`. Isolates that policy from
// transport-layer code (HTTPRequestRouter), so the same decision
// can be reused by TerminalService and remote endpoints.
// macOS 14+, Swift 5.10

import Foundation

/// Resolves whether to append `--dangerously-skip-permissions` when launching Claude.
///
/// The router (and any other entry point that launches Claude) should depend on this
/// protocol rather than reading `UserDefaults` directly, so the policy lives in one
/// place and stays consistent with the in-app launcher.
@MainActor
protocol ClaudePermissionsResolving: AnyObject {
    /// `true` when the user has explicitly opted into
    /// `--dangerously-skip-permissions` in General settings.
    var claudeSkipPermissions: Bool { get }
}

/// `GeneralPreferences` already owns the underlying UserDefaults-backed flag,
/// so we simply conform it.
extension GeneralPreferences: ClaudePermissionsResolving {}
