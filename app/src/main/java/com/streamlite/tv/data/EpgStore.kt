package com.streamlite.tv.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-wide EPG (programme) cache. Survives screen navigation — once a
 * channel's now/next programme is fetched, it is never fetched again
 * in this session, so "Loading programme…" stops reappearing.
 *
 * Call [prefetchAll] after the stream list is loaded; it fills the cache
 * for every channel in the background without blocking the UI.
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
     * Fetch now/next for every stream id in the background.
     * Skips ids already cached. Safe to call repeatedly — only one
     * prefetch runs at a time.
     */
    fun prefetchAll(client: XtreamClient, streamIds: List<Int>) {
        if (prefetchJob?.isActive == true) return
        prefetchJob = CoroutineScope(Dispatchers.IO).launch {
            for (id in streamIds) {
                if (cache.containsKey(id)) continue
                try {
                    val (now, next) = client.epgNowNext(id)
                    val text = format(now, next)
                    cache[id] = text
                } catch (_: Exception) {
                    cache[id] = ""
                }
                // Be gentle with the panel: small gap between requests
                delay(120)
            }
        }
    }

    fun clear() {
        prefetchJob?.cancel()
        prefetchJob = null
        cache.clear()
    }
}
