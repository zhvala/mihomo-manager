package com.mihomo.manager.data.shell

import javax.inject.Inject
import javax.inject.Singleton

enum class Profile { PROXY, DIRECT }

data class MihomoStatus(
    val isRunning: Boolean,
    val pid: Int?,
    val currentProfile: Profile,
    val isHealthy: Boolean,
)

data class ValidationResult(
    val isValid: Boolean,
    val message: String,
)

@Singleton
class MihomoController @Inject constructor(
    private val shell: ShellExecutor,
) {
    companion object {
        const val CONFIG_YAML = "/data/adb/mihomo/conf/config.yaml"
        const val CONFIG_PROXY = "/data/adb/mihomo/conf/config-proxy.yaml"
        const val CONFIG_DIRECT = "/data/adb/mihomo/conf/config-direct.yaml"
        const val CONF_DIR = "/data/adb/mihomo/conf"
        const val MIHOMO_BIN = "/data/adb/mihomo/bin/mihomo"
        const val MIHOMO_SERVICE = "/data/adb/service.d/mihomo.sh"
        const val MIHOMO_LOG = "/data/adb/mihomo/logs/mihomo.log"
        const val WATCHER_LOG = "/data/local/tmp/mihomo_watcher.log"
        const val HOME_SSIDS = "/data/adb/mihomo/conf/home_ssids.conf"
        const val CRON_FILE = "/data/adb/cron/root"
    }

    suspend fun getStatus(): MihomoStatus {
        val statusResult = shell.exec("sh $MIHOMO_SERVICE status")
        val isRunning = statusResult.output.contains("[OK] running")
        val pid = if (isRunning) {
            Regex("pid=(\\d+)").find(statusResult.output)?.groupValues?.get(1)?.toIntOrNull()
        } else null

        val profile = detectCurrentProfile()

        val healthResult = shell.exec("sh $MIHOMO_SERVICE health")
        val isHealthy = healthResult.output.contains("[HEALTH] ok")

        return MihomoStatus(isRunning, pid, profile, isHealthy)
    }

    private suspend fun detectCurrentProfile(): Profile {
        val result = shell.exec("head -5 '$CONFIG_YAML'")
        return if (result.output.contains("direct", ignoreCase = true)) Profile.DIRECT else Profile.PROXY
    }

    suspend fun start(): ShellResult = shell.exec("sh $MIHOMO_SERVICE start")
    suspend fun stop(): ShellResult = shell.exec("sh $MIHOMO_SERVICE stop")
    suspend fun restart(): ShellResult = shell.exec("sh $MIHOMO_SERVICE restart")

    suspend fun switchToProxy(): ShellResult {
        val copyResult = shell.copyFile(CONFIG_PROXY, CONFIG_YAML)
        return if (copyResult.isSuccess) restart() else copyResult
    }

    suspend fun switchToDirect(): ShellResult {
        val copyResult = shell.copyFile(CONFIG_DIRECT, CONFIG_YAML)
        return if (copyResult.isSuccess) restart() else copyResult
    }

    suspend fun validateConfig(configPath: String): ValidationResult {
        val result = shell.exec("$MIHOMO_BIN -t -d '$CONF_DIR' -f '$configPath' 2>&1")
        return if (result.exitCode == 0) {
            ValidationResult(true, "配置验证通过")
        } else {
            ValidationResult(false, result.output.ifBlank { result.error })
        }
    }

    suspend fun validateProxyConfig(): ValidationResult = validateConfig(CONFIG_PROXY)

    suspend fun readProxyConfig(): String {
        val result = shell.readFile(CONFIG_PROXY)
        return if (result.isSuccess) result.output else ""
    }

    suspend fun saveProxyConfig(content: String): ShellResult = shell.writeFile(CONFIG_PROXY, content)

    suspend fun readHomeSSIDs(): List<String> {
        val result = shell.readFile(HOME_SSIDS)
        return if (result.isSuccess) {
            result.output.lines().filter { it.isNotBlank() }
        } else emptyList()
    }

    suspend fun saveHomeSSIDs(ssids: List<String>): ShellResult =
        shell.writeFile(HOME_SSIDS, ssids.joinToString("\n"))

    suspend fun readWatcherLog(lines: Int = 100): String {
        val result = shell.exec("tail -$lines '$WATCHER_LOG'")
        return if (result.isSuccess) result.output else ""
    }

    suspend fun readMihomoLog(lines: Int = 100): String {
        val result = shell.exec("tail -$lines '$MIHOMO_LOG'")
        return if (result.isSuccess) result.output else ""
    }
}
