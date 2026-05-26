package com.devson.nvplayer.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.devson.nvplayer.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileTransferOps {

    data class MoveResult(
        val destinationPath: String,
        val wasDirectMove: Boolean
    )

    fun isFileConflict(context: Context, destFolder: String, fileName: String): Boolean {
        val extDir = android.os.Environment.getExternalStorageDirectory()
        val destDir = File(extDir, destFolder)
        val destFile = File(destDir, fileName)
        if (destFile.exists()) return true

        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DATA} = ?"
        val selectionArgs = arrayOf(destFile.absolutePath)
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.count > 0) return true
            }
        } catch (e: Exception) {
            Log.e("FileTransferOps", "Failed to query conflict in MediaStore: ${e.localizedMessage}")
        }
        return false
    }

    private fun deleteFileFromMediaStore(context: Context, path: String) {
        try {
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            context.contentResolver.delete(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                selection,
                arrayOf(path)
            )
        } catch (e: Exception) {
            Log.e("FileTransferOps", "Failed to delete from MediaStore: ${e.localizedMessage}")
        }
    }

    fun tryDirectMove(source: File, dest: File): Boolean {
        return try {
            source.renameTo(dest)
        } catch (e: Exception) {
            Log.e("FileTransferOps", "Direct move failed: ${e.localizedMessage}")
            false
        }
    }

    fun triggerMediaScanFast(context: Context, newFilePath: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(newFilePath),
                arrayOf("video/*"),
                null
            )
        } catch (e: Exception) {
            Log.e("FileTransferOps", "Media scan trigger failed: ${e.localizedMessage}")
        }
    }

    suspend fun moveVideoScoped(
        context: Context,
        sourceVideo: VideoItem,
        destRelativePath: String,
        overwrite: Boolean = false,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<MoveResult> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(sourceVideo.path)
            val extDir = android.os.Environment.getExternalStorageDirectory()
            val destDir = File(extDir, destRelativePath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            val destFile = File(destDir, sourceFile.name)

            // If source and destination paths are identical, do nothing (success)
            if (sourceFile.absolutePath == destFile.absolutePath) {
                onProgress(100)
                return@withContext Result.success(MoveResult(destFile.absolutePath, true))
            }

            if (destFile.exists() || isFileConflict(context, destRelativePath, sourceFile.name)) {
                if (overwrite) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    deleteFileFromMediaStore(context, destFile.absolutePath)
                } else {
                    throw Exception("File already exists at destination")
                }
            }

            // Android 10+: Try updating MediaStore RELATIVE_PATH directly (O(1) move)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        val formattedPath = if (destRelativePath.endsWith("/")) destRelativePath else "$destRelativePath/"
                        put(MediaStore.Video.Media.RELATIVE_PATH, formattedPath)
                    }
                    val updated = context.contentResolver.update(sourceVideo.uri, values, null, null)
                    if (updated > 0) {
                        var newPath = ""
                        context.contentResolver.query(sourceVideo.uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                newPath = cursor.getString(0) ?: ""
                            }
                        }
                        if (newPath.isNotEmpty()) {
                            triggerMediaScanFast(context, newPath)
                            triggerMediaScanFast(context, sourceVideo.path)
                            onProgress(100)
                            return@withContext Result.success(MoveResult(newPath, true))
                        }
                    }
                } catch (e: Exception) {
                    Log.d("FileTransferOps", "MediaStore direct RELATIVE_PATH update move failed: ${e.localizedMessage}")
                }
            }

            if (tryDirectMove(sourceFile, destFile)) {
                triggerMediaScanFast(context, destFile.absolutePath)
                try {
                    context.contentResolver.delete(sourceVideo.uri, null, null)
                } catch (e: Exception) {
                    Log.w("FileTransferOps", "Failed to delete source URI from MediaStore: ${e.localizedMessage}")
                }
                triggerMediaScanFast(context, sourceFile.absolutePath)
                onProgress(100)
                MoveResult(destFile.absolutePath, true)
            } else {
                val copyResult = copyVideoScoped(context, sourceVideo, destRelativePath, overwrite, onProgress)
                val destPath = copyResult.getOrThrow()
                
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // Pre-Q: delete directly, then remove MediaStore row
                    if (sourceFile.exists()) {
                        sourceFile.delete()
                    }
                    try {
                        context.contentResolver.delete(sourceVideo.uri, null, null)
                    } catch (e: Exception) {
                        Log.w("FileTransferOps", "Failed to delete source URI: ${e.localizedMessage}")
                    }
                    triggerMediaScanFast(context, sourceFile.absolutePath)
                    MoveResult(destPath, true)
                } else {
                    // Q+: cannot delete directly, so we delegate deletion to the UI/VM
                    MoveResult(destPath, false)
                }
            }
        }
    }

    suspend fun copyVideoScoped(
        context: Context,
        sourceVideo: VideoItem,
        destRelativePath: String,
        overwrite: Boolean = false,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(sourceVideo.path)
            val extDir = android.os.Environment.getExternalStorageDirectory()
            val destDir = File(extDir, destRelativePath)
            val destFile = File(destDir, sourceFile.name)

            // If source and destination paths are identical, do nothing (success)
            if (sourceFile.absolutePath == destFile.absolutePath) {
                onProgress(100)
                return@withContext Result.success(destFile.absolutePath)
            }

            if (destFile.exists() || isFileConflict(context, destRelativePath, sourceFile.name)) {
                if (overwrite) {
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    deleteFileFromMediaStore(context, destFile.absolutePath)
                } else {
                    throw Exception("File already exists at destination")
                }
            }

            val resolver = context.contentResolver
            val extension = sourceFile.extension.lowercase()
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: resolver.getType(sourceVideo.uri)
                ?: "video/mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    val formattedPath = if (destRelativePath.endsWith("/")) destRelativePath else "$destRelativePath/"
                    put(MediaStore.Video.Media.RELATIVE_PATH, formattedPath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val destUri = resolver.insert(collection, contentValues)
                    ?: throw Exception("Failed to insert media item in MediaStore")

                try {
                    resolver.openFileDescriptor(sourceVideo.uri, "r")?.use { srcPfd ->
                        FileInputStream(srcPfd.fileDescriptor).channel.use { srcChannel ->
                            resolver.openFileDescriptor(destUri, "w")?.use { destPfd ->
                                FileOutputStream(destPfd.fileDescriptor).channel.use { destChannel ->
                                    val size = srcChannel.size()
                                    var transferred = 0L
                                    while (transferred < size) {
                                        val count = srcChannel.transferTo(transferred, size - transferred, destChannel)
                                        if (count <= 0) break
                                        transferred += count
                                        if (size > 0) {
                                            val progress = ((transferred * 100) / size).toInt()
                                            onProgress(progress.coerceIn(0, 100))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val updatedValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(destUri, updatedValues, null, null)

                    var absolutePath = ""
                    val projection = arrayOf(MediaStore.Video.Media.DATA)
                    resolver.query(destUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                            absolutePath = cursor.getString(columnIndex) ?: ""
                        }
                    }

                    if (absolutePath.isEmpty()) {
                        absolutePath = destUri.toString()
                    }

                    triggerMediaScanFast(context, absolutePath)
                    absolutePath
                } catch (e: Exception) {
                    resolver.delete(destUri, null, null)
                    throw e
                }
            } else {
                // Pre-Q (legacy storage API)
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }

                FileInputStream(sourceFile).channel.use { srcChannel ->
                    FileOutputStream(destFile).channel.use { destChannel ->
                        val size = srcChannel.size()
                        var transferred = 0L
                        while (transferred < size) {
                            val count = srcChannel.transferTo(transferred, size - transferred, destChannel)
                            if (count <= 0) break
                            transferred += count
                            if (size > 0) {
                                val progress = ((transferred * 100) / size).toInt()
                                onProgress(progress.coerceIn(0, 100))
                            }
                        }
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Video.Media.DATA, destFile.absolutePath)
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                triggerMediaScanFast(context, destFile.absolutePath)
                destFile.absolutePath
            }
        }
    }

    suspend fun copyVideoToTreeUri(
        context: Context,
        sourceVideo: VideoItem,
        destTreeUri: Uri,
        overwrite: Boolean = false,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val rootDoc = DocumentFile.fromTreeUri(context, destTreeUri)
                ?: throw Exception("Failed to get DocumentFile from Tree Uri")

            val fileName = File(sourceVideo.path).name
            val existingFile = rootDoc.findFile(fileName)
            if (existingFile != null) {
                if (overwrite) {
                    existingFile.delete()
                } else {
                    throw Exception("File already exists at destination")
                }
            }

            val extension = File(sourceVideo.path).extension.lowercase()
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: resolver.getType(sourceVideo.uri)
                ?: "video/mp4"
            val newFile = rootDoc.createFile(mimeType, fileName)
                ?: throw Exception("Failed to create file in destination directory")

            val destFileUri = newFile.uri

            resolver.openFileDescriptor(sourceVideo.uri, "r")?.use { srcPfd ->
                FileInputStream(srcPfd.fileDescriptor).channel.use { srcChannel ->
                    resolver.openFileDescriptor(destFileUri, "w")?.use { destPfd ->
                        FileOutputStream(destPfd.fileDescriptor).channel.use { destChannel ->
                            val size = srcChannel.size()
                            var transferred = 0L
                            while (transferred < size) {
                                val count = srcChannel.transferTo(transferred, size - transferred, destChannel)
                                if (count <= 0) break
                                transferred += count
                                if (size > 0) {
                                    val progress = ((transferred * 100) / size).toInt()
                                    onProgress(progress.coerceIn(0, 100))
                                }
                            }
                        }
                    }
                }
            }

            destFileUri.path?.let { path ->
                if (destFileUri.scheme == "file") {
                    triggerMediaScanFast(context, path)
                }
            }

            destFileUri
        }
    }
}
