package com.devson.nvplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.database.EpisodeEntity
import com.devson.nvplayer.data.database.MovieEntity
import com.devson.nvplayer.data.database.SeasonEntity
import com.devson.nvplayer.data.database.SeriesEntity
import com.devson.nvplayer.data.model.LibraryCategory
import com.devson.nvplayer.data.model.LibraryMediaItem
import com.devson.nvplayer.data.model.LibraryMediaType
import com.devson.nvplayer.data.model.LibraryUiState
import com.devson.nvplayer.data.model.SeasonWithEpisodes
import com.devson.nvplayer.data.model.SeriesDetail
import com.devson.nvplayer.data.parser.MediaFilenameParser
import com.devson.nvplayer.data.parser.ParsedMediaInfo
import com.devson.nvplayer.domain.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    application: Application,
    private val videoListViewModel: VideoListViewModel
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val mediaLibraryDao = db.mediaLibraryDao()
    private val watchHistoryDao = db.watchHistoryDao()

    private val _selectedCategory = MutableStateFlow(LibraryCategory.ALL)
    val selectedCategory: StateFlow<LibraryCategory> = _selectedCategory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _parsedItems = MutableStateFlow<List<LibraryMediaItem>>(emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        videoListViewModel.isLoading,
        _parsedItems,
        _selectedCategory
    ) { isLoading, items, category ->
        if (isLoading && items.isEmpty()) {
            LibraryUiState.Loading
        } else {
            val filteredItems = when (category) {
                LibraryCategory.ALL -> items
                LibraryCategory.MOVIES -> items.filter { it.type == LibraryMediaType.MOVIE }
                LibraryCategory.TV_SHOWS -> items.filter { it.type == LibraryMediaType.TV_SHOW }
                LibraryCategory.ANIME -> items.filter { it.type == LibraryMediaType.ANIME }
            }

            val continueWatching = items
                .filter { it.playbackPositionMs > 5000L && it.durationMs > 0 && it.playbackPositionMs < (it.durationMs * 0.95) }
                .sortedByDescending { it.playbackPositionMs }
                .take(10)

            val recentlyAdded = filteredItems
                .take(15)

            val heroItems = if (filteredItems.isNotEmpty()) {
                filteredItems.take(5)
            } else {
                items.take(5)
            }

            LibraryUiState.Success(
                heroItems = heroItems,
                continueWatching = continueWatching,
                recentlyAdded = recentlyAdded,
                allItems = filteredItems,
                selectedCategory = category
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    init {
        viewModelScope.launch {
            videoListViewModel.videosFlat.collect { videos ->
                processVideos(videos)
            }
        }
    }

    fun selectCategory(category: LibraryCategory) {
        _selectedCategory.value = category
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            videoListViewModel.loadVideos(forceRefresh = true)
            _isRefreshing.value = false
        }
    }

    private fun processVideos(videos: List<Video>) {
        if (videos.isEmpty()) {
            _parsedItems.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyList = watchHistoryDao.getAllHistorySync()
                val historyMap = historyList.associateBy { it.uri }

                val mediaItems = mutableListOf<LibraryMediaItem>()
                val seriesMap = mutableMapOf<String, MutableList<Pair<Video, ParsedMediaInfo>>>()

                for (video in videos) {
                    val parsed = MediaFilenameParser.parse(video.title, durationMillis = video.duration)
                    val history = historyMap[video.uri]
                    val positionMs = history?.lastPositionMs ?: 0L
                    val isWatched = video.duration > 0 && positionMs > (video.duration * 0.9)

                    when (parsed) {
                        is ParsedMediaInfo.Movie -> {
                            val item = LibraryMediaItem(
                                id = video.uri,
                                title = parsed.cleanedTitle,
                                cleanedTitle = parsed.cleanedTitle,
                                videoUri = video.uri,
                                posterUri = video.thumbnailUri ?: video.uri,
                                backdropUri = video.thumbnailUri ?: video.uri,
                                type = LibraryMediaType.MOVIE,
                                year = parsed.year,
                                durationMs = video.duration,
                                playbackPositionMs = positionMs,
                                isWatched = isWatched,
                                synopsis = "Local movie file in high definition."
                            )
                            mediaItems.add(item)

                            // Cache movie in Room
                            val existingMovie = mediaLibraryDao.getMovieByUri(video.uri)
                            if (existingMovie == null) {
                                mediaLibraryDao.insertMovie(
                                    MovieEntity(
                                        title = parsed.cleanedTitle,
                                        year = parsed.year,
                                        fileUri = video.uri,
                                        durationMillis = video.duration,
                                        lastPlaybackPosition = positionMs,
                                        isWatched = isWatched
                                    )
                                )
                            }
                        }
                        is ParsedMediaInfo.TvShow -> {
                            val key = parsed.cleanedTitle
                            seriesMap.getOrPut(key) { mutableListOf() }.add(video to parsed)
                        }
                        is ParsedMediaInfo.Anime -> {
                            val key = parsed.cleanedTitle
                            seriesMap.getOrPut(key) { mutableListOf() }.add(video to parsed)
                        }
                        is ParsedMediaInfo.Unclassified -> {
                            val item = LibraryMediaItem(
                                id = video.uri,
                                title = video.title,
                                cleanedTitle = parsed.cleanedTitle,
                                videoUri = video.uri,
                                posterUri = video.thumbnailUri ?: video.uri,
                                backdropUri = video.thumbnailUri ?: video.uri,
                                type = LibraryMediaType.UNCLASSIFIED,
                                durationMs = video.duration,
                                playbackPositionMs = positionMs,
                                isWatched = isWatched
                            )
                            mediaItems.add(item)
                        }
                    }
                }

                // Process Series / Anime groups
                for ((title, episodes) in seriesMap) {
                    val firstPair = episodes.firstOrNull() ?: continue
                    val isAnime = episodes.any { it.second is ParsedMediaInfo.Anime }
                    val mediaType = if (isAnime) LibraryMediaType.ANIME else LibraryMediaType.TV_SHOW
                    val seasons = episodes.mapNotNull {
                        when (val info = it.second) {
                            is ParsedMediaInfo.TvShow -> info.seasonNumber
                            is ParsedMediaInfo.Anime -> info.seasonNumber ?: 1
                            else -> 1
                        }
                    }.distinct()

                    // Check or insert series into Room DB
                    var seriesEntity = mediaLibraryDao.getSeriesByTitle(title)
                    if (seriesEntity == null) {
                        val seriesId = mediaLibraryDao.insertSeries(
                            SeriesEntity(
                                title = title,
                                synopsis = if (isAnime) "Anime Series with ${episodes.size} episodes." else "TV Show with ${episodes.size} episodes across ${seasons.size} seasons."
                            )
                        )
                        seriesEntity = mediaLibraryDao.getSeriesById(seriesId)
                    }

                    val latestVideo = episodes.first().first
                    val latestHistory = historyMap[latestVideo.uri]

                    val item = LibraryMediaItem(
                        id = seriesEntity?.id?.toString() ?: title,
                        title = title,
                        cleanedTitle = title,
                        videoUri = latestVideo.uri,
                        posterUri = latestVideo.thumbnailUri ?: latestVideo.uri,
                        backdropUri = latestVideo.thumbnailUri ?: latestVideo.uri,
                        type = mediaType,
                        seasonCount = seasons.size.coerceAtLeast(1),
                        episodeCount = episodes.size,
                        durationMs = latestVideo.duration,
                        playbackPositionMs = latestHistory?.lastPositionMs ?: 0L,
                        seriesId = seriesEntity?.id,
                        synopsis = seriesEntity?.synopsis
                    )
                    mediaItems.add(item)

                    // Sync seasons & episodes to Room
                    if (seriesEntity != null) {
                        for (seasonNum in seasons) {
                            var seasonEntity = mediaLibraryDao.getSeasonByNumber(seriesEntity.id, seasonNum)
                            if (seasonEntity == null) {
                                val sId = mediaLibraryDao.insertSeason(
                                    SeasonEntity(
                                        seriesId = seriesEntity.id,
                                        seasonNumber = seasonNum
                                    )
                                )
                                seasonEntity = SeasonEntity(id = sId, seriesId = seriesEntity.id, seasonNumber = seasonNum)
                            }

                            val seasonEpisodes = episodes.filter { pair ->
                                val sNum = when (val info = pair.second) {
                                    is ParsedMediaInfo.TvShow -> info.seasonNumber
                                    is ParsedMediaInfo.Anime -> info.seasonNumber ?: 1
                                    else -> 1
                                }
                                sNum == seasonNum
                            }

                            for (epPair in seasonEpisodes) {
                                val epVideo = epPair.first
                                val epInfo = epPair.second
                                val epNum = when (epInfo) {
                                    is ParsedMediaInfo.TvShow -> epInfo.episodeNumber
                                    is ParsedMediaInfo.Anime -> epInfo.episodeNumber
                                    else -> 1
                                }
                                val epHistory = historyMap[epVideo.uri]
                                val epPos = epHistory?.lastPositionMs ?: 0L
                                val epWatched = epVideo.duration > 0 && epPos > (epVideo.duration * 0.9)

                                val existingEp = mediaLibraryDao.getEpisodeByUri(epVideo.uri)
                                if (existingEp == null) {
                                    mediaLibraryDao.insertEpisode(
                                        EpisodeEntity(
                                            seasonId = seasonEntity.id,
                                            episodeNumber = epNum,
                                            title = epVideo.title,
                                            fileUri = epVideo.uri,
                                            durationMillis = epVideo.duration,
                                            lastPlaybackPosition = epPos,
                                            isWatched = epWatched
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                _parsedItems.value = mediaItems
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getSeriesDetails(seriesId: Long): SeriesDetail? = withContext(Dispatchers.IO) {
        val series = mediaLibraryDao.getSeriesById(seriesId) ?: return@withContext null
        val seasons = mediaLibraryDao.getSeasonsForSeries(seriesId).firstOrNull() ?: emptyList()
        val seasonsWithEpisodes = mutableListOf<SeasonWithEpisodes>()
        var totalEpisodes = 0

        for (season in seasons) {
            val episodes = mediaLibraryDao.getEpisodesForSeason(season.id).firstOrNull() ?: emptyList()
            seasonsWithEpisodes.add(SeasonWithEpisodes(season, episodes))
            totalEpisodes += episodes.size
        }

        SeriesDetail(
            series = series,
            seasonsWithEpisodes = seasonsWithEpisodes,
            totalEpisodes = totalEpisodes,
            type = if (series.synopsis?.contains("Anime", ignoreCase = true) == true) LibraryMediaType.ANIME else LibraryMediaType.TV_SHOW
        )
    }

    class Factory(
        private val application: Application,
        private val videoListViewModel: VideoListViewModel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(application, videoListViewModel) as T
        }
    }
}
