package com.streamlite.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.IOException
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
                    // 2026-11-05 (Batch 8 fix): some icon hosts (esp. news logos)
                    // return 403 to non-browser clients and drop requests on slow
                    // 4 Mbps — so the REAL image never arrived and only the fallback
                    // showed. Browser User-Agent + one retry fixes both.
                    .addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
                            )
                            .build()
                        var lastError: IOException? = null
                        var response: okhttp3.Response? = null
                        repeat(2) { _ ->
                            if (response != null) return@repeat
                            try {
                                val resp = chain.proceed(req)
                                // 404 etc. = permanent, don't retry. 5xx = retry once.
                                if (resp.isSuccessful || resp.code < 500) {
                                    response = resp
                                } else {
                                    resp.close()
                                }
                            } catch (e: IOException) {
                                lastError = e
                            }
                        }
                        response ?: throw (lastError ?: IOException("Image request failed"))
                    }
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
