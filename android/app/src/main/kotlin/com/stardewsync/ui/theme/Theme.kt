package com.stardewsync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StardewGreen = Color(0xFF5B7C3E)
private val StardewGreenDark = Color(0xFF3D5529)
private val StardewGreenContainer = Color(0xFFD4E8B8)

private val LightColorScheme = lightColorScheme(
    primary = StardewGreen,
    onPrimary = Color.White,
    primaryContainer = StardewGreenContainer,
    onPrimaryContainer = StardewGreenDark,
)

@Composable
fun StardewSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
