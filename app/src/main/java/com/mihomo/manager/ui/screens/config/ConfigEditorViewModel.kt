package com.mihomo.manager.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.manager.data.model.MihomoConfig
import com.mihomo.manager.data.model.ParsedRule
import com.mihomo.manager.data.model.Proxy
import com.mihomo.manager.data.model.ProxyGroup
import com.mihomo.manager.data.repository.AppRepository
import com.mihomo.manager.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigEditorUiState(
    val isLoading: Boolean = false,
    val configContent: String = "",
    val originalContent: String = "",
    val parsedConfig: MihomoConfig? = null,
    val isValidated: Boolean = false,
    val isValid: Boolean = false,
    val validationMessage: String? = null,
    val errorMessage: String? = null,
    val hasChanges: Boolean = false,
    val proxyGroups: List<String> = emptyList(),
    val uidToAppName: Map<Int, String> = emptyMap(),
)

@HiltViewModel
class ConfigEditorViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val appRepository: AppRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConfigEditorUiState())
    val uiState: StateFlow<ConfigEditorUiState> = _uiState

    init { loadConfig() }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val content = configRepository.readProxyConfigRaw()
            val parsed = configRepository.parseConfig(content)
            val groups = configRepository.getProxyGroupNames(content)
            _uiState.update {
                it.copy(
                    isLoading = false, configContent = content, originalContent = content,
                    parsedConfig = parsed, proxyGroups = groups, hasChanges = false,
                    isValidated = false, validationMessage = null,
                    uidToAppName = appRepository.uidToAppNameMap,
                )
            }
        }
    }

    fun updateConfig(content: String) {
        val parsed = configRepository.parseConfig(content)
        _uiState.update {
            it.copy(
                configContent = content, parsedConfig = parsed,
                hasChanges = content != it.originalContent,
                isValidated = false, validationMessage = null,
            )
        }
    }

    fun updateParsedConfig(config: MihomoConfig) {
        val content = configRepository.serializeConfig(config)
        updateConfig(content)
    }

    fun addProxy(proxy: Proxy) {
        val config = _uiState.value.parsedConfig ?: return
        updateParsedConfig(config.copy(proxies = config.proxies + proxy))
    }

    fun updateProxy(index: Int, proxy: Proxy) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.proxies.toMutableList()
        if (index in list.indices) { list[index] = proxy; updateParsedConfig(config.copy(proxies = list)) }
    }

    fun deleteProxy(index: Int) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.proxies.toMutableList()
        if (index in list.indices) { list.removeAt(index); updateParsedConfig(config.copy(proxies = list)) }
    }

    fun addProxyGroup(group: ProxyGroup) {
        val config = _uiState.value.parsedConfig ?: return
        updateParsedConfig(config.copy(proxyGroups = config.proxyGroups + group))
    }

    fun updateProxyGroup(index: Int, group: ProxyGroup) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.proxyGroups.toMutableList()
        if (index in list.indices) { list[index] = group; updateParsedConfig(config.copy(proxyGroups = list)) }
    }

    fun deleteProxyGroup(index: Int) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.proxyGroups.toMutableList()
        if (index in list.indices) { list.removeAt(index); updateParsedConfig(config.copy(proxyGroups = list)) }
    }

    fun addRule(ruleString: String) {
        val config = _uiState.value.parsedConfig ?: return
        updateParsedConfig(config.copy(rules = listOf(ruleString) + config.rules))
    }

    fun updateRule(index: Int, ruleString: String) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.rules.toMutableList()
        if (index in list.indices) { list[index] = ruleString; updateParsedConfig(config.copy(rules = list)) }
    }

    fun deleteRule(index: Int) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.rules.toMutableList()
        if (index in list.indices) { list.removeAt(index); updateParsedConfig(config.copy(rules = list)) }
    }

    fun moveRule(from: Int, to: Int) {
        val config = _uiState.value.parsedConfig ?: return
        val list = config.rules.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            updateParsedConfig(config.copy(rules = list))
        }
    }

    fun validateConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(validationMessage = "正在验证配置...") }
            val result = configRepository.validateConfigContent(_uiState.value.configContent)
            _uiState.update {
                it.copy(isValidated = true, isValid = result.isValid, validationMessage = result.message)
            }
        }
    }

    fun saveAndApplyConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, validationMessage = "正在应用配置...") }
            val result = configRepository.applyConfig(_uiState.value.configContent)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false, originalContent = it.configContent, hasChanges = false,
                        validationMessage = "配置已应用并重启服务",
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "应用失败: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun resetChanges() {
        _uiState.update {
            val parsed = configRepository.parseConfig(it.originalContent)
            it.copy(configContent = it.originalContent, parsedConfig = parsed, hasChanges = false, isValidated = false, validationMessage = null)
        }
    }

    fun backupConfig() {
        viewModelScope.launch {
            val result = configRepository.backupConfig()
            _uiState.update {
                if (result.isSuccess) it.copy(validationMessage = "已备份到: ${result.getOrNull()}")
                else it.copy(errorMessage = "备份失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
}
