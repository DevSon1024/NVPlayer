package com.devson.nvplayer.data.security

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultImportResult(
    val entity: VaultEntity,
    val pendingDeleteUri: Uri? = null
)

class VaultFileManager(
    private val context: Context,
    private val vaultDao: VaultDao
) {
    // 256-bit AES Master Key derived deterministically for Vault encryption
    private val aesKey: SecretKeySpec by lazy {
        val seed = ("com.devson.nvplayer_VaultMasterAES256Key_NosvedPlayer_SecureStorage").toByteArray(Charsets.UTF_8)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = sha.digest(seed)
        SecretKeySpec(keyBytes, "AES")
    }

    val vaultDirectory: File by lazy {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val nomedia = File(vaultDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        val legacyInternal = File(context.filesDir, "vault_secure_media")
        if (legacyInternal.exists() && legacyInternal.isDirectory) {
            legacyInternal.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "vlt") {
                    val dest = File(vaultDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                    }
                    file.delete()
                }
            }
        }
        vaultDir
    }

    val thumbsDirectory: File by lazy {
        val dir = File(vaultDirectory, ".thumbs")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    val tempPlaybackDirectory: File by lazy {
        val dir = File(context.cacheDir, "vault_playback_temp")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    suspend fun importVideoToVault(
        sourceUri: Uri,
        title: String,
        durationMs: Long = 0L
    ): Result<VaultImportResult> = withContext(Dispatchers.IO) {
        try {
            val fileId = UUID.randomUUID().toString()
            val destFile = File(vaultDirectory, "$fileId.vlt")

            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)

            val inputStream: InputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for URI: $sourceUri"))

            val cleanTitle = title.ifBlank { "Protected Video" }
            val titleBytes = cleanTitle.toByteArray(Charsets.UTF_8)
            val dateAdded = System.currentTimeMillis()

            inputStream.use { input ->
                FileOutputStream(destFile).use { fos ->
                    val dos = DataOutputStream(fos)
                    dos.write(iv)
                    dos.write(HEADER_MAGIC)
                    dos.writeInt(titleBytes.size)
                    dos.write(titleBytes)
                    dos.writeLong(durationMs)
                    dos.writeLong(0L)
                    dos.writeLong(dateAdded)
                    dos.flush()

                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, aesKey, IvParameterSpec(iv))

                    CipherOutputStream(fos, cipher).use { cos ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            cos.write(buffer, 0, bytesRead)
                        }
                        cos.flush()
                    }
                }
            }

            val tempPlayback = getPlaybackFileInternal(destFile, fileId)
            val thumbPath = generateAndSaveThumbnail(tempPlayback, fileId)
            val finalDuration = if (durationMs > 0) {
                durationMs
            } else {
                extractDuration(tempPlayback)
            }
            tempPlayback.delete()

            val entity = VaultEntity(
                title = cleanTitle,
                originalUri = sourceUri.toString(),
                vaultPath = destFile.absolutePath,
                thumbnailPath = thumbPath,
                fileSize = destFile.length(),
                durationMs = finalDuration,
                dateAdded = dateAdded
            )

            val insertedId = vaultDao.insert(entity)
            val pendingDeleteUri = removeOriginalSourceFile(sourceUri)

            Result.success(VaultImportResult(entity.copy(id = insertedId), pendingDeleteUri))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rebuildDatabaseFromStorage(): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        try {
            val vltFiles = vaultDirectory.listFiles { file -> file.extension == "vlt" } ?: return@withContext 0
            val existingEntities = vaultDao.getAllVaultMedia().associateBy { File(it.vaultPath).name }

            for (file in vltFiles) {
                if (existingEntities.containsKey(file.name)) {
                    continue
                }

                val metadata = parseVaultFileHeader(file)
                val fileId = file.nameWithoutExtension
                val existingThumb = File(thumbsDirectory, "$fileId.jpg")
                val thumbPath = if (existingThumb.exists()) {
                    existingThumb.absolutePath
                } else {
                    try {
                        val tempPlay = getPlaybackFileInternal(file, fileId)
                        val tp = generateAndSaveThumbnail(tempPlay, fileId)
                        tempPlay.delete()
                        tp
                    } catch (_: Exception) {
                        null
                    }
                }

                val entity = VaultEntity(
                    title = metadata?.title ?: file.nameWithoutExtension,
                    originalUri = "",
                    vaultPath = file.absolutePath,
                    thumbnailPath = thumbPath,
                    fileSize = file.length(),
                    durationMs = metadata?.durationMs ?: 0L,
                    dateAdded = metadata?.dateAdded ?: file.lastModified()
                )

                vaultDao.insert(entity)
                restoredCount++
            }
        } catch (_: Exception) {}
        restoredCount
    }

    private data class VaultHeaderMetadata(
        val title: String,
        val durationMs: Long,
        val fileSize: Long,
        val dateAdded: Long
    )

    private fun parseVaultFileHeader(file: File): VaultHeaderMetadata? {
        return try {
            FileInputStream(file).use { fis ->
                val dis = DataInputStream(fis)
                val iv = ByteArray(16)
                if (dis.read(iv) != 16) return null

                val magic = ByteArray(8)
                if (dis.read(magic) != 8) return null
                if (!magic.contentEquals(HEADER_MAGIC)) return null

                val titleLen = dis.readInt()
                if (titleLen <= 0 || titleLen > 2048) return null
                val titleBytes = ByteArray(titleLen)
                dis.readFully(titleBytes)
                val title = String(titleBytes, Charsets.UTF_8)

                val durationMs = dis.readLong()
                val fileSize = dis.readLong()
                val dateAdded = dis.readLong()

                VaultHeaderMetadata(
                    title = title,
                    durationMs = durationMs,
                    fileSize = fileSize,
                    dateAdded = dateAdded
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun removeOriginalSourceFile(sourceUri: Uri): Uri? {
        var successfullyDeleted = false

        try {
            if (DocumentsContract.isDocumentUri(context, sourceUri)) {
                successfullyDeleted = DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
            }
        } catch (_: Exception) {}

        if (!successfullyDeleted) {
            try {
                val deletedRows = context.contentResolver.delete(sourceUri, null, null)
                if (deletedRows > 0) successfullyDeleted = true
            } catch (_: Exception) {}
        }

        var originalPath: String? = null
        try {
            val cursor = context.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    originalPath = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                }
            }
        } catch (_: Exception) {}

        if (originalPath != null) {
            try {
                val file = File(originalPath)
                if (file.exists()) {
                    if (file.delete()) {
                        successfullyDeleted = true
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(originalPath),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }

        if (successfullyDeleted) {
            try {
                context.contentResolver.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null)
            } catch (_: Exception) {}
            return null
        }

        return resolveMediaStoreUri(sourceUri, originalPath)
    }

    private fun resolveMediaStoreUri(sourceUri: Uri, knownPath: String?): Uri? {
        if (sourceUri.authority == "media") {
            return sourceUri
        }
        val pathToQuery = knownPath ?: try {
            var p: String? = null
            context.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    p = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                }
            }
            p
        } catch (_: Exception) { null }

        if (pathToQuery != null) {
            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.DATA} = ?",
                    arrayOf(pathToQuery),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        return ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun getPlaybackFile(vaultEntity: VaultEntity): File {
        val vaultFile = File(vaultEntity.vaultPath)
        val fileId = vaultFile.nameWithoutExtension
        return getPlaybackFileInternal(vaultFile, fileId)
    }

    private fun getPlaybackFileInternal(vaultFile: File, fileId: String): File {
        val tempFile = File(tempPlaybackDirectory, "$fileId.mp4")
        if (tempFile.exists() && tempFile.length() > 0) {
            return tempFile
        }

        val partFile = File(tempPlaybackDirectory, "$fileId.mp4.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        try {
            FileInputStream(vaultFile).buffered(256 * 1024).use { fis ->
                val dis = DataInputStream(fis)
                val iv = ByteArray(16)
                dis.readFully(iv)

                val magic = ByteArray(8)
                dis.readFully(magic)

                if (magic.contentEquals(HEADER_MAGIC)) {
                    val titleLen = dis.readInt()
                    if (titleLen in 1..2048) {
                        val titleBytes = ByteArray(titleLen)
                        dis.readFully(titleBytes)
                    }
                    dis.readLong()
                    dis.readLong()
                    dis.readLong()
                } else {
                    fis.close()
                    FileInputStream(vaultFile).buffered(256 * 1024).use { legacyFis ->
                        legacyFis.skip(16)
                        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))
                        CipherInputStream(legacyFis, cipher).use { cis ->
                            FileOutputStream(partFile).buffered(256 * 1024).use { fos ->
                                val buffer = ByteArray(256 * 1024)
                                var read: Int
                                while (cis.read(buffer).also { read = it } != -1) {
                                    fos.write(buffer, 0, read)
                                }
                                fos.flush()
                            }
                        }
                    }
                    if (partFile.renameTo(tempFile)) {
                        return tempFile
                    } else {
                        return partFile
                    }
                }

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(iv))

                CipherInputStream(dis, cipher).use { cis ->
                    FileOutputStream(partFile).buffered(256 * 1024).use { fos ->
                        val buffer = ByteArray(256 * 1024)
                        var read: Int
                        while (cis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                        fos.flush()
                    }
                }
            }

            if (partFile.renameTo(tempFile)) {
                return tempFile
            } else {
                return partFile
            }
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            throw e
        }
    }

    suspend fun restoreVideoFromVault(
        vaultEntity: VaultEntity,
        destinationDirectory: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(vaultEntity.vaultPath)
            if (!vaultFile.exists()) {
                return@withContext Result.failure(Exception("Vault file not found: ${vaultEntity.vaultPath}"))
            }

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
            }

            var destFileName = vaultEntity.title
            if (!destFileName.contains('.')) {
                destFileName = "$destFileName.mp4"
            }
            val restoredFile = File(destinationDirectory, destFileName)

            val tempPlay = getPlaybackFileInternal(vaultFile, vaultFile.nameWithoutExtension)
            tempPlay.copyTo(restoredFile, overwrite = true)
            tempPlay.delete()

            vaultFile.delete()
            vaultEntity.thumbnailPath?.let { File(it).delete() }
            vaultDao.delete(vaultEntity)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(restoredFile.absolutePath),
                null,
                null
            )

            Result.success(restoredFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanently(vaultEntity: VaultEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(vaultEntity.vaultPath)
            if (vaultFile.exists()) {
                vaultFile.delete()
            }
            val tempPlayback = File(tempPlaybackDirectory, "${vaultFile.nameWithoutExtension}.mp4")
            if (tempPlayback.exists()) tempPlayback.delete()

            vaultEntity.thumbnailPath?.let {
                val thumbFile = File(it)
                if (thumbFile.exists()) thumbFile.delete()
            }
            vaultDao.delete(vaultEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cleanPlaybackTemp() {
        try {
            tempPlaybackDirectory.listFiles()?.forEach { file ->
                if (file.name != ".nomedia") {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun generateAndSaveThumbnail(videoFile: File, fileId: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                val thumbFile = File(thumbsDirectory, "$fileId.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractDuration(videoFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durString?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    companion object {
        private val HEADER_MAGIC = "NSDVLT01".toByteArray(Charsets.US_ASCII)
    }
}
