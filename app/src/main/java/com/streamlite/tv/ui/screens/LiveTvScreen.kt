package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.ui.PlayerItem
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.EpgStore
import com.streamlite.tv.data.LiveStream
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.ScrollStateStore
import com.streamlite.tv.data.M3uChannel
import com.streamlite.tv.data.M3uParser
import com.streamlite.tv.data.XtreamCategory
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.components.CategoryChip
import com.streamlite.tv.ui.components.ChannelLogo
import com.streamlite.tv.ui.components.ErrorBox
import com.streamlite.tv.ui.components.LoadingBox
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

private data class ChannelItem(
    val id: Int,
    val name: String,
    val logo: String,
    val group: String,
    val directUrl: String, // non-empty for M3U; Xtream URLs are built from id
)

@Composable
fun LiveTvScreen(
    session: LoginSession,
    m3uCache: MutableState<List<M3uChannel>?>,
    onPlay: (url: String, title: String, playlist: List<PlayerItem>, startIndex: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember(session) {
        if (session.type == LoginType.XTREAM) {
            XtreamClient(session.server, session.username, session.password)
        } else null
    }
    var categories by remember { mutableStateOf<List<XtreamCategory>?>(null) }
    // 2026-11-05 (Batch 8, Point 2): category search in the sidebar
    var catSearchQuery by remember { mutableStateOf("") }
    val visibleCats = remember(catSearchQuery, categories) {
        if (catSearchQuery.isBlank()) categories
        else categories?.filter { it.name.contains(catSearchQuery, ignoreCase = true) }
    }
    var selectedCat by remember { mutableStateOf(ScrollStateStore.liveCat) } // "" = All
    var channels by remember { mutableStateOf<List<ChannelItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    // XCIPTV-style: prefetch ALL streams once, category clicks filter locally (no network)
    var allStreams by remember { mutableStateOf<List<LiveStream>?>(null) }
    // App-wide EPG cache (survives navigation) + background prefetch of all programmes
    val epgCache = EpgStore.cache
    var m3uAll by remember { mutableStateOf<List<M3uChannel>?>(m3uCache.value) }

    fun loadXtreamCategory(catId: String) {
        selectedCat = catId
        ScrollStateStore.liveCat = catId
        error = null
        // Local filter over prefetched streams — instant, zero network (XCIPTV model)
        val src = allStreams
        channels = src
            ?.filter { catId.isEmpty() || it.categoryId == catId }
            ?.map { ChannelItem(it.streamId, it.name, it.logo, it.categoryId, "") }
    }

    fun applyM3uFilter(group: String) {
        selectedCat = group
        ScrollStateStore.liveCat = group
        val all = m3uAll ?: emptyList()
        val filtered = if (group.isEmpty()) all else all.filter { it.group == group }
        channels = filtered.mapIndexed { idx, c -> ChannelItem(idx, c.name, c.logo, c.group, c.url) }
    }

    fun reload() {
        error = null
        channels = null
        scope.launch {
            try {
                if (client != null) {
                    // Use bulk-synced data if available (from SyncScreen)
                    val cats: List<XtreamCategory>
                    val streams: List<LiveStream>
                    if (DataRepository.isSynced && DataRepository.liveCategories != null) {
                        cats = DataRepository.liveCategories!!
                        streams = DataRepository.liveStreams!!
                    } else {
                        cats = withContext(Dispatchers.IO) { client.liveCategories() }
                        // Single bulk download of ALL live streams (XCIPTV model)
                        streams = withContext(Dispatchers.IO) { client.liveStreamsAll() }
                        // Prefetch now/next programme for every channel in background
                        EpgStore.prefetchAll(client, streams.map { it.streamId })
                    }
                    categories = cats
                    allStreams = streams
                    loadXtreamCategory(selectedCat)
                } else {
                    val list = m3uAll ?: withContext(Dispatchers.IO) {
                        M3uParser.download(session.m3uUrl)
                    }.also {
                        m3uAll = it
                        m3uCache.value = it
                    }
                    categories = list.map { it.group }.distinct().sorted()
                        .map { XtreamCategory(it, it) }
                    applyM3uFilter(selectedCat)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load channels."
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Category counts for sidebar (XCIPTV-style "NAME (count)")
    // Xtream: from the prefetched in-memory streams (zero extra network).
    // M3U: from the parsed playlist groups.
    val catCounts = remember(allStreams, categories, m3uAll) {
        val map = mutableMapOf<String, Int>()
        allStreams?.forEach { s ->
            map[s.categoryId] = (map[s.categoryId] ?: 0) + 1
        }
        if (map.isEmpty()) {
            m3uAll?.forEach { c ->
                map[c.group] = (map[c.group] ?: 0) + 1
            }
        }
        map
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground),
    ) {
        // LEFT SIDEBAR: categories
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(Color.Black)
                .padding(vertical = 24.dp),
        ) {
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            // Category search box (top of the category list)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(Color(0xFF0F1E2E), RoundedCornerShape(10.dp))
                    .border(1.dp, SlTextDim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = SlTextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = catSearchQuery,
                    onValueChange = { catSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = SlText, fontSize = 15.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (catSearchQuery.isEmpty()) Text("Search category...", color = SlTextDim, fontSize = 14.sp)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            if (categories == null && error == null) {
                Box(Modifier.padding(horizontal = 20.dp)) { LoadingBox() }
            } else {
                LazyColumn(
                    state = ScrollStateStore.liveSidebar,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // "All" entry
                    item(key = "all") {
                        SidebarCategoryRow(
                            name = "All Channels",
                            count = allStreams?.size ?: m3uAll?.size ?: 0,
                            selected = selectedCat.isEmpty(),
                            onClick = {
                                if (client != null) loadXtreamCategory("") else applyM3uFilter("")
                            },
                        )
                    }
                    items(visibleCats ?: emptyList(), key = { it.id }) { cat ->
                        SidebarCategoryRow(
                            name = cat.name,
                            count = catCounts[cat.id] ?: 0,
                            selected = selectedCat == cat.id,
                            onClick = {
                                if (client != null) loadXtreamCategory(cat.id) else applyM3uFilter(cat.id)
                            },
                        )
                    }
                }
            }
        }

        // RIGHT: channel list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(24.dp),
        ) {
        // 2026-11-04 (Batch 3, Point 4): Search is a SMALL icon box next to A-Z.
        // Click/OK expands it to a full field; X collapses it back.
        var searchExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searchExpanded) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0F1E2E), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = TextStyle(color = SlText, fontSize = 15.sp),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) Text("Search channels...", color = SlTextDim, fontSize = 14.sp)
                            inner()
                        }
                    },
                )
                Card(
                    onClick = {
                        searchExpanded = false
                        searchQuery = ""
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1E2E),
                        contentColor = SlText,
                    ),
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search", tint = SlText)
                    }
                }
            } else {
                Card(
                    onClick = { searchExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1E2E),
                        contentColor = SlText,
                    ),
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = SlText)
                    }
                }
            }
            Card(
                onClick = { sortAsc = !sortAsc },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F1E2E),
                    contentColor = SlText,
                ),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(if (sortAsc) "A-Z ↓" else "Z-A ↑", fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Capture once: avoids NPE if state changes between null-check and usage
        // Apply search filter + sort (defined before Recent Views so replay can use it)
        val ch = channels?.let { list ->
            var result = if (searchQuery.isBlank()) list
                else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
            result = if (sortAsc) result.sortedBy { it.name.lowercase() }
                else result.sortedByDescending { it.name.lowercase() }
            result
        }

        // 2026-11-04 (Batch 3, Point 3): "Recent Played" section removed per user request.

        val err = error
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                err != null -> ErrorBox(err) { reload() }
                ch == null -> LoadingBox()
                ch.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("No channels here.", color = SlTextDim, fontSize = 16.sp) }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5), // XCIPTV-style: 5 channels per row
                    state = ScrollStateStore.liveGrid,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(8.dp),
                ) {
                    items(ch, key = { it.id to it.directUrl }) { item ->
                        ChannelGridCard(
                            item = item,
                            onPlay = {
                                val url = item.directUrl.ifEmpty { client!!.liveHlsUrl(item.id) }
                                val queue = ch.map { c ->
                                    PlayerItem(
                                        c.directUrl.ifEmpty { client!!.liveHlsUrl(c.id) },
                                        c.name,
                                    )
                                }
                                val idx = ch.indexOf(item).coerceAtLeast(0)
                                onPlay(url, item.name, queue, idx)
                            },
                        )
                    }
                }
            }
        }
        } // content Column
    } // Row
}

