package com.mihomo.manager.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mihomo.manager.data.shell.MihomoController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            PathItem("配置目录", MihomoController.CONF_DIR)
            PathItem("代理配置", MihomoController.CONFIG_PROXY)
            PathItem("直连配置", MihomoController.CONFIG_DIRECT)
            PathItem("活动配置", MihomoController.CONFIG_YAML)
            PathItem("Mihomo 二进制", MihomoController.MIHOMO_BIN)
            PathItem("服务脚本", MihomoController.MIHOMO_SERVICE)
            PathItem("Mihomo 日志", MihomoController.MIHOMO_LOG)
            PathItem("Watcher 日志", MihomoController.WATCHER_LOG)
            PathItem("WiFi 白名单", MihomoController.HOME_SSIDS)
            PathItem("Cron 文件", MihomoController.CRON_FILE)
        }
    }
}

@Composable
private fun PathItem(label: String, path: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
