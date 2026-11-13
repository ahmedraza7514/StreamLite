package com.streamlite.tv.player

import android.content.Context
import androidx.media3.common.MimeTypes
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
                    // 2026-11-13 (v2.18.0, Points 1-2): audio me AAC
                    // ko tarjeeh — bohat se phones/TV boxes me AC3 /
                    // E-AC3 ka decoder hota hi nahi; jahan stream
                    // AAC bhi deti hai wahan pehle khamosh (AC3)
                    // track chun liya jata tha. Ab AAC mile to wahi
                    // chalegi; sirf AC3 ho to wahi rahe gi.
                    .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG)
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

    /**
     * Halka player sirf 4-Screen (MultiView) ke liye — 2026-11-12,
     * v2.14.0, Ahmed Bhai ka hukum: "smooth aur light banao takay
     * TV box py fast run ho".
     *
     * Single player wale [create] se farq:
     * - Quality cap 480p (854x480): quadrant aadhi screen ka chhota
     *   hissa hai, wahan 720p ka farq nazar nahi ata — lekin decode
     *   aur bandwidth ka bojh takreeban aadha ho jata hai. 4 streams
     *   ek sath 4 Mbps line par isi liye chal sakti hain.
     * - Buffer chhota aur tay (10s/30s): 4 players ek sath bare
     *   buffer bharein to 2GB box ki RAM aur line dono par bojh
     *   parta hai aur asal playback rukti hai; chhota buffer live
     *   streams ko halka aur tez rakhta hai.
     * - Hardware decoding + software fallback [create] jaisa hi hai.
     *
     * NOTE: BufferPrefStore wali Settings is par laagu nahi hoti —
     * ye MultiView ke liye tay halki setting hai.
     */
    fun createForMultiView(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(854, 480)
                    // v2.18.0: AAC audio ko tarjeeh (uper create()
                    // wali note dekho) — 4-Screen me bhi wohi rule.
                    .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG)
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
