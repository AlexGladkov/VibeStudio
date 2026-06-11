package studio.vibe.desktop.terminal

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.core.common.AgentExitSequence
import studio.vibe.shared.feature.settings.domain.model.GeneralPreferencesReading
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [AgentSessionFactory].
 *
 * All tests target the two `internal` seams — [AgentSessionFactory.buildEffectiveLaunchCommand]
 * and [AgentSessionFactory.buildAgentEnv] — so no real PTY process is ever spawned.
 *
 * Covered scenarios:
 * 1. Env vars assembled correctly (API key injected, PATH augmented).
 * 2. Agent without an API-key env-var does not inject a spurious key.
 * 3. `--dangerously-skip-permissions` appended for Claude when preference is enabled.
 * 4. `--dangerously-skip-permissions` NOT appended for non-Claude agents.
 * 5. `--dangerously-skip-permissions` NOT appended even for Claude when preference is off.
 * 6. Working directory is reflected in the env (HOME is set from system properties).
 */
class AgentSessionFactoryTest {

    private lateinit var serviceScope: CoroutineScope
    private lateinit var prefs: GeneralPreferencesReading

    @BeforeTest
    fun setUp() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        prefs = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        serviceScope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeFactory(claudeSkipPermissions: Boolean = false): AgentSessionFactory {
        every { prefs.claudeSkipPermissions } returns claudeSkipPermissions
        return AgentSessionFactory(generalPreferences = prefs, serviceScope = serviceScope)
    }

    private fun stubAgent(
        id: String = "test-agent",
        launchCommand: String = "$id\n",
        apiKeyEnvVar: String? = "TEST_API_KEY",
        jsonStreamArgs: List<String>? = null,
    ): AIAgent = object : AIAgent {
        override val id = id
        override val displayName = id
        override val executableName = id
        override val launchCommand = launchCommand
        override val apiKeyEnvironmentVariable = apiKeyEnvVar
        override val installHint = ""
        override val shortDescription = ""
        override val prerequisite = null
        override val prerequisiteCheckCommand = null
        override val setupInstructions = null
        override val exitSequence = AgentExitSequence.CtrlC
        override val supportsJsonStreamOutput: Boolean = jsonStreamArgs != null
        override val jsonStreamOutputArgs: List<String>? = jsonStreamArgs
    }

    private fun stubClaude(launchCommand: String = "claude\n"): AIAgent =
        stubAgent(id = "claude", launchCommand = launchCommand, apiKeyEnvVar = "ANTHROPIC_API_KEY")

    // ── 1. API key injected into env ──────────────────────────────────────────

    @Test
    fun `buildAgentEnv injects api key into the correct env variable`() {
        val factory = makeFactory()
        val agent = stubAgent(apiKeyEnvVar = "ANTHROPIC_API_KEY")
        val apiKey = "sk-ant-test-key-12345"

        val env = factory.buildAgentEnv(agent, apiKey)

        assertEquals(apiKey, env["ANTHROPIC_API_KEY"], "API key must be present under the declared env-var name")
    }

    @Test
    fun `buildAgentEnv includes PATH with trusted binary directories`() {
        val factory = makeFactory()
        val agent = stubAgent()

        val env = factory.buildAgentEnv(agent, apiKeyValue = null)

        val path = env["PATH"]
        assertNotNull(path, "PATH must be present in agent env")
        // trustedBinDirectories includes /usr/local/bin unconditionally.
        assertTrue(
            path.contains("/usr/local/bin"),
            "Trusted bin dir /usr/local/bin must appear in PATH: $path",
        )
    }

    @Test
    fun `buildAgentEnv does not leak sensitive env vars`() {
        val factory = makeFactory()
        val agent = stubAgent()

        // Simulate a parent env with secrets — AgentEnvironmentBuilder uses allowlist.
        val env = factory.buildAgentEnv(agent, apiKeyValue = null)

        // These should NEVER appear (they are not in the allowlist).
        assertFalse(env.containsKey("DATABASE_URL"), "DATABASE_URL must not leak into agent env")
        assertFalse(env.containsKey("AWS_SECRET_ACCESS_KEY"), "AWS secrets must not leak")
        assertFalse(env.containsKey("GITHUB_TOKEN"), "GITHUB_TOKEN must not leak")
    }

    // ── 2. Agent without API-key var ──────────────────────────────────────────

    @Test
    fun `buildAgentEnv does not inject api key when agent has no apiKeyEnvironmentVariable`() {
        val factory = makeFactory()
        val agent = stubAgent(apiKeyEnvVar = null)

        val env = factory.buildAgentEnv(agent, apiKeyValue = "should-not-appear")

        // Neither ANTHROPIC_API_KEY nor OPENAI_API_KEY should carry a test-only value
        // that was not already present in the system env before the test.
        // We only check the allowlisted variables that could be injected by the builder.
        assertFalse(
            env.any { (_, v) -> v == "should-not-appear" },
            "API key value must not appear in env when agent declares no apiKeyEnvironmentVariable",
        )
    }

