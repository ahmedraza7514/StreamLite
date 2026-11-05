package com.streamlite.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlCard
import com.streamlite.tv.ui.theme.SlSurface
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = SlText,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SlAccent)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = Color(0xFFFF6B6B),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
        Card(
            onClick = onRetry,
            colors = CardDefaults.cardColors(
                containerColor = SlSurface,
                contentColor = SlText,
            ),
        ) {
            Text("Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
        }
    }
}

@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SlAccent else SlSurface,
            contentColor = if (selected) Color.Black else SlText,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/** Big navigation card used on the home screen. */
@Composable
fun MenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2026-11-04 (Point 11): D-pad focus MUST be visible — scale up + WHITE border on focus.
    // 2026-11-04 (Batch 4, Point 6+8): border changed from accent blue to WHITE (user: cursor not visible).
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1f, label = "menuCardScale")
    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) SlCard else SlSurface,
            contentColor = SlText,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = SlText)
            Text(subtitle, fontSize = 15.sp, color = SlTextDim, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// 2026-11-05 (Batch 6, Point 15): bare AsyncImage showed a BLANK box whenever
// a logo failed to load (dead URL, slow 4 Mbps timeout) — that is why most
// channel icons never appeared. Now: sanitized URL + crossfade + a fallback
// tile with the channel's first letter, so a box is NEVER blank.
// 2026-11-05 (Batch 8 fix): the channel box must NEVER show TEXT — the
// channel's own image thumbnail must load (User-Agent + retry in
// StreamLiteApp). If a logo truly cannot load, show a TV icon image,
// never a letter.
@Composable
fun ChannelLogo(url: String, modifier: Modifier = Modifier, name: String = "") {
    ThumbImage(url, name, modifier, ContentScale.Fit, "TV", iconFallback = true)
}

@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier, name: String = "") {
    ThumbImage(url, name, modifier, ContentScale.Crop, "No image")
}

/** Panels sometimes return icon URLs with spaces or trailing junk — Coil
 *  fails those silently, leaving a blank box. Trim + encode spaces. */
private fun cleanImageUrl(url: String): String {
    val t = url.trim()
    if (t.isEmpty()) return ""
    return t.replace(" ", "%20")
}

@Composable
private fun ThumbImage(
    url: String,
    name: String,
    modifier: Modifier,
    contentScale: ContentScale,
    placeholderText: String,
    iconFallback: Boolean = false,
) {
    val clean = cleanImageUrl(url)
    if (clean.isEmpty()) {
        if (iconFallback) IconFallback(modifier) else FallbackTile(name, placeholderText, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(clean)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                // Channel boxes: icon image while loading — never text.
                if (iconFallback) IconFallback(Modifier.fillMaxSize())
                else FallbackTile(name, placeholderText, Modifier.fillMaxSize())
            }
            is AsyncImagePainter.State.Error,
            is AsyncImagePainter.State.Empty -> {
                if (iconFallback) IconFallback(Modifier.fillMaxSize())
                else FallbackTile(name, placeholderText, Modifier.fillMaxSize())
            }
            else -> {
                SubcomposeAsyncImageContent()
            }
        }
    }
}

@Composable
private fun FallbackTile(name: String, placeholderText: String, modifier: Modifier) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: placeholderText
    Box(
        modifier = modifier.background(SlCard),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = SlTextDim, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Generic TV icon image — the channel-box fallback. An IMAGE, never text. */
@Composable
private fun IconFallback(modifier: Modifier) {
    Box(
        modifier = modifier.background(SlCard),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.LiveTv,
            contentDescription = null,
            tint = SlTextDim,
            modifier = Modifier.size(48.dp),
        )
    }
}
