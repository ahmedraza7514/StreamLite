package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
    // XCIPTV-style prefetch: all VOD once, category clicks filter locally
    var allMovies by remember { mutableStateOf<List<VodStream>?>(null) }

    fun loadCategory(catId: String) {
        selectedCat = catId
        error = null
        // Local filter over prefetched VOD — instant, zero network (XCIPTV model)
        movies = allMovies?.filter { catId.isEmpty() || it.categoryId == catId }
    }

    fun reload() {
        error = null
        movies = null
        scope.launch {
            try {
                categories = withContext(Dispatchers.IO) { client.vodCategories() }
                allMovies = withContext(Dispatchers.IO) { client.vodStreamsAll() }
                loadCategory(selectedCat)
            } catch (e: Exception) {
                error = e.message ?: "Could not load movies."
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Category counts for sidebar
    val catCounts = remember(allMovies, categories) {
        val map = mutableMapOf<String, Int>()
        allMovies?.forEach { m -> map[m.categoryId] = (map[m.categoryId] ?: 0) + 1 }
        map
    }

    Row(modifier = Modifier.fillMaxSize().background(SlBackground)) {
        // LEFT SIDEBAR
        Column(
            modifier = Modifier.width(280.dp).fillMaxSize()
                .background(Color(0xFF0F1E2E)).padding(vertical = 24.dp),
        ) {
            Text("Movies", style = MaterialTheme.typography.headlineSmall, color = SlText,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))
            if (categories == null && error == null) {
                Box(Modifier.padding(horizontal = 20.dp)) { LoadingBox() }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item(key = "all") {
                        SidebarRow("All Movies", allMovies?.size ?: 0, selectedCat.isEmpty()) { loadCategory("") }
                    }
                    items(categories ?: emptyList(), key = { it.id }) { cat ->
                        SidebarRow(cat.name, catCounts[cat.id] ?: 0, selectedCat == cat.id) { loadCategory(cat.id) }
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
                    if (searchQuery.isEmpty()) Text("Search movies...", color = SlTextDim, fontSize = 14.sp)
                    inner() } },
            )
            Card(onClick = { sortAsc = !sortAsc }, shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E2E), contentColor = SlText)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(if (sortAsc) "A-Z ↓" else "Z-A ↑", fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))

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
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(mv, key = { it.streamId }) { m ->
                        Card(
                            onClick = {
                                val info = buildString {
                                    if (m.rating.isNotEmpty()) append("RATING: ${m.rating}")
                                }
                                onPlay(client.vodUrl(m), m.name, info)
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
            contentColor = if (selected) Color.Black else SlText,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("($count)", fontSize = 13.sp, color = if (selected) Color.Black else SlTextDim)
        }
    }
}
