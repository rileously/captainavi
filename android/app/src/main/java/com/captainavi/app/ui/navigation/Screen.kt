package com.captainavi.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Helm", Icons.Default.DirectionsBoat)
    object Map : Screen("map", "Chart", Icons.Default.Map)
    object Tides : Screen("tides", "Marine", Icons.Default.Waves)
    object Waypoints : Screen("waypoints", "Marks", Icons.Default.Place)
    object History : Screen("history", "Log", Icons.Default.History)
    object Settings : Screen("settings", "Config", Icons.Default.Settings)
}
