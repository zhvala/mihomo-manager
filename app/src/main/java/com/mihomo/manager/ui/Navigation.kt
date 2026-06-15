package com.mihomo.manager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mihomo.manager.ui.screens.apps.AppRulesScreen
import com.mihomo.manager.ui.screens.config.ConfigEditorScreen
import com.mihomo.manager.ui.screens.dashboard.DashboardScreen
import com.mihomo.manager.ui.screens.logs.LogViewerScreen
import com.mihomo.manager.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object ConfigEditor : Screen("config_editor")
    data object AppRules : Screen("app_rules")
    data object LogViewer : Screen("log_viewer")
    data object Settings : Screen("settings")
}

@Composable
fun MihomoNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToConfig = { navController.navigate(Screen.ConfigEditor.route) },
                onNavigateToAppRules = { navController.navigate(Screen.AppRules.route) },
                onNavigateToLogs = { navController.navigate(Screen.LogViewer.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.ConfigEditor.route) {
            ConfigEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.AppRules.route) {
            AppRulesScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.LogViewer.route) {
            LogViewerScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
