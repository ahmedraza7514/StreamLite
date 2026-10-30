package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamlite.tv.player.PlayerFactory

/**
 * Full-screen playback. The player is built by [PlayerFactory] with the
 * slow-network tuning (large buffer, hardware decoding, 720p start cap).
 *
 * If an HLS (.m3u8) stream fails, we retry once over plain MPEG-TS (.ts),
 * which some panels serve more reliably.
 */
@Composable
fun PlayerScreen(url: String, title: String) {
    val context = LocalContext.current
    val triedTsFallback = remember { mutableStateOf(false) }

    val player = remember(url) {
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
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.play()
        exo
    }

    DisposableEffect(url) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 3000
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    )
}
