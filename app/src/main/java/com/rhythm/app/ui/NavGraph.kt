package com.rhythm.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rhythm.app.ui.screens.HomeScreen
import com.rhythm.app.ui.screens.ModelScreen
import com.rhythm.app.ui.screens.RhythmScreen
import com.rhythm.app.ui.screens.SettingsScreen

sealed class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Dest("home", "Home", Icons.Filled.Home)
    data object Rhythm : Dest("rhythm", "Rhythm", Icons.Filled.ShowChart)
    data object Model : Dest("model", "Model", Icons.Filled.AutoGraph)
    data object Settings : Dest("settings", "Settings", Icons.Filled.Settings)
}

private val screens = listOf(Dest.Home, Dest.Rhythm, Dest.Model, Dest.Settings)

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val vm: RhythmViewModel = viewModel(factory = RhythmViewModel.factory(LocalContext.current.applicationContext as android.app.Application))

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { s ->
                    val selected = current?.hierarchy?.any { it.route == s.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(s.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Dest.Home.route,
            modifier = Modifier.fillMaxSize().padding(pad)
        ) {
            composable(Dest.Home.route) { HomeScreen(vm) }
            composable(Dest.Rhythm.route) { RhythmScreen(vm) }
            composable(Dest.Model.route) { ModelScreen(vm) }
            composable(Dest.Settings.route) { SettingsScreen(vm) }
        }
    }
}
