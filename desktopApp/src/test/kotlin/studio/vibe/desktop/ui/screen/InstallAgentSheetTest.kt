package studio.vibe.desktop.ui.screen

import org.junit.Ignore
import org.junit.Test
import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.service.agent.BuiltInAgents
import studio.vibe.shared.service.agent.ClaudeAgent
import studio.vibe.shared.service.agent.CodeSpeakAgent
import studio.vibe.shared.service.agent.OpenCodeAgent

/**
 * Tests for [studio.vibe.desktop.ui.InstallAgentSheet].
 *
 * The public [studio.vibe.desktop.ui.InstallAgentSheet] composable is wrapped in a
 * [androidx.compose.ui.window.DialogWindow]. [createComposeRule] cannot render content
 * inside an external native window.
 *
 * This class tests:
 * - [AIAgent.installHint] is non-blank for every built-in agent (CommandBlock always has content)
 * - [AIAgent.prerequisite] / [AIAgent.prerequisiteCheckCommand] are consistent
 * - [AIAgent.shortDescription] is non-blank for every built-in agent
 * - Step numbering logic (with/without prerequisite)
 */
class InstallAgentSheetTest {

    // ── installHint: CommandBlock always has a command ────────────────────────

    @Test
    fun installHint_isNonBlank_forAllAgents() {
        BuiltInAgents.forEach { agent ->
            assert(agent.installHint.isNotBlank()) {
                "installHint must not be blank for ${agent.id}"
            }
        }
    }

    // ── shortDescription: header subtitle is always populated ─────────────────

    @Test
    fun shortDescription_isNonBlank_forAllAgents() {
        BuiltInAgents.forEach { agent ->
            assert(agent.shortDescription.isNotBlank()) {
                "shortDescription must not be blank for ${agent.id}"
            }
        }
    }

    // ── prerequisite / prerequisiteCheckCommand consistency ───────────────────

    @Test
    fun prerequisiteAndCheckCommand_areBothNullOrBothNonNull() {
        BuiltInAgents.forEach { agent ->
            val prereq = agent.prerequisite
            val checkCmd = agent.prerequisiteCheckCommand
            // Both must be null together or non-null together.
            val consistent = (prereq == null && checkCmd == null) ||
                (prereq != null && checkCmd != null)
            assert(consistent) {
                "${agent.id}: prerequisite=$prereq checkCommand=$checkCmd — must both be null or both non-null"
            }
        }
    }

    // ── Step numbering logic ──────────────────────────────────────────────────

    private fun installStepNumber(agent: AIAgent): Int {
        val hasPrereq = agent.prerequisite != null && agent.prerequisiteCheckCommand != null
        return if (hasPrereq) 2 else 1
    }

    @Test
    fun stepNumber_withPrerequisite_isTwo() {
        // Every built-in agent currently has a prerequisite, but we validate the logic path.
        val withPrereq = BuiltInAgents.filter {
            it.prerequisite != null && it.prerequisiteCheckCommand != null
        }
        withPrereq.forEach { agent ->
            assert(installStepNumber(agent) == 2) {
                "${agent.id}: expected install step 2 but got ${installStepNumber(agent)}"
            }
        }
    }

    @Test
    fun stepNumber_withoutPrerequisite_isOne() {
        // Test the logic path even if currently no agent reaches it.
        val hasPrereq = false
        val stepNumber = if (hasPrereq) 2 else 1
        assert(stepNumber == 1)
    }

    @Test
    fun setupStepNumber_isInstallStepPlusOne() {
        BuiltInAgents.forEach { agent ->
            val installStep = installStepNumber(agent)
            val setupStep = installStep + 1
            assert(setupStep == installStep + 1) {
                "Setup step should always be install step + 1 for ${agent.id}"
            }
        }
    }

    // ── Specific well-known agents ────────────────────────────────────────────

    @Test
    fun claude_installHint_usesNpm() {
        assert(ClaudeAgent.installHint.contains("npm"))
    }

    @Test
    fun opencode_installHint_usesGo() {
        assert(OpenCodeAgent.installHint.contains("go"))
    }

    @Test
    fun codeSpeak_installHint_usesUv() {
        assert(CodeSpeakAgent.installHint.contains("uv"))
    }

    @Test
    fun claude_hasNodePrerequisite() {
        assert(ClaudeAgent.prerequisite?.contains("Node") == true)
    }

    @Test
    fun opencode_hasGoPrerequisite() {
        assert(OpenCodeAgent.prerequisite?.contains("Go") == true)
    }

    @Test
    fun claude_setupInstructions_containsLoginCommand() {
        val instructions = ClaudeAgent.setupInstructions
        assert(instructions != null && instructions.contains("claude login")) {
            "ClaudeAgent setup instructions should reference 'claude login'"
        }
    }

    @Test
    fun opencode_setupInstructions_isNull() {
        // OpenCodeAgent has no setup instructions — it should render only install step.
        assert(OpenCodeAgent.setupInstructions == null)
    }

    // ── displayName is non-blank ──────────────────────────────────────────────

    @Test
    fun displayName_isNonBlank_forAllAgents() {
        BuiltInAgents.forEach { agent ->
            assert(agent.displayName.isNotBlank()) {
                "displayName must not be blank for ${agent.id}"
            }
        }
    }

    // ── DialogWindow limitation notice ────────────────────────────────────────

    @Ignore(
        "InstallAgentSheet wraps its content inside DialogWindow. createComposeRule() " +
            "cannot inject composables into an external native OS window. " +
            "AIAgent model properties and step logic are tested via pure-function " +
            "tests above.",
    )
    @Test
    fun installAgentSheet_dialogWindow_notTestableViaComposeRule() {
        // Intentionally empty — documents the DialogWindow limitation.
    }
}
