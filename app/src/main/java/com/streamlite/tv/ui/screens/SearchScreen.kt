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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.M3uChannel
import com.streamlite.tv.data.SeriesItem
import com.streamlite.tv.data.VodStream
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.PlayerItem
import com.streamlite.tv.ui.components.PosterImage
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Overall search across Live TV + Movies + Series (Ahmed Bhai's ask).
 *
 * - Live TV results come from the in-memory lists (instant, no network).
 * - Movies / Series load their full lists on demand when you search
 *   (same cost as opening "All Movies" / "All Series" once).
 * Fully remote-friendly: every result is a focusable card.
 */
@Composable
fun SearchScreen(
    session: LoginSession,
    m3uChannels: List<M3uChannel>?,
    onPlayLive: (url: String, title: String, queue: List<PlayerItem>, index: Int) -> Unit,
    onPlayMovie: (url: String, title: String, info: String) -> Unit,
    onOpenDetail: (seriesId: Int, title: String) -> Unit,
) {
    val client = remember {
        if (session.type == LoginType.XTREAM) {
            XtreamClient(session.server, session.username, session.password)
        } else null
    }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loadingLists by remember { mutableStateOf(false) }
    var movies by remember { mutableStateOf<List<VodStream>?>(null) }
    var series by remember { mutableStateOf<List<SeriesItem>?>(null) }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        submitted = q
        if (client == null) return // M3U: live-only search, lists stay null
        loadingLists = true
        scope.launch {
            try {
                val mv = withContext(Dispatchers.IO) { client.vodStreamsAll() }
                val sr = withContext(Dispatchers.IO) { client.seriesAll() }
                movies = mv
                series = sr
            } catch (_: Exception) {
                // keep previous results; sections just stay empty
            } finally {
                loadingLists = false
            }
        }
    }

    // Live results (in-memory — instant)
    val liveResults: List<Pair<String, String>> = remember(submitted, m3uChannels) {
        if (submitted.isEmpty()) emptyList()
        else if (client != null) {
            (DataRepository.liveStreams ?: emptyList())
                .filter { it.name.contains(submitted, ignoreCase = true) }
                .map { it.name to client.liveHlsUrl(it.streamId) }
        } else {
            (m3uChannels ?: emptyList())
                .filter { it.name.contains(submitted, ignoreCase = true) }
                .map { it.name to it.url }
        }
    }
    val movieResults = remember(submitted, movies) {
        if (submitted.isEmpty()) emptyList()
        else (movies ?: emptyList()).filter { it.name.contains(submitted, ignoreCase = true) }
    }
    val seriesResults = remember(submitted, series) {
        if (submitted.isEmpty()) emptyList()
        else (series ?: emptyList()).filter { it.name.contains(submitted, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Search", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Search live TV, movies and series together",
            fontSize = 14.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TvTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Type to search...",
                    imeAction = ImeAction.Search,
                    onImeAction = { doSearch() },
                )
            }
            Card(
                onClick = { doSearch() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SlAccent,
                    contentColor = Color.Black,
                ),
            ) {
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text("Search", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loadingLists) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = SlAccent,
                    modifier = Modifier.width(22.dp).height(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("Loading movies & series...", fontSize = 14.sp, color = SlTextDim)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (submitted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Type a name above and press Search.",
                    fontSize = 15.sp,
                    color = SlTextDim,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ---- Live TV ----
            item(key = "h-live") {
                SectionHeader("Live TV (${liveResults.size})")
            }
            items(liveResults, key = { "live-${it.second}" }) { (name, url) ->
                Card(
                    onClick = {
                        val queue = liveResults.map { (n, u) -> PlayerItem(u, n) }
                        val idx = queue.indexOfFirst { it.url == url }.coerceAtLeast(0)
                        onPlayLive(url, name, queue, idx)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1E2E),
                        contentColor = Color.White,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Text("LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlAccent)
                    }
                }
            }

            // ---- Movies ----
            if (client != null) {
                item(key = "h-movies") {
                    SectionHeader("Movies (${movieResults.size})")
                }
                items(movieResults, key = { "vod-${it.streamId}" }) { m ->
                    Card(
                        onClick = {
                            val info = if (m.rating.isNotEmpty()) "★ ${m.rating}" else ""
                            onPlayMovie(client.vodUrl(m), m.name, info)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F1E2E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PosterImage(m.logo, Modifier.width(44.dp).height(66.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.name, fontSize = 15.sp, color = Color.White, maxLines = 1)
                                if (m.rating.isNotEmpty()) {
                                    Text("★ ${m.rating}", fontSize = 12.sp, color = SlTextDim)
                                }
                            }
                            Text("MOVIE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlAccent)
                        }
                    }
                }

                // ---- Series ----
                item(key = "h-series") {
                    SectionHeader("Series (${seriesResults.size})")
                }
                items(seriesResults, key = { "sr-${it.seriesId}" }) { s ->
                    Card(
                        onClick = { onOpenDetail(s.seriesId, s.name) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F1E2E),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PosterImage(s.cover, Modifier.width(44.dp).height(66.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(s.name, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("SERIES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlAccent)
                        }
                    }
                }
            }

            if (liveResults.isEmpty() && movieResults.isEmpty() && seriesResults.isEmpty() && !loadingLists) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Nothing found for \"$submitted\".", fontSize = 15.sp, color = SlTextDim)
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}
