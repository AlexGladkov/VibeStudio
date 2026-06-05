package studio.vibe.shared.service.security

import studio.vibe.shared.contract.AIAgent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentEnvironmentBuilderTest {

    private fun makeAgent(apiKeyVar: String? = "ANTHROPIC_API_KEY") = object : AIAgent {
        override val id = "test"
        override val displayName = "Test Agent"
        override val executableName = "claude"
        override val launchCommand = "claude"
        override val apiKeyEnvironmentVariable = apiKeyVar
        override val installHint = ""
        override val shortDescription = ""
        override val prerequisite: String? = null
        override val prerequisiteCheckCommand: String? = null
        override val setupInstructions: String? = null
    }

    @Test
    fun build_withApiKey_injectsKeyIntoEnv() {
        val agent = makeAgent("ANTHROPIC_API_KEY")
        val env = AgentEnvironmentBuilder.build(
            agent = agent,
            apiKeyValue = "sk-test-key",
            currentEnv = emptyMap(),
        )

        assertEquals("sk-test-key", env["ANTHROPIC_API_KEY"])
    }

    @Test
    fun build_withNullApiKey_doesNotSetKeyEnvVar() {
        val agent = makeAgent("ANTHROPIC_API_KEY")
        val env = AgentEnvironmentBuilder.build(
            agent = agent,
            apiKeyValue = null,
            currentEnv = emptyMap(),
        )

        assertNull(env["ANTHROPIC_API_KEY"])
    }

    @Test
    fun build_agentWithNoApiKeyVar_noKeyInjected() {
        val agent = makeAgent(apiKeyVar = null)
        val env = AgentEnvironmentBuilder.build(
            agent = agent,
            apiKeyValue = "sk-some",
            currentEnv = emptyMap(),
        )

        // Should not contain any api key entries for the agent
        assertNull(env["ANTHROPIC_API_KEY"])
    }

    @Test
    fun build_allowslistedVarsFromCurrentEnv_areForwarded() {
        val agent = makeAgent()
        val currentEnv = mapOf(
            "HOME" to "/home/user",
            "PATH" to "/usr/bin:/bin",
            "AWS_SECRET_KEY" to "should-be-blocked",
            "DATABASE_URL" to "should-be-blocked",
        )

        val env = AgentEnvironmentBuilder.build(agent, null, currentEnv)

        assertEquals("/home/user", env["HOME"])
        assertNotNull(env["PATH"])
        // Sensitive vars must NOT be forwarded
        assertNull(env["AWS_SECRET_KEY"])
        assertNull(env["DATABASE_URL"])
    }

    @Test
    fun build_alwaysSetsTerminalCapabilityVars() {
        val agent = makeAgent()
        val env = AgentEnvironmentBuilder.build(agent, null, emptyMap())

        assertEquals("xterm-256color", env["TERM"])
        assertEquals("truecolor", env["COLORTERM"])
    }

    @Test
    fun build_langNotInCurrentEnv_setsDefault() {
        val agent = makeAgent()
        val env = AgentEnvironmentBuilder.build(agent, null, emptyMap())

        assertEquals("en_US.UTF-8", env["LANG"])
    }

    @Test
    fun build_langInCurrentEnv_preservesExistingLang() {
        val agent = makeAgent()
        val currentEnv = mapOf("LANG" to "fr_FR.UTF-8")
        val env = AgentEnvironmentBuilder.build(agent, null, currentEnv)

        assertEquals("fr_FR.UTF-8", env["LANG"])
    }

    @Test
    fun build_pathExtendedWithTrustedBinaries() {
        val agent = makeAgent()
        val currentEnv = mapOf(
            "PATH" to "/usr/bin:/bin",
            "HOME" to "/home/user",
        )

        val env = AgentEnvironmentBuilder.build(agent, null, currentEnv)

        val path = env["PATH"] ?: ""
        // Trusted dirs like /opt/homebrew/bin should be in the PATH
        assertTrue(path.contains("/opt/homebrew/bin"))
    }

    @Test
    fun build_emptyApiKeyValue_doesNotInjectKey() {
        val agent = makeAgent("ANTHROPIC_API_KEY")
        val env = AgentEnvironmentBuilder.build(agent, "", emptyMap())

        assertNull(env["ANTHROPIC_API_KEY"])
    }
}
