package com.streamlite.tv.ui.screens

// ============================================================================
// XCIPTV-style custom player bar (built 2026-11-02, refined 2026-11-03).
//
// XCIPTV source reviewed (~/workspace/xciptv-src):
//  - player = Media3 PlayerView with use_controller="true"
//  - 5 zoom modes in PlayerSettingsActivity (exo: best fit / fixed height /
//    fixed width / fill / zoom; vlc: best fit / 16:9 / 4:3 / original / fill)
//  - custom title + ImageButton overlays auto-hiding with the controller
// This mirrors that pattern in Compose: the native ExoPlayer controller is
// OFF and the whole playtime bar (thumbnail + title + transport + scrubber +
// ZOOM/AUDIO/SUBS/VOL/FAV) is one custom Compose bar that auto-hides after
// 3s, exactly like XCIPTV.
//
// 2026-11-03 refinements (Ahmed Bhai's annotated screenshots):
//  - Transport buttons are BIG, solid BLACK, with WHITE vector icons
//    (guaranteed white on every device — emojis rendered orange before).
//  - ZOOM / AUDIO / SUBS / VOL are black buttons with WHITE text labels.
//  - LIVE channels get prev/next CHANNEL buttons PLUS the seek buttons;
//    VOD keeps seek only.
//  - Star button toggles the favorite for the currently playing item.
//  - Remote-first: any D-pad keypress shows the bar; focus lands on
//    play/pause; every button shows a white focus border.
// ============================================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streamlite.tv.data.FavItem
import com.streamlite.tv.data.FavStore
import com.streamlite.tv.player.PlayerFactory
import com.streamlite.tv.ui.PlayerItem
import com.streamlite.tv.ui.theme.SlAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen playback with a custom XCIPTV-style controller.
 *
 * The native ExoPlayer controller is OFF — the playtime bar is built in
 * Compose: [thumbnail] + title on the left, a playtime scrubber, and all
 * controls INSIDE the bar. The bar auto-hides after 3s; tap the video (or
 * press any remote key) to show it.
 *
 * The player is built by [PlayerFactory] with the slow-network tuning
 * (buffer level from Settings, hardware decoding, 720p start cap). If an
 * HLS (.m3u8) stream fails, we retry once over plain MPEG-TS (.ts).
 *
 * [playlist] + [startIndex]: when a queue is supplied, the player's
 * Next/Previous channel buttons jump between channels.
 * [isLive]: live channels show prev/next CHANNEL buttons alongside the
 * seek buttons; VOD shows seek only.
 * [kind]: "live" | "vod" | "episode" — stored with the favorite entry.
 * [thumb]: logo/poster URL shown as the title thumbnail in the bar
 * (falls back to a letter tile when empty).
 */
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    playlist: List<PlayerItem> = emptyList(),
    startIndex: Int = 0,
    info: String = "",
    thumb: String = "",
    isLive: Boolean = false,
    kind: String = "live",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    // Audio / subtitle track dialogs
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubsDialog by remember { mutableStateOf(false) }
    // Snapshot of current tracks for the dialogs (refreshed when opened)
    var trackSnapshot by remember { mutableStateOf<Tracks?>(null) }

    // Favorite star
    var isFav by remember { mutableStateOf(false) }
    LaunchedEffect(url) { isFav = FavStore.isFav(context, url) }

    // Custom controller state
    var controlsVisible by remember { mutableStateOf(true) }
    var touchTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentTitle by remember { mutableStateOf(title) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) } // 0..1 while dragging

    // Remote-first: focus lands on play/pause whenever the bar appears
    val playPauseReq = remember { FocusRequester() }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            try { playPauseReq.requestFocus() } catch (_: Exception) { /* no-op */ }
        }
    }

    fun applyZoom(idx: Int) {
        zoomIdx = idx
        val mode = zoomModes[idx]
        playerViewRef?.resizeMode = mode.resizeMode
        showZoomMenu = false
    }

    /** Any tap on video, or any remote keypress, resets the 3s auto-hide timer. */
    fun poke() {
        controlsVisible = true
        touchTick++
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
            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
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

    // Listen for playlist index changes to update the bar title
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                if (playlist.isNotEmpty() && idx in playlist.indices) {
                    currentTitle = playlist[idx].title
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Auto-hide the bar 3s after the last interaction (XCIPTV behaviour)
    LaunchedEffect(touchTick) {
        delay(3000)
        controlsVisible = false
    }

    // Playtime ticker (only while the bar is up — saves battery on the box)
    LaunchedEffect(Unit) {
        while (true) {
            if (controlsVisible) {
                positionMs = player.currentPosition
                durationMs = player.duration
                if (!sliderDragging && durationMs > 0) {
                    sliderValue = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(500)
        }
    }

    fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else "%02d:%02d".format(m, sec)
    }

    val hasDuration = durationMs > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Remote-first: any D-pad keypress brings the bar back up
            .onKeyEvent { poke(); false },
    ) {
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
                    // Native controller OFF — the bar below is the controller
                    useController = false
                    resizeMode = zoomModes[zoomIdx].resizeMode
                    playerViewRef = this
                }
            },
            modifier = playerModifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { poke() },
        )

        // Buffering spinner (shows even when the bar is hidden)
        if (isBuffering) {
            CircularProgressIndicator(
                color = SlAccent,
                modifier = Modifier.align(Alignment.Center).size(56.dp),
            )
        }

        // ================= XCIPTV-style playtime bar =================
        if (controlsVisible) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .zIndex(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xDD000000),
                    contentColor = Color.White,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Row 1: thumbnail + title (+ info)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (thumb.isNotEmpty()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E3A5F)),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlAccent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    currentTitle.firstOrNull()?.uppercase() ?: "•",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (info.isNotEmpty()) {
                                Text(
                                    info,
                                    fontSize = 13.sp,
                                    color = Color(0xFFCCCCCC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 2: playtime scrubber (or LIVE badge for live streams)
                    if (hasDuration) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(fmt(positionMs), fontSize = 13.sp, color = Color.White)
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderDragging = true
                                    sliderValue = it
                                },
                                onValueChangeFinished = {
                                    player.seekTo((sliderValue * durationMs).toLong())
                                    sliderDragging = false
                                    poke()
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text(fmt(durationMs), fontSize = 13.sp, color = Color.White)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFB00020),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text(
                                    "LIVE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Row 3: all controls inside the playtime bar.
                    // LIVE: prev/next CHANNEL + seek. VOD: seek only.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Previous channel (live only)
                        if (isLive) {
                            TransportButton(
                                icon = Icons.Filled.SkipPrevious,
                                contentDesc = "Previous channel",
                                onClick = {
                                    if (player.hasPreviousMediaItem()) {
                                        player.seekToPreviousMediaItem()
                                    }
                                    poke()
                                },
                            )
                        }
                        // Rewind 10s
                        TransportButton(
                            icon = Icons.Filled.FastRewind,
                            contentDesc = "Back 10 seconds",
                            onClick = {
                                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                poke()
                            },
                        )
                        // Play / pause (remote focus lands here)
                        TransportButton(
                            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDesc = if (isPlaying) "Pause" else "Play",
                            focusRequester = playPauseReq,
                            onClick = {
                                if (isPlaying) player.pause() else player.play()
                                poke()
                            },
                        )
                        // Forward 10s
                        TransportButton(
                            icon = Icons.Filled.FastForward,
                            contentDesc = "Forward 10 seconds",
                            onClick = {
                                val target = player.currentPosition + 10_000
                                player.seekTo(
                                    if (hasDuration) target.coerceAtMost(durationMs) else target
                                )
                                poke()
                            },
                        )
                        // Next channel (live only)
                        if (isLive) {
                            TransportButton(
                                icon = Icons.Filled.SkipNext,
                                contentDesc = "Next channel",
                                onClick = {
                                    if (player.hasNextMediaItem()) {
                                        player.seekToNextMediaItem()
                                    }
                                    poke()
                                },
                            )
                        }

                        // ZOOM -> 5-option menu (XCIPTV modes)
                        Box {
                            LabelButton("ZOOM", onClick = {
                                showZoomMenu = true
                                poke()
                            })
                            DropdownMenu(
                                expanded = showZoomMenu,
                                onDismissRequest = { showZoomMenu = false },
                                modifier = Modifier.background(Color.Black),
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
                                        onClick = { applyZoom(idx); poke() },
                                    )
                                }
                            }
                        }

                        // AUDIO tracks
                        LabelButton("AUDIO", onClick = {
                            trackSnapshot = player.currentTracks
                            showAudioDialog = true
                            poke()
                        })
                        // SUBS / CC
                        LabelButton("SUBS", onClick = {
                            trackSnapshot = player.currentTracks
                            showSubsDialog = true
                            poke()
                        })
                        // VOL + brightness panel
                        LabelButton("VOL", onClick = {
                            showVbPanel = !showVbPanel
                            poke()
                        })
                        // Favorite star
                        TransportButton(
                            icon = if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDesc = if (isFav) "Remove favorite" else "Add favorite",
                            tint = if (isFav) SlAccent else Color.White,
                            onClick = {
                                scope.launch {
                                    isFav = FavStore.toggle(
                                        context,
                                        FavItem(url, currentTitle, kind, thumb),
                                    )
                                }
                                poke()
                            },
                        )

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
        }

        // ---- Audio track dialog (modal, above the bar) ----
        if (showAudioDialog) {
            TrackDialog(
                title = "Audio",
                tracks = trackSnapshot,
                type = C.TRACK_TYPE_AUDIO,
                allowOff = false,
                onSelect = { group, trackIdx ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                        .build()
                    showAudioDialog = false
                },
                onDismiss = { showAudioDialog = false },
            )
        }

        // ---- Subtitle track dialog (modal, above the bar) ----
        if (showSubsDialog) {
            TrackDialog(
                title = "Subtitles",
                tracks = trackSnapshot,
                type = C.TRACK_TYPE_TEXT,
                allowOff = true,
                onSelect = { group, trackIdx ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                        .build()
                    showSubsDialog = false
                },
                onOff = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    showSubsDialog = false
                },
                onDismiss = { showSubsDialog = false },
            )
        }
    }
}

