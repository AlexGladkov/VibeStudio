package studio.vibe.shared.preferences

import studio.vibe.shared.contract.SettingsStorage
import studio.vibe.shared.model.CodeSpeakCommand

/**
 * Key-value backed preferences for CodeSpeak behaviour.
 *
 * Keys are prefixed with `cs_` to avoid collisions with other services.
 * Backed by [SettingsStorage] for cross-platform persistence.
 *
 * Note: notification permission handling is platform-specific and must be
 * handled in platform-specific code. [notifyOnComplete] is persisted here;
 * the platform layer is responsible for requesting system permissions.
 */
class CodeSpeakPreferences(private val storage: SettingsStorage) : CodeSpeakPreferencesWriting {

    private object Keys {
        const val AUTO_BUILD_ON_SAVE = "cs_auto_build_on_save"
        const val BUILD_ON_PROJECT_OPEN = "cs_build_on_open"
        const val AUTO_OPEN_PANEL = "cs_auto_open_panel"
        const val DEFAULT_COMMAND = "cs_default_command"
        const val NOTIFY_ON_COMPLETE = "cs_notify_on_complete"
        const val SHOW_FAILING_ONLY = "cs_show_failing_only"
    }

    /**
     * Automatically run `codespeak build` after saving a spec file. Default: `false`.
     */
    override var autoBuildOnSave: Boolean
        get() = storage.getBool(Keys.AUTO_BUILD_ON_SAVE)
        set(value) { storage.setBool(Keys.AUTO_BUILD_ON_SAVE, value) }

    /**
     * Run `codespeak build` when switching to a CodeSpeak project. Default: `false`.
     */
    override var buildOnProjectOpen: Boolean
        get() = storage.getBool(Keys.BUILD_ON_PROJECT_OPEN)
        set(value) { storage.setBool(Keys.BUILD_ON_PROJECT_OPEN, value) }

    /**
     * Automatically open the build panel when a command starts. Default: `true`.
     */
    override var autoOpenBuildPanel: Boolean
        get() = storage.getString(Keys.AUTO_OPEN_PANEL)?.toBooleanStrictOrNull() ?: true
        set(value) { storage.setBool(Keys.AUTO_OPEN_PANEL, value) }

    /**
     * Default command executed when pressing the run button. Default: [CodeSpeakCommand.BUILD].
     */
    override var defaultCommand: CodeSpeakCommand
        get() = storage.getString(Keys.DEFAULT_COMMAND)
            ?.let { raw -> CodeSpeakCommand.entries.firstOrNull { it.id == raw } }
            ?: CodeSpeakCommand.BUILD
        set(value) { storage.setString(Keys.DEFAULT_COMMAND, value.id) }

    /**
     * Send a system notification when a command completes. Default: `false`.
     *
     * Platform-specific code is responsible for requesting notification permission
     * when this is set to `true`.
     */
    override var notifyOnComplete: Boolean
        get() = storage.getBool(Keys.NOTIFY_ON_COMPLETE)
        set(value) { storage.setBool(Keys.NOTIFY_ON_COMPLETE, value) }

    /**
     * Show only failing specs in the spec list. Default: `false`.
     */
    override var showFailingOnly: Boolean
        get() = storage.getBool(Keys.SHOW_FAILING_ONLY)
        set(value) { storage.setBool(Keys.SHOW_FAILING_ONLY, value) }
}
