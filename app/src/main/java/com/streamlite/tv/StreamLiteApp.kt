package com.streamlite.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// 2026-11-04: Lightweight mode for 2GB TV boxes — cap Coil's memory cache
// so channel thumbnails don't freeze the box. Default is 25% of app memory;
// 15% is plenty for thumbnails and leaves RAM for ExoPlayer.

class StreamLiteApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            // 2026-11-05 (Batch 7): dead/slow icon hosts used to hang for a long
            // time, leaving BLANK boxes (esp. news channels). Short timeouts make
            // a bad URL fail FAST so the fallback tile shows instead of blank.
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
            }
            .diskCache {
                // 2026-11-05 (Batch 6, Point 17): 50MB was too small to hold
                // ALL preloaded thumbnails — 100MB disk cache (storage only,
                // zero extra RAM) so the preload actually sticks.
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
