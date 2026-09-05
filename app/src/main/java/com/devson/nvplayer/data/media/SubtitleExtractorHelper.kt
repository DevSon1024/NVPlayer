package com.devson.nvplayer.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.devson.nvplayer.data.model.SubtitleStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.mediaarea.mediainfo.lib.MediaInfo
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext

object SubtitleExtractorHelper {

    private const val TAG = "SubtitleExtractorHelper"

    data class VideoMeta(
        val displayName: String,
        val sizeBytes: Long,
        val formattedSize: String
    )

    fun resolveVideoMetadata(context: Context, uri: Uri): VideoMeta {
        var name = "video"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "video"
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying metadata for $uri", e)
        }

        val formattedSize = when {
            size >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            size > 0 -> "$size B"
            else -> "Unknown size"
        }

        return VideoMeta(
            displayName = name,
            sizeBytes = size,
            formattedSize = formattedSize
        )
    }

    suspend fun inspectTracks(
        context: Context,
        uri: Uri,
        fileName: String
    ): List<SubtitleStreamInfo> = withContext(Dispatchers.IO) {
        // Layer 1: Direct Matroska EBML header inspection (fast, zero memory, discovers ASS/SSA/SRT natively)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (MatroskaSubtitleDemuxer.isMatroskaFile(pfd.fileDescriptor)) {
                    val mkvTracks = MatroskaSubtitleDemuxer.inspectMatroskaTracks(pfd.fileDescriptor)
                    if (mkvTracks.isNotEmpty()) {
                        return@withContext mkvTracks
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Matroska EBML inspection skipped", e)
        }

        val tracks = mutableListOf<SubtitleStreamInfo>()

        // Layer 2: MediaInfo deep inspection
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val fd = pfd.detachFd()
                val mi = MediaInfo()
                try {
                    mi.Open(fd, fileName)
                    val count = mi.Count_Get(MediaInfo.Stream.Text)
                    for (i in 0 until count) {
                        val format = mi.getInfo(MediaInfo.Stream.Text, i, "Format")
                        val codecId = mi.getInfo(MediaInfo.Stream.Text, i, "CodecID")
                        val title = mi.getInfo(MediaInfo.Stream.Text, i, "Title")
                        val langStr = mi.getInfo(MediaInfo.Stream.Text, i, "Language/String")
                        val rawLang = if (langStr.isNotBlank()) langStr else mi.getInfo(MediaInfo.Stream.Text, i, "Language")
                        val idStr = mi.getInfo(MediaInfo.Stream.Text, i, "ID")
                        val trackId = idStr.toIntOrNull() ?: (i + 1)

                        val isImage = isImageBasedFormat(format, codecId)
                        val codecName = when {
                            isImage -> "Image-based (PGS/VobSub) - Cannot export as text"
                            format.contains("ASS", ignoreCase = true) || codecId.contains("ASS", ignoreCase = true) -> "ASS"
                            format.contains("SSA", ignoreCase = true) || codecId.contains("SSA", ignoreCase = true) -> "SSA"
                            format.contains("VTT", ignoreCase = true) || codecId.contains("VTT", ignoreCase = true) -> "VTT"
                            format.contains("Timed Text", ignoreCase = true) || codecId.contains("tx3g", ignoreCase = true) -> "mov_text"
                            else -> "subrip"
                        }

                        val extension = when {
                            isImage -> ""
                            codecName == "ASS" || codecName == "SSA" -> "ass"
                            codecName == "VTT" -> "vtt"
                            else -> "srt"
                        }

                        val displayTitle = when {
                            title.isNotBlank() -> title
                            rawLang.isNotBlank() -> "$rawLang Subtitle"
                            else -> "Subtitle Track #${i + 1}"
                        }

                        val displayLang = if (rawLang.isNotBlank()) rawLang else "und"

                        tracks.add(
                            SubtitleStreamInfo(
                                index = i,
                                trackId = trackId,
                                title = displayTitle,
                                language = displayLang,
                                codecName = codecName,
                                extension = extension
                            )
                        )
                    }
                } finally {
                    try { mi.Close() } catch (_: Exception) {}
                    try { pfd.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaInfo inspection failed, falling back to MediaExtractor", e)
        }

        if (tracks.isNotEmpty()) {
            return@withContext tracks
        }

        // Layer 3: Android MediaExtractor fallback (MP4, etc.)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                    var subIndex = 0
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                        if (isSubtitleMime(mime)) {
                            val lang = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                                format.getString(MediaFormat.KEY_LANGUAGE) ?: "und"
                            } else {
                                "und"
                            }

                            val title = if (format.containsKey("title")) {
                                format.getString("title") ?: "Subtitle Track #${subIndex + 1}"
                            } else {
                                "Subtitle Track #${subIndex + 1}"
                            }

                            val trackId = if (format.containsKey("track-id")) {
                                format.getInteger("track-id")
                            } else {
                                i + 1
                            }

                            val isImage = isImageBasedMime(mime)
                            val codecName = when {
                                isImage -> "Image-based (PGS/VobSub) - Cannot export as text"
                                mime.contains("ass", ignoreCase = true) || mime.contains("ssa", ignoreCase = true) -> "ASS"
                                mime.contains("vtt", ignoreCase = true) -> "VTT"
                                mime.contains("tx3g", ignoreCase = true) -> "mov_text"
                                else -> "subrip"
                            }

                            val extension = when {
                                isImage -> ""
                                codecName == "ASS" -> "ass"
                                codecName == "VTT" -> "vtt"
                                else -> "srt"
                            }

                            tracks.add(
                                SubtitleStreamInfo(
                                    index = subIndex,
                                    trackId = trackId,
                                    title = title,
                                    language = lang,
                                    codecName = codecName,
                                    extension = extension
                                )
                            )
                            subIndex++
                        }
                    }
                } finally {
                    extractor.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaExtractor inspection failed", e)
        }

        tracks
    }

    suspend fun extractTrack(
        context: Context,
        videoUri: Uri,
        videoName: String,
        track: SubtitleStreamInfo,
        targetExtension: String? = null,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        if (!track.isExtractable) {
            throw IllegalArgumentException("Cannot export image-based or unsupported track: ${track.title}")
        }

        val effectiveExt = (targetExtension ?: track.extension).lowercase()

        val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
            ?: throw IOException("Unable to open file descriptor for: $videoUri")

        val isMkv = pfd.use { MatroskaSubtitleDemuxer.isMatroskaFile(it.fileDescriptor) }

        val cleanVideoName = videoName.substringBeforeLast(".")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "video" }

        val cleanLanguage = track.language
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "und" }

        val targetFileName = "${cleanVideoName}_${cleanLanguage}_${track.index}.$effectiveExt"

        val formattedContent: String

        if (isMkv) {
            // Demux directly using MatroskaSubtitleDemuxer
            val pfdDemux = context.contentResolver.openFileDescriptor(videoUri, "r")
                ?: throw IOException("Unable to open file descriptor for Matroska demuxing")
            val (cues, scriptHeader) = pfdDemux.use {
                MatroskaSubtitleDemuxer.demuxTrackCues(it.fileDescriptor, track, onProgress)
            }

            if (cues.isEmpty()) {
                throw IOException("No subtitle cues found in track '${track.title}'")
            }

            formattedContent = when (effectiveExt) {
                "ass" -> formatAssFromDemuxed(cleanVideoName, scriptHeader, cues)
                "vtt" -> formatVttFromDemuxed(cues)
                else -> formatSrtFromDemuxed(cues)
            }
        } else {
            // Android MediaExtractor fallback for MP4, etc.
            val rawCues = mutableListOf<ParsedCue>()
            var scriptHeader: String? = null

            val pfdExtractor = context.contentResolver.openFileDescriptor(videoUri, "r")
                ?: throw IOException("Unable to open file descriptor")

            pfdExtractor.use { parcelFd ->
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(parcelFd.fileDescriptor)

                    val targetTrackIndex = findMatchingTrackIndex(extractor, track)
                    if (targetTrackIndex == -1) {
                        throw IOException("Subtitle track '${track.title}' not found in container")
                    }

                    val trackFormat = extractor.getTrackFormat(targetTrackIndex)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""

                    if (trackFormat.containsKey("csd-0")) {
                        try {
                            val csdBuffer = trackFormat.getByteBuffer("csd-0")
                            if (csdBuffer != null) {
                                val csdBytes = ByteArray(csdBuffer.remaining())
                                csdBuffer.get(csdBytes)
                                scriptHeader = String(csdBytes, Charsets.UTF_8)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Unable to read csd-0 header", e)
                        }
                    }

                    extractor.selectTrack(targetTrackIndex)
                    val buffer = ByteBuffer.allocateDirect(128 * 1024)

                    while (coroutineContext.isActive) {
                        buffer.clear()
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        val startTimeUs = extractor.sampleTime
                        val sampleBytes = ByteArray(sampleSize)
                        buffer.get(sampleBytes)

                        val text = parseSampleText(sampleBytes, mime, effectiveExt)
                        if (text.isNotBlank()) {
                            rawCues.add(ParsedCue(startTimeUs, -1L, text))
                        }

                        extractor.advance()
                        onProgress?.invoke(0.5f)
                    }
                } finally {
                    extractor.release()
                }
            }

            if (rawCues.isEmpty()) {
                throw IOException("No subtitle cues detected in stream '${track.title}'")
            }

            rawCues.sortBy { it.startTimeUs }
            val processedCues = sanitizeCueTimestamps(rawCues)

            formattedContent = when (effectiveExt) {
                "ass" -> formatAssContent(cleanVideoName, scriptHeader, processedCues)
                "vtt" -> formatVttContent(processedCues)
                else -> formatSrtContent(processedCues)
            }
        }

        saveToDownloads(
            context = context,
            fileName = targetFileName,
            extension = effectiveExt,
            content = formattedContent
        )
    }

    suspend fun extractAllTracks(
        context: Context,
        videoUri: Uri,
        videoName: String,
        tracks: List<SubtitleStreamInfo>,
        targetExtension: String? = null,
        onProgress: ((currentTrack: Int, totalTracks: Int, trackTitle: String) -> Unit)? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val extractable = tracks.filter { it.isExtractable }
        if (extractable.isEmpty()) {
            throw IOException("No extractable subtitle tracks available")
        }

        val resultFiles = mutableListOf<File>()
        extractable.forEachIndexed { i, track ->
            if (!coroutineContext.isActive) return@withContext resultFiles
            onProgress?.invoke(i + 1, extractable.size, track.title)
            val file = extractTrack(context, videoUri, videoName, track, targetExtension)
            resultFiles.add(file)
        }
        resultFiles
    }

    private fun formatSrtFromDemuxed(cues: List<MatroskaSubtitleDemuxer.DemuxedCue>): String = buildString {
        var srtIndex = 1
        for (i in cues.indices) {
            val cue = cues[i]
            val cleanText = cleanSubtitleText(cue.rawText)
            if (cleanText.isNotBlank()) {
                val startMs = cue.startTimeMs
                val nextStartMs = if (i + 1 < cues.size) cues[i + 1].startTimeMs else -1L
                val durationMs = if (cue.durationMs > 0) {
                    cue.durationMs
                } else if (nextStartMs > startMs && (nextStartMs - startMs) in 500L..8000L) {
                    nextStartMs - startMs
                } else {
                    estimateDurationMs(cleanText)
                }

                var endMs = startMs + durationMs
                if (nextStartMs > startMs && endMs > nextStartMs) {
                    endMs = maxOf(startMs + 200L, nextStartMs - 50L)
                }

                appendLine(srtIndex)
                appendLine("${formatMsToSrt(startMs)} --> ${formatMsToSrt(endMs)}")
                appendLine(cleanText)
                appendLine()
                srtIndex++
            }
        }
    }

    private fun formatVttFromDemuxed(cues: List<MatroskaSubtitleDemuxer.DemuxedCue>): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        var vttIndex = 1
        for (i in cues.indices) {
            val cue = cues[i]
            val cleanText = cleanSubtitleText(cue.rawText)
            if (cleanText.isNotBlank()) {
                val startMs = cue.startTimeMs
                val nextStartMs = if (i + 1 < cues.size) cues[i + 1].startTimeMs else -1L
                val durationMs = if (cue.durationMs > 0) {
                    cue.durationMs
                } else if (nextStartMs > startMs && (nextStartMs - startMs) in 500L..8000L) {
                    nextStartMs - startMs
                } else {
                    estimateDurationMs(cleanText)
                }

                var endMs = startMs + durationMs
                if (nextStartMs > startMs && endMs > nextStartMs) {
                    endMs = maxOf(startMs + 200L, nextStartMs - 50L)
                }

                appendLine(vttIndex)
                appendLine("${formatMsToVtt(startMs)} --> ${formatMsToVtt(endMs)}")
                appendLine(cleanText)
                appendLine()
                vttIndex++
            }
        }
    }

    private fun formatAssFromDemuxed(
        title: String,
        existingHeader: String?,
        cues: List<MatroskaSubtitleDemuxer.DemuxedCue>
    ): String = buildString {
        if (!existingHeader.isNullOrBlank() && existingHeader.contains("[Script Info]")) {
            appendLine(existingHeader.trim())
            if (!existingHeader.contains("[Events]")) {
                appendLine()
                appendLine("[Events]")
                appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            }
        } else {
            appendLine("[Script Info]")
            appendLine("Title: $title")
            appendLine("ScriptType: v4.00+")
            appendLine("WrapStyle: 0")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine("YCbCr Matrix: None")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        }

        cues.forEachIndexed { i, cue ->
            val startMs = cue.startTimeMs
            val nextStartMs = if (i + 1 < cues.size) cues[i + 1].startTimeMs else -1L
            val durationMs = if (cue.durationMs > 0) {
                cue.durationMs
            } else if (nextStartMs > startMs && (nextStartMs - startMs) in 500L..8000L) {
                nextStartMs - startMs
            } else {
                estimateDurationMs(cue.rawText)
            }

            var endMs = startMs + durationMs
            if (nextStartMs > startMs && endMs > nextStartMs) {
                endMs = maxOf(startMs + 200L, nextStartMs - 50L)
            }

            val startStr = formatMsToAss(startMs)
            val endStr = formatMsToAss(endMs)
            val raw = cue.rawText

            if (raw.startsWith("Dialogue:", ignoreCase = true) || raw.startsWith("Comment:", ignoreCase = true)) {
                appendLine(raw)
            } else {
                val parts = raw.split(",", limit = 9)
                if (parts.size >= 9 && parts[0].trim().toIntOrNull() != null && parts[1].trim().toIntOrNull() != null) {
                    val layer = parts[1].trim()
                    val style = parts[2].trim()
                    val name = parts[3].trim()
                    val ml = parts[4].trim()
                    val mr = parts[5].trim()
                    val mv = parts[6].trim()
                    val effect = parts[7].trim()
                    val text = parts[8].replace("\r", "").replace("\n", "\\N")
                    appendLine("Dialogue: $layer,$startStr,$endStr,$style,$name,$ml,$mr,$mv,$effect,$text")
                } else {
                    val cleaned = raw.replace("\r", "").replace("\n", "\\N")
                    appendLine("Dialogue: 0,$startStr,$endStr,Default,,0,0,0,,$cleaned")
                }
            }
        }
    }

    fun cleanSubtitleText(raw: String): String {
        var text = raw
        if (raw.startsWith("Dialogue:", ignoreCase = true) || raw.startsWith("Comment:", ignoreCase = true)) {
            val dialogueParts = raw.substringAfter(":").split(",", limit = 10)
            if (dialogueParts.size >= 10) {
                text = dialogueParts[9]
            } else {
                text = dialogueParts.lastOrNull() ?: raw
            }
        } else {
            val parts = raw.split(",", limit = 9)
            if (parts.size >= 9 && parts[0].trim().toIntOrNull() != null && parts[1].trim().toIntOrNull() != null) {
                text = parts[8]
            }
        }

        // Strip ASS override codes: {\...}
        text = text.replace(Regex("\\{[^}]*\\}"), "")
        // Convert \N and \n to standard line breaks
        text = text.replace("\\N", "\n").replace("\\n", "\n")
        text = text.replace("\\h", " ")
        // Remove ASS drawing codes
        text = text.replace(Regex("m\\s+-?\\d+\\s+-?\\d+.*"), "")
        return text.trim()
    }

    private data class ParsedCue(
        val startTimeUs: Long,
        val durationUs: Long,
        val text: String
    )

    private data class ProcessedCue(
        val startTimeUs: Long,
        val endTimeUs: Long,
        val text: String
    )

    private fun sanitizeCueTimestamps(cues: List<ParsedCue>): List<ProcessedCue> {
        val result = mutableListOf<ProcessedCue>()

        for (i in cues.indices) {
            val cue = cues[i]
            val startTimeUs = maxOf(0L, cue.startTimeUs)
            val nextStartTimeUs = if (i + 1 < cues.size) cues[i + 1].startTimeUs else -1L

            val durationUs = if (cue.durationUs > 0) {
                cue.durationUs
            } else if (nextStartTimeUs > startTimeUs && (nextStartTimeUs - startTimeUs) in 500_000L..8_000_000L) {
                nextStartTimeUs - startTimeUs
            } else {
                estimateDurationUs(cue.text)
            }

            var endTimeUs = startTimeUs + durationUs
            if (nextStartTimeUs > startTimeUs && endTimeUs > nextStartTimeUs) {
                endTimeUs = maxOf(startTimeUs + 200_000L, nextStartTimeUs - 50_000L)
            }

            result.add(ProcessedCue(startTimeUs, endTimeUs, cue.text))
        }

        return result
    }

    private fun estimateDurationUs(text: String): Long {
        val length = text.trim().length
        val calculated = length * 60_000L
        return maxOf(1_500_000L, minOf(5_000_000L, calculated))
    }

    private fun estimateDurationMs(text: String): Long {
        val length = text.trim().length
        val calculated = length * 60L
        return maxOf(1_500L, minOf(5_000L, calculated))
    }

    private fun parseSampleText(bytes: ByteArray, mime: String, extension: String): String {
        if (bytes.isEmpty()) return ""

        if (mime.contains("tx3g", ignoreCase = true) || mime.contains("quicktime", ignoreCase = true)) {
            if (bytes.size >= 2) {
                val len = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                val safeLen = minOf(len, bytes.size - 2)
                if (safeLen > 0) {
                    return String(bytes, 2, safeLen, Charsets.UTF_8).trim()
                }
            }
        }

        val raw = String(bytes, Charsets.UTF_8).trim()
        return raw.trimEnd('\u0000')
    }

    private fun formatSrtContent(cues: List<ProcessedCue>): String = buildString {
        cues.forEachIndexed { index, cue ->
            val clean = cleanSubtitleText(cue.text)
            if (clean.isNotBlank()) {
                appendLine(index + 1)
                appendLine("${formatSrtTimestamp(cue.startTimeUs)} --> ${formatSrtTimestamp(cue.endTimeUs)}")
                appendLine(clean)
                appendLine()
            }
        }
    }

    private fun formatVttContent(cues: List<ProcessedCue>): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        cues.forEachIndexed { index, cue ->
            val clean = cleanSubtitleText(cue.text)
            if (clean.isNotBlank()) {
                appendLine(index + 1)
                appendLine("${formatVttTimestamp(cue.startTimeUs)} --> ${formatVttTimestamp(cue.endTimeUs)}")
                appendLine(clean)
                appendLine()
            }
        }
    }

    private fun formatAssContent(
        title: String,
        existingHeader: String?,
        cues: List<ProcessedCue>
    ): String = buildString {
        if (!existingHeader.isNullOrBlank() && existingHeader.contains("[Script Info]")) {
            appendLine(existingHeader.trim())
            if (!existingHeader.contains("[Events]")) {
                appendLine()
                appendLine("[Events]")
                appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            }
        } else {
            appendLine("[Script Info]")
            appendLine("Title: $title")
            appendLine("ScriptType: v4.00+")
            appendLine("WrapStyle: 0")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine("YCbCr Matrix: None")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine("Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        }

        cues.forEach { cue ->
            val start = formatAssTimestamp(cue.startTimeUs)
            val end = formatAssTimestamp(cue.endTimeUs)
            if (cue.text.startsWith("Dialogue:", ignoreCase = true)) {
                appendLine(cue.text)
            } else {
                val cleanedText = cue.text.replace("\r", "").replace("\n", "\\N")
                appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,$cleanedText")
            }
        }
    }

    private fun formatMsToSrt(ms: Long): String {
        val totalSec = ms / 1000
        val s = totalSec % 60
        val totalMin = totalSec / 60
        val m = totalMin % 60
        val h = totalMin / 60
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }

    private fun formatMsToVtt(ms: Long): String {
        val totalSec = ms / 1000
        val s = totalSec % 60
        val totalMin = totalSec / 60
        val m = totalMin % 60
        val h = totalMin / 60
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
    }

    private fun formatMsToAss(ms: Long): String {
        val totalSec = ms / 1000
        val s = totalSec % 60
        val totalMin = totalSec / 60
        val m = totalMin % 60
        val h = totalMin / 60
        val cs = (ms % 1000) / 10
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }

    private fun formatSrtTimestamp(us: Long): String {
        val totalMs = us / 1000
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val s = totalSec % 60
        val totalMin = totalSec / 60
        val m = totalMin % 60
        val h = totalMin / 60
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }

    private fun formatVttTimestamp(us: Long): String {
        val totalMs = us / 1000
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val s = totalSec % 60
        val totalMin = totalSec / 60
        val m = totalMin % 60
        val h = totalMin / 60
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, ms)
    }

    private fun formatAssTimestamp(us: Long): String {
        val totalMs = us / 1000
        val cs = (totalMs % 1000) / 10
        val totalSec = totalMs / 1000
        val s = totalSec % 60
        val totalMin = totalSec / 60
        val m = totalMin % 60
        val h = totalMin / 60
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    }

    private fun saveToDownloads(
        context: Context,
        fileName: String,
        extension: String,
        content: String
    ): File {
        val mimeType = when (extension.lowercase()) {
            "srt" -> "application/x-subrip"
            "ass" -> "text/x-ssa"
            "vtt" -> "text/vtt"
            else -> "text/plain"
        }

        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val contentBytes = content.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore download record for $fileName")

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(utf8Bom)
                os.write(contentBytes)
                os.flush()
            } ?: throw IOException("Failed to write subtitle data to MediaStore")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            return file
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)
            file.outputStream().use { os ->
                os.write(utf8Bom)
                os.write(contentBytes)
                os.flush()
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            return file
        }
    }

    private fun findMatchingTrackIndex(extractor: MediaExtractor, track: SubtitleStreamInfo): Int {
        val subtitleTrackIndices = mutableListOf<Int>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (isSubtitleMime(mime)) {
                if (format.containsKey("track-id") && format.getInteger("track-id") == track.trackId) {
                    return i
                }
                subtitleTrackIndices.add(i)
            }
        }

        if (track.index in subtitleTrackIndices.indices) {
            return subtitleTrackIndices[track.index]
        }

        return subtitleTrackIndices.firstOrNull() ?: -1
    }

    private fun isSubtitleMime(mime: String): Boolean {
        val lower = mime.lowercase()
        return lower.startsWith("text/") ||
               lower.startsWith("application/x-subrip") ||
               lower.startsWith("application/x-quicktime-tx3g") ||
               lower.contains("subrip") ||
               lower.contains("vtt") ||
               lower.contains("ssa") ||
               lower.contains("ass") ||
               lower.contains("tx3g") ||
               lower.contains("pgs") ||
               lower.contains("vobsub")
    }

    private fun isImageBasedFormat(format: String, codecId: String): Boolean {
        val combined = "$format $codecId".lowercase()
        return combined.contains("pgs") ||
               combined.contains("hdmv") ||
               combined.contains("vobsub") ||
               combined.contains("dvd") ||
               combined.contains("dvb") ||
               combined.contains("bitmap") ||
               combined.contains("image")
    }

    private fun isImageBasedMime(mime: String): Boolean {
        val lower = mime.lowercase()
        return lower.contains("pgs") ||
               lower.contains("vobsub") ||
               lower.contains("image") ||
               lower.contains("dvd")
    }

    private fun MediaInfo.getInfo(
        stream: MediaInfo.Stream,
        index: Int,
        parameter: String
    ): String = Get(stream, index, parameter) ?: ""
}
