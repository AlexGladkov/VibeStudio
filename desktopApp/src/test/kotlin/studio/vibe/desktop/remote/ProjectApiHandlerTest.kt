@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import studio.vibe.shared.core.common.project.ProjectManaging
import studio.vibe.shared.feature.terminal.domain.contract.TerminalRemoteHost
import studio.vibe.shared.core.common.FilePath
import studio.vibe.shared.core.common.project.Project
import studio.vibe.shared.core.common.project.ProjectManagerError
import studio.vibe.shared.feature.remote.domain.model.RemoteAuthResult
import studio.vibe.shared.feature.remote.domain.model.RemoteDevice
import studio.vibe.shared.feature.remote.domain.contract.RemoteAuthorizing
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Unit-level tests for [ProjectApiHandler].
 *
 * Uses Ktor [testApplication] with a manually-wired handler — no OS ports, no
 * full [RemoteRouting] stack.  Auth is always satisfied unless the test
 * explicitly uses [unauthenticatedAuthHandler].
 */
class ProjectApiHandlerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val projectId1: Uuid = Uuid.parse("11111111-0000-0000-0000-000000000001")
    private val projectId2: Uuid = Uuid.parse("22222222-0000-0000-0000-000000000002")

    private fun fakeDevice(): RemoteDevice = RemoteDevice(
        id = Uuid.random(),
        displayName = "Test Device",
        ipAddress = "127.0.0.1",
        connectedAt = Clock.System.now(),
    )

    private fun fakeProject(
        id: Uuid = Uuid.random(),
        name: String = "TestProject",
        path: String = "/tmp/test",
    ): Project = Project(
        id = id,
        name = name,
        path = FilePath(path),
    )

    private fun authenticatedAuthHandler(): RemoteAuthHandler {
        val device = fakeDevice()
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validateToken(any(), any()) } returns RemoteAuthResult.success(device)
        return RemoteAuthHandler(authService, json)
    }

    private fun unauthenticatedAuthHandler(): RemoteAuthHandler {
        val authService = mockk<RemoteAuthorizing>(relaxed = true)
        coEvery { authService.validateToken(any(), any()) } returns
            RemoteAuthResult.failure(studio.vibe.shared.feature.remote.domain.model.RemoteAuthError.InvalidToken)
        return RemoteAuthHandler(authService, json)
    }

    private fun emptyTerminalHost(): TerminalRemoteHost = mockk<TerminalRemoteHost>(relaxed = true) {
        every { sessionsByProject } returns MutableStateFlow(emptyMap())
        every { outputFlow(any()) } returns null
    }

    /**
     * Wires [ProjectApiHandler] into a minimal [testApplication].
     * Routes are registered under `/api/v1` to mirror production routing.
     */
    private fun withHandlerApp(
        projectManaging: ProjectManaging?,
        terminalHost: TerminalRemoteHost = emptyTerminalHost(),
        authHandler: RemoteAuthHandler = authenticatedAuthHandler(),
        block: suspend io.ktor.client.HttpClient.() -> Unit,
    ) = testApplication {
        val handler = ProjectApiHandler(
            projectManaging = projectManaging,
            terminalService = terminalHost,
            scrollbackAccessing = null,
            activeBridges = ConcurrentHashMap(),
            authHandler = authHandler,
            json = json,
        )
        application {
            install(ContentNegotiation) { json(json) }
            routing {
                route("/api/v1") {
                    handler.registerProjects(this)
                }
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }
        client.block()
    }

    // ── listProjects — returns mapped list ────────────────────────────────────

    @Test
    fun listProjects_returnsAllProjectsMapped() {
        val p1 = fakeProject(id = projectId1, name = "Alpha", path = "/tmp/alpha")
        val p2 = fakeProject(id = projectId2, name = "Beta", path = "/tmp/beta")

        val pm = mockk<ProjectManaging>(relaxed = true)
        every { pm.projects } returns MutableStateFlow(listOf(p1, p2))
        every { pm.activeProjectId } returns MutableStateFlow(projectId1)
        every { pm.recentProjects } returns MutableStateFlow(emptyList())

        withHandlerApp(pm) {
            val response = get("/api/v1/projects") {
                header("X-Auth-Token", "valid-token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Alpha"), "Body must contain project name Alpha: $body")
            assertTrue(body.contains("Beta"), "Body must contain project name Beta: $body")
            assertTrue(body.contains(projectId1.toString()), "Body must contain active project id: $body")
            assertTrue(body.contains("/tmp/alpha"), "Body must contain path: $body")
        }
    }

    // ── listProjects — projectManaging null → empty list ─────────────────────

    @Test
    fun listProjects_nullProjectManaging_returnsEmptyList() {
        withHandlerApp(projectManaging = null) {
            val response = get("/api/v1/projects") {
                header("X-Auth-Token", "valid-token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"projects\":[]"), "Body must contain empty projects: $body")
        }
    }

    // ── createProject (open) — happy path → 200 with project ─────────────────

    @Test
    fun openProject_happyPath_returns200WithProject() {
        val newProject = fakeProject(id = projectId1, name = "NewProject", path = "/home/user/myapp")

        val pm = mockk<ProjectManaging>(relaxed = true)
        coEvery { pm.addProject(FilePath("/home/user/myapp")) } returns newProject
        every { pm.activeProjectId } returns MutableStateFlow(null)

        withHandlerApp(pm) {
            val response = post("/api/v1/projects/open") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"path":"/home/user/myapp"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("NewProject"), "Body must contain project name: $body")
            assertTrue(body.contains(projectId1.toString()), "Body must contain project id: $body")
            assertTrue(body.contains("/home/user/myapp"), "Body must contain project path: $body")
        }
    }

    // ── createProject (open) — duplicate path → 500 with open_failed ─────────

    @Test
    fun openProject_duplicatePath_returns500WithErrorCode() {
        val pm = mockk<ProjectManaging>(relaxed = true)
        coEvery { pm.addProject(FilePath("/tmp/existing")) } throws
            ProjectManagerError.Duplicate(
                existingId = projectId1,
                path = FilePath("/tmp/existing"),
            )

        withHandlerApp(pm) {
            val response = post("/api/v1/projects/open") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"path":"/tmp/existing"}""")
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("open_failed"), "Body must contain open_failed error code: $body")
        }
    }

    // ── createProject (open) — non-existent path → 500 with open_failed ──────

    @Test
    fun openProject_nonExistentPath_returns500WithErrorCode() {
        val pm = mockk<ProjectManaging>(relaxed = true)
        coEvery { pm.addProject(FilePath("/no/such/path")) } throws
            ProjectManagerError.InvalidPath(FilePath("/no/such/path"))

        withHandlerApp(pm) {
            val response = post("/api/v1/projects/open") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"path":"/no/such/path"}""")
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("open_failed"), "Body must contain open_failed error code: $body")
        }
    }

    // ── createProject (open) — projectManaging null → 501 ────────────────────

    @Test
    fun openProject_nullProjectManaging_returns501() {
        withHandlerApp(projectManaging = null) {
            val response = post("/api/v1/projects/open") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("""{"path":"/tmp/any"}""")
            }

            assertEquals(HttpStatusCode.NotImplemented, response.status)
        }
    }

    // ── getProject — activate known project → 200 ok ─────────────────────────

    @Test
    fun activateProject_knownProject_returns200() {
        val pm = mockk<ProjectManaging>(relaxed = true)
        every { pm.setActiveProjectId(projectId1) } returns Unit

        withHandlerApp(pm) {
            val response = post("/api/v1/projects/$projectId1/activate") {
                header("X-Auth-Token", "valid-token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("true"), "Body must contain ok:true: $body")
        }
    }

    // ── getProject — activate unknown project ID format → 400 ────────────────

    @Test
    fun activateProject_invalidProjectId_returns400() {
        val pm = mockk<ProjectManaging>(relaxed = true)

        withHandlerApp(pm) {
            val response = post("/api/v1/projects/not-a-uuid/activate") {
                header("X-Auth-Token", "valid-token")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("bad_request"), "Body must contain bad_request: $body")
        }
    }

    // ── listProjects — response mapping correct ───────────────────────────────

    @Test
    fun listProjects_responseMappingCorrect_idNamePathPresent() {
        val project = fakeProject(id = projectId1, name = "MappingTest", path = "/tmp/mapping")

        val pm = mockk<ProjectManaging>(relaxed = true)
        every { pm.projects } returns MutableStateFlow(listOf(project))
        every { pm.activeProjectId } returns MutableStateFlow(projectId1)
        every { pm.recentProjects } returns MutableStateFlow(emptyList())

        withHandlerApp(pm) {
            val response = get("/api/v1/projects") {
                header("X-Auth-Token", "valid-token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            // Verify all ProjectResponse fields are present in the JSON
            assertTrue(body.contains("\"id\""), "Response must include id field: $body")
            assertTrue(body.contains("\"name\""), "Response must include name field: $body")
            assertTrue(body.contains("\"path\""), "Response must include path field: $body")
            assertTrue(body.contains("\"sessions\""), "Response must include sessions field: $body")
            assertTrue(body.contains("active_project_id"), "Response must include active_project_id: $body")
            assertTrue(body.contains(projectId1.toString()), "active_project_id must match: $body")
        }
    }

    // ── listProjects — sessions field is empty list when no terminal sessions ─

    @Test
    fun listProjects_noTerminalSessions_sessionsIsEmptyArray() {
        val project = fakeProject(id = projectId1)

        val pm = mockk<ProjectManaging>(relaxed = true)
        every { pm.projects } returns MutableStateFlow(listOf(project))
        every { pm.activeProjectId } returns MutableStateFlow(null)
        every { pm.recentProjects } returns MutableStateFlow(emptyList())

        withHandlerApp(pm, terminalHost = emptyTerminalHost()) {
            val response = get("/api/v1/projects") {
                header("X-Auth-Token", "valid-token")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"sessions\":[]"), "Sessions must be empty array: $body")
        }
    }

    // ── unauthenticated → 401 ─────────────────────────────────────────────────

    @Test
    fun listProjects_missingToken_returns401() {
        val pm = mockk<ProjectManaging>(relaxed = true)

        withHandlerApp(pm, authHandler = unauthenticatedAuthHandler()) {
            val response = get("/api/v1/projects") {
                // No X-Auth-Token header
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    // ── openProject — invalid JSON body → 400 ────────────────────────────────

    @Test
    fun openProject_invalidJson_returns400() {
        val pm = mockk<ProjectManaging>(relaxed = true)

        withHandlerApp(pm) {
            val response = post("/api/v1/projects/open") {
                header("X-Auth-Token", "valid-token")
                contentType(ContentType.Application.Json)
                setBody("not-valid-json")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }
}
