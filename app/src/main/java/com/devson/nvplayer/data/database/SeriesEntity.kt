package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["title"])
    ]
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val tmdbId: Long? = null,
    val synopsis: String? = null,
    val posterUri: String? = null
)
