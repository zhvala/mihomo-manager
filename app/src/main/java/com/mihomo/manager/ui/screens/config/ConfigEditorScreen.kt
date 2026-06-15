package com.mihomo.manager.ui.screens.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mihomo.manager.data.model.DnsConfig
import com.mihomo.manager.data.model.MihomoConfig
import com.mihomo.manager.data.model.Proxy
import com.mihomo.manager.data.model.ProxyGroup
import com.mihomo.manager.data.model.TunConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConfigEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadConfig() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置编辑") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { viewModel.saveAndApplyConfig() }, enabled = !uiState.isLoading && uiState.isValidated && uiState.isValid) {
                        Icon(Icons.Default.Save, "保存")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { viewModel.validateConfig() }, enabled = uiState.configContent.isNotBlank() && !uiState.isLoading) { Text("验证") }
                    Button(onClick = { viewModel.saveAndApplyConfig() }, enabled = !uiState.isLoading && uiState.isValidated && uiState.isValid) { Text("保存应用") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("表单") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("YAML") })
            }
            uiState.validationMessage?.let { msg ->
                Card(
                    Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = if (uiState.isValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (uiState.isValid) Icons.Default.CheckCircle else Icons.Default.Error, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            when (selectedTab) {
                0 -> FormEditor(uiState, viewModel)
                1 -> TextEditor(uiState.configContent, { viewModel.updateConfig(it) }, uiState.isLoading)
            }
        }
    }
}

@Composable
private fun FormEditor(uiState: ConfigEditorUiState, viewModel: ConfigEditorViewModel) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val config = uiState.parsedConfig
    if (config == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Text("无法解析配置文件")
                Text("请使用 YAML 标签页直接编辑")
            }
        }
        return
    }
    var expandedSection by remember { mutableStateOf("basic") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ExpandableSection("基础设置", expandedSection == "basic", { expandedSection = if (expandedSection == "basic") "" else "basic" }) { BasicSettingsForm(config) { viewModel.updateParsedConfig(it) } } }
        item { ExpandableSection("TUN 设置", expandedSection == "tun", { expandedSection = if (expandedSection == "tun") "" else "tun" }) { TunSettingsForm(config.tun) { viewModel.updateParsedConfig(config.copy(tun = it)) } } }
        item { ExpandableSection("DNS 设置", expandedSection == "dns", { expandedSection = if (expandedSection == "dns") "" else "dns" }) { DnsSettingsForm(config.dns) { viewModel.updateParsedConfig(config.copy(dns = it)) } } }
        item { ExpandableSection("代理 (${config.proxies.size})", expandedSection == "proxies", { expandedSection = if (expandedSection == "proxies") "" else "proxies" }) { ProxiesList(config.proxies, viewModel::addProxy, viewModel::updateProxy, viewModel::deleteProxy) } }
        item { ExpandableSection("代理组 (${config.proxyGroups.size})", expandedSection == "groups", { expandedSection = if (expandedSection == "groups") "" else "groups" }) { ProxyGroupsList(config.proxyGroups, config.proxies.map { it.name }, viewModel::addProxyGroup, viewModel::updateProxyGroup, viewModel::deleteProxyGroup) } }
        item { ExpandableSection("规则 (${config.rules.size})", expandedSection == "rules", { expandedSection = if (expandedSection == "rules") "" else "rules" }) { RulesList(config.rules, config.proxies.map { it.name }, config.proxyGroups.map { it.name }, uiState.uidToAppName, viewModel::addRule, viewModel::updateRule, viewModel::deleteRule, viewModel::moveRule) } }
    }
}

