package studio.vibe.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.X_OK
import platform.posix.access
import platform.posix.getenv
import studio.vibe.shared.constants.SecurityConstants
import studio.vibe.shared.contract.BinaryResolver
import studio.vibe.shared.model.FilePath

@OptIn(ExperimentalForeignApi::class)
class MacosBinaryResolver : BinaryResolver {

    override fun findExecutable(name: String): FilePath? {
        val homeDir = getenv("HOME")?.toKString() ?: ""
        val directories = SecurityConstants.trustedBinDirectories(homeDir)

        for (dir in directories) {
            val candidate = "$dir/$name"
            if (access(candidate, X_OK) == 0) {
                return FilePath(candidate)
            }
        }

        return null
    }
}
