package com.devson.nvplayer.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a distinct folder mapped via MediaStore.
 */
@Immutable
data class VideoFolder(
    val id: String,
    val name: String,
    // val videoCount: Int
)

fun VideoFolder.getSectionLabel(): String {
    val firstChar = name.trim().firstOrNull()?.uppercaseChar()
    return if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
}