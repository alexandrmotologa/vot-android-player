package com.vot.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vot.player.export.ExportState

@Composable
fun ExportProgressDialog(
    exportState: ExportState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (exportState !is ExportState.Downloading && exportState !is ExportState.Muxing) {
                onDismiss()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = "Export Video")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (exportState) {
                    is ExportState.Idle -> {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Starting export...")
                    }
                    is ExportState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(exportState.status, style = MaterialTheme.typography.bodyMedium)
                        Text("${exportState.progressPercent}%", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    is ExportState.Muxing -> {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(exportState.status, style = MaterialTheme.typography.bodyMedium)
                    }
                    is ExportState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Export Complete!", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Saved to: ${exportState.filePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                    is ExportState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Export Failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(exportState.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (exportState is ExportState.Success || exportState is ExportState.Error) {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (exportState is ExportState.Downloading || exportState is ExportState.Muxing) {
                TextButton(onClick = onDismiss) {
                    Text("Run in background")
                }
            }
        }
    )
}
