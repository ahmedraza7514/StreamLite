package com.streamlite.tv.player

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streamlite.tv.data.BufferPrefStore

/**
 * Builds the ExoPlayer instance, tuned for slow networks (the whole point of StreamLite).
 *
 * Buffer level comes from Settings → Player Buffer ([BufferPrefStore]):
 * - FAST: starts playback almost instantly with a small buffer — best for
 *   2GB TV boxes where RAM is tight and every second of load time matters.
 * - BALANCED: quick start with a healthy buffer — the default.
 * - SMOOTH: large buffer window so playback rarely stalls on a 4 Mbps line.
 *
 * Hardware decoding is preferred with software fallback, so weak TV boxes keep up.
 * Adaptive track selection is capped at 720p to start: avoids the initial overshoot
 * where the player grabs a 1080p rendition the line cannot sustain. The selector
 * still steps down further (480p/360p) when bandwidth dips.
 *
 * Trade-off (accepted): zapping between channels takes a moment while the
 * buffer fills. Smooth playback matters more than fast zapping here.
 */
object PlayerFactory {

    fun create(context: Context): ExoPlayer {
        // minBuffer / maxBuffer / bufferForPlayback / bufferForPlaybackAfterRebuffer
        val (minBufferMs, maxBufferMs, forPlaybackMs, forRebufferMs) = when (BufferPrefStore.level) {
            BufferPrefStore.FAST -> intArrayOf(8_000, 20_000, 800, 2_000)
            BufferPrefStore.SMOOTH -> intArrayOf(15_000, 60_000, 2_500, 5_000)
            else -> intArrayOf(12_000, 40_000, 1_500, 3_000) // BALANCED
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ minBufferMs,
                /* maxBufferMs = */ maxBufferMs,
                /* bufferForPlaybackMs = */ forPlaybackMs,
                /* bufferForPlaybackAfterRebufferMs = */ forRebufferMs,
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
