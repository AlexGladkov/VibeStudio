import Foundation
@testable import VibeStudio

/// Mock implementation of ``APIKeyResolving`` for unit tests.
struct MockAPIKeyResolver: APIKeyResolving {
    var keys: [String: String] = [:]

    func resolve(for envVar: String) -> String? {
        keys[envVar]
    }
}
