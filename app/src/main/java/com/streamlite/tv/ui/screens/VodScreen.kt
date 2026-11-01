package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.streamlite.tv.ui.theme.SlAccent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.RecentViewsStore
import com.streamlite.tv.data.ScrollStateStore
import com.streamlite.tv.data.VodStream
import com.streamlite.tv.data.XtreamCategory
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.components.CategoryChip
import com.streamlite.tv.ui.components.ErrorBox
import com.streamlite.tv.ui.components.LoadingBox
import com.streamlite.tv.ui.components.PosterImage
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

@Composable
fun VodScreen(
    session: LoginSession,
    onPlay: (url: String, title: String, info: String) -> Unit,
) {
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<XtreamCategory>?>(null) }
    var selectedCat by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<VodStream>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }

    fun loadCategory(catId: String) {
        selectedCat = catId
        error = null
        movies = null // show loading
        scope.launch {
            try {
                // Per-category load — avoids OOM from bulk-loading 10k+ movies on TV boxes
                movies = withContext(Dispatchers.IO) {
                    if (catId.isEmpty()) client.vodStreamsAll()
                    else client.vodStreams(catId)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load movies."
            }
        }
    }

    fun reload() {
        error = null
        scope.launch {
            try {
                categories = withContext(Dispatchers.IO) { client.vodCategories() }
                loadCategory(selectedCat)
            } catch (e: Exception) {
                error = e.message ?: "Could not load movies."
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Category counts hidden (would require bulk load) — names only for TV-box safety

    Row(modifier = Modifier.fillMaxSize().background(SlBackground)) {
        // LEFT SIDEBAR
        Column(
            modifier = Modifier.width(280.dp).fillMaxSize()
                .background(Color.Black).padding(vertical = 24.dp),
        ) {
            Text("Movies", style = MaterialTheme.typography.headlineSmall, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))
            if (categories == null && error == null) {
                Box(Modifier.padding(horizontal = 20.dp)) { LoadingBox() }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item(key = "all") {
                        SidebarRow("All Movies", 0, selectedCat.isEmpty()) { loadCategory("") }
                    }
                    items(categories ?: emptyList(), key = { it.id }) { cat ->
                        SidebarRow(cat.name, 0, selectedCat == cat.id) { loadCategory(cat.id) }
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
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp), singleLine = true,
                decorationBox = { inner -> Box {
                    if (searchQuery.isEmpty()) Text("Search movies...", color = Color(0xFF999999), fontSize = 14.sp)
                    inner() } },
            )
            Card(onClick = { sortAsc = !sortAsc }, shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E), contentColor = SlText)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(if (sortAsc) "A-Z ↓" else "Z-A ↑", fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Recent Views
        val recent = RecentViewsStore.items.filter { it.kind == "vod" }
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
                        onClick = { onPlay(r.url, r.title, r.info) },
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

        val mv = movies?.let { list ->
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
                mv == null -> LoadingBox()
                mv.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("No movies here.", color = SlTextDim, fontSize = 16.sp) }
                else -> LazyVerticalGrid(
                    state = ScrollStateStore.vodGrid,
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(mv, key = { it.streamId }) { m ->
                        Card(
                            onClick = {
                                // Fetch full movie details (genre/date/plot) then play
                                scope.launch {
                                    val fullInfo = try {
                                        val vi = withContext(Dispatchers.IO) { client.vodInfo(m.streamId) }
                                        buildString {
                                            if (vi.rating.isNotEmpty()) append("★ ${vi.rating}  ")
                                            if (vi.genre.isNotEmpty()) append("${vi.genre}  ")
                                            if (vi.releaseDate.isNotEmpty()) append("${vi.releaseDate}")
                                            val head = toString().trim()
                                            clear()
                                            if (head.isNotEmpty()) append(head)
                                            if (vi.plot.isNotEmpty()) {
                                                if (isNotEmpty()) append("\n")
                                                append(vi.plot.take(180))
                                            }
                                        }.ifEmpty {
                                            if (m.rating.isNotEmpty()) "★ ${m.rating}" else ""
                                        }
                                    } catch (e: Exception) {
                                        if (m.rating.isNotEmpty()) "★ ${m.rating}" else ""
                                    }
                                    onPlay(client.vodUrl(m), m.name, fullInfo)
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent,
                                contentColor = SlText,
                            ),
                        ) {
                            Column {
                                PosterImage(
                                    m.logo,
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                )
                                Text(
                                    m.name,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                if (m.rating.isNotEmpty()) {
                                    Text("★ ${m.rating}", fontSize = 12.sp, color = SlTextDim)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    } // Row
}

@Composable
private fun SidebarRow(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
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
            // Count hidden — per-category loading doesn't have totals (TV-box memory safety)
        }
    }
}