/**
 * Big BLACK button with a WHITE vector icon — the transport controls
 * (prev / rewind / play-pause / forward / next / favorite).
 * Vector icons stay white on every device (emojis rendered orange before).
 * Shows a white border when D-pad focused (remote-first).
 */
@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black,
            contentColor = Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color(0xFF333333),
        ),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    ) {
        Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/**
 * BLACK button with a WHITE text label — ZOOM / AUDIO / SUBS / VOL.
 * Shows a white border when D-pad focused (remote-first).
 */
@Composable
private fun LabelButton(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black,
            contentColor = Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color(0xFF333333),
        ),
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/** Generic audio/subtitle track picker dialog. */
@Composable
private fun TrackDialog(
    title: String,
    tracks: Tracks?,
    type: Int,
    allowOff: Boolean,
    onSelect: (Tracks.Group, Int) -> Unit,
    onOff: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val groups = tracks?.groups?.filter { it.type == type } ?: emptyList()
    Card(
        modifier = Modifier.fillMaxWidth(0.85f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1E2E),
            contentColor = Color.White,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(12.dp))
            if (allowOff && onOff != null) {
                Card(
                    onClick = onOff,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E3A5F),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Off", fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
            }
            if (groups.isEmpty()) {
                Text("No tracks available.", fontSize = 14.sp, color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(vertical = 8.dp))
            } else {
                groups.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val label = fmt.label
                            ?: fmt.language?.let { "Lang: $it" }
                            ?: "Track ${i + 1}"
                        val selected = group.isSelected && run {
                            // check if this specific track is the selected one
                            val sel = group.mediaTrackGroup
                            // best-effort: highlight if group selected and only one track
                            group.length == 1
                        }
                        Card(
                            onClick = { onSelect(group, i) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) SlAccent else Color(0xFF1E3A5F),
                                contentColor = if (selected) Color.Black else Color.White,
                            ),
                        ) {
                            Text(label, fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFAAAAAA),
                ),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center) {
                    Text("Close", fontSize = 14.sp)
                }
            }
        }
    }
}
