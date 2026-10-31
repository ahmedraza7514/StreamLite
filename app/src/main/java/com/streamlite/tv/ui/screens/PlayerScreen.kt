package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.streamlite.tv.player.PlayerFactory
import com.streamlite.tv.ui.PlayerItem
import com.streamlite.tv.ui.theme.SlAccent

/**
 * Full-screen playback. The player is built by [PlayerFactory] with the
 * slow-network tuning (large buffer, hardware decoding, 720p start cap).
 *
 * If an HLS (.m3u8) stream fails, we retry once over plain MPEG-TS (.ts),
 * which some panels serve more reliably.
 *
 * [playlist] + [startIndex]: when a queue is supplied, the player's
 * Next/Previous buttons jump between channels instead of doing nothing.
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    playlist: List<PlayerItem> = emptyList(),
    startIndex: Int = 0,
    info: String = "",
) {
    val context = LocalContext.current
    val triedTsFallback = remember { mutableStateOf(false) }

    // XCIPTV-style zoom menu — all 5 modes actually change the picture
    data class ZoomMode(val label: String, val aspect: Float?, val resizeMode: Int)
    val zoomModes = remember {
        listOf(
            ZoomMode("Zoom: best fit", null, AspectRatioFrameLayout.RESIZE_MODE_FIT),
            ZoomMode("Zoom: 16:9", 16f / 9f, AspectRatioFrameLayout.RESIZE_MODE_FILL),
            ZoomMode("Zoom: 4:3", 4f / 3f, AspectRatioFrameLayout.RESIZE_MODE_FILL),
            ZoomMode("Zoom: original size", null, AspectRatioFrameLayout.RESIZE_MODE_FIT),
            ZoomMode("Zoom: Fill", null, AspectRatioFrameLayout.RESIZE_MODE_FILL),
        )
    }
    var zoomIdx by remember { mutableIntStateOf(0) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Volume & brightness
    var showVbPanel by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1f) }
    var brightness by remember { mutableStateOf(-1f) } // -1 = system default
    val activity = context as? android.app.Activity

    fun applyZoom(idx: Int) {
        zoomIdx = idx
        val mode = zoomModes[idx]
        playerViewRef?.resizeMode = mode.resizeMode
        showZoomMenu = false
    }

    val player = remember(url, playlist) {
        val exo: ExoPlayer = PlayerFactory.create(context)
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!triedTsFallback.value && url.endsWith(".m3u8")) {
                    triedTsFallback.value = true
                    exo.setMediaItem(MediaItem.fromUri(url.dropLast(5) + ".ts"))
                    exo.prepare()
                    exo.play()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                triedTsFallback.value = false
            }
        })
        if (playlist.isNotEmpty()) {
            val items = playlist.map { MediaItem.fromUri(it.url) }
            val safeIndex = startIndex.coerceIn(0, items.lastIndex)
            exo.setMediaItems(items, safeIndex, 0)
        } else {
            exo.setMediaItem(MediaItem.fromUri(url))
        }
        exo.prepare()
        exo.play()
        exo
    }

    DisposableEffect(url) {
        onDispose { player.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Title overlay (top-left), auto-hides with controller
        var showTitle by remember { mutableStateOf(true) }
        if (showTitle) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xAA000000),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                if (info.isNotEmpty()) {
                    Text(
                        info,
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA),
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                    )
                }
            }
        }

        val currentAspect = zoomModes[zoomIdx].aspect
        val playerModifier = if (currentAspect != null) {
            Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .aspectRatio(currentAspect)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    resizeMode = zoomModes[zoomIdx].resizeMode
                    playerViewRef = this
                }
            },
            modifier = playerModifier,
        )

        // Top-right controls: ZOOM + Volume/Brightness
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ZOOM button -> 5-option menu
            Box {
                Card(
                    onClick = { showZoomMenu = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xAA000000),
                        contentColor = Color.White,
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("ZOOM", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                DropdownMenu(
                    expanded = showZoomMenu,
                    onDismissRequest = { showZoomMenu = false },
                ) {
                    zoomModes.forEachIndexed { idx, mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.label,
                                    color = if (idx == zoomIdx) SlAccent else Color.White,
                                    fontSize = 15.sp,
                                )
                            },
                            onClick = { applyZoom(idx) },
                        )
                    }
                }
            }

            // Volume/Brightness toggle button
            Card(
                onClick = { showVbPanel = !showVbPanel },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xAA000000),
                    contentColor = Color.White,
                ),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("VOL", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Volume & brightness sliders panel
            if (showVbPanel) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xDD000000),
                        contentColor = Color.White,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp).width(200.dp)) {
                        Text("Volume", fontSize = 14.sp, color = Color.White)
                        Slider(
                            value = volume,
                            onValueChange = {
                                volume = it
                                player.volume = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Brightness", fontSize = 14.sp, color = Color.White)
                        Slider(
                            value = if (brightness < 0) 0.5f else brightness,
                            onValueChange = {
                                brightness = it
                                activity?.window?.let { win ->
                                    val lp = win.attributes
                                    lp.screenBrightness = it
                                    win.attributes = lp
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
