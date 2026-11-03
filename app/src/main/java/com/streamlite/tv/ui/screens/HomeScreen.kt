package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.ui.Screen
import com.streamlite.tv.ui.components.MenuCard
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlTextDim

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
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(1) {
                MenuCard(
                    title = "Live TV",
                    subtitle = "Watch live channels",
                    onClick = { onNavigate(Screen.LiveTv) },
                    modifier = Modifier.width(330.dp).height(180.dp),
                )
            }
            if (session.type == LoginType.XTREAM) {
                items(1) {
                    MenuCard(
                        title = "Movies",
                        subtitle = "Browse the movie library",
                        onClick = { onNavigate(Screen.Vod) },
                        modifier = Modifier.width(330.dp).height(180.dp),
                    )
                }
                items(1) {
                    MenuCard(
                        title = "Series",
                        subtitle = "Browse TV series",
                        onClick = { onNavigate(Screen.Series) },
                        modifier = Modifier.width(330.dp).height(180.dp),
                    )
                }
            }
            items(1) {
                MenuCard(
                    title = "4 Screen",
                    subtitle = "Watch 4 channels at once",
                    onClick = { onNavigate(Screen.MultiView) },
                    modifier = Modifier.width(330.dp).height(180.dp),
                )
            }
            items(1) {
                MenuCard(
                    title = "Search",
                    subtitle = "Search live TV, movies & series",
                    onClick = { onNavigate(Screen.Search) },
                    modifier = Modifier.width(330.dp).height(180.dp),
                )
            }
            items(1) {
                MenuCard(
                    title = "Favorites",
                    subtitle = "Your starred channels & videos",
                    onClick = { onNavigate(Screen.Favorites) },
                    modifier = Modifier.width(330.dp).height(180.dp),
                )
            }
            items(1) {
                MenuCard(
                    title = "Account",
                    subtitle = "Add & switch accounts",
                    onClick = { onNavigate(Screen.Account) },
                    modifier = Modifier.width(330.dp).height(180.dp),
                )
            }
            items(1) {
                MenuCard(
                    title = "Settings",
                    subtitle = "App settings",
                    onClick = { onNavigate(Screen.Settings) },
                    modifier = Modifier.width(330.dp).height(180.dp),
                )
            }
            items(1) {
                MenuCard(
                    title = "Log out",
                    subtitle = "Switch account",
                    onClick = onLogout,
                    modifier = Modifier.width(330.dp).height(180.dp),
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
