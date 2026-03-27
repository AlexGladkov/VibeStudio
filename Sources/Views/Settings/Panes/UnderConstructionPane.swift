// MARK: - UnderConstructionPane
// Generic placeholder for settings panes not yet implemented.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - UnderConstructionPane

/// Centered placeholder view indicating a feature is under development.
///
/// - Parameter title: Name of the feature or section (e.g. assistant name).
struct UnderConstructionPane: View {

    let title: String

    var body: some View {
        VStack(spacing: DSSpacing.md) {
            Image(systemName: "hammer.fill")
                .font(.system(size: 48))
                .foregroundStyle(DSColor.textMuted)

            Text("Under Construction")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(DSColor.textPrimary)

            Text("Настройки для \(title) будут добавлены в следующих версиях.")
                .font(.system(size: 13))
                .foregroundStyle(DSColor.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DSColor.surfaceDefault)
    }
}
