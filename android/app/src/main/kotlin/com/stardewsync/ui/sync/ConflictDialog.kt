package com.stardewsync.ui.sync

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.util.Date

@Composable
fun ConflictDialog(
    info: ConflictInfo,
    onOverwrite: () -> Unit,
    onCancel: () -> Unit,
) {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val destLabel = if (info.direction == SyncDirection.PULL) "local" else "server"
    val srcLabel = if (info.direction == SyncDirection.PULL) "server" else "local"
    val destMs = if (info.direction == SyncDirection.PULL) info.localMs else info.serverMs
    val srcMs = if (info.direction == SyncDirection.PULL) info.serverMs else info.localMs

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Overwrite newer save?") },
        text = {
            Text(
                "The $destLabel copy of \"${info.slotId}\" is newer " +
                    "(${fmt.format(Date(destMs))}) than the $srcLabel copy " +
                    "(${fmt.format(Date(srcMs))}).\n\nOverwrite the $destLabel copy?"
            )
        },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text("Overwrite", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
