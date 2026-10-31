package com.streamlite.tv.ui

/** Simple manual back-stack. D-pad Back pops via BackHandler in MainActivity. */
sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data object Sync : Screen()
    data object LiveTv : Screen()
    data object Vod : Screen()
    data object Series : Screen()
    data class SeriesDetail(val seriesId: Int, val title: String) : Screen()
    /**
     * Full-screen player. [playlist] holds the playable queue (url + title);
     * when non-empty the player's Next/Previous buttons jump between items.
     * [startIndex] is which playlist entry to start from.
     */
    data class Player(
        val url: String,
        val title: String,
        val playlist: List<PlayerItem> = emptyList(),
        val startIndex: Int = 0,
        val info: String = "",
    ) : Screen()
}

/** One entry in the player's Next/Previous queue. */
data class PlayerItem(val url: String, val title: String)
