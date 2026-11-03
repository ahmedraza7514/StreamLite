package com.streamlite.tv.ui

/** Simple manual back-stack. D-pad Back pops via BackHandler in MainActivity. */
sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    /** Re-sync screen. [popAfter] = return to the previous screen instead of Home. */
    data class Sync(val popAfter: Boolean = false) : Screen()
    data object LiveTv : Screen()
    data object Vod : Screen()
    data object Series : Screen()
    data object MultiView : Screen()
    data object Search : Screen()
    data object Favorites : Screen()
    data object Account : Screen()
    /** Login used to ADD another account (does not switch to it). */
    data object AddAccount : Screen()
    data object Settings : Screen()
    data class SeriesDetail(val seriesId: Int, val title: String) : Screen()
    /**
     * Full-screen player. [playlist] holds the playable queue (url + title);
     * when non-empty the player's Next/Previous buttons jump between items.
     * [startIndex] is which playlist entry to start from.
     * [isLive] = this is a live channel: the bar shows prev/next CHANNEL
     * buttons alongside the seek buttons (VOD shows seek only).
     * [kind] = "live" | "vod" | "episode" (used for the favorite entry).
     */
    data class Player(
        val url: String,
        val title: String,
        val playlist: List<PlayerItem> = emptyList(),
        val startIndex: Int = 0,
        val info: String = "",
        val isLive: Boolean = false,
        val kind: String = "live",
    ) : Screen()
}

/** One entry in the player's Next/Previous queue. */
data class PlayerItem(val url: String, val title: String)
