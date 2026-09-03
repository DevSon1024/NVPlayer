package com.devson.nvplayer.ui.common.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Geometric faceted Diamond shape with 45-degree chamfered facet points.
 */
class DiamondFacetedShape(private val chamferDp: Float = 6f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val chamfer = (chamferDp * density.density).coerceAtMost(size.height / 2f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(chamfer, 0f)
            lineTo(w - chamfer, 0f)
            lineTo(w, h * 0.5f)
            lineTo(w - chamfer, h)
            lineTo(chamfer, h)
            lineTo(0f, h * 0.5f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiamondFacetedShape) return false
        return chamferDp == other.chamferDp
    }

    override fun hashCode(): Int = chamferDp.hashCode()
}

val DefaultDiamondFacetedShape = DiamondFacetedShape(6f)
val SmallDiamondFacetedShape = DiamondFacetedShape(4.5f)
val LargeDiamondFacetedShape = DiamondFacetedShape(8f)

enum class ProBadgeSize(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
    val fontSize: TextUnit,
    val chamferSize: Float
) {
    SMALL(
        horizontalPadding = 7.dp,
        verticalPadding = 2.dp,
        iconSize = 11.dp,
        fontSize = 10.sp,
        chamferSize = 4.5f
    ),
    MEDIUM(
        horizontalPadding = 9.dp,
        verticalPadding = 3.5.dp,
        iconSize = 13.dp,
        fontSize = 11.5.sp,
        chamferSize = 6f
    ),
    LARGE(
        horizontalPadding = 12.dp,
        verticalPadding = 5.dp,
        iconSize = 16.dp,
        fontSize = 13.5.sp,
        chamferSize = 8f
    )
}

/**
 * Premium Diamond-textured PRO Badge with an animated crystalline gleam / shining beam.
 */
@Composable
fun ShiningProBadge(
    modifier: Modifier = Modifier,
    size: ProBadgeSize = ProBadgeSize.MEDIUM,
    showIcon: Boolean = true,
    text: String = "PRO"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "diamond_shining_sweep")
    val shineProgress by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine_progress"
    )

    val shape = when (size) {
        ProBadgeSize.SMALL -> SmallDiamondFacetedShape
        ProBadgeSize.MEDIUM -> DefaultDiamondFacetedShape
        ProBadgeSize.LARGE -> LargeDiamondFacetedShape
    }

    val diamondBackgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF00E5FF),
            Color(0xFF0091EA),
            Color(0xFF2979FF),
            Color(0xFF651FFF)
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val diamondBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE0F7FA).copy(alpha = 0.9f),
            Color(0xFF80D8FF).copy(alpha = 0.7f),
            Color(0xFFB388FF).copy(alpha = 0.8f),
            Color(0xFFE0F7FA).copy(alpha = 0.95f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(diamondBackgroundBrush)
            .border(BorderStroke(1.dp, diamondBorderBrush), shape)
            .drawWithContent {
                drawContent()
                val w = this.size.width
                val h = this.size.height
                val startX = w * shineProgress
                val endX = startX + (w * 0.45f)
                val shineBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(endX, h)
                )
                drawRect(brush = shineBrush)
            }
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
        ) {
            if (showIcon) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size.iconSize)
                )
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = size.fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                lineHeight = size.fontSize
            )
        }
    }
}
