package com.devson.nvplayer.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.devson.nvplayer.BuildConfig
import com.devson.nvplayer.R
import com.devson.nvplayer.player.engine.MPVPlayerEngine
import `is`.xyz.mpv.MPVLib

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToCredits: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Retrieve app version info safely
    val versionName = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    val isDebug = BuildConfig.DEBUG
    val buildType = if (isDebug) "Beta (Debug)" else "Release (Stable)"

    // Media engine details safely retrieved
    val mpvVersion = remember {
        if (MPVPlayerEngine.isInitialized) {
            try {
                MPVLib.getPropertyString("mpv-version") ?: "0.37.0 (libmpv)"
            } catch (e: Throwable) {
                "0.37.0 (libmpv)"
            }
        } else {
            "0.37.0 (libmpv)"
        }
    }

    val ffmpegVersion = remember {
        if (MPVPlayerEngine.isInitialized) {
            try {
                MPVLib.getPropertyString("ffmpeg-version") ?: "6.1"
            } catch (e: Throwable) {
                "6.1"
            }
        } else {
            "6.1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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

            // 1. Brand / Identity Card
            AboutBrandCard(versionName = versionName, buildType = buildType)

            // 2. Donate Section
            AboutDonateCard(context = context)

            // 3. Device & Hardware details
            AboutSectionLabel(label = "Device Info")
            AboutDeviceInfoCard()

            // 4. Media Engine details
            AboutSectionLabel(label = "Engine Versions")
            AboutEngineCard(mpvVersion = mpvVersion, ffmpegVersion = ffmpegVersion)

            // 5. Links & Action Cards
            AboutSectionLabel(label = "Community & Support")
            AboutLinksCard(context = context)

            // 6. Credits
            AboutCreditsCard(onClick = onNavigateToCredits)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun AboutBrandCard(
    versionName: String,
    buildType: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dynamic Linear Animated Nosved Logo
            AnimatedNosvedLogo(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(96.dp)
            )

            Text(
                text = "Nosved Player",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Simple, lightweight, powerful media player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "v$versionName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = buildType,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutDeviceInfoCard() {
    AboutCard {
        InfoRow(label = "Device Model", value = Build.MODEL)
        InfoRow(label = "Brand", value = Build.BRAND)
        InfoRow(label = "Android Version", value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoRow(label = "CPU Architecture", value = Build.SUPPORTED_ABIS.joinToString(", "))
    }
}

@Composable
private fun AboutEngineCard(
    mpvVersion: String,
    ffmpegVersion: String
) {
    AboutCard {
        InfoRow(label = "MPV Version", value = mpvVersion)
        InfoRow(label = "FFmpeg Version", value = ffmpegVersion)
    }
}

@Composable
private fun AboutDonateCard(context: Context) {
    val upiId = "devendraps0103@okicici"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column {
                    Text(
                        text = "Support Development",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Help us keep the player free and open-source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UPI ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = upiId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("UPI ID", upiId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "UPI ID copied!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI ID",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = "Copy", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutLinksCard(context: Context) {
    AboutCard {
        LinkRow(
            icon = Icons.Default.Code,
            title = "GitHub Repository",
            subtitle = "Browse source code and contribute",
            onClick = {
                openUrl(context, "https://github.com/DevSon1024/Nosved-Player")
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 44.dp))
        LinkRow(
            icon = Icons.Default.Update,
            title = "Latest Release",
            subtitle = "Check out the latest release notes and updates",
            onClick = {
                openUrl(context, "https://github.com/DevSon1024/Nosved-Player/releases")
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 44.dp))
        LinkRow(
            icon = Icons.Default.BugReport,
            title = "Report an Issue",
            subtitle = "Found a bug? Help us improve by listing it",
            onClick = {
                openUrl(context, "https://github.com/DevSon1024/Nosved-Player/issues/new")
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 44.dp))
        LinkRow(
            icon = Icons.AutoMirrored.Filled.Send,
            title = "Telegram Channel",
            subtitle = "Join our Telegram community channel",
            onClick = {
                openUrl(context, "https://t.me/Nosved_Player")
            }
        )
    }
}

@Composable
private fun AboutCreditsCard(onClick: () -> Unit) {
    AboutCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Credits & Open Source",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Open source libraries, versions, and licenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open browser link", Toast.LENGTH_SHORT).show()
    }
}

// ============================================================================
// Nosved logo geometry — extracted directly from the reference mark (as
// percentages of the icon's bounding box, 0f..100f on each axis). Three
// closed contours reproduce the exact silhouette:
//   1) OuterContour      — outer silhouette of the whole N + play mark
//   2) BarsHoleContour   — the connected hollow void inside both vertical bars
//   3) TriangleHoleContour — the small hollow void inside the play triangle
// Filling (OuterContour - BarsHoleContour - TriangleHoleContour) with an
// even-odd rule reproduces the hollow, linear "N▷" mark precisely.
// ============================================================================

private val NosvedOuterContour = listOf(
    22.46f to 21.19f, 20.80f to 24.02f, 20.80f to 75.68f, 21.68f to 77.83f,
    25.10f to 79.49f, 38.18f to 79.49f, 41.02f to 78.42f, 42.48f to 75.20f,
    42.68f to 61.52f, 61.23f to 79.00f, 74.80f to 79.49f, 77.44f to 78.61f,
    79.10f to 75.68f, 78.32f to 22.07f, 75.59f to 20.41f, 59.57f to 20.90f,
    57.62f to 23.63f, 57.42f to 33.50f, 40.72f to 20.61f
)

private val NosvedBarsHoleContour = listOf(
    24.02f to 22.95f, 39.84f to 22.75f, 59.96f to 38.48f, 60.74f to 22.95f,
    76.37f to 23.44f, 75.98f to 76.76f, 62.01f to 76.76f, 46.29f to 61.72f,
    46.58f to 59.28f, 62.89f to 49.12f, 41.31f to 35.94f, 40.04f to 38.38f,
    40.04f to 75.78f, 38.38f to 77.15f, 23.73f to 76.56f
)

private val NosvedTriangleHoleContour = listOf(
    44.34f to 41.60f, 56.45f to 48.93f, 55.86f to 50.20f,
    44.82f to 56.54f, 43.26f to 55.76f, 43.26f to 42.58f
)

/** Builds a closed Path from a percentage-space polygon, rounding every vertex. */
private fun buildRoundedContour(
    pointsPct: List<Pair<Float, Float>>,
    w: Float,
    h: Float,
    cornerRadius: Float
): Path {
    val n = pointsPct.size
    val pts = pointsPct.map { Offset(it.first / 100f * w, it.second / 100f * h) }
    val path = Path()
    for (i in 0 until n) {
        val curr = pts[i]
        val prev = pts[(i - 1 + n) % n]
        val next = pts[(i + 1) % n]

        val toPrev = prev - curr
        val toNext = next - curr
        val prevLen = toPrev.getDistance()
        val nextLen = toNext.getDistance()
        val r = minOf(cornerRadius, prevLen * 0.45f, nextLen * 0.45f)

        val startPt = if (prevLen > 0f) curr + toPrev / prevLen * r else curr
        val endPt = if (nextLen > 0f) curr + toNext / nextLen * r else curr

        if (i == 0) path.moveTo(startPt.x, startPt.y) else path.lineTo(startPt.x, startPt.y)
        path.quadraticTo(curr.x, curr.y, endPt.x, endPt.y)
    }
    path.close()
    return path
}

/** Returns the first `progress` fraction (by length) of `path`, for a draw-on reveal. */
private fun trimmedSegment(path: Path, progress: Float): Path {
    val out = Path()
    if (progress <= 0f) return out
    val measure = PathMeasure().apply { setPath(path, false) }
    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), out, true)
    return out
}

/** Point along `path` at fractional `progress` (0f..1f) — used to lead the stroke with a glow. */
private fun tipPosition(path: Path, progress: Float): Offset? {
    if (progress <= 0f || progress >= 1f) return null
    val measure = PathMeasure().apply { setPath(path, false) }
    return measure.getPosition(measure.length * progress)
}

private fun stageProgress(overall: Float, start: Float, end: Float): Float =
    ((overall - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Animated Nosved logo: draws the mark on stroke-by-stroke like a pen (outer
 * silhouette, then the hollow bars, then the play triangle), then solidifies
 * into the crisp filled mark with a soft shine sweep. Triggers automatically
 * the moment this composable enters composition (i.e. on screen entry).
 */
@Composable
private fun AnimatedNosvedLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val containerAlpha = remember { Animatable(0f) }
    val containerScale = remember { Animatable(0.72f) }
    val drawProgress = remember { Animatable(0f) }
    val fillReveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { containerAlpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
        launch {
            containerScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        delay(90)
        drawProgress.animateTo(1f, tween(durationMillis = 1150, easing = FastOutSlowInEasing))
        fillReveal.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        // subtle settle "pop" once the mark has fully solidified
        containerScale.animateTo(1.05f, tween(120, easing = FastOutSlowInEasing))
        containerScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val idleTransition = rememberInfiniteTransition(label = "nosved_idle")
    val breathe by idleTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val shimmer by idleTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val strokeWidthPx = with(LocalDensity.current) { 3.4.dp.toPx() }
    val glowRadiusPx = with(LocalDensity.current) { 5.dp.toPx() }

    Box(
        modifier = modifier
            .scale(containerScale.value * breathe)
            .alpha(containerAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val outerPath = buildRoundedContour(NosvedOuterContour, w, h, w * 0.028f)
            val barsHolePath = buildRoundedContour(NosvedBarsHoleContour, w, h, w * 0.02f)
            val triHolePath = buildRoundedContour(NosvedTriangleHoleContour, w, h, w * 0.014f)

            // ---- Phase 1: line-drawing sketch reveal ----
            if (drawProgress.value < 1f || fillReveal.value < 1f) {
                val outerP = stageProgress(drawProgress.value, 0f, 0.5f)
                val barsP = stageProgress(drawProgress.value, 0.38f, 0.8f)
                val triP = stageProgress(drawProgress.value, 0.68f, 1f)
                val sketchAlpha = 1f - fillReveal.value

                val strokeStyle = Stroke(strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(trimmedSegment(outerPath, outerP), color.copy(alpha = sketchAlpha), style = strokeStyle)
                drawPath(trimmedSegment(barsHolePath, barsP), color.copy(alpha = sketchAlpha), style = strokeStyle)
                drawPath(trimmedSegment(triHolePath, triP), color.copy(alpha = sketchAlpha), style = strokeStyle)

                // leading glow "pen tip" following whichever stroke is currently drawing
                val tip = when {
                    outerP in 0.001f..0.999f -> tipPosition(outerPath, outerP)
                    barsP in 0.001f..0.999f -> tipPosition(barsHolePath, barsP)
                    triP in 0.001f..0.999f -> tipPosition(triHolePath, triP)
                    else -> null
                }
                tip?.let { p ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.85f * sketchAlpha), Color.Transparent),
                            center = p,
                            radius = glowRadiusPx * 2.4f
                        ),
                        radius = glowRadiusPx * 2.4f,
                        center = p
                    )
                    drawCircle(color = color.copy(alpha = sketchAlpha), radius = glowRadiusPx * 0.55f, center = p)
                }
            }

            // ---- Phase 2: crisp solid mark + idle shine sweep ----
            if (fillReveal.value > 0f) {
                val combined = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addPath(outerPath)
                    addPath(barsHolePath)
                    addPath(triHolePath)
                }
                clipPath(combined) {
                    drawRect(color = color.copy(alpha = fillReveal.value))
                    if (fillReveal.value >= 1f) {
                        val bandWidth = w * 0.5f
                        val startX = -bandWidth + shimmer * (w + bandWidth * 2)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                start = Offset(startX, 0f),
                                end = Offset(startX + bandWidth, h)
                            )
                        )
                    }
                }
            }
        }
    }
}