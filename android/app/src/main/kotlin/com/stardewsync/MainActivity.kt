package com.stardewsync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.stardewsync.data.prefs.AppPreferences
import com.stardewsync.service.FileAccessService
import com.stardewsync.ui.navigation.AppNavGraph
import com.stardewsync.ui.theme.StardewSyncTheme

class MainActivity : ComponentActivity() {

    lateinit var fileAccessService: FileAccessService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPreferences(applicationContext)
        fileAccessService = FileAccessService(applicationContext, prefs)
        fileAccessService.onActivityCreated(this)

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        fileAccessService.manageBackend.handleActivityResult(requestCode, resultCode, data)
    }
}
