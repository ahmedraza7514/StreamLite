package com.streamlite.tv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Minimal Xtream Codes API client. All calls are blocking — call from Dispatchers.IO.
 * JSON is parsed with org.json (opt* accessors) so mixed string/number fields never crash.
 */
class XtreamClient(server: String, username: String, password: String) {

    private val base = server.trim().trimEnd('/')
    private val user = username.trim()
    private val pass = password.trim()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    fun apiUrl(action: String, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=$action$extra"

    private fun get(url: String): String {
        // Use a widely-accepted UA: some panels block unknown clients with custom codes (e.g. 512)
        val req = Request.Builder().url(url).header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Server replied HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("Empty response from server")
        }
    }

    private fun getJson(url: String): JSONObject = JSONObject(get(url))

    /** True when the account exists and is active. Throws on network or auth failure. */
    fun login(): Boolean {
        val root = getJson(apiUrl(""))
        val info = root.optJSONObject("user_info") ?: throw IOException("Unexpected login response")
        return info.optString("status") == "Active"
    }

    // ---------- categories ----------

    fun liveCategories(): List<XtreamCategory> = parseCategories(get(apiUrl("get_live_categories")))
    fun vodCategories(): List<XtreamCategory> = parseCategories(get(apiUrl("get_vod_categories")))
    fun seriesCategories(): List<XtreamCategory> = parseCategories(get(apiUrl("get_series_categories")))

    private fun parseCategories(raw: String): List<XtreamCategory> {
        val out = ArrayList<XtreamCategory>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("category_id")
            val name = o.optString("category_name")
            if (id.isNotEmpty() && name.isNotEmpty()) out += XtreamCategory(id, name)
        }
        return out
    }

    // ---------- live ----------

    fun liveStreams(categoryId: String): List<LiveStream> =
        parseLiveStreams(get(apiUrl("get_live_streams", "&category_id=${enc(categoryId)}")))

    fun liveStreamsAll(): List<LiveStream> =
        parseLiveStreams(get(apiUrl("get_live_streams")))

    private fun parseLiveStreams(raw: String): List<LiveStream> {
        val arr = JSONArray(raw)
        val out = ArrayList<LiveStream>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("stream_id", -1)
            if (id < 0) continue
            val catId = o.optString("category_id").ifEmpty {
                val ids = o.optJSONArray("category_ids")
                if (ids != null && ids.length() > 0) ids.optString(0) else ""
            }
            out += LiveStream(
                streamId = id,
                name = o.optString("name").ifEmpty { "Channel $id" },
                logo = o.optString("stream_icon"),
                categoryId = catId,
            )
        }
        return out
    }

    // ---------- VOD ----------

    fun vodStreams(categoryId: String): List<VodStream> =
        parseVodStreams(get(apiUrl("get_vod_streams", "&category_id=${enc(categoryId)}")))

    fun vodStreamsAll(): List<VodStream> =
        parseVodStreams(get(apiUrl("get_vod_streams")))

    private fun parseVodStreams(raw: String): List<VodStream> {
        val arr = JSONArray(raw)
        val out = ArrayList<VodStream>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("stream_id", -1)
            if (id < 0) continue
            out += VodStream(
                streamId = id,
                name = o.optString("name").ifEmpty { "Movie $id" },
                logo = o.optString("stream_icon"),
                categoryId = o.optString("category_id"),
                container = o.optString("container_extension").ifEmpty { "mp4" },
                rating = o.optString("rating"),
            )
        }
        return out
    }

    // ---------- series ----------

    fun series(categoryId: String): List<SeriesItem> =
        parseSeries(get(apiUrl("get_series", "&category_id=${enc(categoryId)}")))

    fun seriesAll(): List<SeriesItem> =
        parseSeries(get(apiUrl("get_series")))

    private fun parseSeries(raw: String): List<SeriesItem> {
        val arr = JSONArray(raw)
        val out = ArrayList<SeriesItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("series_id", -1)
            if (id < 0) continue
            out += SeriesItem(
                seriesId = id,
                name = o.optString("name").ifEmpty { "Series $id" },
                cover = o.optString("cover"),
                categoryId = o.optString("category_id"),
                rating = o.optString("rating"),
            )
        }
        return out
    }

    fun seriesInfo(seriesId: Int): SeriesInfo {
        val root = getJson(apiUrl("get_series_info", "&series_id=$seriesId"))
        val info = root.optJSONObject("info")
        val seasons = linkedMapOf<Int, MutableList<Episode>>()
        val eps = root.optJSONObject("episodes")
        if (eps != null) {
            val keys = eps.keys()
            while (keys.hasNext()) {
                val seasonKey = keys.next()
                val seasonNum = seasonKey.toIntOrNull() ?: continue
                val arr = eps.optJSONArray(seasonKey) ?: continue
                val list = seasons.getOrPut(seasonNum) { mutableListOf() }
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    list += Episode(
                        id = e.optString("id"),
                        title = e.optString("title").ifEmpty { "Episode ${e.optInt("episode_num", i + 1)}" },
                        container = e.optString("container_extension").ifEmpty { "mp4" },
                        season = seasonNum,
                        episodeNum = e.optInt("episode_num", i + 1),
                        plot = e.optJSONObject("info")?.optString("plot") ?: "",
                    )
                }
                list.sortBy { it.episodeNum }
            }
        }
        return SeriesInfo(
            cover = info?.optString("cover") ?: "",
            plot = info?.optString("plot") ?: "",
            rating = info?.optString("rating") ?: "",
            seasons = seasons.toSortedMap(),
        )
    }

    // ---------- EPG ----------

    /** Returns (now, next) for a live stream. Either may be null. */
    fun epgNowNext(streamId: Int): Pair<EpgEntry?, EpgEntry?> {
        val root = getJson(apiUrl("get_simple_data_table", "&stream_id=$streamId"))
        val arr = root.optJSONArray("epg_listings") ?: return null to null
        val now = System.currentTimeMillis()
        val entries = ArrayList<EpgEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = parseEpgTime(o.optString("start"))
            val end = parseEpgTime(o.optString("end"))
            if (start <= 0 || end <= 0) continue
            entries += EpgEntry(o.optString("title"), o.optString("description"), start, end)
        }
        entries.sortBy { it.start }
        for ((idx, e) in entries.withIndex()) {
            if (now in e.start until e.end) {
                return e to entries.getOrNull(idx + 1)
            }
        }
        return null to null
    }

    private fun parseEpgTime(v: String): Long {
        if (v.isEmpty()) return 0
        v.toLongOrNull()?.let { return if (it > 1_000_000_000_000L) it else it * 1000 }
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(v)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // ---------- playback URLs ----------

    fun liveHlsUrl(streamId: Int): String = "$base/live/$user/$pass/$streamId.m3u8"
    fun liveTsUrl(streamId: Int): String = "$base/live/$user/$pass/$streamId.ts"
    fun vodUrl(s: VodStream): String = "$base/movie/$user/$pass/${s.streamId}.${s.container}"
    fun episodeUrl(e: Episode): String = "$base/series/$user/$pass/${e.id}.${e.container}"
}
