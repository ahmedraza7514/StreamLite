package com.streamlite.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
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

    // 2026-11-13 (v2.20.2): counts ki YAAD — app band karke
    // dobara kholne par bhi Home ke cards ke neeche aakhri
    // gintee nazar aye (liveStreams band hone par memory se
    // urh jata tha, aur Point 4 ki wajah se opening par
    // auto-update bhi band hai). Ye sirf purani gintee dikhana
    // hai — media ki update phir bhi sirf manual hukum par
    // hogi. Sync ke aakhir me saveCounts counts ko phone par
    // mehfooz kar deta hai; app khulte hi StreamLiteApp
    // loadCounts se wapis parh leta hai.
    var liveTotalCount: Int = 0

    fun loadCounts(context: Context) {
        try {
            val p = context.getSharedPreferences("sl_counts", Context.MODE_PRIVATE)
            liveTotalCount = p.getInt("live", 0)
            vodTotalCount = p.getInt("vod", 0)
            seriesTotalCount = p.getInt("series", 0)
        } catch (_: Exception) { /* pehli dafa — kuch mehfooz nahi */ }
    }

    private fun saveCounts(context: Context) {
        try {
            liveTotalCount = liveStreams?.size ?: liveTotalCount
            context.getSharedPreferences("sl_counts", Context.MODE_PRIVATE)
                .edit()
                .putInt("live", liveTotalCount)
                .putInt("vod", vodTotalCount)
                .putInt("series", seriesTotalCount)
                .apply()
        } catch (_: Exception) { /* gintee mehfooz na bhi ho to sync nahi rukti */ }
    }

    // Per-category item counts, captured during sync (zero extra network —
    // the per-category sizes are already fetched for the totals above).
    // VodScreen / SeriesScreen sidebars read these; null = not synced yet.
    var vodCatCounts: Map<String, Int>? = null
    var seriesCatCounts: Map<String, Int>? = null

    var isSynced = false

    // 2026-11-13 (v2.18.0, Point 5): aakhri sync ki thumbnail
    // (Live logos + VOD posters + Series covers) wali list — Home
    // ke "Update Thumbnails" button ke liye, taake sirf images
    // taaza karne ke liye poori media sync dobara na karni pare.
    @Volatile var thumbUrls: List<String> = emptyList()
        private set
    @Volatile var thumbPriority: Int = 0
        private set

    // 2026-11-13 (v2.19.0, Point 5): "Update Thumbnails" button
    // ab KHUD-KAFI hai. Pehle jab list khaali hoti thi to button
    // Sync screen par bhej deta tha — Ahmed Bhai ne teen bar
    // check karke bataya ke click se koi thumbnail load nahi
    // hota tha. Ab: list khaali ho (maslan taaza opening, sync
    // na hua ho) to pehle khamoshi se catalog ki lists load
    // karke URLs jama karta hai, phir preload chalata hai.
    // Button wohi kare ga jo us ka naam hai.
    private val thumbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Sirf thumbnails load karo — baghair Sync screen ke. */
    fun startThumbnailUpdate(
        session: LoginSession,
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val appCtx = context.applicationContext
        if (ThumbnailPreloader.running) return
        if (thumbUrls.isNotEmpty()) {
            ThumbnailPreloader.preload(
                appCtx,
                thumbUrls,
                onProgress,
                priorityCount = thumbPriority,
            )
            return
        }
        thumbScope.launch {
            try {
                val client = XtreamClient(session.server, session.username, session.password)
                val live = runCatching { client.liveStreamsAll() }
                    .getOrDefault(emptyList())
                if (liveStreams == null) liveStreams = live
                val liveLogos = live.map { it.logo }
                val vc = runCatching { client.vodCategories() }
                    .getOrDefault(emptyList())
                val vodLists = vc.map { cat ->
                    async {
                        runCatching { client.vodStreams(cat.id) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
                val vodLogos = vodLists.flatMap { list -> list.map { it.logo } }
                val sc = runCatching { client.seriesCategories() }
                    .getOrDefault(emptyList())
                val seriesLists = sc.map { cat ->
                    async {
                        runCatching { client.series(cat.id) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
                val seriesCovers = seriesLists.flatMap { list -> list.map { it.cover } }
                thumbUrls = liveLogos + vodLogos + seriesCovers
                thumbPriority = liveLogos.size
                if (thumbUrls.isNotEmpty()) {
                    ThumbnailPreloader.preload(
                        appCtx,
                        thumbUrls,
                        onProgress,
                        priorityCount = thumbPriority,
                    )
                }
            } catch (_: Exception) {
                // Network/panel error — button dobara dabane par
                // phir koshish ho gi.
            }
        }
    }

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

        // v2.20.2: is sync ki gintee phone par mehfooz kar do —
        // app dobara khulne par Home ke cards par yahi nazar
        // aye gi (uper loadCounts/saveCounts ki note).
        if (appContext != null) saveCounts(appContext)

        // Batch 6, Point 17: preload ALL thumbnails into Coil's disk cache
        // NOW, during sync — fire-and-forget, it keeps running in the
        // background after sync finishes so categories open instantly.
        if (appContext != null) {
            // 2026-11-13 (v2.16.0): Live logos PEHLE aur priority ke
            // sath — ye sab se zyada khulte hain; VOD/Series posters
            // cache ki jagah ke hisaab se baad me (ThumbnailPreloader
            // unhen cache bharne par rok deta hai).
            val liveLogos = liveStreams.orEmpty().map { it.logo }
            // 2026-11-13 (v2.18.0, Point 5): thumbnail list YAAD
            // rakho — Home ke "Update Thumbnails" button ko baghair
            // dobara sync kiye sirf images taaza karni hain.
            thumbUrls = liveLogos + vodLogos + seriesCovers
            thumbPriority = liveLogos.size
            // 2026-11-13 (v2.20.1 — TASHEEH): v2.20.0 me sync is
            // preload ke mukammal hone ka intezar karta tha —
            // Ahmed Bhai ne test karke wapas kar diya: 48,850
            // thumbnails par Update Media Contents ka page bohat
            // dair ruka rehta tha. Ab preload BACKGROUND me chalti
            // hai (is page ka kaam media sync hai, wo foran
            // mukammal ho jata hai) — thumbnails peeche load hoti
            // rehti hain, un ki progress Homepage ke "Update
            // Thumbnails" wale hisse me nazar ati hai.
            ThumbnailPreloader.preload(
                appContext,
                thumbUrls,
                onThumbnailProgress,
                priorityCount = thumbPriority,
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
