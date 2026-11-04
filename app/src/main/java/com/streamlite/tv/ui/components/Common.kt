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
import coil.compose.AsyncImage
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
    // 2026-11-04 (Point 11): D-pad focus MUST be visible — scale up + accent border on focus.
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1f, label = "menuCardScale")
    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = SlAccent,
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

@Composable
fun ChannelLogo(url: String, modifier: Modifier = Modifier) {
    if (url.isNotEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier.background(SlCard),
            contentAlignment = Alignment.Center,
        ) {
            Text("TV", color = SlTextDim, fontSize = 14.sp)
        }
    }
}

@Composable
fun PosterImage(url: String, modifier: Modifier = Modifier) {
    if (url.isNotEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(SlCard),
            contentAlignment = Alignment.Center,
        ) {
            Text("No image", color = SlTextDim, fontSize = 13.sp)
        }
    }
}
