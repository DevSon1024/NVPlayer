package com.devson.nvplayer.ui.common.components.fastscroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScroller(
    itemCount: Int,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    sectionLabelProvider: ((Int) -> String)? = null,
    enabled: Boolean = itemCount > 6,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        content()

        if (enabled && itemCount > 0) {
            val density = LocalDensity.current
            val coroutineScope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current

            var isDragging by remember { mutableStateOf(false) }
            var isUserScrolling by remember { mutableStateOf(false) }
            var currentDragY by remember { mutableFloatStateOf(0f) }
            var currentDragIndex by remember { mutableIntStateOf(0) }
            var previousLabel by remember { mutableStateOf("") }

            val isScrollInProgress = listState?.isScrollInProgress == true || gridState?.isScrollInProgress == true

            // Keep thumb visible during scrolling or dragging, and fade out smoothly afterwards
            var isThumbVisible by remember { mutableStateOf(false) }

            LaunchedEffect(isScrollInProgress, isDragging) {
                if (isScrollInProgress || isDragging) {
                    isThumbVisible = true
                    isUserScrolling = true
                } else {
                    delay(1400)
                    isThumbVisible = false
                    isUserScrolling = false
                }
            }

            val alpha by animateFloatAsState(
                targetValue = if (isThumbVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 250),
                label = "fast_scroll_alpha"
            )

            val thumbWidth by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 4.dp,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                label = "fast_scroll_width"
            )

            val totalHeightPx = with(density) { maxHeight.toPx() }
            val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
            val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().toPx() }

            val thumbHeightDp = 48.dp
            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
            val trackHeightPx = (totalHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(thumbHeightPx)
            val maxScrollDistancePx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)

            // Calculate continuous sub-pixel scroll progress when NOT dragging
            val normalScrollProgress by remember(itemCount, listState, gridState) {
                derivedStateOf {
                    if (listState != null) {
                        val layoutInfo = listState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) 0f
                        else {
                            val firstVisible = visibleItems.first()
                            val itemSize = firstVisible.size.coerceAtLeast(1)
                            val firstIndex = listState.firstVisibleItemIndex
                            val scrollOffset = listState.firstVisibleItemScrollOffset
                            val continuousIndex = firstIndex + (scrollOffset.toFloat() / itemSize)
                            val visibleCount = visibleItems.size
                            val maxIndex = (itemCount - visibleCount).coerceAtLeast(1).toFloat()
                            (continuousIndex / maxIndex).coerceIn(0f, 1f)
                        }
                    } else if (gridState != null) {
                        val layoutInfo = gridState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) 0f
                        else {
                            val firstVisible = visibleItems.first()
                            val itemHeight = firstVisible.size.height.coerceAtLeast(1)
                            val firstIndex = gridState.firstVisibleItemIndex
                            val scrollOffset = gridState.firstVisibleItemScrollOffset
                            val continuousIndex = firstIndex + (scrollOffset.toFloat() / itemHeight)
                            val visibleCount = visibleItems.size
                            val maxIndex = (itemCount - visibleCount).coerceAtLeast(1).toFloat()
                            (continuousIndex / maxIndex).coerceIn(0f, 1f)
                        }
                    } else {
                        0f
                    }
                }
            }

            val targetThumbOffsetPx = if (isDragging) {
                topPaddingPx + currentDragY
            } else {
                topPaddingPx + (normalScrollProgress * maxScrollDistancePx)
            }

            val animatedThumbOffsetPx by animateFloatAsState(
                targetValue = targetThumbOffsetPx,
                animationSpec = if (isDragging) {
                    spring(stiffness = Spring.StiffnessHigh, dampingRatio = 1f)
                } else {
                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                },
                label = "fast_scroll_spring_offset"
            )

            val activeIndex = if (isDragging) {
                currentDragIndex
            } else {
                (normalScrollProgress * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
            }

            val badgeText = if (isDragging && activeIndex in 0 until itemCount) {
                sectionLabelProvider?.invoke(activeIndex) ?: ""
            } else ""

            // Tactile feedback on section transition
            LaunchedEffect(badgeText, isDragging) {
                if (isDragging && badgeText.isNotBlank() && badgeText != previousLabel) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    previousLabel = badgeText
                }
            }

            // Fast scroll touch / drag area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha }
            ) {
                // Floating Bubble
                if (sectionLabelProvider != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                val currentBubbleTopPx = (animatedThumbOffsetPx + thumbHeightPx / 2f - 24.dp.toPx())
                                    .coerceIn(
                                        topPaddingPx + 4f,
                                        (totalHeightPx - bottomPaddingPx - 48.dp.toPx()).coerceAtLeast(topPaddingPx + 4f)
                                    )
                                IntOffset(
                                    x = -36.dp.roundToPx(),
                                    y = currentBubbleTopPx.roundToInt()
                                )
                            }
                    ) {
                        AnimatedVisibility(
                            visible = isDragging && badgeText.isNotBlank(),
                            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.7f),
                            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    bottomStart = 16.dp,
                                    topEnd = 4.dp,
                                    bottomEnd = 16.dp
                                ),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shadowElevation = 8.dp,
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Scroller Track & Thumb Touch Target
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp)
                        .width(32.dp)
                        .fillMaxSize()
                        .pointerInput(itemCount, trackHeightPx, thumbHeightPx, topPaddingPx) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val relY = (offset.y - topPaddingPx - thumbHeightPx / 2f).coerceIn(0f, maxScrollDistancePx)
                                    currentDragY = relY
                                    val fraction = if (maxScrollDistancePx > 0f) relY / maxScrollDistancePx else 0f
                                    val targetIdx = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                                    if (targetIdx != currentDragIndex) {
                                        currentDragIndex = targetIdx
                                        coroutineScope.launch {
                                            listState?.scrollToItem(targetIdx)
                                            gridState?.scrollToItem(targetIdx)
                                        }
                                    }
                                },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    val relY = (change.position.y - topPaddingPx - thumbHeightPx / 2f).coerceIn(0f, maxScrollDistancePx)
                                    currentDragY = relY
                                    val fraction = if (maxScrollDistancePx > 0f) relY / maxScrollDistancePx else 0f
                                    val targetIdx = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
                                    if (targetIdx != currentDragIndex) {
                                        currentDragIndex = targetIdx
                                        coroutineScope.launch {
                                            listState?.scrollToItem(targetIdx)
                                            gridState?.scrollToItem(targetIdx)
                                        }
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            )
                        }
                ) {
                    // Visual Thumb Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = animatedThumbOffsetPx.roundToInt()
                                )
                            }
                            .width(thumbWidth)
                            .height(thumbHeightDp)
                            .clip(CircleShape)
                            .background(
                                if (isDragging) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
            }
        }
    }
}
