package studio.vibe.shared.testutil

import studio.vibe.shared.model.CodeSpeakCommand
import studio.vibe.shared.preferences.CodeSpeakPreferencesWriting

class FakeCodeSpeakPreferences(
    override var autoBuildOnSave: Boolean = false,
    override var buildOnProjectOpen: Boolean = false,
    override var autoOpenBuildPanel: Boolean = true,
    override var defaultCommand: CodeSpeakCommand = CodeSpeakCommand.BUILD,
    override var notifyOnComplete: Boolean = false,
    override var showFailingOnly: Boolean = false,
) : CodeSpeakPreferencesWriting
