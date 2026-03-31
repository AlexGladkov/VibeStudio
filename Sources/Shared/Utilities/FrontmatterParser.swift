// MARK: - FrontmatterParser
// Shared YAML frontmatter parsing utility for agent, command, and skill markdown files.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - FrontmatterFields

/// Parsed YAML frontmatter values from an agent or skill markdown file.
struct FrontmatterFields {
    /// The `name:` field value.
    let name: String
    /// The `description:` field value.
    let description: String
    /// The `user_invocable:` field value (defaults to `false`).
    let userInvocable: Bool
}

// MARK: - Frontmatter Parsing

/// Parses YAML-style frontmatter delimited by `---` lines.
/// Extracts `name:`, `description:`, and `user_invocable:` values.
///
/// - Parameter content: Full markdown file content.
/// - Returns: ``FrontmatterFields`` with extracted values (empty/false if not found).
func parseFrontmatter(_ content: String) -> FrontmatterFields {
    let lines = content.components(separatedBy: "\n")
    guard lines.first?.trimmingCharacters(in: .whitespaces) == "---" else {
        return FrontmatterFields(name: "", description: "", userInvocable: false)
    }

    var name = ""
    var description = ""
    var userInvocable = false
    var index = 1

    while index < lines.count {
        let line = lines[index]
        if line.trimmingCharacters(in: .whitespaces) == "---" {
            break
        }
        if line.hasPrefix("name:") {
            name = line.dropFirst("name:".count).trimmingCharacters(in: .whitespaces)
        } else if line.hasPrefix("description:") {
            description = line.dropFirst("description:".count).trimmingCharacters(in: .whitespaces)
        } else if line.hasPrefix("user_invocable:") {
            let value = line.dropFirst("user_invocable:".count).trimmingCharacters(in: .whitespaces)
            userInvocable = value.lowercased() == "true"
        }
        index += 1
    }

    return FrontmatterFields(name: name, description: description, userInvocable: userInvocable)
}
