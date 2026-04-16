// MARK: - KeychainAPIKeyResolver
// APIKeyResolving implementation backed by Keychain.
// macOS 14+, Swift 5.10

import Foundation

/// Resolves API keys from the system Keychain via `KeychainHelper`.
struct KeychainAPIKeyResolver: APIKeyResolving {
    func resolve(for envVar: String) -> String? {
        KeychainHelper.load(account: envVar)
    }
}