    // ── 3. Skip-permissions flag for Claude when pref is on ───────────────────

    @Test
    fun `buildEffectiveLaunchCommand appends skip-permissions for claude when pref enabled`() {
        val factory = makeFactory(claudeSkipPermissions = true)
        val claude = stubClaude(launchCommand = "claude\n")

        val cmd = factory.buildEffectiveLaunchCommand(claude)

        assertTrue(
            cmd.contains("--dangerously-skip-permissions"),
            "Flag must be appended for Claude when claudeSkipPermissions=true: $cmd",
        )
        // Command must still be newline-terminated for PTY stdin injection.
        assertTrue(cmd.endsWith("\n"), "Command must end with newline: $cmd")
    }

    // ── 4. Skip-permissions NOT added for non-Claude agents ───────────────────

    @Test
    fun `buildEffectiveLaunchCommand does not append skip-permissions for non-claude agent`() {
        val factory = makeFactory(claudeSkipPermissions = true)
        val other = stubAgent(id = "opencode", launchCommand = "opencode\n")

        val cmd = factory.buildEffectiveLaunchCommand(other)

        assertFalse(
            cmd.contains("--dangerously-skip-permissions"),
            "Flag must NOT appear for non-Claude agents even when pref is true: $cmd",
        )
        assertEquals("opencode\n", cmd)
    }

    // ── 5. Skip-permissions NOT added when pref is off ────────────────────────

    @Test
    fun `buildEffectiveLaunchCommand does not append skip-permissions when pref disabled`() {
        val factory = makeFactory(claudeSkipPermissions = false)
        val claude = stubClaude(launchCommand = "claude\n")

        val cmd = factory.buildEffectiveLaunchCommand(claude)

        assertFalse(
            cmd.contains("--dangerously-skip-permissions"),
            "Flag must NOT be added when claudeSkipPermissions=false: $cmd",
        )
        assertEquals("claude\n", cmd)
    }

    // ── 6. HOME is present in env ─────────────────────────────────────────────

    @Test
    fun `buildAgentEnv always contains HOME`() {
        val factory = makeFactory()
        val agent = stubAgent()

        val env = factory.buildAgentEnv(agent, apiKeyValue = null)

        assertNotNull(env["HOME"], "HOME must be present in agent env for shell initialisation")
    }

    // ── 7. jsonStreamOutputArgs appended when present ─────────────────────────

    @Test
    fun `buildEffectiveLaunchCommand appends jsonStreamOutputArgs when agent declares them`() {
        val factory = makeFactory()
        val agent = stubAgent(
            id = "claude",
            launchCommand = "claude\n",
            jsonStreamArgs = listOf("--output-format", "stream-json"),
        )

        val cmd = factory.buildEffectiveLaunchCommand(agent)

        assertTrue(
            cmd.contains("--output-format stream-json"),
            "jsonStreamOutputArgs must be appended to the launch command: $cmd",
        )
        assertTrue(cmd.endsWith("\n"), "Command must end with newline: $cmd")
    }

    @Test
    fun `buildEffectiveLaunchCommand does not add json args when agent has none`() {
        val factory = makeFactory()
        val agent = stubAgent(id = "opencode", launchCommand = "opencode\n", jsonStreamArgs = null)

        val cmd = factory.buildEffectiveLaunchCommand(agent)

        assertFalse(
            cmd.contains("--output-format"),
            "No JSON stream args should appear when agent.jsonStreamOutputArgs is null: $cmd",
        )
        assertEquals("opencode\n", cmd)
    }

    @Test
    fun `buildEffectiveLaunchCommand appends both jsonStreamArgs and skipPermissions for claude`() {
        val factory = makeFactory(claudeSkipPermissions = true)
        val agent = stubAgent(
            id = "claude",
            launchCommand = "claude\n",
            jsonStreamArgs = listOf("--output-format", "stream-json"),
        )

        val cmd = factory.buildEffectiveLaunchCommand(agent)

        assertTrue(
            cmd.contains("--output-format stream-json"),
            "JSON stream args must be present: $cmd",
        )
        assertTrue(
            cmd.contains("--dangerously-skip-permissions"),
            "Skip-permissions flag must also be present for Claude when pref is on: $cmd",
        )
        assertTrue(cmd.endsWith("\n"), "Command must end with newline: $cmd")
    }

    @Test
    fun `buildEffectiveLaunchCommand uses ClaudeAgent jsonStreamOutputArgs correctly`() {
        // Verify against the real built-in agent definition, not a stub.
        val factory = makeFactory(claudeSkipPermissions = false)
        val cmd = factory.buildEffectiveLaunchCommand(studio.vibe.shared.core.common.ClaudeAgent)

        assertTrue(
            cmd.contains("--output-format stream-json"),
            "ClaudeAgent launch command must include JSON stream args: $cmd",
        )
        assertFalse(
            cmd.contains("--dangerously-skip-permissions"),
            "Skip-permissions must NOT appear when pref is off: $cmd",
        )
        assertTrue(cmd.endsWith("\n"), "Command must end with newline: $cmd")
    }
}
