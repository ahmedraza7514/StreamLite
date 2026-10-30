package com.streamlite.tv.player

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Builds the ExoPlayer instance, tuned for slow networks (the whole point of StreamLite).
 *
 * - Large buffer window (15s min / 60s max) so playback rarely stalls on a 4 Mbps line.
 * - Hardware decoding preferred with software fallback, so weak TV boxes keep up.
 * - Adaptive track selection capped at 720p to start: avoids the initial overshoot
 *   where the player grabs a 1080p rendition the line cannot sustain. The selector
 *   still steps down further (480p/360p) when bandwidth dips.
 *
 * Trade-off (accepted): zapping between channels takes a few seconds while the
 * buffer fills. Smooth playback matters more than fast zapping here.
 */
object PlayerFactory {

    fun create(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1280, 720)
                    .setForceHighestSupportedBitrate(false),
            )
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            .build()
    }
}
