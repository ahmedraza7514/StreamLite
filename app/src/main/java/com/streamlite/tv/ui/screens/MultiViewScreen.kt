package com.streamlite.tv.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.LiveStream
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim

/**
 * 4-Screen multiview: pick 4 live channels, watch them all at once in a 2x2 grid.
 * Tap a quadrant to hear its audio (others muted).
 */
@Composable
fun MultiViewScreen(session: LoginSession) {
    var selected by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var started by remember { mutableStateOf(false) }

    if (!started) {
        ChannelPicker(
            session = session,
            selected = selected,
            onToggle = { ch ->
                selected = if (selected.contains(ch)) {
                    selected - ch
                } else if (selected.size < 4) {
                    selected + ch
                } else {
                    selected
                }
            },
            onStart = { started = true },
        )
    } else {
        QuadGrid(channels = selected.take(4), session = session)
    }
}

@Composable
private fun ChannelPicker(
    session: LoginSession,
    selected: List<LiveStream>,
    onToggle: (LiveStream) -> Unit,
    onStart: () -> Unit,
) {
    val channels = remember { DataRepository.liveStreams ?: emptyList() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, channels) {
        if (query.isBlank()) channels
        else channels.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SlBackground).padding(24.dp),
    ) {
        Text(
            "4 Screen — Pick 4 Channels",
            style = MaterialTheme.typography.headlineSmall,
            color = SlText,
        )
        Text(
            "${selected.size} / 4 selected — tap channels to pick",
            fontSize = 14.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        // Search
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1E2E), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = SlText, fontSize = 15.sp),
            singleLine = true,
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search channels...", color = SlTextDim, fontSize = 14.sp)
                inner()
            },
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.streamId }) { ch ->
                val isSel = selected.contains(ch)
                Card(
                    onClick = { onToggle(ch) },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) SlAccent else Color(0xFF0F1E2E),
                        contentColor = if (isSel) Color.Black else SlText,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isSel) "✓ " else "",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(ch.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            enabled = selected.size == 4,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SlAccent,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF333333),
                disabledContentColor = SlTextDim,
            ),
        ) {
            Text(
                if (selected.size == 4) "Start Watching" else "Pick ${4 - selected.size} more",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun QuadGrid(channels: List<LiveStream>, session: LoginSession) {
    val context = LocalContext.current
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    // One ExoPlayer per quadrant
    val players = remember {
        List(4) { idx ->
            ExoPlayer.Builder(context).build().apply {
                val ch = channels.getOrNull(idx)
                if (ch != null) {
                    val url = client.liveHlsUrl(ch.streamId)
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
                // Only first quadrant has audio initially
                volume = if (idx == 0) 1f else 0f
            }
        }
    }
    var activeIdx by remember { mutableStateOf(0) }

    // Update volumes when active quadrant changes
    players.forEachIndexed { idx, player ->
        player.volume = if (idx == activeIdx) 1f else 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            players.forEach { it.release() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top row: 2 quadrants
        Row(modifier = Modifier.weight(1f)) {
            QuadPlayer(
                player = players[0],
                title = channels.getOrNull(0)?.name ?: "",
                isActive = activeIdx == 0,
                onTap = { activeIdx = 0 },
                modifier = Modifier.weight(1f),
            )
            QuadPlayer(
                player = players[1],
                title = channels.getOrNull(1)?.name ?: "",
                isActive = activeIdx == 1,
                onTap = { activeIdx = 1 },
                modifier = Modifier.weight(1f),
            )
        }
        // Bottom row: 2 quadrants
        Row(modifier = Modifier.weight(1f)) {
            QuadPlayer(
                player = players[2],
                title = channels.getOrNull(2)?.name ?: "",
                isActive = activeIdx == 2,
                onTap = { activeIdx = 2 },
                modifier = Modifier.weight(1f),
            )
            QuadPlayer(
                player = players[3],
                title = channels.getOrNull(3)?.name ?: "",
                isActive = activeIdx == 3,
                onTap = { activeIdx = 3 },
                modifier = Modifier.weight(1f),
            )
        }
        // Hint bar
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Tap a screen to hear its audio  •  🔊 = active",
                fontSize = 13.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun QuadPlayer(
    player: ExoPlayer,
    title: String,
    isActive: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .border(
                width = if (isActive) 3.dp else 1.dp,
                color = if (isActive) SlAccent else Color(0xFF333333),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable { onTap() },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setVideoSurfaceType(PlayerView.VIDEO_SURFACE_TYPE_TEXTURE_VIEW)
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Title overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                (if (isActive) "🔊 " else "") + title,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}
