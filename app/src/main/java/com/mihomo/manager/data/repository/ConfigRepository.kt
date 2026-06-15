package com.mihomo.manager.data.repository

import android.util.Log
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.mihomo.manager.data.model.MihomoConfig
import com.mihomo.manager.data.model.ParsedRule
import com.mihomo.manager.data.model.RuleType
import com.mihomo.manager.data.shell.MihomoController
import com.mihomo.manager.data.shell.ShellExecutor
import com.mihomo.manager.data.shell.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConfigRepository"

@Singleton
class ConfigRepository @Inject constructor(
    private val shell: ShellExecutor,
    private val mihomoController: MihomoController,
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
        )
    )

    suspend fun readProxyConfigRaw(): String = mihomoController.readProxyConfig()

    fun parseConfig(content: String): MihomoConfig? {
        return try {
            yaml.decodeFromString(MihomoConfig.serializer(), content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config", e)
            null
        }
    }

    suspend fun parseConfigResult(content: String): Result<MihomoConfig> = withContext(Dispatchers.IO) {
        val config = parseConfig(content)
        if (config != null) Result.success(config) else Result.failure(Exception("Failed to parse config"))
    }

    fun serializeConfig(config: MihomoConfig): String =
        yaml.encodeToString(MihomoConfig.serializer(), config)

    suspend fun saveProxyConfig(content: String): Result<Unit> {
        val result = mihomoController.saveProxyConfig(content)
        return if (result.isSuccess) Result.success(Unit)
        else Result.failure(Exception(result.error))
    }

    suspend fun validateConfig(): ValidationResult = mihomoController.validateProxyConfig()

    suspend fun validateConfigContent(content: String): ValidationResult {
        val tempPath = "${MihomoController.CONF_DIR}/config-temp-validate.yaml"
        shell.writeFile(tempPath, content)
        val result = mihomoController.validateConfig(tempPath)
        shell.exec("rm -f '$tempPath'")
        return result
    }

    suspend fun applyConfig(content: String): Result<Unit> {
        val saveResult = saveProxyConfig(content)
        if (saveResult.isFailure) return saveResult
        val switchResult = mihomoController.switchToProxy()
        return if (switchResult.isSuccess) Result.success(Unit)
        else Result.failure(Exception(switchResult.error))
    }

    fun getProxyGroupNames(content: String): List<String> {
        val config = parseConfig(content) ?: return listOf("DIRECT", "REJECT")
        return config.proxyGroups.map { it.name } + listOf("DIRECT", "REJECT")
    }

    fun addRule(content: String, rule: ParsedRule, position: Int = 0): String {
        val config = parseConfig(content) ?: return content
        val rules = config.rules.toMutableList()
        rules.add(position.coerceIn(0, rules.size), rule.toRuleString())
        return serializeConfig(config.copy(rules = rules))
    }

    fun removeRule(content: String, index: Int): String {
        val config = parseConfig(content) ?: return content
        val rules = config.rules.toMutableList()
        if (index in rules.indices) rules.removeAt(index)
        return serializeConfig(config.copy(rules = rules))
    }

    @Deprecated("Use addUidRule instead")
    fun addProcessRule(content: String, packageName: String, target: String, position: Int = 0): String =
        addRule(content, ParsedRule(RuleType.PROCESS_NAME, packageName, target), position)

    fun addUidRule(content: String, uid: Int, target: String, position: Int = 0): String =
        addRule(content, ParsedRule(RuleType.UID, uid.toString(), target), position)

    suspend fun backupConfig(): Result<String> {
        val timestamp = System.currentTimeMillis()
        val backupPath = "${MihomoController.CONFIG_PROXY}.backup.$timestamp"
        val result = shell.copyFile(MihomoController.CONFIG_PROXY, backupPath)
        return if (result.isSuccess) Result.success(backupPath)
        else Result.failure(Exception(result.error))
    }

    suspend fun getBackupList(): List<String> {
        val files = shell.listDir(MihomoController.CONF_DIR)
        return files.filter { it.startsWith("config-proxy.yaml.backup.") }.sortedDescending()
    }

    suspend fun restoreFromBackup(backupPath: String): Result<Unit> {
        val result = shell.copyFile(backupPath, MihomoController.CONFIG_PROXY)
        return if (result.isSuccess) Result.success(Unit)
        else Result.failure(Exception(result.error))
    }
}
