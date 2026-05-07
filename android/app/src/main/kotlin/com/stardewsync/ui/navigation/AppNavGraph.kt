package com.stardewsync.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stardewsync.data.api.ApiClient
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.DiscoveryService
import com.stardewsync.service.FileAccessService
import com.stardewsync.ui.browser.DirectoryBrowserScreen
import com.stardewsync.ui.connect.ServerConnectScreen
import com.stardewsync.ui.fileaccess.FileAccessSettingsScreen
import com.stardewsync.ui.home.HomeScreen
import com.stardewsync.ui.sync.SyncScreen

object AppNavGraph {
    const val ROUTE_HOME = "home"
    const val ROUTE_CONNECT = "connect"
    const val ROUTE_SYNC = "sync/{ip}/{port}/{pin}"
    const val ROUTE_FILE_ACCESS = "file_access"
    const val ROUTE_BROWSER = "browser/{initialPath}"

    fun syncRoute(ip: String, port: String, pin: String) =
        "sync/${Uri.encode(ip)}/${Uri.encode(port)}/${Uri.encode(pin)}"

    fun browserRoute(initialPath: String) = "browser/${Uri.encode(initialPath)}"
}

@Composable
fun AppNavGraph(prefs: AppPreferences, fileAccess: FileAccessService) {
    val navController = rememberNavController()
    val discovery = DiscoveryService(navController.context)

    NavHost(navController = navController, startDestination = AppNavGraph.ROUTE_HOME) {

        composable(AppNavGraph.ROUTE_HOME) {
            HomeScreen(navController = navController, prefs = prefs)
        }

        composable(AppNavGraph.ROUTE_CONNECT) {
            ServerConnectScreen(
                navController = navController,
                prefs = prefs,
                discovery = discovery,
            )
        }

        composable(
            route = AppNavGraph.ROUTE_SYNC,
            arguments = listOf(
                navArgument("ip") { type = NavType.StringType },
                navArgument("port") { type = NavType.StringType },
                navArgument("pin") { type = NavType.StringType },
            ),
        ) { backStack ->
            val ip = backStack.arguments?.getString("ip") ?: ""
            val port = backStack.arguments?.getString("port") ?: "24742"
            val pin = backStack.arguments?.getString("pin") ?: ""
            val api = ApiClient("http://$ip:$port", pin)
            SyncScreen(
                navController = navController,
                api = api,
                fileAccess = fileAccess,
                prefs = prefs,
                ip = ip,
                port = port,
            )
        }

        composable(AppNavGraph.ROUTE_FILE_ACCESS) {
            FileAccessSettingsScreen(navController = navController, fileAccess = fileAccess)
        }

        composable(
            route = AppNavGraph.ROUTE_BROWSER,
            arguments = listOf(navArgument("initialPath") { type = NavType.StringType }),
        ) { backStack ->
            val initialPath = Uri.decode(backStack.arguments?.getString("initialPath") ?: "")
            DirectoryBrowserScreen(
                navController = navController,
                fileAccess = fileAccess,
                initialPath = initialPath,
            )
        }
    }
}
