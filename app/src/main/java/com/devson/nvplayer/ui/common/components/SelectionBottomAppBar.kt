package com.devson.nvplayer.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewSettings

@Composable
fun SelectionBottomAppBar(
    selectedFolders: Set<VideoFolder>,
    videosByFolder: Map<VideoFolder, List<Video>>,
    viewSettings: ViewSettings,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    var showShareWarningDialog by remember { mutableStateOf(false) }
    val totalFiles = remember(selectedFolders, videosByFolder) {
        selectedFolders.sumOf { folder -> (videosByFolder[folder] ?: emptyList()).size }
    }

    if (showShareWarningDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShareWarningDialog = false },
            title = {
                Text(
                    text = "Share Folder Files",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Warning: You are sending $totalFiles file(s) at once across the selected folder(s). Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showShareWarningDialog = false
                        onShare()
                    }
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showShareWarningDialog = false }
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move
                ActionColumn(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    label = "Move",
                    onClick = onMove,
                    modifier = Modifier.weight(1f)
                )
                // Copy
                ActionColumn(
                    icon = Icons.Filled.ContentCopy,
                    label = "Copy",
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                )
                // Delete
                ActionColumn(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
                // Rename
                if (selectedFolders.size == 1) {
                    ActionColumn(
                        icon = Icons.Filled.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = onRename,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                // Share (with file count warning)
                ActionColumn(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = {
                        if (totalFiles > 1) {
                            showShareWarningDialog = true
                        } else {
                            onShare()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionColumn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
