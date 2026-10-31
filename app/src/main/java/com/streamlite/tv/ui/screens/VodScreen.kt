package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
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

@Composable
fun VodScreen(
    session: LoginSession,
    onPlay: (url: String, title: String) -> Unit,
) {
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<XtreamCategory>?>(null) }
    var selectedCat by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<VodStream>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadCategory(catId: String) {
        selectedCat = catId
        movies = null
        error = null
        scope.launch {
            try {
                movies = withContext(Dispatchers.IO) {
                    if (catId.isEmpty()) client.vodStreamsAll() else client.vodStreams(catId)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load movies."
            }
        }
    }

    fun reload() {
        error = null
        movies = null
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Movies", style = MaterialTheme.typography.headlineMedium, color = SlText)
        Spacer(Modifier.height(12.dp))

        if (categories == null && error == null) {
            Box(Modifier.height(48.dp)) { LoadingBox() }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                movies == null -> LoadingBox()
                movies!!.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("No movies here.", color = SlTextDim, fontSize = 16.sp) }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(movies!!, key = { it.streamId }) { m ->
                        Surface(
                            onClick = { onPlay(client.vodUrl(m), m.name) },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = SlText,
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                            border = ClickableSurfaceDefaults.border(),
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
}
