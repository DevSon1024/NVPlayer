package com.devson.nvplayer.data.model

import android.net.Uri

data class FolderItem(
    val name: String,
    val path: String,
    val videoCount: Int,
    val thumbnailUri: Uri?
)
