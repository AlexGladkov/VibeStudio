// MARK: - AppLaunchEnvironment
// Process launch-mode detection used by the application entry point.
// macOS 14+, Swift 5.10

import Foundation

/// Describes process launch traits that affect early application startup.
///
/// Hosted XCTest launches the real app process before the test runner finishes
/// attaching. Startup work that opens sockets, starts PTYs, runs git, or blocks
/// on TCC can race that handshake, so the app entry point uses this helper to
/// keep the process lightweight until XCTest owns the session.
enum AppLaunchEnvironment {

    /// Returns `true` when the app process is being launched as an XCTest host.
    ///
    /// XCTest normally sets `XCTestConfigurationFilePath`. Some Xcode/macOS
    /// combinations also expose injection/session keys, so those are accepted
    /// as equivalent signals for hosted unit/UI test launches.
    static var isRunningHostedXCTest: Bool {
        let environment = ProcessInfo.processInfo.environment
        let xctestKeys = [
            "XCTestConfigurationFilePath",
            "XCTestBundlePath",
            "XCTestSessionIdentifier",
            "XCInjectBundleInto",
            "XCInjectBundle"
        ]
        return xctestKeys.contains { key in
            guard let value = environment[key] else { return false }
            return !value.isEmpty
        }
    }
}
