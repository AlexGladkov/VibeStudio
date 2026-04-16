// MARK: - APIKeyResolving
// Protocol for resolving API keys from secure storage.
// macOS 14+, Swift 5.10

import Foundation

/// Resolves API keys for AI agents from a secure backing store.
///
/// Abstraction layer between `ToolbarViewModel` (Application layer) and
/// `KeychainHelper` (Infrastructure layer), following DIP.
protocol APIKeyResolving: Sendable {
    /// Resolve the API key for the given environment variable name.
    ///
    /// - Parameter envVar: Environment variable name (e.g. `"ANTHROPIC_API_KEY"`).
    /// - Returns: The stored key, or `nil` if not found.
    func resolve(for envVar: String) -> String?
}
