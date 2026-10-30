package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.tv.foundation.lazy.grid.GridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ClickableSurface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.Episode
import com.streamlite.tv.data.LoginSession
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

@Composable
fun SeriesScreen(
    session: LoginSession,
    onOpenDetail: (seriesId: Int, title: String) -> Unit,
) {
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<XtreamCategory>?>(null) }
    var selectedCat by remember { mutableStateOf("") }
    var series by remember { mutableStateOf<List<SeriesItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadCategory(catId: String) {
        selectedCat = catId
        series = null
        error = null
        scope.launch {
            try {
                series = withContext(Dispatchers.IO) {
                    if (catId.isEmpty()) client.seriesAll() else client.series(catId)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load series."
            }
        }
    }

    fun reload() {
        error = null
        series = null
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Series", style = MaterialTheme.typography.headlineMedium, color = SlText)
        Spacer(Modifier.height(12.dp))

        if (categories == null && error == null) {
            Box(Modifier.height(48.dp)) { LoadingBox() }
        } else {
            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "all") {
                    CategoryChip("All", selectedCat.isEmpty()) { loadCategory("") }
                }
                items(categories ?: emptyList(), key = { it.id }) { cat ->
                    CategoryChip(cat.name, selectedCat == cat.id) { loadCategory(cat.id) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null -> ErrorBox(error!!) { reload() }
                series == null -> LoadingBox()
                series!!.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("No series here.", color = SlTextDim, fontSize = 16.sp) }
                else -> TvLazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(series!!, key = { it.seriesId }) { s ->
                        ClickableSurface(
                            onClick = { onOpenDetail(s.seriesId, s.name) },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = SlText,
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            border = ClickableSurfaceDefaults.border(),
                        ) {
                            Column {
                                PosterImage(
                                    s.cover,
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                )
                                Text(
                                    s.name,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                if (s.rating.isNotEmpty()) {
                                    Text("★ ${s.rating}", fontSize = 12.sp, color = SlTextDim)
                                }
                            }
                        }
                    }
                }
            }
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
        when {
            error != null -> ErrorBox(error!!) { reload() }
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

                TvLazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(i.seasons.keys.toList(), key = { it }) { s ->
                        CategoryChip("Season $s", selectedSeason == s) { selectedSeason = s }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val episodes: List<Episode> = i.seasons[selectedSeason] ?: emptyList()
                TvLazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        ClickableSurface(
                            onClick = { onPlay(client.episodeUrl(ep), "${title} — ${ep.title}") },
                            modifier = Modifier.fillMaxWidth(),
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
