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

    suspend fun syncAll(client: XtreamClient, onProgress: (String) -> Unit) {
        // LIVE TV
        onProgress("LIVE TV")
        val lc = withContext(Dispatchers.IO) { client.liveCategories() }
        val ls = withContext(Dispatchers.IO) { client.liveStreamsAll() }
        liveCategories = lc
        liveStreams = ls
        // Prefetch EPG in background
        EpgStore.prefetchAll(client, ls.map { it.streamId })

        // VOD
        onProgress("VOD")
        vodCategories = withContext(Dispatchers.IO) { client.vodCategories() }
        vodStreams = withContext(Dispatchers.IO) { client.vodStreamsAll() }

        // SERIES
        onProgress("SERIES")
        seriesCategories = withContext(Dispatchers.IO) { client.seriesCategories() }
        seriesList = withContext(Dispatchers.IO) { client.seriesAll() }

        // GUIDE (EPG is prefetched in background)
        onProgress("GUIDE")
        // EPG prefetch runs in background via EpgStore

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
