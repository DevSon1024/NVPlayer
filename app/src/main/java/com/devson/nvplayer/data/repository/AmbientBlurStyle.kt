package com.devson.nvplayer.data.repository

import com.devson.nvplayer.player.ambient.AmbientVisualMode

enum class AmbientBlurStyle(
    val key: String,
    val displayName: String,
    val description: String,
    val visualMode: AmbientVisualMode
) {
    GLOW(
        key = "GLOW",
        displayName = "True Ambient Glow",
        description = "Dynamic Fibonacci-spiral ambient light bleed",
        visualMode = AmbientVisualMode.GLOW
    ),
    FRAME_EXTEND(
        key = "FRAME_EXTEND",
        displayName = "Frame Extend",
        description = "Predictive border detail extension into padding",
        visualMode = AmbientVisualMode.FRAME_EXTEND
    );

    companion object {
        fun fromKey(key: String): AmbientBlurStyle {
            return values().firstOrNull { it.key.equals(key, ignoreCase = true) } ?: GLOW
        }
    }
}
