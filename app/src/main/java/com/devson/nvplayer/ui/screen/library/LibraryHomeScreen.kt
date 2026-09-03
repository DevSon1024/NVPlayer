package com.devson.nvplayer.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.nvplayer.data.model.LibraryCategory
import com.devson.nvplayer.data.model.LibraryMediaItem
import com.devson.nvplayer.data.model.LibraryMediaType
import com.devson.nvplayer.data.model.LibraryUiState
import com.devson.nvplayer.ui.screen.library.components.CategoryFilterRow
import com.devson.nvplayer.ui.screen.library.components.ContinueWatchingRow
import com.devson.nvplayer.ui.screen.library.components.MediaHeroCarousel
import com.devson.nvplayer.ui.screen.library.components.MediaPosterCard
import com.devson.nvplayer.ui.screen.library.components.RecentlyAddedRow
import com.devson.nvplayer.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (LibraryMediaItem) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search"
                            )
                        }
                    },
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search movies, series, anime...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        keyboardController?.hide()
                                        onNavigateToSearch(searchQuery.trim())
                                    }
                                }
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) {
                                keyboardController?.hide()
                                onNavigateToSearch(searchQuery.trim())
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp)
                                )
                            }
                            Text(
                                text = "Smart Library",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Search Library")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh Library")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is LibraryUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is LibraryUiState.Success -> {
                    val isGlobalEmpty = state.allItems.isEmpty() && selectedCategory == LibraryCategory.ALL && state.heroItems.isEmpty() && state.continueWatching.isEmpty()
                    if (isGlobalEmpty) {
                        LibraryEmptyState(onRefresh = { viewModel.refresh() })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Hero Carousel (3-5 items)
                            if (state.heroItems.isNotEmpty() && selectedCategory == LibraryCategory.ALL) {
                                item(key = "hero_carousel") {
                                    MediaHeroCarousel(
                                        items = state.heroItems,
                                        onItemClick = { item ->
                                            if (item.seriesId != null && (item.type == LibraryMediaType.TV_SHOW || item.type == LibraryMediaType.ANIME)) {
                                                onSeriesClick(item.seriesId)
                                            } else {
                                                onMediaClick(item)
                                            }
                                        }
                                    )
                                }
                            }

                            // 2. Category Filter Row
                            item(key = "category_filter") {
                                CategoryFilterRow(
                                    selectedCategory = selectedCategory,
                                    onSelectCategory = { viewModel.selectCategory(it) }
                                )
                            }

                            if (state.allItems.isEmpty()) {
                                item(key = "empty_category") {
                                    CategoryEmptyState(
                                        category = selectedCategory,
                                        onScanClick = { viewModel.refresh() }
                                    )
                                }
                            } else {
                                // 3. Continue Watching Row
                                if (state.continueWatching.isNotEmpty() && selectedCategory == LibraryCategory.ALL) {
                                    item(key = "continue_watching") {
                                        ContinueWatchingRow(
                                            items = state.continueWatching,
                                            onItemClick = onMediaClick
                                        )
                                    }
                                }

                                // 4. Recently Added Row
                                if (state.recentlyAdded.isNotEmpty()) {
                                    item(key = "recently_added") {
                                        RecentlyAddedRow(
                                            title = when (selectedCategory) {
                                                LibraryCategory.ALL -> "Recently Added"
                                                LibraryCategory.MOVIES -> "Latest Movies"
                                                LibraryCategory.TV_SHOWS -> "Popular TV Shows"
                                                LibraryCategory.ANIME -> "Trending Anime"
                                            },
                                            items = state.recentlyAdded,
                                            onItemClick = { item ->
                                                if (item.seriesId != null && (item.type == LibraryMediaType.TV_SHOW || item.type == LibraryMediaType.ANIME)) {
                                                    onSeriesClick(item.seriesId)
                                                } else {
                                                    onMediaClick(item)
                                                }
                                            }
                                        )
                                    }
                                }

                                // 5. Categorized Section: All Parsed Media Items
                                item(key = "all_media_header") {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Text(
                                            text = if (selectedCategory == LibraryCategory.ALL) "All" else "All ${selectedCategory.displayName}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Grid-like chunked rows for performance
                                val chunkedItems = state.allItems.chunked(3)
                                items(chunkedItems, key = { row -> row.firstOrNull()?.id ?: "" }) { rowItems ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (item in rowItems) {
                                            MediaPosterCard(
                                                item = item,
                                                onClick = {
                                                    if (item.seriesId != null && (item.type == LibraryMediaType.TV_SHOW || item.type == LibraryMediaType.ANIME)) {
                                                        onSeriesClick(item.seriesId)
                                                    } else {
                                                        onMediaClick(item)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.MovieFilter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your Smart Library is Empty",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Scan your local storage or download movies, series, and anime to automatically categorize them here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRefresh,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Storage")
        }
    }
}

@Composable
private fun CategoryEmptyState(
    category: LibraryCategory,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (category) {
                        LibraryCategory.MOVIES -> Icons.Filled.Movie
                        LibraryCategory.TV_SHOWS -> Icons.Filled.Tv
                        LibraryCategory.ANIME -> Icons.Filled.MovieFilter
                        else -> Icons.Filled.VideoLibrary
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No ${category.displayName} Found",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No media files detected under this category. Switch to another tab or refresh your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onScanClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Re-scan Library")
        }
    }
}
