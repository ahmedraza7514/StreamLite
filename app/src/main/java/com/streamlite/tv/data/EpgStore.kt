package com.streamlite.tv.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * App-wide EPG (programme) cache. Survives screen navigation — once a
 * channel's now/next programme is fetched, it is never fetched again
 * in this session, so "Loading programme…" stops reappearing.
 *
 * Call [prefetchAll] after the stream list is loaded; it fills the cache
 * for every channel using parallel requests (10 at a time) so the sync
 * screen can show real progress and finish before the user enters.
 */
object EpgStore {
    val cache: SnapshotStateMap<Int, String> = mutableStateMapOf()

    private var prefetchJob: Job? = null

    /** Human-readable now/next line, e.g. "News Hour • Next: Sports". */
    fun format(now: EpgEntry?, next: EpgEntry?): String = buildString {
        if (now != null) append(now.title)
        if (next != null) {
            if (isNotEmpty()) append("  •  ")
            append("Next: ").append(next.title)
        }
    }

    /**
     * Fetch now/next for every stream id using parallel requests.
     * Reports (done, total) progress. Skips ids already cached.
     * Safe to call repeatedly — only one prefetch runs at a time.
     */
    fun prefetchAll(
        client: XtreamClient,
        streamIds: List<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        if (prefetchJob?.isActive == true) return
        prefetchJob = CoroutineScope(Dispatchers.IO).launch {
            val ids = streamIds.filter { !cache.containsKey(it) }
            val total = ids.size
            if (total == 0) {
                onProgress(0, 0)
                return@launch
            }
            val done = AtomicInteger(0)
            val semaphore = Semaphore(12) // 12 parallel requests — fast but gentle
            val jobs = ids.map { id ->
                async {
                    semaphore.withPermit {
                        try {
                            val (now, next) = client.epgNowNext(id)
                            cache[id] = format(now, next)
                        } catch (_: Exception) {
                            cache[id] = ""
                        }
                        onProgress(done.incrementAndGet(), total)
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    /** Suspend version — waits for prefetch to complete. Use in sync flow. */
    suspend fun prefetchAllAndWait(
        client: XtreamClient,
        streamIds: List<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val ids = streamIds.filter { !cache.containsKey(it) }
        val total = ids.size
        if (total == 0) {
            onProgress(0, 0)
            return
        }
        val done = AtomicInteger(0)
        val semaphore = Semaphore(12)
        val jobs = ids.map { id ->
            CoroutineScope(Dispatchers.IO).async {
                semaphore.withPermit {
                    try {
                        val (now, next) = client.epgNowNext(id)
                        cache[id] = format(now, next)
                    } catch (_: Exception) {
                        cache[id] = ""
                    }
                    onProgress(done.incrementAndGet(), total)
                }
            }
        }
        jobs.awaitAll()
    }

    fun clear() {
        prefetchJob?.cancel()
        prefetchJob = null
        cache.clear()
    }
}
