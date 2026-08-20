package com.devson.nvplayer.ui.screens.videolist.utils

import com.devson.nvplayer.domain.model.SortDirection
import com.devson.nvplayer.domain.model.SortField
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.WatchHistory

fun List<VideoFolder>.applyFolderSort(
    folderMap: Map<VideoFolder, List<Video>>,
    field: SortField,
    direction: SortDirection,
    historyMap: Map<String, WatchHistory> = emptyMap()
): List<VideoFolder> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.name.lowercase() }
        SortField.DATE -> sortedBy { folder ->
            folderMap[folder]?.maxOfOrNull {
                if (it.dateAdded.toString().length < 13) it.dateAdded * 1000L else it.dateAdded
            } ?: 0L
        }
        SortField.PLAYED_TIME -> sortedBy { folder ->
            folderMap[folder]?.maxOfOrNull {
                historyMap[it.uri]?.lastPlayedAt ?: it.lastPlayedAt ?: it.playedTime ?: 0L
            } ?: 0L
        }
        SortField.LENGTH -> sortedBy { folder -> folderMap[folder]?.sumOf { it.duration } ?: 0L }
        SortField.SIZE -> sortedBy { folder -> folderMap[folder]?.sumOf { it.size } ?: 0L }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}
