@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop

import java.nio.file.Files

/**
 * Creates an isolated [DesktopServiceContainer] for tests by temporarily
 * redirecting the user.home JVM property to a per-test temp directory.
 *
 * [JvmPersistenceStore.appSupportDirectory] is derived from user.home (on macOS:
 * `$HOME/Library/Application Support/VibeStudio`). By pointing user.home at a
 * fresh temp dir, the container starts with zero persisted projects and cannot
 * contaminate real user data.
 *
 * The original user.home is restored in the returned [TestContainerScope.dispose]
 * function.
 *
 * Usage:
 * ```kotlin
 * private lateinit var container: DesktopServiceContainer
 * private lateinit var tempHome: java.io.File
 *
 * @Before
 * fun setup() {
 *     val (c, home) = createIsolatedContainer()
 *     container = c
 *     tempHome = home
 * }
 *
 * @After
 * fun teardown() {
 *     container.dispose()
 *     tempHome.deleteRecursively()
 * }
 * ```
 */
fun createIsolatedContainer(): Pair<DesktopServiceContainer, java.io.File> {
    val tempHome = Files.createTempDirectory("vs-test-home").toFile()

    // Redirect user.home so JvmPersistenceStore resolves to our temp dir.
    val originalHome = System.getProperty("user.home")
    System.setProperty("user.home", tempHome.absolutePath)

    val container = try {
        DesktopServiceContainer()
    } finally {
        // Restore immediately after construction — the property is only needed
        // during JvmPersistenceStore.appSupportDirectory() which is called once
        // lazily via ProjectStoreImpl.projectsFile.
        System.setProperty("user.home", originalHome ?: "")
    }

    return container to tempHome
}
