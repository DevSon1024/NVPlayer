package com.devson.nvplayer.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.devson.nvplayer.service.FrameExtractionService
import kotlinx.coroutines.flow.StateFlow

enum class FrameFormat(val extension: String, val mimeType: String, val compressFormat: Bitmap.CompressFormat) {
    JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", "image/png", Bitmap.CompressFormat.PNG)
}

data class ExtractionUiState(
    val isExtracting: Boolean = false,
    val progress: Float = 0f,
    val extractedCount: Int = 0,
    val totalFramesToExtract: Int = 0,
    val statusMessage: String? = null,
    val outputFolderPath: String? = null,
    val isSuccess: Boolean = false,
    val error: String? = null
)

class FrameExtractionViewModel : ViewModel() {

    val uiState: StateFlow<ExtractionUiState> = FrameExtractionService.uiState

    companion object {
        const val MAX_EXTRACTION_DURATION_MS = 2 * 60 * 1000L // 2 minutes maximum
        const val DEFAULT_INTERVAL_MS = 1000L // 1 frame per second
    }

    fun startFrameExtraction(
        context: Context,
        videoUri: Uri,
        videoName: String,
        startTimeMs: Long,
        endTimeMs: Long,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        format: FrameFormat = FrameFormat.JPEG,
        quality: Int = 90
    ) {
        FrameExtractionService.startExtraction(
            context = context,
            videoUri = videoUri,
            videoName = videoName,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            intervalMs = intervalMs,
            format = format,
            quality = quality
        )
    }

    fun cancelExtraction(context: Context) {
        FrameExtractionService.cancelExtraction(context)
    }

    fun resetState() {
        // Managed by service state
    }
}
