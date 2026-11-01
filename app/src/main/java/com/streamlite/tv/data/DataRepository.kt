package com.streamlite.tv.data

import kotlinx.coroutines.Dispatchers
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

        // VOD
        onProgress("VOD")
        vodCategories = withContext(Dispatchers.IO) { client.vodCategories() }
        vodStreams = withContext(Dispatchers.IO) { client.vodStreamsAll() }

        // SERIES
        onProgress("SERIES")
        seriesCategories = withContext(Dispatchers.IO) { client.seriesCategories() }
        seriesList = withContext(Dispatchers.IO) { client.seriesAll() }

        // GUIDE — EPG prefetched HERE with parallel requests and real progress,
        // so "Loading programme..." never appears after sync completes.
        onProgress("GUIDE")
        EpgStore.prefetchAllAndWait(client, ls.map { it.streamId }) { done, total ->
            onEpgProgress(done, total)
        }

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
