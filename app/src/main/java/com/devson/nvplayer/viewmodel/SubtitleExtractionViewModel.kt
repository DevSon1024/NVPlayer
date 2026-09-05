package com.devson.nvplayer.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.media.SubtitleExtractorHelper
import com.devson.nvplayer.data.model.SubtitleStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface SubtitleExtractionUiState {
    data object Idle : SubtitleExtractionUiState
    data class Analyzing(val videoName: String) : SubtitleExtractionUiState
    data class Ready(
        val videoUri: Uri,
        val videoName: String,
        val fileSizeFormatted: String,
        val tracks: List<SubtitleStreamInfo>
    ) : SubtitleExtractionUiState
    data class Extracting(
        val currentTrackIndex: Int,
        val totalTracks: Int,
        val progressText: String
    ) : SubtitleExtractionUiState
    data class Success(
        val extractedFiles: List<File>,
        val savedDirectory: String
    ) : SubtitleExtractionUiState
    data class Error(val message: String) : SubtitleExtractionUiState
}

class SubtitleExtractionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<SubtitleExtractionUiState>(SubtitleExtractionUiState.Idle)
    val uiState: StateFlow<SubtitleExtractionUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var lastReadyState: SubtitleExtractionUiState.Ready? = null

    fun analyzeVideo(context: Context, uri: Uri) {
        activeJob?.cancel()
        val meta = SubtitleExtractorHelper.resolveVideoMetadata(context, uri)
        _uiState.value = SubtitleExtractionUiState.Analyzing(meta.displayName)

        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = SubtitleExtractorHelper.inspectTracks(
                    context = context,
                    uri = uri,
                    fileName = meta.displayName
                )
                val readyState = SubtitleExtractionUiState.Ready(
                    videoUri = uri,
                    videoName = meta.displayName,
                    fileSizeFormatted = meta.formattedSize,
                    tracks = tracks
                )
                lastReadyState = readyState
                _uiState.value = readyState
            } catch (e: Exception) {
                _uiState.value = SubtitleExtractionUiState.Error(
                    e.localizedMessage ?: "Failed to inspect video streams"
                )
            }
        }
    }

    fun extractTrack(context: Context, track: SubtitleStreamInfo, targetFormat: String? = null) {
        val currentReady = lastReadyState ?: return
        activeJob?.cancel()

        val formatLabel = targetFormat?.uppercase() ?: track.extension.uppercase()
        _uiState.value = SubtitleExtractionUiState.Extracting(
            currentTrackIndex = 1,
            totalTracks = 1,
            progressText = "Extracting ${track.title} as $formatLabel..."
        )

        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = SubtitleExtractorHelper.extractTrack(
                    context = context,
                    videoUri = currentReady.videoUri,
                    videoName = currentReady.videoName,
                    track = track,
                    targetExtension = targetFormat
                )
                val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "NosvedPlayer/Extracted-Subtitles").path
                val savedDir = file.parent ?: defaultDir
                _uiState.value = SubtitleExtractionUiState.Success(
                    extractedFiles = listOf(file),
                    savedDirectory = savedDir
                )
            } catch (e: Exception) {
                _uiState.value = SubtitleExtractionUiState.Error(
                    e.localizedMessage ?: "Failed to extract track ${track.title}"
                )
            }
        }
    }

    fun extractAll(context: Context, targetFormat: String? = "srt") {
        val currentReady = lastReadyState ?: return
        val extractable = currentReady.tracks.filter { it.isExtractable }
        if (extractable.isEmpty()) return

        activeJob?.cancel()
        _uiState.value = SubtitleExtractionUiState.Extracting(
            currentTrackIndex = 1,
            totalTracks = extractable.size,
            progressText = "Preparing to extract ${extractable.size} subtitle tracks..."
        )

        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = SubtitleExtractorHelper.extractAllTracks(
                    context = context,
                    videoUri = currentReady.videoUri,
                    videoName = currentReady.videoName,
                    tracks = currentReady.tracks,
                    targetExtension = targetFormat
                ) { current, total, trackTitle ->
                    _uiState.value = SubtitleExtractionUiState.Extracting(
                        currentTrackIndex = current,
                        totalTracks = total,
                        progressText = "Extracting track $current of $total: $trackTitle..."
                    )
                }

                val defaultDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "NosvedPlayer/Extracted-Subtitles").path
                val savedDir = files.firstOrNull()?.parent ?: defaultDir

                _uiState.value = SubtitleExtractionUiState.Success(
                    extractedFiles = files,
                    savedDirectory = savedDir
                )
            } catch (e: Exception) {
                _uiState.value = SubtitleExtractionUiState.Error(
                    e.localizedMessage ?: "Failed to extract all subtitle tracks"
                )
            }
        }
    }

    fun cancelExtraction() {
        activeJob?.cancel()
        val ready = lastReadyState
        if (ready != null) {
            _uiState.value = ready
        } else {
            _uiState.value = SubtitleExtractionUiState.Idle
        }
    }

    fun dismissResult() {
        val ready = lastReadyState
        if (ready != null) {
            _uiState.value = ready
        } else {
            _uiState.value = SubtitleExtractionUiState.Idle
        }
    }

    fun reset() {
        activeJob?.cancel()
        lastReadyState = null
        _uiState.value = SubtitleExtractionUiState.Idle
    }
}
