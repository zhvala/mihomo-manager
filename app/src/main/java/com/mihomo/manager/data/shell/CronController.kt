package com.mihomo.manager.data.shell

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CronController @Inject constructor(
    private val shell: ShellExecutor,
) {
    companion object {
        const val CRON_FILE = "/data/adb/cron/root"
        const val WATCHER_PATTERN = "watcher.sh"
    }

    suspend fun isEnabled(): Boolean {
        val result = shell.readFile(CRON_FILE)
        if (!result.isSuccess) return false
        return result.output.lines().any {
            it.contains(WATCHER_PATTERN) && !it.trimStart().startsWith("#")
        }
    }

    suspend fun enable(): ShellResult {
        val result = shell.readFile(CRON_FILE)
        if (!result.isSuccess) return result
        val updated = result.output.lines().joinToString("\n") { line ->
            if (line.contains(WATCHER_PATTERN) && line.trimStart().startsWith("#")) {
                line.replace(Regex("^(\\s*)#\\s*"), "$1")
            } else line
        }
        return shell.writeFile(CRON_FILE, updated)
    }

    suspend fun disable(): ShellResult {
        val result = shell.readFile(CRON_FILE)
        if (!result.isSuccess) return result
        val updated = result.output.lines().joinToString("\n") { line ->
            if (line.contains(WATCHER_PATTERN) && !line.trimStart().startsWith("#")) {
                "# $line"
            } else line
        }
        return shell.writeFile(CRON_FILE, updated)
    }

    suspend fun toggle(): ShellResult = if (isEnabled()) disable() else enable()
}
