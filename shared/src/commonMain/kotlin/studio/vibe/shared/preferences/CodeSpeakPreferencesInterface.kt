package studio.vibe.shared.preferences

import studio.vibe.shared.model.CodeSpeakCommand

/**
 * Abstracts CodeSpeak behaviour preferences.
 *
 * Consumers depend on this interface rather than the concrete
 * [CodeSpeakPreferences] class, enabling test doubles.
 */
interface CodeSpeakPreferencesReading {
    val autoBuildOnSave: Boolean
    val buildOnProjectOpen: Boolean
    val autoOpenBuildPanel: Boolean
    val defaultCommand: CodeSpeakCommand
    val notifyOnComplete: Boolean
    val showFailingOnly: Boolean
}

interface CodeSpeakPreferencesWriting : CodeSpeakPreferencesReading {
    override var autoBuildOnSave: Boolean
    override var buildOnProjectOpen: Boolean
    override var autoOpenBuildPanel: Boolean
    override var defaultCommand: CodeSpeakCommand
    override var notifyOnComplete: Boolean
    override var showFailingOnly: Boolean
}
