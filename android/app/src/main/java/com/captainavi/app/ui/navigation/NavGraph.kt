package com.captainavi.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.captainavi.app.ui.screens.dashboard.DashboardScreen
import com.captainavi.app.ui.screens.map.OfflineMapScreen
import com.captainavi.app.ui.screens.settings.SettingsScreen
import com.captainavi.app.ui.screens.tides.MarineDataScreen
import com.captainavi.app.ui.screens.trips.CatchLogScreen
import com.captainavi.app.ui.screens.trips.TripHistoryScreen
import com.captainavi.app.ui.screens.waypoints.WaypointsScreen
import com.captainavi.app.service.SavedTraceState
import com.captainavi.app.ui.theme.MarineTheme

@Composable
fun CaptainAviNavigation() {
    val navController = rememberNavController()
    val colors = MarineTheme.colors
    // Footer includes Catch as a full page (no modal). Six tabs on purpose.
    val items = listOf(
        Screen.Dashboard,
        Screen.Map,
        Screen.Catch,
        Screen.Tides,
        Screen.Waypoints,
        Screen.History,
    )

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                tonalElevation = 0.dp,
                shadowElevation = 16.dp
            ) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth().height(74.dp),
                    containerColor = colors.surface,
                    tonalElevation = 0.dp
                ) {
                    items.forEach { screen ->
                        val isSelected = currentRoute == screen.route ||
                            (currentRoute == Screen.Settings.route && screen == Screen.Dashboard)
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.textPrimary,
                                unselectedIconColor = colors.textMuted,
                                unselectedTextColor = colors.textMuted,
                                indicatorColor = colors.accent.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Map.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToMap = { navController.navigate(Screen.Map.route) },
                    onNavigateToTides = { navController.navigate(Screen.Tides.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToCatch = { navController.navigate(Screen.Catch.route) },
                )
            }
            composable(Screen.Map.route) {
                OfflineMapScreen()
            }
            composable(Screen.Catch.route) {
                CatchLogScreen(
                    onStartTripHint = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Screen.Tides.route) {
                MarineDataScreen(
                    onOpenMap = { navController.navigate(Screen.Map.route) },
                )
            }
            composable(Screen.Waypoints.route) {
                WaypointsScreen()
            }
            composable(Screen.History.route) {
                TripHistoryScreen(
                    onLoadTrace = { tripId ->
                        SavedTraceState.load(tripId)
                        navController.navigate(Screen.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLogCatch = {
                        navController.navigate(Screen.Catch.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
