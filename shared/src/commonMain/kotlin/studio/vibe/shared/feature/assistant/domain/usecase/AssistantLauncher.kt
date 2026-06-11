@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.shared.feature.assistant.domain.usecase

/** @deprecated Moved to core/common. */
typealias ResumeRequest = studio.vibe.shared.core.common.assistant.ResumeRequest

/**
 * Backward-compatible type alias.
 *
 * The interface was moved to [studio.vibe.shared.service.assistant.AssistantLaunching]
 * because it is a domain service, not a use case. Existing imports of
 * `studio.vibe.shared.usecase.AssistantLauncher` continue to work via this alias
 * while call-sites are migrated.
 */
typealias AssistantLauncher = studio.vibe.shared.feature.assistant.domain.contract.AssistantLaunching
