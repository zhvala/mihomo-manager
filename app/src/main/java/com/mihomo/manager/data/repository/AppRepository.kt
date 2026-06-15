package com.mihomo.manager.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.mihomo.manager.data.shell.ShellExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppRepository"

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val userId: Int,
    val uid: Int,
    val isSystemApp: Boolean,
) {
    val isWorkProfile: Boolean get() = userId == 10
    val displayName: String get() = if (isWorkProfile) "(Work) $appName" else appName
}

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shell: ShellExecutor,
) {
    enum class CacheState { NOT_LOADED, LOADING, LOADED, ERROR }

    private val packageManager: PackageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()

    @Volatile private var cachedThirdPartyApps: List<AppInfo>? = null
    @Volatile private var cachedAllApps: List<AppInfo>? = null
    @Volatile private var cachedUidToAppName: Map<Int, String>? = null
    @Volatile private var cachedHasWorkProfile: Boolean? = null

    private val _cacheState = MutableStateFlow(CacheState.NOT_LOADED)
    val cacheState: StateFlow<CacheState> = _cacheState

    val uidToAppNameMap: Map<Int, String> get() = cachedUidToAppName ?: emptyMap()
    val isCacheLoaded: Boolean get() = _cacheState.value == CacheState.LOADED && cachedAllApps != null

    fun getCachedHasWorkProfile(): Boolean = cachedHasWorkProfile ?: false

    fun getCachedApps(includeSystem: Boolean): List<AppInfo> =
        if (includeSystem) cachedAllApps ?: emptyList() else cachedThirdPartyApps ?: emptyList()

    fun preloadCache() {
        scope.launch { loadCacheIfNeeded() }
    }

    fun refreshCache() {
        scope.launch {
            loadMutex.withLock {
                clearCacheInternal()
                loadCacheInternalUnlocked()
            }
        }
    }

    private fun clearCacheInternal() {
        cachedAllApps = null
        cachedThirdPartyApps = null
        cachedUidToAppName = null
        cachedHasWorkProfile = null
        _cacheState.value = CacheState.NOT_LOADED
    }

    private suspend fun loadCacheIfNeeded() {
        if (_cacheState.value == CacheState.LOADED) return
        loadMutex.withLock {
            if (_cacheState.value == CacheState.LOADED) return
            loadCacheInternalUnlocked()
        }
    }

    private suspend fun loadCacheInternalUnlocked() {
        try {
            _cacheState.value = CacheState.LOADING
            cachedHasWorkProfile = hasWorkProfile()
            val allApps = loadAllAppsInternal(includeSystem = true)
            cachedAllApps = allApps
            cachedThirdPartyApps = allApps.filter { !it.isSystemApp }
            cachedUidToAppName = allApps.associate { it.uid to it.displayName }
            _cacheState.value = CacheState.LOADED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache", e)
            _cacheState.value = CacheState.ERROR
        }
    }

    private suspend fun ensureCacheLoaded() {
        if (_cacheState.value == CacheState.LOADED) return
        loadCacheIfNeeded()
        awaitCacheReady()
    }

    private suspend fun awaitCacheReady() {
        if (_cacheState.value == CacheState.LOADED || _cacheState.value == CacheState.ERROR) return
        _cacheState.first { it == CacheState.LOADED || it == CacheState.ERROR }
    }

    suspend fun getUser0Apps(includeSystem: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val flag = if (includeSystem) "" else "-3 "
            val result = shell.exec("pm list packages ${flag}-U --user 0")
            if (!result.isSuccess) return@withContext getUser0AppsViaPackageManager(includeSystem)
            val regex = Regex("package:(\\S+)\\s+uid:(\\d+)")
            result.output.lines()
                .mapNotNull { regex.find(it) }
                .mapNotNull { match ->
                    val pkg = match.groupValues[1]
                    val uid = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    getAppInfoFromPackage(pkg, 0, uid, includeSystem)
                }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "getUser0Apps failed", e)
            getUser0AppsViaPackageManager(includeSystem)
        }
    }

    private fun getUser0AppsViaPackageManager(includeSystem: Boolean): List<AppInfo> {
        return try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { includeSystem || (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { info ->
                    AppInfo(
                        packageName = info.packageName,
                        appName = packageManager.getApplicationLabel(info).toString(),
                        icon = try { packageManager.getApplicationIcon(info) } catch (_: Exception) { null },
                        userId = 0,
                        uid = info.uid,
                        isSystemApp = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "getUser0AppsViaPackageManager failed", e)
            emptyList()
        }
    }

    private suspend fun getAppInfoFromPackage(
        packageName: String, userId: Int, uid: Int, includeSystem: Boolean,
    ): AppInfo? {
        val appName = getAppLabel(packageName) ?: packageName
        val isSystem = isSystemApp(packageName)
        if (!includeSystem && isSystem) return null
        val icon = try {
            if (userId == 0) packageManager.getApplicationIcon(packageName) else null
        } catch (_: Exception) { null }
        return AppInfo(packageName, appName, icon, userId, uid, isSystem)
    }

    private suspend fun getAppLabel(packageName: String): String? {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(info).toString()
            if (label.isNotBlank() && label != packageName) label else null
        } catch (_: Exception) { null }
    }

    private suspend fun isSystemApp(packageName: String): Boolean {
        val result = shell.exec("pm path $packageName")
        return result.output.contains("/system/")
    }

    suspend fun getUser10Apps(): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!hasWorkProfile()) return@withContext emptyList()
        try {
            val result = shell.exec("pm list packages -3 -U --user 10")
            if (!result.isSuccess) return@withContext emptyList()
            val regex = Regex("package:(\\S+)\\s+uid:(\\d+)")
            result.output.lines()
                .mapNotNull { regex.find(it) }
                .mapNotNull { match ->
                    val pkg = match.groupValues[1]
                    val uid = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    val appName = getAppLabel(pkg) ?: pkg
                    AppInfo(pkg, appName, null, userId = 10, uid = uid, isSystemApp = false)
                }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "getUser10Apps failed", e)
            emptyList()
        }
    }

    suspend fun getAllApps(includeSystem: Boolean): List<AppInfo> {
        ensureCacheLoaded()
        return if (includeSystem) cachedAllApps ?: emptyList() else cachedThirdPartyApps ?: emptyList()
    }

    private suspend fun loadAllAppsInternal(includeSystem: Boolean): List<AppInfo> {
        val user0 = getUser0Apps(includeSystem)
        val user10 = getUser10Apps()
        return user0 + user10
    }

    suspend fun searchApps(query: String, includeSystem: Boolean): List<AppInfo> {
        val apps = getAllApps(includeSystem)
        if (query.isBlank()) return apps
        val q = query.lowercase()
        return apps.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    suspend fun hasWorkProfile(): Boolean {
        val result = shell.exec("pm list users")
        return result.output.contains("UserInfo{10:")
    }
}
