package com.streamlite.tv.data

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Batch 6, Point 17 (2026-11-05): preload EVERY thumbnail (live logos, VOD
 * posters, series covers) into Coil's disk cache during "Update Media
 * Contents" sync, so entering a category later shows images instantly
 * instead of loading them one-by-one.
 *
 * Runs on an app-wide scope — it keeps going in the background even after
 * the sync screen closes. Downloads go to DISK cache only (memory cache
 * disabled here) so the 2GB TV box never feels it. 8 downloads at a time
 * so a 4 Mbps line isn't choked. A dead/slow icon URL is skipped, never
 * fails the batch.
 */
object ThumbnailPreloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var total: Int = 0
        private set
    @Volatile var done: Int = 0
        private set
    // 2026-11-13 (v2.18.0): FAIL ginna bhi zaroori hai — kitni
    // images asal me load huin aur kitni panel ki taraf se mare
    // hue (dead) links thay, ye farq nazar ana chahiye. Pehle har
    // koshish "done" gin li jati thi, kamyab ho ya na ho.
    @Volatile var failed: Int = 0
        private set
    @Volatile var running: Boolean = false
        private set

    fun preload(
        context: Context,
        urls: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        priorityCount: Int = 0,
    ) {
        val unique = urls.mapNotNull { u ->
            val t = u.trim()
            if (t.isEmpty()) null else t.replace(" ", "%20")
        }.distinct()
        if (unique.isEmpty()) {
            onProgress(0, 0)
            return
        }
        total = unique.size
        done = 0
        failed = 0
        running = true
        val appCtx = context.applicationContext
        scope.launch {
            val loader = appCtx.imageLoader
            val counter = AtomicInteger(0)
            val failCounter = AtomicInteger(0)
            val sem = Semaphore(8)
            try {
                unique.mapIndexed { idx, url ->
                    async {
                        sem.withPermit {
                            try {
                                // 2026-11-13 (v2.16.0): pehle
                                // [priorityCount] URLs (Live TV logos)
                                // har surat download hon ge; us ke baad
                                // wale (VOD/Series posters) SIRF tab jab
                                // disk cache me jagah baqi ho — warna
                                // posters ki bheer Live logos ko cache
                                // se nikal deti hai.
                                val dc = loader.diskCache
                                val cacheFull = dc != null &&
                                    dc.size > dc.maxSize * 9 / 10
                                if (idx < priorityCount || !cacheFull) {
                                    val req = ImageRequest.Builder(appCtx)
                                        .data(url)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .build()
                                    val result = loader.execute(req)
                                    if (result is ErrorResult) {
                                        failed = failCounter.incrementAndGet()
                                    }
                                }
                            } catch (_: Exception) {
                                // Dead/slow icon URL — skip it, never fail the batch.
                                failed = failCounter.incrementAndGet()
                            } finally {
                                val d = counter.incrementAndGet()
                                done = d
                                onProgress(d, total)
                            }
                        }
                    }
                }.awaitAll()
            } finally {
                running = false
            }
        }
    }
}
