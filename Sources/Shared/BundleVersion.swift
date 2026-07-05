// MARK: - Bundle Version Helpers
// Single source of truth for reading the running app's version strings.
// macOS 14+, Swift 5.10

import Foundation

extension Bundle {

    /// Marketing version, e.g. `0.4.0` (`CFBundleShortVersionString`).
    var appShortVersion: String {
        object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    /// Build number, e.g. `1` (`CFBundleVersion`).
    var appBuildNumber: String {
        object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
    }

    /// Compact display string used in unobtrusive UI (sidebar footer, settings):
    /// `v0.4.0 (1)`. The build number disambiguates otherwise-identical
    /// marketing versions so it's always clear which build is running.
    var appVersionDisplay: String {
        "v\(appShortVersion) (\(appBuildNumber))"
    }
}
