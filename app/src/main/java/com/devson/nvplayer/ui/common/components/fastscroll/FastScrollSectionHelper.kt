package com.devson.nvplayer.ui.common.components.fastscroll

import com.devson.nvplayer.domain.model.SortField
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.ui.screens.videolist.state.ExplorerItem
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FastScrollSectionHelper {

    private val monthYearFormat by lazy {
        SimpleDateFormat("MMM yyyy", Locale.getDefault())
    }

    fun getVideoSectionLabel(video: Video?, sortField: SortField): String {
        if (video == null) return ""
        return when (sortField) {
            SortField.TITLE -> {
                val firstChar = video.title.trim().firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar.isLetter()) {
                    firstChar.toString()
                } else {
                    "#"
                }
            }
            SortField.DATE -> {
                formatTimestamp(video.dateAdded)
            }
            SortField.PLAYED_TIME -> {
                val playedAt = video.lastPlayedAt ?: video.playedTime ?: 0L
                if (playedAt > 0L) {
                    formatTimestamp(playedAt)
                } else {
                    "Unplayed"
                }
            }
            SortField.LENGTH -> {
                formatDuration(video.duration)
            }
            SortField.SIZE -> {
                formatSize(video.size)
            }
        }
    }

    fun getFolderSectionLabel(
        folder: VideoFolder?,
        videos: List<Video>?,
        sortField: SortField
    ): String {
        if (folder == null) return ""
        return when (sortField) {
            SortField.TITLE -> {
                val firstChar = folder.name.trim().firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar.isLetter()) {
                    firstChar.toString()
                } else {
                    "#"
                }
            }
            SortField.DATE -> {
                val maxDate = videos?.maxOfOrNull { it.dateAdded } ?: 0L
                formatTimestamp(maxDate)
            }
            SortField.PLAYED_TIME -> {
                val maxPlayed = videos?.maxOfOrNull { it.lastPlayedAt ?: it.playedTime ?: 0L } ?: 0L
                if (maxPlayed > 0L) formatTimestamp(maxPlayed) else "Unplayed"
            }
            SortField.LENGTH -> {
                val totalDuration = videos?.sumOf { it.duration } ?: 0L
                formatDuration(totalDuration)
            }
            SortField.SIZE -> {
                val totalSize = videos?.sumOf { it.size } ?: 0L
                formatSize(totalSize)
            }
        }
    }

    fun getExplorerSectionLabel(
        item: ExplorerItem?,
        folderVideos: List<Video>?,
        sortField: SortField
    ): String {
        return when (item) {
            is ExplorerItem.FolderItem -> getFolderSectionLabel(item.folder, folderVideos, sortField)
            is ExplorerItem.VideoItem -> getVideoSectionLabel(item.video, sortField)
            null -> ""
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        if (timeMs <= 0L) return "Unknown"
        return try {
            val ms = if (timeMs.toString().length < 13) timeMs * 1000L else timeMs
            monthYearFormat.format(Date(ms))
        } catch (e: Exception) {
            ""
        }
    }
}
