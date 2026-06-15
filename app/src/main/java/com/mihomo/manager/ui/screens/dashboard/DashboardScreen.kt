package com.mihomo.manager.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mihomo.manager.data.shell.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToConfig: () -> Unit,
    onNavigateToAppRules: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mihomo") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "刷新") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.hasRoot) {
                StatusCard(uiState)
                ModeSwitchCard(uiState.currentProfile, uiState.isLoading, viewModel::switchToProxy, viewModel::switchToDirect)
                AutoSwitchCard(uiState.autoSwitchEnabled, uiState.isLoading, viewModel::toggleAutoSwitch)
                WiFiWhitelistCard(uiState.homeSSIDs, uiState.currentSSID, viewModel::addHomeSSID, viewModel::removeHomeSSID)
                QuickActionsCard(onNavigateToConfig, onNavigateToAppRules, onNavigateToLogs)
            } else {
                RootRequiredCard(viewModel::requestRoot)
            }
        }
    }
}

@Composable
private fun RootRequiredCard(onRequestRoot: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text("需要 Root 权限", style = MaterialTheme.typography.titleMedium)
            Text("此应用需要 Root 权限才能管理 Mihomo", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequestRoot) { Text("请求 Root 权限") }
        }
    }
}

@Composable
private fun StatusCard(uiState: DashboardUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("运行状态", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (uiState.isRunning) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
                    tint = if (uiState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isRunning) "运行中" else "已停止")
                uiState.pid?.let { Text(" (PID: $it)", style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Router, null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.currentProfile == Profile.PROXY) "模式: 代理" else "模式: 直连")
            }
            uiState.currentSSID?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, null)
                    Spacer(Modifier.width(8.dp))
                    Text("WiFi: $it")
                }
            }
        }
    }
}

@Composable
private fun ModeSwitchCard(currentProfile: Profile, isLoading: Boolean, onProxy: () -> Unit, onDirect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("模式切换", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onProxy, Modifier.weight(1f), enabled = !isLoading || currentProfile != Profile.PROXY) {
                    Text("代理模式")
                }
                OutlinedButton(onClick = onDirect, Modifier.weight(1f), enabled = !isLoading || currentProfile != Profile.DIRECT) {
                    Text("直连模式")
                }
            }
        }
    }
}

@Composable
private fun AutoSwitchCard(isEnabled: Boolean, isLoading: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("自动切换", style = MaterialTheme.typography.titleMedium)
                Text("基于 WiFi 白名单自动切换模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = isEnabled, onCheckedChange = { onToggle() }, enabled = !isLoading)
        }
    }
}

@Composable
private fun WiFiWhitelistCard(ssids: List<String>, currentSSID: String?, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("WiFi 白名单 (直连)", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Wifi, "添加")
                }
            }
            if (ssids.isEmpty()) {
                Text("暂无白名单 WiFi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ssids.forEach { ssid ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, null, tint = if (ssid == currentSSID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(ssid)
                            if (ssid == currentSSID) {
                                Spacer(Modifier.width(4.dp))
                                Text("(当前)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { onRemove(ssid) }) { Icon(Icons.Default.Delete, "删除") }
                    }
                }
            }
        }
    }
    if (showAddDialog) {
        AddSSIDDialog(currentSSID, onDismiss = { showAddDialog = false }, onConfirm = { onAdd(it); showAddDialog = false })
    }
}

@Composable
private fun AddSSIDDialog(currentSSID: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var ssid by remember { mutableStateOf(currentSSID ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 WiFi") },
        text = {
            Column {
                OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("WiFi SSID") }, modifier = Modifier.fillMaxWidth())
                if (currentSSID != null) {
                    TextButton(onClick = { ssid = currentSSID }) { Text("使用当前 WiFi: $currentSSID") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(ssid) }, enabled = ssid.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun QuickActionsCard(onConfig: () -> Unit, onAppRules: () -> Unit, onLogs: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("快捷操作", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onConfig, Modifier.weight(1f)) { Text("配置编辑") }
                OutlinedButton(onClick = onAppRules, Modifier.weight(1f)) { Text("应用规则") }
                OutlinedButton(onClick = onLogs, Modifier.weight(1f)) { Text("日志") }
            }
        }
    }
}
