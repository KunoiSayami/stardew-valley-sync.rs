package com.stardewsync.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.ui.navigation.AppNavGraph

@Composable
fun HomeScreen(navController: NavController, prefs: AppPreferences) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(prefs))
    val destination by vm.destination.collectAsState()

    LaunchedEffect(destination) {
        when (val d = destination) {
            is HomeDestination.Loading -> {}
            is HomeDestination.Connect -> navController.navigate(AppNavGraph.ROUTE_CONNECT) {
                popUpTo(AppNavGraph.ROUTE_HOME) { inclusive = true }
            }
            is HomeDestination.Sync -> navController.navigate(AppNavGraph.syncRoute(d.ip, d.port, d.pin)) {
                popUpTo(AppNavGraph.ROUTE_HOME) { inclusive = true }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
