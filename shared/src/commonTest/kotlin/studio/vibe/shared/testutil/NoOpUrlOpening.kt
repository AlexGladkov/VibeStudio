package studio.vibe.shared.testutil

import studio.vibe.shared.contract.UrlOpening

/** Test double for [UrlOpening] — records opened URLs for assertion. */
class NoOpUrlOpening : UrlOpening {
    val openedUrls: MutableList<String> = mutableListOf()
    override fun openUrl(url: String) {
        openedUrls += url
    }
}
