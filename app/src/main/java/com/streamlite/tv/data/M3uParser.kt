package com.streamlite.tv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class M3uChannel(
    val name: String,
    val logo: String,
    val url: String,
    val group: String,
)

/** Minimal M3U playlist parser + downloader. */
object M3uParser {

    private val logoRe = Regex("""tvg-logo="([^"]*)"""")
    private val groupRe = Regex("""group-title="([^"]*)"""")

    fun parse(text: String): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        var name = ""
        var logo = ""
        var group = ""
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    name = line.substringAfterLast(",", "").trim()
                    logo = logoRe.find(line)?.groupValues?.getOrNull(1) ?: ""
                    group = groupRe.find(line)?.groupValues?.getOrNull(1) ?: ""
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (line.startsWith("http")) {
                        out += M3uChannel(
                            name = name.ifEmpty { line },
                            logo = logo,
                            url = line,
                            group = group.ifEmpty { "All" },
                        )
                    }
                    name = ""
                    logo = ""
                    group = ""
                }
            }
        }
        return out
    }

    /** Blocking — call from Dispatchers.IO. */
    fun download(url: String): List<M3uChannel> {
        val req = Request.Builder().url(url).header("User-Agent", "StreamLite/1.0").build()
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
            .newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Playlist HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw IOException("Empty playlist")
                val channels = parse(body)
                if (channels.isEmpty()) throw IOException("No channels found in playlist")
                return channels
            }
    }
}
