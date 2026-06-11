package studio.vibe.shared.testutil

import studio.vibe.shared.feature.remote.data.PlatformLogger

/**
 * In-memory [PlatformLogger] test double.
 *
 * Captures all log calls for assertion purposes.
 */
class FakePlatformLogger : PlatformLogger {

    data class LogEntry(val level: Level, val tag: String, val message: String)

    enum class Level { INFO, WARNING, ERROR }

    val entries = mutableListOf<LogEntry>()

    val infoEntries: List<LogEntry> get() = entries.filter { it.level == Level.INFO }
    val warningEntries: List<LogEntry> get() = entries.filter { it.level == Level.WARNING }
    val errorEntries: List<LogEntry> get() = entries.filter { it.level == Level.ERROR }

    override fun info(tag: String, message: String) {
        entries.add(LogEntry(Level.INFO, tag, message))
    }

    override fun warning(tag: String, message: String) {
        entries.add(LogEntry(Level.WARNING, tag, message))
    }

    override fun error(tag: String, message: String) {
        entries.add(LogEntry(Level.ERROR, tag, message))
    }

    fun reset() = entries.clear()
}
