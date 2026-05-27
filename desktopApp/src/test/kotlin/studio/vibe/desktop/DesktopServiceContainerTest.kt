@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Smoke-level tests for [DesktopServiceContainer].
 *
 * These tests verify that the manual DI wiring compiles and that every
 * service accessor is non-null after construction.  They deliberately avoid
 * Compose UI, pty4j processes, or Swing — the goal is purely construction
 * correctness.
 *
 * [dispose] is called in [teardown] to cancel the coroutine scope and kill any
 * background work started by the container (e.g. [AgentAvailabilityServiceImpl.refreshAll]).
 */
class DesktopServiceContainerTest {

    private lateinit var container: DesktopServiceContainer

    @AfterTest
    fun teardown() {
        if (::container.isInitialized) {
            container.dispose()
        }
    }

    @Test
    fun container_canBeInstantiated() {
        container = DesktopServiceContainer()
        assertNotNull(container)
    }

    @Test
    fun container_processRunner_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.processRunner)
    }

    @Test
    fun container_persistenceStore_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.persistenceStore)
    }

    @Test
    fun container_credentialStorage_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.credentialStorage)
    }

    @Test
    fun container_binaryResolver_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.binaryResolver)
    }

    @Test
    fun container_settingsStorage_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.settingsStorage)
    }

    @Test
    fun container_gitService_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.gitService)
    }

    @Test
    fun container_projectStore_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.projectStore)
    }

    @Test
    fun container_agentAvailabilityChecking_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.agentAvailabilityChecking)
    }

    @Test
    fun container_apiKeyResolving_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.apiKeyResolving)
    }

    @Test
    fun container_aiCommitService_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.aiCommitService)
    }

    @Test
    fun container_generalPreferences_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.generalPreferences)
    }

    @Test
    fun container_remoteControlPreferences_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.remoteControlPreferences)
    }

    @Test
    fun container_codeSpeakPreferences_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.codeSpeakPreferences)
    }

    @Test
    fun container_fileTreeBuilder_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.fileTreeBuilder)
    }

    @Test
    fun container_terminalService_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.terminalService)
    }

    @Test
    fun container_scope_isNonNull() {
        container = DesktopServiceContainer()
        assertNotNull(container.scope)
    }

    @Test
    fun container_dispose_doesNotThrow() {
        container = DesktopServiceContainer()
        // Calling dispose must not throw even when no sessions are open.
        container.dispose()
    }
}
