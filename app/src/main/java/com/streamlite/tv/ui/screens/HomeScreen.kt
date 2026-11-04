package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.ui.Screen
import com.streamlite.tv.ui.components.MenuCard
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlSurface
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim

// 2026-11-04 (Batch 2, TV-box feedback):
// XCIPTV-style home: BIG elements on top (Live TV / Movies / Series /
// 4 Screen), then a SMALL GRID below (Search / Favorites / Account /
// Settings / Log out). Remote-first: D-pad moves across both sections.

@Composable
fun HomeScreen(
    session: LoginSession,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(48.dp),
    ) {
        Text("StreamLite", style = MaterialTheme.typography.displaySmall, color = SlAccent)
        Text(
            "What do you want to watch?",
            fontSize = 17.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )

        // ---- BIG elements on top (XCIPTV-style) ----
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MenuCard(
                title = "Live TV",
                subtitle = "Watch live channels",
                onClick = { onNavigate(Screen.LiveTv) },
                modifier = Modifier.weight(1f).height(200.dp),
            )
            if (session.type == LoginType.XTREAM) {
                MenuCard(
                    title = "Movies",
                    subtitle = "Browse the movie library",
                    onClick = { onNavigate(Screen.Vod) },
                    modifier = Modifier.weight(1f).height(200.dp),
                )
                MenuCard(
                    title = "Series",
                    subtitle = "Browse TV series",
                    onClick = { onNavigate(Screen.Series) },
                    modifier = Modifier.weight(1f).height(200.dp),
                )
            }
            MenuCard(
                title = "4 Screen",
                subtitle = "Watch 4 channels at once",
                onClick = { onNavigate(Screen.MultiView) },
                modifier = Modifier.weight(1f).height(200.dp),
            )
        }

        Spacer(Modifier.height(28.dp))

        // ---- SMALL grid below ----
        val smallItems = listOf(
            Triple("Search", "Find channels & videos", Screen.Search),
            Triple("Favorites", "Your starred items", Screen.Favorites),
            Triple("Account", "Add & switch accounts", Screen.Account),
            Triple("Settings", "App settings", Screen.Settings),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(smallItems) { (title, subtitle, screen) ->
                SmallMenuCard(
                    title = title,
                    subtitle = subtitle,
                    onClick = { onNavigate(screen) },
                )
            }
            item {
                SmallMenuCard(
                    title = "Log out",
                    subtitle = "Switch account",
                    onClick = onLogout,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "StreamLite v1.0 — tuned for slow networks",
            fontSize = 13.sp,
            color = SlTextDim,
        )
    }
}

/** Compact card for the small home grid (Search / Favorites / Account / Settings / Log out). */
@Composable
private fun SmallMenuCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = SlSurface,
            contentColor = SlText,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, fontSize = 17.sp, color = SlText)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = SlTextDim,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
            )
        }
    }
}
