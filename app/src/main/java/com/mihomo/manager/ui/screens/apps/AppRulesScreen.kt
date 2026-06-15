package com.mihomo.manager.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.manager.data.repository.AppInfo
import com.mihomo.manager.data.repository.AppRepository
import com.mihomo.manager.data.repository.ConfigRepository
import com.mihomo.manager.data.shell.ShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppRule(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val target: String,
    val isWorkProfile: Boolean,
)

data class AppRulesUiState(
    val isLoading: Boolean = false,
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val existingRules: List<AppRule> = emptyList(),
    val proxyGroups: List<String> = emptyList(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val hasWorkProfile: Boolean = false,
    val hasRootAccess: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class AppRulesViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val appRepository: AppRepository,
    private val shell: ShellExecutor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppRulesUiState())
    val uiState: StateFlow<AppRulesUiState> = _uiState
    private var searchJob: Job? = null

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                if (appRepository.isCacheLoaded) {
                    loadWithApps(appRepository.getCachedApps(_uiState.value.showSystemApps))
                } else {
                    val hasRoot = shell.requestRoot()
                    if (!hasRoot) {
                        _uiState.update { it.copy(isLoading = false, hasRootAccess = false) }
                        return@launch
                    }
                    val apps = appRepository.getAllApps(_uiState.value.showSystemApps)
                    loadWithApps(apps)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun loadWithApps(apps: List<AppInfo>) {
        val rawConfig = configRepository.readProxyConfigRaw()
        val proxyGroups = configRepository.getProxyGroupNames(rawConfig)
        val existingRules = parseExistingRules(rawConfig, apps)
        val query = _uiState.value.searchQuery
        val filtered = if (query.isBlank()) apps else {
            val q = query.lowercase()
            apps.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        _uiState.update {
            it.copy(
                isLoading = false, apps = apps, filteredApps = filtered,
                existingRules = existingRules, proxyGroups = proxyGroups,
                hasRootAccess = true, hasWorkProfile = appRepository.getCachedHasWorkProfile(),
            )
        }
    }

    private fun parseExistingRules(configContent: String, apps: List<AppInfo>): List<AppRule> {
        return configContent.lines()
            .map { it.trim().removePrefix("- ") }
            .filter { it.startsWith("UID,") }
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 3) return@mapNotNull null
                val uid = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
                val target = parts[2].trim()
                val app = apps.find { it.uid == uid }
                AppRule(
                    packageName = app?.packageName ?: "uid:$uid",
                    appName = app?.appName ?: "UID $uid",
                    uid = uid, target = target,
                    isWorkProfile = app?.isWorkProfile ?: false,
                )
            }
    }

    fun searchApps(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val apps = _uiState.value.apps
            val q = query.lowercase()
            val filtered = if (q.isBlank()) apps else
                apps.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            _uiState.update { it.copy(filteredApps = filtered) }
        }
    }

    fun addAppRule(app: AppInfo, target: String) {
        viewModelScope.launch {
            try {
                var content = configRepository.readProxyConfigRaw()
                content = configRepository.addUidRule(content, app.uid, target)
                val validation = configRepository.validateConfigContent(content)
                if (validation.isValid) {
                    configRepository.applyConfig(content)
                    _uiState.update { it.copy(successMessage = "已添加 ${app.appName} 的规则") }
                    loadData()
                } else {
                    _uiState.update { it.copy(errorMessage = "验证失败: ${validation.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun removeAppRule(rule: AppRule) {
        viewModelScope.launch {
            try {
                var content = configRepository.readProxyConfigRaw()
                val lines = content.lines().toMutableList()
                val index = lines.indexOfFirst { it.trim().removePrefix("- ").startsWith("UID,${rule.uid},") }
                if (index >= 0) {
                    lines.removeAt(index)
                    content = lines.joinToString("\n")
                    val validation = configRepository.validateConfigContent(content)
                    if (validation.isValid) {
                        configRepository.applyConfig(content)
                        _uiState.update { it.copy(successMessage = "已删除 ${rule.appName} 的规则") }
                        loadData()
                    } else {
                        _uiState.update { it.copy(errorMessage = "验证失败: ${validation.message}") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun toggleSystemApps() {
        _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
        loadData()
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppRulesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App 规则") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSystemApps() }) {
                        Icon(
                            if (uiState.showSystemApps) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "系统应用",
                        )
                    }
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.searchApps(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("搜索应用...") },
                singleLine = true,
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.hasRootAccess || uiState.apps.isNotEmpty()) {
                if (uiState.existingRules.isNotEmpty()) {
                    Text("已配置的应用规则", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    LazyColumn(Modifier.weight(0.4f)) {
                        items(uiState.existingRules) { rule ->
                            ExistingRuleItem(rule, onDelete = { viewModel.removeAppRule(rule) })
                        }
                    }
                    HorizontalDivider()
                }
                Text("选择应用添加规则", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (uiState.hasWorkProfile) {
                    Text("包含工作资料 (Work Profile) 应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(uiState.filteredApps) { app ->
                        val hasRule = uiState.existingRules.any { it.uid == app.uid }
                        AppListItem(app, hasRule) {
                            selectedApp = app
                            showAddDialog = true
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text("需要 Root 权限", style = MaterialTheme.typography.titleMedium)
                        Text("请授予应用 Root 权限后重试", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { viewModel.loadData() }) { Text("重试") }
                    }
                }
            }
        }

        if (showAddDialog && selectedApp != null) {
            AddRuleDialog(
                app = selectedApp,
                proxyGroups = uiState.proxyGroups,
                onDismiss = { showAddDialog = false; selectedApp = null },
                onConfirm = { app, target ->
                    viewModel.addAppRule(app, target)
                    showAddDialog = false; selectedApp = null
                },
            )
        }

        uiState.errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearMessages() },
                title = { Text("错误") },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { viewModel.clearMessages() }) { Text("确定") } },
            )
        }
    }
}

@Composable
private fun ExistingRuleItem(rule: AppRule, onDelete: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(if (rule.isWorkProfile) "(Work) ${rule.appName}" else rule.appName)
        },
        supportingContent = {
            Text("UID:${rule.uid} → ${rule.target}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(
                if (rule.isWorkProfile) Icons.Default.Work else Icons.Default.Android,
                contentDescription = null,
                tint = if (rule.isWorkProfile) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        },
    )
}

@Composable
private fun AppListItem(app: AppInfo, hasRule: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(app.displayName) },
        supportingContent = {
            Text("UID:${app.uid} | ${app.packageName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(
                if (app.isWorkProfile) Icons.Default.Work else Icons.Default.Android,
                contentDescription = null,
                tint = if (app.isWorkProfile) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (hasRule) Icon(Icons.Default.Check, contentDescription = "已配置", tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.fillMaxWidth().let { m -> if (!hasRule) m then Modifier else m }.also {  },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    app: AppInfo?,
    proxyGroups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (AppInfo, String) -> Unit,
) {
    var selectedTarget by remember { mutableStateOf(proxyGroups.firstOrNull() ?: "DIRECT") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (app != null) {
                    Text("应用: ${app.appName}", style = MaterialTheme.typography.bodyMedium)
                    Text("UID: ${app.uid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedTarget,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        proxyGroups.forEach { group ->
                            DropdownMenuItem(text = { Text(group) }, onClick = { selectedTarget = group; expanded = false })
                        }
                    }
                }
                Text("将使用 UID 规则匹配此应用的所有流量",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { app?.let { onConfirm(it, selectedTarget) } }, enabled = app != null) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
