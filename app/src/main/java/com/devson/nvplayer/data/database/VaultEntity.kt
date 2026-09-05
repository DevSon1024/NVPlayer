package com.devson.nvplayer.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_media",
    indices = [
        Index(value = ["vaultPath"], unique = true)
    ]
)
data class VaultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val originalUri: String,
    val vaultPath: String,
    val thumbnailPath: String? = null,
    val fileSize: Long,
    val durationMs: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlaybackPosition: Long = 0L
)
