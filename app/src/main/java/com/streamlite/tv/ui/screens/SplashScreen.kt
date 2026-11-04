package com.streamlite.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamlite.tv.R

// 2026-11-04: Startup splash — plays res/raw/splash.mp4 full-screen, then
// calls onFinished(). No controls, no skip UI; the video is ~10s.

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash}")
            setMediaItem(MediaItem.fromUri(uri))
            // When the video ends (or errors), move on — never trap the user.
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) onFinished()
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    onFinished()
                }
            })
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { exo.release() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
