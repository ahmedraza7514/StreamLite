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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamlite.tv.data.FavItem
import com.streamlite.tv.data.FavStore
import com.streamlite.tv.ui.components.ChannelLogo
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.launch

/**
 * Favorites screen. Items are starred from the player bar's star button.
 * Tap a row to play it; Remove deletes it. Fully remote-friendly.
 */
@Composable
fun FavoritesScreen(
    onPlayFav: (FavItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favs by FavStore.flow(context).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Favorites", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "${favs.size} saved — star anything from the player to add it here",
            fontSize = 14.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (favs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No favorites yet.\nPlay something and tap the ★ button in the player bar.",
                    fontSize = 15.sp,
                    color = SlTextDim,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(favs, key = { it.url }) { f ->
                Card(
                    onClick = { onPlayFav(f) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1E2E),
                        contentColor = Color.White,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (f.logo.isNotEmpty()) {
                            ChannelLogo(f.logo, Modifier.width(72.dp).height(44.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(44.dp)
                                    .background(Color(0xFF1E3A5F), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    f.title.firstOrNull()?.uppercase() ?: "•",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlAccent,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(f.title, fontSize = 16.sp, color = Color.White, maxLines = 1)
                            Text(
                                when (f.kind) {
                                    "vod" -> "Movie"
                                    "episode" -> "Episode"
                                    else -> "Live channel"
                                },
                                fontSize = 12.sp,
                                color = SlTextDim,
                            )
                        }
                        Card(
                            onClick = { scope.launch { FavStore.remove(context, f.url) } },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF3A1A1A),
                                contentColor = Color(0xFFFF8A8A),
                            ),
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(16.dp)) }
        }
    }
}
