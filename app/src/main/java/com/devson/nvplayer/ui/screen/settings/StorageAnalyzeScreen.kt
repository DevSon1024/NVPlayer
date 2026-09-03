package com.devson.nvplayer.ui.screen.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.ui.screen.videolist.components.video.VideoThumbnail
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.viewmodel.VideoListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

enum class StorageUnitDisplay(val label: String, val shortName: String) {
    GB("Gigabytes (GB)", "GB"),
    MB("Megabytes (MB)", "MB"),
    KB("Kilobytes (KB)", "KB"),
    BYTES("Bytes (B)", "Bytes")
}

data class ExactStorageInfo(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val usedPercentage: Float = 0f,
    val freePercentage: Float = 0f,
    val videoTotalBytes: Long = 0L,
    val videoCount: Int = 0,
    val topFolders: List<FolderStorageItem> = emptyList(),
    val largestVideos: List<Video> = emptyList()
)

data class FolderStorageItem(
    val folderName: String,
    val path: String,
    val totalBytes: Long,
    val videoCount: Int,
    val percentageOfVideoStorage: Float
)

fun formatBytesWithUnit(bytes: Long, unit: StorageUnitDisplay): String {
    val nf = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    return when (unit) {
        StorageUnitDisplay.GB -> "${nf.format(bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))} GB"
        StorageUnitDisplay.MB -> "${nf.format(bytes.toDouble() / (1024.0 * 1024.0))} MB"
        StorageUnitDisplay.KB -> "${nf.format(bytes.toDouble() / 1024.0)} KB"
        StorageUnitDisplay.BYTES -> "$bytes Bytes"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzeScreen(
    onNavigateBack: () -> Unit,
    onPlayVideo: (Uri) -> Unit,
    videoListViewModel: VideoListViewModel
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var selectedUnit by remember { mutableStateOf(StorageUnitDisplay.GB) }
    var storageInfo by remember { mutableStateOf<ExactStorageInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val videosFlat by videoListViewModel.videosFlat.collectAsState()

    LaunchedEffect(videosFlat) {
        isLoading = true
        storageInfo = withContext(Dispatchers.IO) {
            calculateStorageInfo(context, videosFlat)
        }
        isLoading = false
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Storage Analyzer", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (isLoading || storageInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val info = storageInfo ?: ExactStorageInfo()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    StorageOverviewCard(
                        info = info,
                        selectedUnit = selectedUnit,
                        onUnitSelected = { selectedUnit = it }
                    )
                }

                item {
                    Text(
                        text = "Storage by Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(info.topFolders) { folder ->
                    FolderStorageRow(item = folder, unit = selectedUnit)
                }

                item {
                    Text(
                        text = "Largest Videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(info.largestVideos) { video ->
                    LargestVideoRow(
                        video = video,
                        unit = selectedUnit,
                        onPlayVideo = onPlayVideo
                    )
                }
            }
        }
    }
}

private fun calculateStorageInfo(context: Context, videos: List<Video>): ExactStorageInfo {
    val stat = StatFs(Environment.getDataDirectory().path)
    val blockSize = stat.blockSizeLong
    val totalBytes = stat.blockCountLong * blockSize
    val freeBytes = stat.availableBlocksLong * blockSize
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

    val nonHiddenVideos = videos.filter { v ->
        !v.path.split("/").any { it.startsWith(".") }
    }
    val videoTotalBytes = nonHiddenVideos.sumOf { it.size }

    val foldersMap = nonHiddenVideos.groupBy { it.folderName }
    val topFolders = foldersMap.map { (folder, list) ->
        val size = list.sumOf { it.size }
        FolderStorageItem(
            folderName = folder,
            path = list.firstOrNull()?.path?.substringBeforeLast('/') ?: "",
            totalBytes = size,
            videoCount = list.size,
            percentageOfVideoStorage = if (videoTotalBytes > 0) (size.toFloat() / videoTotalBytes.toFloat()) * 100f else 0f
        )
    }.sortedByDescending { it.totalBytes }

    val largestVideos = nonHiddenVideos.sortedByDescending { it.size }.take(10)

    val usedPercentage = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f
    val freePercentage = if (totalBytes > 0) (freeBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f

    return ExactStorageInfo(
        totalBytes = totalBytes,
        freeBytes = freeBytes,
        usedBytes = usedBytes,
        usedPercentage = usedPercentage,
        freePercentage = freePercentage,
        videoTotalBytes = videoTotalBytes,
        videoCount = nonHiddenVideos.size,
        topFolders = topFolders,
        largestVideos = largestVideos
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageOverviewCard(
    info: ExactStorageInfo,
    selectedUnit: StorageUnitDisplay,
    onUnitSelected: (StorageUnitDisplay) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Device Storage Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                StorageUnitDisplay.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = selectedUnit == unit,
                        onClick = { onUnitSelected(unit) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = StorageUnitDisplay.entries.size),
                        label = { Text(unit.shortName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (info.usedPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Used: ${formatBytesWithUnit(info.usedBytes, selectedUnit)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Total: ${formatBytesWithUnit(info.totalBytes, selectedUnit)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Videos Occupy:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatBytesWithUnit(info.videoTotalBytes, selectedUnit)} (${info.videoCount} files)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun FolderStorageRow(
    item: FolderStorageItem,
    unit: StorageUnitDisplay
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.folderName.ifBlank { "Root" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.videoCount} videos • ${formatBytesWithUnit(item.totalBytes, unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "${item.percentageOfVideoStorage.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun LargestVideoRow(
    video: Video,
    unit: StorageUnitDisplay,
    onPlayVideo: (Uri) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayVideo(Uri.parse(video.uri)) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VideoThumbnail(
                uri = video.uri,
                modifier = Modifier
                    .size(68.dp, 48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                showPlayIcon = false
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytesWithUnit(video.size, unit)} • ${formatDuration(video.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
