package com.healthassistant.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthassistant.data.repository.HealthRepository
import com.healthassistant.ui.dashboard.DashboardScreen
import com.healthassistant.ui.data.DataScreen
import com.healthassistant.ui.settings.SettingsScreen

/** 底部导航栏项目 */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Dashboard : BottomNavItem("dashboard", "仪表盘", Icons.Default.Dashboard)
    data object Data : BottomNavItem("data", "数据", Icons.Default.BarChart)
    data object Settings : BottomNavItem("settings", "设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(repository: HealthRepository) {
    val navController = rememberNavController()
    val items = listOf(BottomNavItem.Dashboard, BottomNavItem.Data, BottomNavItem.Settings)
    var selectedMetric by remember { mutableStateOf("glucose") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            if (currentDestination?.route != item.route) {
                                if (item is BottomNavItem.Dashboard) {
                                    // 返回仪表盘：直接弹出到根
                                    navController.popBackStack(
                                        BottomNavItem.Dashboard.route,
                                        inclusive = false,
                                    )
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(BottomNavItem.Dashboard.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(
                    repository = repository,
                    onNavigateToData = { metric ->
                        selectedMetric = metric
                        navController.navigate(BottomNavItem.Data.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(BottomNavItem.Data.route) {
                DataScreen(repository = repository, metric = selectedMetric)
            }
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(repository = repository)
            }
        }
    }
}