@Composable
private fun ExpandableSection(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            ListItem(
                headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
                trailingContent = {
                    IconButton(onClick = onToggle) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "展开") }
                },
                modifier = Modifier.clickable(onClick = onToggle),
            )
            if (expanded) { HorizontalDivider(); Column(Modifier.padding(16.dp)) { content() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicSettingsForm(config: MihomoConfig, onUpdate: (MihomoConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = config.mixedPort.toString(), onValueChange = { it.toIntOrNull()?.let { v -> onUpdate(config.copy(mixedPort = v)) } }, label = { Text("端口") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("允许局域网连接"); Switch(checked = config.allowLan, onCheckedChange = { onUpdate(config.copy(allowLan = it)) })
        }
        var modeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = it }) {
            OutlinedTextField(value = config.mode, onValueChange = {}, readOnly = true, label = { Text("模式") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
            ExposedDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                listOf("rule", "global", "direct").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onUpdate(config.copy(mode = it)); modeExpanded = false }) }
            }
        }
        var logExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = logExpanded, onExpandedChange = { logExpanded = it }) {
            OutlinedTextField(value = config.logLevel, onValueChange = {}, readOnly = true, label = { Text("日志级别") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(logExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
            ExposedDropdownMenu(expanded = logExpanded, onDismissRequest = { logExpanded = false }) {
                listOf("info", "warning", "error", "debug", "silent").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onUpdate(config.copy(logLevel = it)); logExpanded = false }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunSettingsForm(tunConfig: TunConfig?, onUpdate: (TunConfig) -> Unit) {
    val config = tunConfig ?: TunConfig()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("启用 TUN"); Switch(checked = config.enable, onCheckedChange = { onUpdate(config.copy(enable = it)) })
        }
        if (config.enable) {
            var stackExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = stackExpanded, onExpandedChange = { stackExpanded = it }) {
                OutlinedTextField(value = config.stack, onValueChange = {}, readOnly = true, label = { Text("协议栈") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stackExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(expanded = stackExpanded, onDismissRequest = { stackExpanded = false }) {
                    listOf("system", "gvisor", "mixed").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onUpdate(config.copy(stack = it)); stackExpanded = false }) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("自动路由"); Switch(checked = config.autoRoute, onCheckedChange = { onUpdate(config.copy(autoRoute = it)) })
            }
        }
    }
}

@Composable
private fun DnsSettingsForm(dnsConfig: DnsConfig?, onUpdate: (DnsConfig) -> Unit) {
    val config = dnsConfig ?: DnsConfig()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("启用 DNS"); Switch(checked = config.enable, onCheckedChange = { onUpdate(config.copy(enable = it)) })
        }
        if (config.enable) {
            OutlinedTextField(value = config.listen, onValueChange = { onUpdate(config.copy(listen = it)) }, label = { Text("监听地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = config.nameserver.joinToString("\n"), onValueChange = { onUpdate(config.copy(nameserver = it.lines().filter { l -> l.isNotBlank() })) }, label = { Text("DNS 服务器") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        }
    }
}

@Composable
private fun ProxiesList(proxies: List<Proxy>, onAdd: (Proxy) -> Unit, onEdit: (Int, Proxy) -> Unit, onDelete: (Int) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<Pair<Int, Proxy>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${proxies.size} 个代理", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { showAddDialog = true }) { Text("添加代理") }
        }
        proxies.forEachIndexed { index, proxy ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(proxy.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${proxy.type} | ${proxy.server}:${proxy.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { editingProxy = index to proxy }) { Icon(Icons.Default.Edit, "编辑", Modifier.size(20.dp)) }
                        IconButton(onClick = { onDelete(index) }) { Icon(Icons.Default.Delete, "删除", Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
    if (showAddDialog) EditProxyDialog(null, { showAddDialog = false }) { onAdd(it); showAddDialog = false }
    editingProxy?.let { (idx, _) -> EditProxyDialog(editingProxy?.second, { editingProxy = null }) { onEdit(idx, it); editingProxy = null } }
}

@Composable
private fun ProxyGroupsList(groups: List<ProxyGroup>, availableProxies: List<String>, onAdd: (ProxyGroup) -> Unit, onEdit: (Int, ProxyGroup) -> Unit, onDelete: (Int) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Pair<Int, ProxyGroup>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${groups.size} 个代理组", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { showAddDialog = true }) { Text("添加代理组") }
        }
        groups.forEachIndexed { index, group ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${group.type} | ${group.proxies.size} 个代理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { editingGroup = index to group }) { Icon(Icons.Default.Edit, "编辑", Modifier.size(20.dp)) }
                        IconButton(onClick = { onDelete(index) }) { Icon(Icons.Default.Delete, "删除", Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
    if (showAddDialog) EditProxyGroupDialog(null, availableProxies, groups.map { it.name }, { showAddDialog = false }) { onAdd(it); showAddDialog = false }
    editingGroup?.let { (idx, g) -> EditProxyGroupDialog(g, availableProxies, groups.map { it.name }, { editingGroup = null }) { onEdit(idx, it); editingGroup = null } }
}

@Composable
private fun RulesList(
    rules: List<String>, proxies: List<String>, proxyGroups: List<String>,
    uidToAppName: Map<Int, String>,
    onAdd: (String) -> Unit, onEdit: (Int, String) -> Unit, onDelete: (Int) -> Unit, onMove: (Int, Int) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Pair<Int, String>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${rules.size} 条规则", style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { showAddDialog = true }) { Text("添加规则") }
        }
        LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(rules) { index, rule ->
                val parts = rule.split(",").map { it.trim() }
                val displayText = buildString {
                    append(parts.getOrElse(0) { "" })
                    if (parts.size > 1) append(" ${parts[1]}")
                    if (parts.size > 2) append(" → ${parts[2]}")
                    if (parts[0] == "UID" && parts.size > 1) {
                        val uid = parts[1].toIntOrNull()
                        uid?.let { uidToAppName[it]?.let { name -> append(" ($name)") } }
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DragHandle, "拖拽", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(displayText, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { editingRule = index to rule }, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "编辑", Modifier.size(16.dp)) }
                        IconButton(onClick = { onDelete(index) }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "删除", Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
    if (showAddDialog) EditRuleDialog(null, proxies, proxyGroups, { showAddDialog = false }) { onAdd(it); showAddDialog = false }
    editingRule?.let { (idx, r) -> EditRuleDialog(r, proxies, proxyGroups, { editingRule = null }) { onEdit(idx, it); editingRule = null } }
}

@Composable
private fun TextEditor(content: String, onContentChange: (String) -> Unit, isLoading: Boolean) {
    if (isLoading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    OutlinedTextField(
        value = content, onValueChange = onContentChange,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProxyDialog(initialProxy: Proxy?, onDismiss: () -> Unit, onConfirm: (Proxy) -> Unit) {
    var name by remember { mutableStateOf(initialProxy?.name ?: "") }
    var type by remember { mutableStateOf(initialProxy?.type ?: "ss") }
    var server by remember { mutableStateOf(initialProxy?.server ?: "") }
    var port by remember { mutableStateOf(initialProxy?.port?.toString() ?: "443") }
    var password by remember { mutableStateOf(initialProxy?.password ?: "") }
    var cipher by remember { mutableStateOf(initialProxy?.cipher ?: "auto") }
    var uuid by remember { mutableStateOf(initialProxy?.uuid ?: "") }
    var network by remember { mutableStateOf(initialProxy?.network ?: "") }
    var sni by remember { mutableStateOf(initialProxy?.sni ?: "") }
    var udp by remember { mutableStateOf(initialProxy?.udp ?: true) }
    var skipCert by remember { mutableStateOf(initialProxy?.skipCertVerify ?: false) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialProxy == null) "添加代理" else "编辑代理") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = type, onValueChange = {}, readOnly = true, label = { Text("类型") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("ss", "vmess", "vless", "trojan", "hysteria2", "tuic", "socks5", "http").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { type = it; typeExpanded = false }) }
                    }
                }
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("服务器") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("端口") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (type in listOf("ss", "trojan", "hysteria2", "tuic", "socks5", "http"))
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth())
                if (type in listOf("vmess", "vless", "tuic"))
                    OutlinedTextField(value = uuid, onValueChange = { uuid = it }, label = { Text("UUID") }, modifier = Modifier.fillMaxWidth())
                if (type in listOf("vmess", "vless"))
                    OutlinedTextField(value = network, onValueChange = { network = it }, label = { Text("网络") }, modifier = Modifier.fillMaxWidth())
                if (type in listOf("vmess", "vless", "trojan"))
                    OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("SNI") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("UDP"); Switch(checked = udp, onCheckedChange = { udp = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("跳过证书验证"); Switch(checked = skipCert, onCheckedChange = { skipCert = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(Proxy(name, type, server, port.toIntOrNull() ?: 443,
                    password = password.ifBlank { null }, cipher = if (type == "ss") cipher else null,
                    sni = sni.ifBlank { null }, skipCertVerify = skipCert, udp = udp,
                    uuid = uuid.ifBlank { null }, network = network.ifBlank { null }))
            }, enabled = name.isNotBlank() && server.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProxyGroupDialog(initialGroup: ProxyGroup?, availableProxies: List<String>, existingGroups: List<String>, onDismiss: () -> Unit, onConfirm: (ProxyGroup) -> Unit) {
    var name by remember { mutableStateOf(initialGroup?.name ?: "") }
    var type by remember { mutableStateOf(initialGroup?.type ?: "select") }
    var selectedProxies by remember { mutableStateOf(initialGroup?.proxies?.toSet() ?: emptySet()) }
    var url by remember { mutableStateOf(initialGroup?.url ?: "http://connectivitycheck.gstatic.com/generate_204") }
    var interval by remember { mutableStateOf(initialGroup?.interval?.toString() ?: "300") }
    var tolerance by remember { mutableStateOf(initialGroup?.tolerance?.toString() ?: "50") }
    var typeExpanded by remember { mutableStateOf(false) }
    val allAvailable = availableProxies + existingGroups.filter { it != initialGroup?.name } + listOf("DIRECT", "REJECT")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialGroup == null) "添加代理组" else "编辑代理组") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = type, onValueChange = {}, readOnly = true, label = { Text("类型") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("select", "url-test", "fallback", "load-balance", "relay").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { type = it; typeExpanded = false }) }
                    }
                }
                Text("选择代理", style = MaterialTheme.typography.labelMedium)
                allAvailable.forEach { proxy ->
                    Row(Modifier.fillMaxWidth().clickable {
                        selectedProxies = if (proxy in selectedProxies) selectedProxies - proxy else selectedProxies + proxy
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = proxy in selectedProxies, onCheckedChange = {
                            selectedProxies = if (it) selectedProxies + proxy else selectedProxies - proxy
                        })
                        Text(proxy, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (type in listOf("url-test", "fallback", "load-balance")) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("健康检查 URL") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = interval, onValueChange = { interval = it }, label = { Text("间隔 (秒)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = tolerance, onValueChange = { tolerance = it }, label = { Text("容差 (ms)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ProxyGroup(name, type, selectedProxies.toList(),
                    url = if (type in listOf("url-test", "fallback", "load-balance")) url else null,
                    interval = if (type in listOf("url-test", "fallback", "load-balance")) interval.toIntOrNull() else null,
                    tolerance = if (type in listOf("url-test", "fallback", "load-balance")) tolerance.toIntOrNull() else null))
            }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRuleDialog(initialRule: String?, proxies: List<String>, proxyGroups: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = initialRule?.split(",")?.map { it.trim() }
    var ruleType by remember { mutableStateOf(parts?.getOrNull(0) ?: "DOMAIN-SUFFIX") }
    var ruleValue by remember { mutableStateOf(if (ruleType == "MATCH") "" else parts?.getOrNull(1) ?: "") }
    var ruleTarget by remember { mutableStateOf(if (ruleType == "MATCH") parts?.getOrNull(1) ?: "DIRECT" else parts?.getOrNull(2) ?: "DIRECT") }
    var typeExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    val targets = listOf("DIRECT", "REJECT") + proxyGroups + proxies

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "添加规则" else "编辑规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = ruleType, onValueChange = {}, readOnly = true, label = { Text("规则类型") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "GEOIP", "GEOSITE", "IP-CIDR", "IP-CIDR6", "UID", "PROCESS-NAME", "MATCH").forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { ruleType = it; typeExpanded = false })
                        }
                    }
                }
                if (ruleType != "MATCH") {
                    OutlinedTextField(value = ruleValue, onValueChange = { ruleValue = it }, label = { Text("值") }, modifier = Modifier.fillMaxWidth())
                }
                ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = it }) {
                    OutlinedTextField(value = ruleTarget, onValueChange = {}, readOnly = true, label = { Text("目标") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(targetExpanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                        targets.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { ruleTarget = it; targetExpanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rule = if (ruleType == "MATCH") "$ruleType,$ruleTarget" else "$ruleType,$ruleValue,$ruleTarget"
                onConfirm(rule)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
