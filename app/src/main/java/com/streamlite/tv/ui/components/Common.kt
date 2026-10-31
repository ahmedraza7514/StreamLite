package com.streamlite.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
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
        Surface(
            onClick = onRetry,
            colors = ClickableSurfaceDefaults.colors(
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) SlAccent else SlSurface,
            contentColor = if (selected) Color.Black else SlText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
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
    Surface(
        onClick = onClick,
        modifier = modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SlSurface,
            contentColor = SlText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(),
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
