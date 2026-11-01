package com.streamlite.tv.data

/** Plain data models for the Xtream Codes API. Parsed defensively with org.json
 *  because panels are inconsistent about string-vs-number field types. */

data class XtreamCategory(val id: String, val name: String)

data class LiveStream(
    val streamId: Int,
    val name: String,
    val logo: String,
    val categoryId: String,
)

data class VodStream(
    val streamId: Int,
    val name: String,
    val logo: String,
    val categoryId: String,
    val container: String,
    val rating: String,
)

data class VodInfo(
    val rating: String,
    val genre: String,
    val releaseDate: String,
    val plot: String,
    val cast: String,
    val director: String,
)

data class SeriesItem(
    val seriesId: Int,
    val name: String,
    val cover: String,
    val categoryId: String,
    val rating: String,
)

data class Episode(
    val id: String,
    val title: String,
    val container: String,
    val season: Int,
    val episodeNum: Int,
    val plot: String,
)

data class EpgEntry(
    val title: String,
    val desc: String,
    val start: Long,
    val end: Long,
)

data class SeriesInfo(
    val cover: String,
    val plot: String,
    val rating: String,
    val seasons: Map<Int, List<Episode>>,
)
