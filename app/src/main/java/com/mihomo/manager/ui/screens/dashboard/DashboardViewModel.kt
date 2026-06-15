package com.mihomo.manager.ui.screens.dashboard

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.manager.data.shell.CronController
import com.mihomo.manager.data.shell.MihomoController
import com.mihomo.manager.data.shell.Profile
import com.mihomo.manager.data.shell.ShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val hasRoot: Boolean = false,
    val isLoading: Boolean = false,
    val isRunning: Boolean = false,
    val pid: Int? = null,
    val currentProfile: Profile = Profile.PROXY,
    val isHealthy: Boolean = false,
    val autoSwitchEnabled: Boolean = false,
    val homeSSIDs: List<String> = emptyList(),
    val currentSSID: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: ShellExecutor,
    private val mihomoController: MihomoController,
    private val cronController: CronController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init { checkRootAndRefresh() }

    fun checkRootAndRefresh() {
        viewModelScope.launch {
            val hasRoot = shell.hasRootAccess()
            _uiState.update { it.copy(hasRoot = hasRoot) }
            if (hasRoot) refresh()
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            val hasRoot = shell.requestRoot()
            _uiState.update { it.copy(hasRoot = hasRoot) }
            if (hasRoot) refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val status = mihomoController.getStatus()
                val cronEnabled = cronController.isEnabled()
                val ssids = mihomoController.readHomeSSIDs()
                val currentSSID = getCurrentSSID()
                _uiState.update {
                    it.copy(
                        isLoading = false, isRunning = status.isRunning, pid = status.pid,
                        currentProfile = status.currentProfile, isHealthy = status.isHealthy,
                        autoSwitchEnabled = !cronEnabled, homeSSIDs = ssids,
                        currentSSID = currentSSID, errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun switchToProxy() { viewModelScope.launch { mihomoController.switchToProxy(); refresh() } }
    fun switchToDirect() { viewModelScope.launch { mihomoController.switchToDirect(); refresh() } }
    fun toggleAutoSwitch() { viewModelScope.launch { cronController.toggle(); refresh() } }

    fun addHomeSSID(ssid: String) {
        viewModelScope.launch {
            val current = _uiState.value.homeSSIDs.toMutableList()
            if (ssid.isNotBlank() && ssid !in current) {
                current.add(ssid)
                mihomoController.saveHomeSSIDs(current)
                refresh()
            }
        }
    }

    fun removeHomeSSID(ssid: String) {
        viewModelScope.launch {
            val current = _uiState.value.homeSSIDs.toMutableList()
            current.remove(ssid)
            mihomoController.saveHomeSSIDs(current)
            refresh()
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSSID(): String? = try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = wm.connectionInfo.ssid?.removeSurrounding("\"")
        if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) null else ssid
    } catch (_: Exception) { null }
}
