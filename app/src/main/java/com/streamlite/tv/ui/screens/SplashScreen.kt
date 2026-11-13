package com.streamlite.tv.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.streamlite.tv.R
import kotlinx.coroutines.delay

// 2026-11-04: Startup splash — plays res/raw/splash.mp4 full-screen, then
// calls onFinished(). If the video fails, shows the app name as fallback
// for 3s so the splash is never a black flash.

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var videoFailed by remember { mutableStateOf(false) }
    // v2.15.0: video ka pehla frame aya ya nahi — poster isi se hatta hai.
    var firstFrame by remember { mutableStateOf(false) }

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash}")
            setMediaItem(MediaItem.fromUri(uri))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) onFinished()
                }
                override fun onRenderedFirstFrame() {
                    firstFrame = true
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    videoFailed = true
                }
            })
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { exo.release() }
    }

    // Fallback: if video fails, show name 3s then move on.
    // Safety: never trap the user longer than 15s.
    LaunchedEffect(videoFailed) {
        if (videoFailed) {
            delay(3000)
            onFinished()
        }
    }
    LaunchedEffect(Unit) {
        delay(15000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (!videoFailed) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = false
                        // 2026-11-05 (Batch 8, Point 7): FILL the screen edge to
                        // edge (fit to page) — no black bars left/right.
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 2026-11-13 (v2.15.0): POSTER — video ka pehla frame ane tak
        // app ka logo + naam screen par rehta hai, taake app khulte hi
        // splash FORAN nazar aye. Pehle video tayyar hone tak sirf
        // kaali/khaali screen rehti thi, is liye app sust lagti thi.
        // Pehla frame ate hi poster khud hat jata hai; video fail ho
        // to poster hi fallback hai (3s wala purana timer barqarar).
        if (!firstFrame) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "A.R. Xtream IPTV logo",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "A.R. Xtream IPTV",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
