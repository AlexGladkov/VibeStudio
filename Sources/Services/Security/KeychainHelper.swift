// MARK: - KeychainHelper
// Secure storage for AI agent API keys via Security.framework.
// macOS 14+, Swift 5.10

import Foundation
import Security

/// Thin wrapper around macOS Keychain Services for storing and retrieving
/// AI agent API keys.
///
/// Keys are stored as generic passwords with:
/// - `kSecAttrService` = app bundle identifier
/// - `kSecAttrAccount` = environment variable name (e.g. "OPENAI_API_KEY")
/// - `kSecAttrAccessible` = `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`
///
/// This ensures keys are encrypted at rest and never leave the device.
enum KeychainHelper {

    private static let service: String = {
        Bundle.main.bundleIdentifier ?? "tech.mobiledeveloper.vibestudio"
    }()

    // MARK: - Save

    /// Store a value in Keychain, overwriting if it already exists.
    ///
    /// - Parameters:
    ///   - account: The account identifier (typically the env var name).
    ///   - value: The secret value to store.
    /// - Returns: `true` if the operation succeeded.
    @discardableResult
    static func save(account: String, value: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }

        // Delete any existing item first to avoid errSecDuplicateItem.
        delete(account: account)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    // MARK: - Load

    /// Retrieve a value from Keychain.
    ///
    /// - Parameter account: The account identifier.
    /// - Returns: The stored secret, or `nil` if not found.
    static func load(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecReturnData as String: true,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let string = String(data: data, encoding: .utf8) else {
            return nil
        }
        return string
    }

    // MARK: - Delete

    /// Remove a value from Keychain.
    ///
    /// - Parameter account: The account identifier.
    /// - Returns: `true` if the item was deleted (or did not exist).
    @discardableResult
    static func delete(account: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]

        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
