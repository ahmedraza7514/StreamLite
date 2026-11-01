package com.streamlite.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * App-wide data repository. Populated once by the "Update Media Contents"
 * sync screen after login (XCIPTV model). Screens read from here instead
 * of fetching individually.
 */
object DataRepository {
    var liveCategories: List<XtreamCategory>? = null
    var liveStreams: List<LiveStream>? = null
    var vodCategories: List<XtreamCategory>? = null
    var vodStreams: List<VodStream>? = null
    var seriesCategories: List<XtreamCategory>? = null
    var seriesList: List<SeriesItem>? = null

    // Total counts (for sync screen display) — counted per-category to avoid OOM
    var vodTotalCount: Int = 0
    var seriesTotalCount: Int = 0

    var isSynced = false

    suspend fun syncAll(
        client: XtreamClient,
        onProgress: (String) -> Unit,
        onEpgProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        // LIVE TV
        onProgress("LIVE TV")
        val lc = withContext(Dispatchers.IO) { client.liveCategories() }
        val ls = withContext(Dispatchers.IO) { client.liveStreamsAll() }
        liveCategories = lc
        liveStreams = ls

        // VOD — categories only (streams load per-category on demand to avoid OOM on TV boxes)
        // Plus parallel per-category COUNT so sync screen can show total videos
        onProgress("VOD")
        val vc = withContext(Dispatchers.IO) { client.vodCategories() }
        vodCategories = vc
        vodTotalCount = withContext(Dispatchers.IO) {
            val sem = Semaphore(12)
            vc.map { cat ->
                async {
                    sem.withPermit {
                        try { client.vodStreams(cat.id).size } catch (e: Exception) { 0 }
                    }
                }
            }.awaitAll().sum()
        }

        // SERIES — categories only (series load per-category on demand to avoid OOM on TV boxes)
        // Plus parallel per-category COUNT so sync screen can show total series
        onProgress("SERIES")
        val sc = withContext(Dispatchers.IO) { client.seriesCategories() }
        seriesCategories = sc
        seriesTotalCount = withContext(Dispatchers.IO) {
            val sem = Semaphore(12)
            sc.map { cat ->
                async {
                    sem.withPermit {
                        try { client.series(cat.id).size } catch (e: Exception) { 0 }
                    }
                }
            }.awaitAll().sum()
        }

        // GUIDE — EPG is now OPTIONAL (user taps the GUIDE box to load it).
        // Skipped during auto-sync so the app opens fast every time.
        onProgress("GUIDE")

        isSynced = true
        onProgress("DONE")
    }

    fun clear() {
        liveCategories = null
        liveStreams = null
        vodCategories = null
        vodStreams = null
        seriesCategories = null
        seriesList = null
        isSynced = false
        EpgStore.clear()
    }
}
