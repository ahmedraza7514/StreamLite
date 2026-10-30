package com.streamlite.tv.ui

/** Simple manual back-stack. D-pad Back pops via BackHandler in MainActivity. */
sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data object LiveTv : Screen()
    data object Vod : Screen()
    data object Series : Screen()
    data class SeriesDetail(val seriesId: Int, val title: String) : Screen()
    data class Player(val url: String, val title: String) : Screen()
}
