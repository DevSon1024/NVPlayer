package com.devson.nvplayer.domain.model

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

enum class LayoutMode {
    LIST, GRID
}

enum class ViewMode {
    ALL_FOLDERS, FILES, FOLDERS
}

enum class SortField {
    TITLE, DATE, PLAYED_TIME, STATUS, LENGTH, SIZE, RESOLUTION, PATH, FRAME_RATE, TYPE
}

enum class SortDirection {
    ASCENDING, DESCENDING
}

@Immutable
data class WatchHistory(
    val uri: String,
    val lastPositionMs: Long,
    val lastPlayedAt: Long = 0L,
    val videoTitle: String? = null,
    val durationMs: Long = 0L,
    val fileSize: Long = 0L
)

@Immutable
data class Video(
    val uri: String,
    val title: String,
    val duration: Long,
    val folderName: String,
    val path: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val playedTime: Long? = null,
    val lastPlayedAt: Long? = null,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val dateExpires: Long? = null,
    val thumbnailUri: String? = null
)

fun List<Video>.applySort(field: SortField, direction: SortDirection): List<Video> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.title.lowercase() }
        SortField.DATE -> sortedBy { it.dateAdded }
        SortField.PLAYED_TIME -> sortedBy { it.playedTime ?: 0L }
        SortField.STATUS -> sortedBy { it.playedTime ?: 0L }
        SortField.LENGTH -> sortedBy { it.duration }
        SortField.SIZE -> sortedBy { it.size }
        SortField.RESOLUTION -> sortedBy { it.resolution ?: "" }
        SortField.PATH -> sortedBy { it.path.lowercase() }
        SortField.FRAME_RATE -> sortedBy { it.frameRate ?: 0f }
        SortField.TYPE -> sortedBy { it.title.substringAfterLast(".", "").lowercase() }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}

fun Video.getSectionLabel(sortField: SortField): String {
    return when (sortField) {
        SortField.TITLE -> {
            val firstChar = title.trim().firstOrNull()?.uppercaseChar()
            if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
        }
        SortField.DATE -> {
            val ms = if (dateAdded.toString().length < 13) dateAdded * 1000L else dateAdded
            if (ms <= 0L) {
                "Unknown"
            } else {
                try {
                    java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))
                } catch (_: Exception) {
                    "Date"
                }
            }
        }
        SortField.PLAYED_TIME, SortField.STATUS -> {
            if (playedTime == null || playedTime <= 0L) "Unwatched" else "Played"
        }
        SortField.LENGTH -> {
            val minutes = duration / 60000
            when {
                minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m"
                else -> "< 1m"
            }
        }
        SortField.SIZE -> {
            when {
                size >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
                size >= 1024L * 1024L -> "${size / (1024 * 1024)} MB"
                else -> "< 1 MB"
            }
        }
        SortField.RESOLUTION -> {
            val res = resolution
            if (!res.isNullOrBlank()) {
                val parts = res.split("x")
                if (parts.size == 2) {
                    val height = parts[1].toIntOrNull() ?: 0
                    when {
                        height >= 2160 -> "4K"
                        height >= 1080 -> "1080p"
                        height >= 720 -> "720p"
                        height >= 480 -> "480p"
                        height > 0 -> "${height}p"
                        else -> res
                    }
                } else res
            } else "Unknown"
        }
        SortField.PATH -> folderName.ifBlank { "Videos" }
        SortField.FRAME_RATE -> {
            val fps = frameRate?.roundToInt() ?: 0
            if (fps > 0) "${fps} fps" else "FPS"
        }
        SortField.TYPE -> {
            val ext = title.substringAfterLast(".", "").uppercase()
            if (ext.isNotBlank()) ext else "VIDEO"
        }
    }
}

