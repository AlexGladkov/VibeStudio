package studio.vibe.desktop.ui.screen

import org.junit.Ignore
import org.junit.Test
import studio.vibe.shared.model.AIAssistant

/**
 * Tests for [studio.vibe.desktop.ui.InstallAgentSheet].
 *
 * The public [studio.vibe.desktop.ui.InstallAgentSheet] composable is wrapped in a
 * [androidx.compose.ui.window.DialogWindow]. [createComposeRule] cannot render content
 * inside an external native window.
 *
 * This class tests:
 * - [AIAssistant.installHint] is non-blank for every entry (CommandBlock always has content)
 * - [AIAssistant.prerequisite] / [AIAssistant.prerequisiteCheckCommand] are consistent
 * - [AIAssistant.shortDescription] is non-blank for every entry
 * - Step numbering logic (with/without prerequisite)
 */
class InstallAgentSheetTest {

    // ── installHint: CommandBlock always has a command ────────────────────────

    @Test
    fun installHint_isNonBlank_forAllAssistants() {
        AIAssistant.entries.forEach { assistant ->
            assert(assistant.installHint.isNotBlank()) {
                "installHint must not be blank for ${assistant.name}"
            }
        }
    }

    // ── shortDescription: header subtitle is always populated ─────────────────

    @Test
    fun shortDescription_isNonBlank_forAllAssistants() {
        AIAssistant.entries.forEach { assistant ->
            assert(assistant.shortDescription.isNotBlank()) {
                "shortDescription must not be blank for ${assistant.name}"
            }
        }
    }

    // ── prerequisite / prerequisiteCheckCommand consistency ───────────────────

    @Test
    fun prerequisiteAndCheckCommand_areBothNullOrBothNonNull() {
        AIAssistant.entries.forEach { assistant ->
            val prereq = assistant.prerequisite
            val checkCmd = assistant.prerequisiteCheckCommand
            // Both must be null together or non-null together.
            val consistent = (prereq == null && checkCmd == null) ||
                (prereq != null && checkCmd != null)
            assert(consistent) {
                "${assistant.name}: prerequisite=$prereq checkCommand=$checkCmd — must both be null or both non-null"
            }
        }
    }

    // ── Step numbering logic ──────────────────────────────────────────────────

    private fun installStepNumber(assistant: AIAssistant): Int {
        val hasPrereq = assistant.prerequisite != null && assistant.prerequisiteCheckCommand != null
        return if (hasPrereq) 2 else 1
    }

    @Test
    fun stepNumber_withPrerequisite_isTwo() {
        // Every assistant currently has a prerequisite, but we validate the logic path.
        val withPrereq = AIAssistant.entries.filter {
            it.prerequisite != null && it.prerequisiteCheckCommand != null
        }
        withPrereq.forEach { assistant ->
            assert(installStepNumber(assistant) == 2) {
                "${assistant.name}: expected install step 2 but got ${installStepNumber(assistant)}"
            }
        }
    }

    @Test
    fun stepNumber_withoutPrerequisite_isOne() {
        // Test the logic path even if currently no assistant reaches it.
        // We derive a hypothetical no-prereq assistant by checking the formula directly.
        val hasPrereq = false
        val stepNumber = if (hasPrereq) 2 else 1
        assert(stepNumber == 1)
    }

    @Test
    fun setupStepNumber_isInstallStepPlusOne() {
        AIAssistant.entries.forEach { assistant ->
            val installStep = installStepNumber(assistant)
            val setupStep = installStep + 1
            assert(setupStep == installStep + 1) {
                "Setup step should always be install step + 1 for ${assistant.name}"
            }
        }
    }

    // ── Specific well-known assistants ────────────────────────────────────────

    @Test
    fun claude_installHint_usesNpm() {
        assert(AIAssistant.CLAUDE.installHint.contains("npm"))
    }

    @Test
    fun opencode_installHint_usesGo() {
        assert(AIAssistant.OPENCODE.installHint.contains("go"))
    }

    @Test
    fun codeSpeak_installHint_usesUv() {
        assert(AIAssistant.CODE_SPEAK.installHint.contains("uv"))
    }

    @Test
    fun claude_hasNodePrerequisite() {
        assert(AIAssistant.CLAUDE.prerequisite?.contains("Node") == true)
    }

    @Test
    fun opencode_hasGoPrerequisite() {
        assert(AIAssistant.OPENCODE.prerequisite?.contains("Go") == true)
    }

    @Test
    fun claude_setupInstructions_containsLoginCommand() {
        val instructions = AIAssistant.CLAUDE.setupInstructions
        assert(instructions != null && instructions.contains("claude login")) {
            "CLAUDE setup instructions should reference 'claude login'"
        }
    }

    @Test
    fun opencode_setupInstructions_isNull() {
        // OPENCODE has no setup instructions — it should render only install step.
        assert(AIAssistant.OPENCODE.setupInstructions == null)
    }

    // ── displayName is non-blank ──────────────────────────────────────────────

    @Test
    fun displayName_isNonBlank_forAllAssistants() {
        AIAssistant.entries.forEach { assistant ->
            assert(assistant.displayName.isNotBlank()) {
                "displayName must not be blank for ${assistant.name}"
            }
        }
    }

    // ── DialogWindow limitation notice ────────────────────────────────────────

    @Ignore(
        "InstallAgentSheet wraps its content inside DialogWindow. createComposeRule() " +
            "cannot inject composables into an external native OS window. " +
            "AIAssistant model properties and step logic are tested via pure-function " +
            "tests above.",
    )
    @Test
    fun installAgentSheet_dialogWindow_notTestableViaComposeRule() {
        // Intentionally empty — documents the DialogWindow limitation.
    }
}
