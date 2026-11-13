package com.streamlite.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.streamlite.tv.data.DataRepository
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

// 2026-11-04: Lightweight mode for 2GB TV boxes — cap Coil's memory cache
// so channel thumbnails don't freeze the box. Default is 25% of app memory;
// 15% is plenty for thumbnails and leaves RAM for ExoPlayer.

class StreamLiteApp : Application(), ImageLoaderFactory {
    // 2026-11-13 (v2.20.2): app khulte hi pichli sync ki gintee
    // (Live/Movies/Series counts) wapis parh lo — Home ke cards
    // par foran nazar aye gi, baghair kisi auto-update ke.
    override fun onCreate() {
        super.onCreate()
        DataRepository.loadCounts(this)
    }

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
                    // 2026-11-13 (v2.17.0): timeouts barha diye —
                    // pehle connect 5s / read 8s tha (Batch 7 ka
                    // TV-box faisla). Ahmed Bhai ki 4 Mbps line par
                    // slow logo wali sites 8 second me haar jati
                    // theen, is liye asal logos bhi nahi ate thay.
                    // Ab waqt zyada hai; loading ke doran fallback
                    // icon wese hi nazar ata hai.
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
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
                // 2026-11-13 (v2.16.0): 100MB BAHUT chhota tha — kul
                // ~93,000 images (15.8k Live logos + 69.8k VOD posters
                // + 7.5k Series covers) us me sama hi nahi sakti
                // theen. Live logos pehle download hote thay, phir
                // posters ki bheer unhen cache se NIKAL deti thi —
                // yahi wajah hai ke Live TV me zyada tar logos load
                // nahi ho pate thay. Ab 1GB disk cache (sirf storage,
                // RAM par koi bojh nahi).
                DiskCache.Builder()
                    // 2026-11-13 (v2.17.0): jagah bhi badal di —
                    // pehle cacheDir tha, jise Android storage kam
                    // hone par KHUD saaf kar deta hai (download ki
                    // hui images har dafa ur jati theen). Ab
                    // filesDir — app ki permanent jagah, system
                    // ise khud saaf nahi karta.
                    .directory(filesDir.resolve("coil_images"))
                    .maxSizeBytes(1024L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
