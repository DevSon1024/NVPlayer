package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["fileUri"], unique = true)
    ]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val year: Int? = null,
    val fileUri: String,
    val durationMillis: Long = 0L,
    val lastPlaybackPosition: Long = 0L,
    val isWatched: Boolean = false
)
