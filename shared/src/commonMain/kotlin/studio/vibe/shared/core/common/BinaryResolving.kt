package studio.vibe.shared.core.common

interface BinaryResolver {
    fun findExecutable(name: String): FilePath?
}
