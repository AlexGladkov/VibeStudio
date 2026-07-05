// MARK: - Settings Navigation Model
// Enum-based navigation for the Settings window sidebar.
// macOS 14+, Swift 5.10

import Foundation
import SwiftUI

// MARK: - SettingsSectionGroup

/// Top-level groups in the settings sidebar.
enum SettingsSectionGroup: String, CaseIterable, Identifiable {
    case general
    case llm

    var id: String { rawValue }

    /// Localized section header displayed in the sidebar.
    var displayName: LocalizedStringKey {
        switch self {
        case .general: return "General"
        case .llm:     return "LLM"
        }
    }

    /// Settings items belonging to this group.
    var items: [SettingsItem] {
        switch self {
        case .general:
            return [.appearance, .remoteControl, .updates]
        case .llm:
            return AIAssistant.allCases.map { .llmAssistant($0) }
        }
    }
}

// MARK: - SettingsItem

/// Individual navigation item in the settings sidebar.
enum SettingsItem: Hashable, Identifiable {
    case appearance
    case remoteControl
    case updates
    case llmAssistant(AIAssistant)

    var id: String {
        switch self {
        case .appearance:
            return "appearance"
        case .remoteControl:
            return "remoteControl"
        case .updates:
            return "updates"
        case .llmAssistant(let assistant):
            return "llm-\(assistant.rawValue)"
        }
    }

    /// The section group this item belongs to.
    var sectionGroup: SettingsSectionGroup {
        switch self {
        case .appearance:        return .general
        case .remoteControl:     return .general
        case .updates:           return .general
        case .llmAssistant:      return .llm
        }
    }

    /// Human-readable label for the sidebar row.
    var displayName: LocalizedStringKey {
        switch self {
        case .appearance:
            return "Appearance"
        case .remoteControl:
            return "Remote Control"
        case .updates:
            return "Updates"
        case .llmAssistant(let assistant):
            // Assistant names are proper nouns; not localized.
            return LocalizedStringKey(assistant.displayName.capitalized)
        }
    }

    /// SF Symbol name for the sidebar icon.
    var systemImage: String {
        switch self {
        case .appearance:        return "folder.fill"
        case .remoteControl:     return "antenna.radiowaves.left.and.right"
        case .updates:           return "arrow.down.circle"
        case .llmAssistant:      return "brain"
        }
    }
}
