package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.M3uChannel
import com.streamlite.tv.data.M3uParser
import com.streamlite.tv.data.XtreamCategory
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.components.CategoryChip
import com.streamlite.tv.ui.components.ChannelLogo
import com.streamlite.tv.ui.components.ErrorBox
import com.streamlite.tv.ui.components.LoadingBox
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onPlay: (url: String, title: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember(session) {
        if (session.type == LoginType.XTREAM) {
            XtreamClient(session.server, session.username, session.password)
        } else null
    }
    var categories by remember { mutableStateOf<List<XtreamCategory>?>(null) }
    var selectedCat by remember { mutableStateOf("") } // "" = All
    var channels by remember { mutableStateOf<List<ChannelItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val epgCache = remember { mutableStateMapOf<Int, String>() }
    var m3uAll by remember { mutableStateOf<List<M3uChannel>?>(m3uCache.value) }

    fun loadXtreamCategory(catId: String) {
        selectedCat = catId
        channels = null
        error = null
        scope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    if (catId.isEmpty()) client!!.liveStreamsAll() else client!!.liveStreams(catId)
                }
                channels = streams.map { ChannelItem(it.streamId, it.name, it.logo, it.categoryId, "") }
            } catch (e: Exception) {
                error = e.message ?: "Could not load channels."
            }
        }
    }

    fun applyM3uFilter(group: String) {
        selectedCat = group
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
                    val cats = withContext(Dispatchers.IO) { client.liveCategories() }
                    categories = cats
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Live TV", style = MaterialTheme.typography.headlineMedium, color = SlText)
        Spacer(Modifier.height(12.dp))

        if (categories == null && error == null) {
            Box(Modifier.height(48.dp)) { LoadingBox() }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(listOf("all"), key = { it }) {
                    CategoryChip("All", selectedCat.isEmpty()) {
                        if (client != null) loadXtreamCategory("") else applyM3uFilter("")
                    }
                }
                items(categories ?: emptyList(), key = { it.id }) { cat ->
                    CategoryChip(cat.name, selectedCat == cat.id) {
                        if (client != null) loadXtreamCategory(cat.id) else applyM3uFilter(cat.id)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null -> ErrorBox(error!!) { reload() }
                channels == null -> LoadingBox()
                channels!!.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("No channels here.", color = SlTextDim, fontSize = 16.sp) }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(channels!!, key = { it.id to it.directUrl }) { item ->
                        ChannelRow(
                            item = item,
                            client = client,
                            epgCache = epgCache,
                            onPlay = {
                                val url = item.directUrl.ifEmpty { client!!.liveHlsUrl(item.id) }
                                onPlay(url, item.name)
                            },
                        )
                    }
                }
            }
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
    Surface(
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
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = SlText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        border = ClickableSurfaceDefaults.border(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(item.logo, Modifier.width(84.dp).height(48.dp))
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
