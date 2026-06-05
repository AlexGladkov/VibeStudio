package studio.vibe.shared.testutil

import studio.vibe.shared.contract.PathResolving

/** Test double for [PathResolving] — always returns a fixed stub home path. */
class NoOpPathResolving(private val homePath: String = "/home/test") : PathResolving {
    override fun resolveHomePath(): String = homePath
}
