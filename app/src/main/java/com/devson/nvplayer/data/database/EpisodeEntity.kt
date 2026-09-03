package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["seasonId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["seasonId"]),
        Index(value = ["fileUri"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seasonId: Long,
    val episodeNumber: Int,
    val title: String? = null,
    val fileUri: String,
    val durationMillis: Long = 0L,
    val lastPlaybackPosition: Long = 0L,
    val isWatched: Boolean = false,
    val introStart: Long? = null,
    val introEnd: Long? = null
)
