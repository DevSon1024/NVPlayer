package com.devson.nvplayer.util

import android.content.Context
import android.util.Log
import com.devson.nvplayer.data.repository.AmbientBlurStyle
import java.io.File
import java.io.FileOutputStream

object ShaderHelper {

    fun copyAssetShader(context: Context, shaderFileName: String): String? {
        return try {
            val destFile = File(context.filesDir, shaderFileName)
            context.assets.open("shaders/$shaderFileName").use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Log.d("ShaderHelper", "Copied shader $shaderFileName to ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("ShaderHelper", "Failed to copy shader $shaderFileName from assets", e)
            val fallback = File(context.filesDir, shaderFileName)
            if (fallback.exists()) fallback.absolutePath else null
        }
    }
}
