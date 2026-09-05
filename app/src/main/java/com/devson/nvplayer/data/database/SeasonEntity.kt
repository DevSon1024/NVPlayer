package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["seriesId"])
    ]
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val seriesId: Long,
    val seasonNumber: Int
)
