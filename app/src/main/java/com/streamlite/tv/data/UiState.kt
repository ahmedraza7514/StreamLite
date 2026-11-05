package com.streamlite.tv.data

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateListOf

/**
 * Recently played items — shown at the top of list screens so the user
 * never has to scroll and search for something they just watched.
 */
data class RecentItem(
    val url: String,
    val title: String,
    val kind: String, // "live", "vod", "episode"
    val info: String = "",
)

object RecentViewsStore {
    private val _items = mutableStateListOf<RecentItem>()
    val items: List<RecentItem> get() = _items

    fun add(item: RecentItem) {
        _items.removeAll { it.url == item.url }
        _items.add(0, item)
        while (_items.size > 15) _items.removeAt(_items.lastIndex)
    }

    fun clear() = _items.clear()
}

/**
 * Preserved scroll positions — so going back from the player returns
 * to the exact place in the list instead of jumping to the top.
 */
object ScrollStateStore {
    val liveList = LazyListState()
    val liveGrid = LazyGridState()
    val vodGrid = LazyGridState()
    val seriesGrid = LazyGridState()
    // 2026-11-05 (Batch 8, Point 1): remember the selected category + sidebar
    // scroll so back from a player returns to the SAME category, not the top.
    var liveCat: String = ""
    var vodCat: String = ""
    var seriesCat: String = ""
    val liveSidebar = LazyListState()
    val vodSidebar = LazyListState()
    val seriesSidebar = LazyListState()
}
