package com.streamlite.tv.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.player.PlayerFactory
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 4-Screen multiview: pick 4 live channels, watch them all at once in a 2x2 grid.
 *
 * 2026-11-03 (Ahmed Bhai's annotated screenshot):
 * - Tap a quadrant -> pauses/plays THAT quadrant independently.
 * - Speaker icon on the side of each quadrant -> mute/unmute (the audible
 *   quadrant switches here too; only one quadrant has audio at a time).
 * - Players are built by PlayerFactory (tuned buffers + 720p cap) so the
 *   2GB TV box doesn't choke on 4 simultaneous streams.
 */
@Composable
fun MultiViewScreen(
    session: LoginSession,
    // 2026-11-04 (Batch 4, Point 9): back from 4-screen returns to the last
    // screen/category — the app must never fully close from here.
    onBack: () -> Unit = {},
) {
    var selected by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var started by remember { mutableStateOf(false) }

    BackHandler { onBack() }

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
    // 2026-11-04 (Batch 2): on the TV box DataRepository.liveStreams can be
    // null/empty when the picker opens (search found nothing, nothing could
    // be added). Fall back to a direct Xtream fetch so the picker always has
    // channels — same bulk call LiveTvScreen uses.
    var channels by remember { mutableStateOf(DataRepository.liveStreams ?: emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (channels.isEmpty() && session.type == LoginType.XTREAM) {
            try {
                val client = XtreamClient(session.server, session.username, session.password)
                channels = withContext(Dispatchers.IO) { client.liveStreamsAll() }
            } catch (e: Exception) {
                loadError = e.message ?: "Could not load channels."
            }
        }
    }
    var query by remember { mutableStateOf("news") }
    // 2026-11-05 (Batch 8, Point 4): separate OVERALL category search below
    // the channel search — filters channels by their category name.
    var catQuery by remember { mutableStateOf("") }
    val categories = remember { DataRepository.liveCategories ?: emptyList() }
    val filtered = remember(query, catQuery, channels, categories) {
        var result = channels
        if (query.isNotBlank()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }
        if (catQuery.isNotBlank()) {
            val catIds = categories.filter { it.name.contains(catQuery, ignoreCase = true) }
                .map { it.id }.toSet()
            result = result.filter { it.categoryId in catIds }
        }
        result
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

        // Overall category search (separate field, below the channel search)
        androidx.compose.foundation.text.BasicTextField(
            value = catQuery,
            onValueChange = { catQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1E2E), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = SlText, fontSize = 15.sp),
            singleLine = true,
            decorationBox = { inner ->
                if (catQuery.isEmpty()) Text("Search category...", color = SlTextDim, fontSize = 14.sp)
                inner()
            },
        )
        Spacer(Modifier.height(12.dp))

        if (loadError != null && channels.isEmpty()) {
            Text(
                loadError!!,
                fontSize = 14.sp,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.streamId }) { ch ->
                val isSel = selected.contains(ch)
                // 2026-11-05 (Batch 8, Point 4): WHITE 3dp border where the
                // remote selection lands — visible from far, like other screens.
                var rowFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = { onToggle(ch) },
                    modifier = Modifier
                        .onFocusChanged { rowFocused = it.isFocused }
                        .border(
                            width = if (rowFocused) 3.dp else 0.dp,
                            color = if (rowFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        ),
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
    // One tuned player per quadrant (PlayerFactory: small buffers + 720p cap
    // so 4 streams don't OOM the 2GB TV box)
    val players = remember {
        List(4) { idx ->
            PlayerFactory.create(context).apply {
                val ch = channels.getOrNull(idx)
                if (ch != null) {
                    val url = client.liveHlsUrl(ch.streamId)
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
                // 2026-11-05 (Batch 8, Point 6): ALL 4 quadrants audible
                // by default — every screen has sound; the speaker button
                // mutes/unmutes only THAT quadrant.
                volume = 1f
            }
        }
    }
    // Per-quadrant mute state (independent for each screen) — all start audible.
    val mutedStates = remember { List(4) { mutableStateOf(false) } }
    // Per-quadrant pause state (independent for each screen)
    val pausedStates = remember { List(4) { mutableStateOf(false) } }

    fun togglePause(idx: Int) {
        val p = players[idx]
        p.playWhenReady = !p.playWhenReady
        pausedStates[idx].value = !p.playWhenReady
    }

    fun toggleMute(idx: Int) {
        mutedStates[idx].value = !mutedStates[idx].value
        players[idx].volume = if (mutedStates[idx].value) 0f else 1f
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
                isAudible = !mutedStates[0].value,
                isPaused = pausedStates[0].value,
                onTap = { togglePause(0) },
                onToggleMute = { toggleMute(0) },
                modifier = Modifier.weight(1f),
            )
            QuadPlayer(
                player = players[1],
                title = channels.getOrNull(1)?.name ?: "",
                isAudible = !mutedStates[1].value,
                isPaused = pausedStates[1].value,
                onTap = { togglePause(1) },
                onToggleMute = { toggleMute(1) },
                modifier = Modifier.weight(1f),
            )
        }
        // Bottom row: 2 quadrants
        Row(modifier = Modifier.weight(1f)) {
            QuadPlayer(
                player = players[2],
                title = channels.getOrNull(2)?.name ?: "",
                isAudible = !mutedStates[2].value,
                isPaused = pausedStates[2].value,
                onTap = { togglePause(2) },
                onToggleMute = { toggleMute(2) },
                modifier = Modifier.weight(1f),
            )
            QuadPlayer(
                player = players[3],
                title = channels.getOrNull(3)?.name ?: "",
                isAudible = !mutedStates[3].value,
                isPaused = pausedStates[3].value,
                onTap = { togglePause(3) },
                onToggleMute = { toggleMute(3) },
                modifier = Modifier.weight(1f),
            )
        }
        // Hint bar
        Box(
            modifier = Modifier.fillMaxWidth().background(Color(0xAA000000)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Tap a screen to pause it  •  tap the speaker icon to mute its audio",
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
    isAudible: Boolean,
    isPaused: Boolean,
    onTap: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var quadFocused by remember { mutableStateOf(false) }
    var speakerFocused by remember { mutableStateOf(false) }
    var playBtnFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .border(
                // 2026-11-04 (Batch 4, Point 6+10): WHITE 3dp border where the
                // D-pad cursor goes — must be clearly visible on the TV box.
                width = when {
                    quadFocused -> 3.dp
                    isAudible -> 3.dp
                    else -> 1.dp
                },
                color = when {
                    quadFocused -> Color.White
                    isAudible -> SlAccent
                    else -> Color(0xFF333333)
                },
                shape = RoundedCornerShape(4.dp),
            )
            .onFocusChanged { quadFocused = it.isFocused }
            .clickable { onTap() },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    // Fill the box completely (no black bars)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Paused indicator (white vector icon — always visible, every device)
        if (isPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        // Title overlay (top-start)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                (if (isAudible) "🔊 " else "") + title,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
        // Speaker mute/unmute button (top-end, black button, white icon)
        Card(
            onClick = onToggleMute,
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black,
                contentColor = Color.White,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = if (speakerFocused) 2.dp else 1.dp,
                color = if (speakerFocused) Color.White else Color(0xFF555555),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .onFocusChanged { speakerFocused = it.isFocused },
        ) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAudible) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (isAudible) "Mute" else "Unmute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 2026-11-04 (Batch 4, Point 10): play/pause button, visible when the
        // quadrant has D-pad focus — D-pad OK here toggles play/pause for
        // that quadrant (same as OK on the quadrant itself).
        if (quadFocused) {
            Card(
                onClick = onTap,
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (playBtnFocused) 2.dp else 1.dp,
                    color = if (playBtnFocused) Color.White else Color(0xFF555555),
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 52.dp)
                    .onFocusChanged { playBtnFocused = it.isFocused },
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (isPaused) "Play" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
