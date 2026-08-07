package com.devson.nvplayer.ui.screens.videolist.components.selection

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
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
import com.devson.nvplayer.util.TagStatusDialog

@Composable
fun VideoSelectionBottomBar(
    selectedVideos: Set<Video>,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onMarkStatus: (String) -> Unit,
    showTagAndShare: Boolean = true
) {
    var showTagDialog by remember { mutableStateOf(false) }

    if (showTagDialog) {
        TagStatusDialog(
            onDismiss = { showTagDialog = false },
            onConfirm = { status ->
                showTagDialog = false
                onMarkStatus(status)
            }
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
                if (selectedVideos.size == 1) {
                    ActionColumn(
                        icon = Icons.Filled.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = onRename,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                // Share
                if (showTagAndShare) {
                    ActionColumn(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Tagging
                if (showTagAndShare) {
                    ActionColumn(
                        icon = Icons.AutoMirrored.Filled.Label,
                        label = "Tag",
                        onClick = { showTagDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
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
