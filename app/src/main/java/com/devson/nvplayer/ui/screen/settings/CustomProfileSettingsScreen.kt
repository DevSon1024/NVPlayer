package com.devson.nvplayer.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.domain.model.HomeSection
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProfileSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val viewSettings by settingsViewModel.viewSettings.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Customize Profile",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Profile Sections Visibility
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomProfileHeader("Section Visibility")
                SettingToggleCard(
                    icon = Icons.Default.Widgets,
                    title = "Show Shortcuts Section",
                    subtitle = "Display Video Feed & Recycle Bin quick action cards",
                    checked = viewSettings.isShortcutsVisible,
                    onCheckedChange = { settingsViewModel.updateIsShortcutsVisible(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.History,
                    title = "Show Watch History Section",
                    subtitle = "Display 'Continue Watching' row for recently played videos",
                    checked = viewSettings.showHistoryCard,
                    onCheckedChange = { settingsViewModel.updateShowHistoryCard(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.Analytics,
                    title = "Show Details Section",
                    subtitle = "Display library statistics, total videos count & storage analyzer",
                    checked = viewSettings.isDetailsVisible,
                    onCheckedChange = { settingsViewModel.updateIsDetailsVisible(it) }
                )
            }

            // Profile Section Layout & Drag/Drop Reordering
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomProfileHeader("Section Layout & Order")
                ReorderableHomeSectionList(
                    order = viewSettings.homeSectionOrder,
                    onOrderChanged = { newOrder ->
                        settingsViewModel.updateHomeSectionOrder(newOrder)
                    }
                )
            }

            // Additional Cards Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomProfileHeader("Profile Screen Cards")
                SettingToggleCard(
                    icon = Icons.Default.VideoLibrary,
                    title = "Show Latest Videos Card",
                    subtitle = "Display a horizontal carousel of your newly added videos",
                    checked = viewSettings.showLatestVideos,
                    onCheckedChange = { settingsViewModel.updateShowLatestVideos(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.PieChart,
                    title = "Show Storage Tracking Card",
                    subtitle = "Display visual storage analyzer showing space statistics",
                    checked = viewSettings.showStorageTracker,
                    onCheckedChange = { settingsViewModel.updateShowStorageTracker(it) }
                )
            }

            // Quick Access & FAB Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomProfileHeader("Quick Access Actions")
                SettingToggleCard(
                    icon = Icons.Default.SmartButton,
                    title = "Show Floating Action Button",
                    subtitle = "Floating menu button to scan storage, view tools or search",
                    checked = viewSettings.showQuickFab,
                    onCheckedChange = { settingsViewModel.updateShowQuickFab(it) }
                )

                if (viewSettings.showQuickFab) {
                    SettingToggleCard(
                        icon = Icons.Default.Visibility,
                        title = "Enable FAB Preview Option",
                        subtitle = "Allows long-press or swipe on FAB to preview tools",
                        checked = viewSettings.enableFabPreview,
                        onCheckedChange = { settingsViewModel.updateEnableFabPreview(it) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReorderableHomeSectionList(
    order: List<HomeSection>,
    onOrderChanged: (List<HomeSection>) -> Unit
) {
    var sectionList by remember(order) { mutableStateOf(order) }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in sectionList.indices && toIndex in sectionList.indices && fromIndex != toIndex) {
            val newList = sectionList.toMutableList()
            val moved = newList.removeAt(fromIndex)
            newList.add(toIndex, moved)
            sectionList = newList
            onOrderChanged(newList)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            sectionList.forEachIndexed { index, section ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Column {
                            Text(
                                text = section.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = section.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { moveItem(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = { moveItem(index, index + 1) },
                            enabled = index < sectionList.lastIndex,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                tint = if (index < sectionList.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                if (index < sectionList.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomProfileHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .border(
                BorderStroke(
                    1.dp,
                    if (checked && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked && enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (checked && enabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (checked && enabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
