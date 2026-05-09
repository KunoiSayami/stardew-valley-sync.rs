package com.stardewsync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService
import com.stardewsync.ui.navigation.AppNavGraph
import com.stardewsync.ui.theme.StardewSyncTheme

class MainActivity : ComponentActivity() {

    lateinit var fileAccessService: FileAccessService

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        fileAccessService.manageBackend.onPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPreferences(applicationContext)
        fileAccessService = FileAccessService(applicationContext, prefs)
        fileAccessService.onActivityCreated(manageStorageLauncher)

        setContent {
            StardewSyncTheme {
                AppNavGraph(prefs, fileAccessService)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileAccessService.onActivityDestroyed()
    }
}