@Composable
private fun SidebarCategoryRow(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SlAccent else Color.Transparent,
            contentColor = if (selected) Color.Black else SlText,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                fontSize = 15.sp,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                "($count)",
                fontSize = 13.sp,
                color = if (selected) Color.Black else Color(0xFFCCCCCC),
            )
        }
    }
}

@Composable
private fun ChannelRow(
    item: ChannelItem,
    client: XtreamClient?,
    epgCache: MutableMap<Int, String>,
    onPlay: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { st ->
                if (st.isFocused && client != null && !epgCache.containsKey(item.id)) {
                    scope.launch {
                        try {
                            val (now, next) = withContext(Dispatchers.IO) {
                                client.epgNowNext(item.id)
                            }
                            epgCache[item.id] = buildString {
                                if (now != null) append("Now: ${now.title}")
                                if (next != null) {
                                    if (now != null) append("    •    ")
                                    append("Next: ${next.title}")
                                }
                            }.ifEmpty { "No programme info" }
                        } catch (_: Exception) {
                            epgCache[item.id] = ""
                        }
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = SlText,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(item.logo, Modifier.width(84.dp).height(48.dp), name = item.name)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 17.sp, color = SlText, maxLines = 1)
                if (client != null) {
                    val epg = epgCache[item.id]
                    Text(
                        epg ?: "Loading programme…",
                        fontSize = 13.sp,
                        color = SlTextDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGridCard(
    item: ChannelItem,
    onPlay: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    // 2026-11-04 (Batch 5, Point 14): WHITE 3dp border on ALL 4 sides + scale pop
    // when D-pad focus lands on the list element
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "gridCardScale")
    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White,
        ),
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        // 2026-11-04 (Batch 2): XCIPTV-style tall logo box (was square 60dp).
        // ChannelLogo uses ContentScale.Fit so the FULL logo is always visible
        // (never cropped). Logo on top, title below.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 2026-11-05 (Batch 8, Point 4): SAME poster size as VOD &
                    // Series (2:3) — Live TV icons were too small.
                    .aspectRatio(2f / 3f)
                    .background(Color(0xFF0F1E2E), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(
                    item.logo,
                    Modifier.fillMaxSize(),
                    name = item.name,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.name,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
