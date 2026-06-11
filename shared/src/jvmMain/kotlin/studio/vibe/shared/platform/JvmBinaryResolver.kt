package studio.vibe.shared.platform

import studio.vibe.shared.core.common.BinaryResolver
import studio.vibe.shared.core.common.FilePath
import java.nio.file.Files
import java.nio.file.Path

class JvmBinaryResolver : BinaryResolver {

    private val isWindows: Boolean =
        System.getProperty("os.name", "").lowercase().contains("win")

    override fun findExecutable(name: String): FilePath? {
        val pathEnv = System.getenv("PATH") ?: return null
        val separator = if (isWindows) ";" else ":"
        val dirs = pathEnv.split(separator)

        val candidates: List<String> = if (isWindows) {
            // On Windows also try common executable extensions
            val extensions = listOf("", ".exe", ".cmd", ".bat")
            extensions.map { ext -> "$name$ext" }
        } else {
            listOf(name)
        }

        for (dir in dirs) {
            if (dir.isBlank()) continue
            for (candidate in candidates) {
                val path: Path = Path.of(dir, candidate)
                if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                    return FilePath(path.toAbsolutePath().toString())
                }
            }
        }

        return null
    }
}
