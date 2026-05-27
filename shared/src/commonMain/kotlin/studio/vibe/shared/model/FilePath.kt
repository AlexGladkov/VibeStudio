package studio.vibe.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class FilePath(val path: String) {
    val name: String get() = path.substringAfterLast('/')
    val parent: FilePath get() = FilePath(path.substringBeforeLast('/', ""))

    fun child(name: String): FilePath = FilePath("$path/$name")

    override fun toString(): String = path
}
