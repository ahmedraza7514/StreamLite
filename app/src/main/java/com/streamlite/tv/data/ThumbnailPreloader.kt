package com.streamlite.tv.data

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
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
    @Volatile var running: Boolean = false
        private set

    fun preload(
        context: Context,
        urls: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
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
        running = true
        val appCtx = context.applicationContext
        scope.launch {
            val loader = appCtx.imageLoader
            val counter = AtomicInteger(0)
            val sem = Semaphore(8)
            try {
                unique.map { url ->
                    async {
                        sem.withPermit {
                            try {
                                val req = ImageRequest.Builder(appCtx)
                                    .data(url)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .build()
                                loader.execute(req)
                            } catch (_: Exception) {
                                // Dead/slow icon URL — skip it, never fail the batch.
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
