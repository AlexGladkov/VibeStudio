// MARK: - SettingsListCard
// Scrollable card list with interleaved thin dividers, used by AI-assistant
// settings panes (agents / commands / skills / plugins / memories …).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SettingsListCard

/// A scrollable `.settingsCard()`-styled list where each element is rendered by
/// `row` and separated from the next by a subtle, horizontally-inset divider.
///
/// Encapsulates the repeated
/// `ScrollView { VStack(spacing: 0) { ForEach … row + trailing Divider } }
///  .frame(maxHeight:).settingsCard()` skeleton used across the settings panes.
///
/// - `items`: the identifiable elements to display.
/// - `maxHeight`: cap on the scroll area height (default
///   `DSLayout.settingsListMaxHeightLarge`).
/// - `row`: builds the view for a single element.
struct SettingsListCard<Item: Identifiable, Row: View>: View {

    let items: [Item]
    var maxHeight: CGFloat = DSLayout.settingsListMaxHeightLarge
    @ViewBuilder let row: (Item) -> Row

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(spacing: 0) {
                ForEach(items) { item in
                    row(item)
                    if item.id != items.last?.id {
                        Divider()
                            .background(DSColor.borderSubtle)
                            .padding(.horizontal, DSSpacing.md)
                    }
                }
            }
        }
        .frame(maxHeight: maxHeight)
        .settingsCard()
    }
}
