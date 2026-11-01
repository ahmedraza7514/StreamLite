package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.Episode
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.RecentViewsStore
import com.streamlite.tv.data.ScrollStateStore
import com.streamlite.tv.data.SeriesInfo
import com.streamlite.tv.data.SeriesItem
import com.streamlite.tv.data.XtreamCategory
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.components.CategoryChip
import com.streamlite.tv.ui.components.ErrorBox
import com.streamlite.tv.ui.components.LoadingBox
import com.streamlite.tv.ui.components.PosterImage
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

@Composable
fun SeriesScreen(
    session: LoginSession,
    onOpenDetail: (seriesId: Int, title: String) -> Unit,
    onPlayEpisode: (url: String, title: String) -> Unit = { _, _ -> },
) {
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<XtreamCategory>?>(null) }
    var selectedCat by remember { mutableStateOf("") }
    var series by remember { mutableStateOf<List<SeriesItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }

    fun loadCategory(catId: String) {
        selectedCat = catId
        error = null
        series = null // show loading
        scope.launch {
            try {
                // Per-category load — avoids OOM from bulk-loading on TV boxes
                series = withContext(Dispatchers.IO) {
                    if (catId.isEmpty()) client.seriesAll()
                    else client.series(catId)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load series."
            }
        }
    }

    fun reload() {
        error = null
        scope.launch {
            try {
                categories = withContext(Dispatchers.IO) { client.seriesCategories() }
                loadCategory(selectedCat)
            } catch (e: Exception) {
                error = e.message ?: "Could not load series."
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Row(modifier = Modifier.fillMaxSize().background(SlBackground)) {
        // LEFT SIDEBAR — pure black background
        Column(
            modifier = Modifier.width(280.dp).fillMaxSize()
                .background(Color.Black).padding(vertical = 24.dp),
        ) {
            Text("Series", style = MaterialTheme.typography.headlineSmall, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))
            if (categories == null && error == null) {
                Box(Modifier.padding(horizontal = 20.dp)) { LoadingBox() }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item(key = "all") {
                        SeriesSidebarRow("All Series", 0, selectedCat.isEmpty()) { loadCategory("") }
                    }
                    items(categories ?: emptyList(), key = { it.id }) { cat ->
                        SeriesSidebarRow(cat.name, 0, selectedCat == cat.id) { loadCategory(cat.id) }
                    }
                }
            }
        }
        // RIGHT: content
        Column(modifier = Modifier.weight(1f).fillMaxSize().padding(24.dp)) {
        // Search + Sort
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f)
                    .background(Color(0xFF0F1E2E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(color = SlText, fontSize = 15.sp), singleLine = true,
                decorationBox = { inner -> Box {
                    if (searchQuery.isEmpty()) Text("Search series...", color = SlTextDim, fontSize = 14.sp)
                    inner() } },
            )
            Card(onClick = { sortAsc = !sortAsc }, shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E), contentColor = SlText)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(if (sortAsc) "A-Z ↓" else "Z-A ↑", fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Recent Views (episodes)
        val recent = RecentViewsStore.items.filter { it.kind == "episode" }
        if (recent.isNotEmpty()) {
            Text(
                "Recent Views",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                items(recent, key = { it.url }) { r ->
                    Card(
                        onClick = { onPlayEpisode(r.url, r.title) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E3A5F),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            r.title,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                .widthIn(max = 200.dp),
                        )
                    }
                }
            }
        }

        val sr = series?.let { list ->
            var result = if (searchQuery.isBlank()) list
                else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
            result = if (sortAsc) result.sortedBy { it.name.lowercase() }
                else result.sortedByDescending { it.name.lowercase() }
            result
        }
        val err = error
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                err != null -> ErrorBox(err) { reload() }
                sr == null -> LoadingBox()
                sr.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("No series here.", color = SlTextDim, fontSize = 16.sp) }
                else -> LazyVerticalGrid(
                    state = ScrollStateStore.seriesGrid,
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(sr, key = { it.seriesId }) { s ->
                        Card(
                            onClick = { onOpenDetail(s.seriesId, s.name) },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent,
                                contentColor = SlText,
                            ),
                        ) {
                            Column {
                                PosterImage(
                                    s.cover,
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                )
                                Text(
                                    s.name,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                if (s.rating.isNotEmpty()) {
                                    Text("★ ${s.rating}", fontSize = 12.sp, color = Color(0xFFCCCCCC))
                                }
                            }
                        }
                    }
                }
            }
        }
        } // Right column
    } // Row
}

@Composable
private fun SeriesSidebarRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SlAccent else Color.Transparent,
            contentColor = if (selected) Color.Black else Color.White,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text("($count)", fontSize = 13.sp, color = if (selected) Color.Black else Color(0xFFCCCCCC))
        }
    }
}

@Composable
fun SeriesDetailScreen(
    session: LoginSession,
    seriesId: Int,
    title: String,
    onPlay: (url: String, title: String) -> Unit,
) {
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }

    fun reload() {
        error = null
        info = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { client.seriesInfo(seriesId) }
                info = loaded
                selectedSeason = loaded.seasons.keys.firstOrNull()
            } catch (e: Exception) {
                error = e.message ?: "Could not load this series."
            }
        }
    }

    LaunchedEffect(seriesId) { reload() }

    val i = info
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        val err = error
        when {
            err != null -> ErrorBox(err) { reload() }
            i == null -> LoadingBox()
            else -> {
                Row {
                    PosterImage(i.cover, Modifier.width(150.dp).aspectRatio(2f / 3f))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, color = SlText)
                        if (i.rating.isNotEmpty()) {
                            Text(
                                "★ ${i.rating}",
                                fontSize = 15.sp,
                                color = SlAccent,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (i.plot.isNotEmpty()) {
                            Text(
                                i.plot,
                                fontSize = 14.sp,
                                color = SlTextDim,
                                maxLines = 6,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(i.seasons.keys.toList(), key = { it }) { s ->
                        CategoryChip("Season $s", selectedSeason == s) { selectedSeason = s }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val episodes: List<Episode> = i.seasons[selectedSeason] ?: emptyList()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        Card(
                            onClick = { onPlay(client.episodeUrl(ep), "${title} — ${ep.title}") },
                            modifier = Modifier.fillMaxWidth(),
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
                                Text(
                                    "E${ep.episodeNum}",
                                    color = SlAccent,
                                    fontSize = 16.sp,
                                    modifier = Modifier.width(56.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(ep.title, fontSize = 16.sp, color = SlText, maxLines = 1)
                                    if (ep.plot.isNotEmpty()) {
                                        Text(
                                            ep.plot,
                                            fontSize = 13.sp,
                                            color = SlTextDim,
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
