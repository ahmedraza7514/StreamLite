package com.streamlite.tv.data

import android.content.Context
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

    // Per-category item counts, captured during sync (zero extra network —
    // the per-category sizes are already fetched for the totals above).
    // VodScreen / SeriesScreen sidebars read these; null = not synced yet.
    var vodCatCounts: Map<String, Int>? = null
    var seriesCatCounts: Map<String, Int>? = null

    var isSynced = false

    suspend fun syncAll(
        client: XtreamClient,
        appContext: Context? = null,
        onProgress: (String) -> Unit,
        onEpgProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        // Batch 6, Point 17: thumbnail preload progress (done, total).
        onThumbnailProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        // LIVE TV
        onProgress("LIVE TV")
        val lc = withContext(Dispatchers.IO) { client.liveCategories() }
        val ls = withContext(Dispatchers.IO) { client.liveStreamsAll() }
        liveCategories = lc
        liveStreams = ls

        // VOD — categories only (streams load per-category on demand to avoid OOM on TV boxes)
        // Plus parallel per-category COUNT so sync screen can show total videos
        // (the per-category map is also kept for the VodScreen sidebar counts)
        onProgress("VOD")
        val vc = withContext(Dispatchers.IO) { client.vodCategories() }
        vodCategories = vc
        // Batch 6, Point 17: this pass ALREADY downloads every VOD list just
        // to count it — keep the poster URLs too (zero extra network) so the
        // preloader can cache them during sync.
        val vodResults = withContext(Dispatchers.IO) {
            val sem = Semaphore(12)
            vc.map { cat ->
                async {
                    sem.withPermit {
                        try { cat.id to client.vodStreams(cat.id) }
                        catch (e: Exception) { cat.id to emptyList() }
                    }
                }
            }.awaitAll()
        }
        vodCatCounts = vodResults.associate { (id, list) -> id to list.size }
        vodTotalCount = vodResults.sumOf { it.second.size }
        val vodLogos = vodResults.flatMap { (_, list) -> list.map { it.logo } }

        // SERIES — categories only (series load per-category on demand to avoid OOM on TV boxes)
        // Plus parallel per-category COUNT so sync screen can show total series
        // (the per-category map is also kept for the SeriesScreen sidebar counts)
        onProgress("SERIES")
        val sc = withContext(Dispatchers.IO) { client.seriesCategories() }
        seriesCategories = sc
        // Batch 6, Point 17: same as VOD — keep the cover URLs from the
        // count pass (zero extra network) for the thumbnail preloader.
        val seriesResults = withContext(Dispatchers.IO) {
            val sem = Semaphore(12)
            sc.map { cat ->
                async {
                    sem.withPermit {
                        try { cat.id to client.series(cat.id) }
                        catch (e: Exception) { cat.id to emptyList() }
                    }
                }
            }.awaitAll()
        }
        seriesCatCounts = seriesResults.associate { (id, list) -> id to list.size }
        seriesTotalCount = seriesResults.sumOf { it.second.size }
        val seriesCovers = seriesResults.flatMap { (_, list) -> list.map { it.cover } }

        // GUIDE — EPG is now OPTIONAL (user taps the GUIDE box to load it).
        // Skipped during auto-sync so the app opens fast every time.
        onProgress("GUIDE")

        // Batch 6, Point 17: preload ALL thumbnails into Coil's disk cache
        // NOW, during sync — fire-and-forget, it keeps running in the
        // background after sync finishes so categories open instantly.
        if (appContext != null) {
            ThumbnailPreloader.preload(
                appContext,
                liveStreams.orEmpty().map { it.logo } + vodLogos + seriesCovers,
                onThumbnailProgress,
            )
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
        vodCatCounts = null
        seriesCatCounts = null
        isSynced = false
        EpgStore.clear()
    }
}
