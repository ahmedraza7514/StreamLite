package com.streamlite.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.BufferPrefStore
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.FavItem
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.M3uChannel
import com.streamlite.tv.data.RecentItem
import com.streamlite.tv.data.RecentViewsStore
import com.streamlite.tv.data.SessionManager
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.PlayerItem
import com.streamlite.tv.ui.Screen
import com.streamlite.tv.ui.screens.AccountScreen
import com.streamlite.tv.ui.screens.FavoritesScreen
import com.streamlite.tv.ui.screens.HomeScreen
import com.streamlite.tv.ui.screens.LiveTvScreen
import com.streamlite.tv.ui.screens.LoginScreen
import com.streamlite.tv.ui.screens.MultiViewScreen
import com.streamlite.tv.ui.screens.PlayerScreen
import com.streamlite.tv.ui.screens.SearchScreen
import com.streamlite.tv.ui.screens.SeriesDetailScreen
import com.streamlite.tv.ui.screens.SeriesScreen
import com.streamlite.tv.ui.screens.SettingsScreen
import com.streamlite.tv.ui.screens.SyncScreen
import com.streamlite.tv.ui.screens.VodScreen
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.data.CrashReporter
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.StreamLiteTheme
import com.streamlite.tv.ui.theme.SlText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        setContent {
            StreamLiteTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var crashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashReport = withContext(Dispatchers.IO) { CrashReporter.takeReport(context) }
    }
    if (crashReport != null) {
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { androidx.compose.material3.Text("Crash report", color = SlText) },
            text = {
                androidx.compose.material3.Text(
                    crashReport!!,
                    color = SlText,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { crashReport = null }) {
                androidx.compose.material3.Text("Close", color = SlText)
            } },
        )
    }
    val sessionManager = remember { SessionManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val navStack = remember { mutableStateListOf<Screen>() }
    var session by remember { mutableStateOf<LoginSession?>(null) }
    var booted by remember { mutableStateOf(false) }
    val m3uCache: MutableState<List<M3uChannel>?> = remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        // Load the player-buffer preference before any player is created
        BufferPrefStore.load(context.applicationContext)
        session = sessionManager.getOnce()
        navStack.add(if (session != null) Screen.Home else Screen.Login)
        booted = true
    }

    BackHandler(enabled = booted && navStack.size > 1) {
        navStack.removeLast()
    }

    if (!booted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text("StreamLite", style = MaterialTheme.typography.displaySmall, color = SlAccent)
        }
        return
    }

    fun goHome(s: LoginSession) {
        session = s
        navStack.clear()
        // XCIPTV-style: bulk sync all content before showing Home
        navStack.add(Screen.Sync())
    }

    fun goHomeAfterSync() {
        navStack.clear()
        navStack.add(Screen.Home)
    }

    /** Switch to another account: wipe synced data, re-sync from scratch. */
    fun switchAccount(s: LoginSession) {
        scope.launch {
            sessionManager.setActive(
                sessionManager.getAccounts().indexOfFirst {
                    it.label() == s.label()
                }.coerceAtLeast(0),
            )
            DataRepository.clear()
            m3uCache.value = null
            session = s
            navStack.clear()
            navStack.add(Screen.Sync())
        }
    }

    /** Sign out removes the ACTIVE account; falls back to Account/Login. */
    fun logout() {
        scope.launch {
            val remaining = sessionManager.removeActive()
            DataRepository.clear()
            m3uCache.value = null
            session = null
            navStack.clear()
            if (remaining.isNotEmpty()) {
                session = remaining.first()
                navStack.add(Screen.Account)
            } else {
                navStack.add(Screen.Login)
            }
        }
    }

    fun play(
        url: String,
        title: String,
        playlist: List<PlayerItem> = emptyList(),
        startIndex: Int = 0,
        info: String = "",
    ) {
        // Record in recent views (kind guessed from info/url)
        val kind = when {
            info.contains("★") || url.contains("/movie/") -> "vod"
            title.contains("—") -> "episode"
            else -> "live"
        }
        RecentViewsStore.add(RecentItem(url, title, kind, info))
        navStack.add(Screen.Player(url, title, playlist, startIndex, info, isLive = kind == "live", kind = kind))
    }

    /** Play a favorite: rebuild the live queue when possible so prev/next works. */
    fun playFav(item: FavItem) {
        val s = session
        if (item.kind == "live" && s != null && s.type == LoginType.XTREAM) {
            val streams = DataRepository.liveStreams
            val streamId = """/(\d+)\.(m3u8|ts)""".toRegex()
                .find(item.url)?.groupValues?.get(1)?.toIntOrNull()
            val idx = streams?.indexOfFirst { it.streamId == streamId } ?: -1
            if (streams != null && idx >= 0) {
                val client = XtreamClient(s.server, s.username, s.password)
                val queue = streams.map { PlayerItem(client.liveHlsUrl(it.streamId), it.name) }
                play(item.url, item.title, queue, idx)
                return
            }
        }
        play(item.url, item.title)
    }

    when (val s = navStack.last()) {
        is Screen.Login -> LoginScreen(sessionManager, ::goHome)
        is Screen.AddAccount -> LoginScreen(
            sessionManager,
            onLoggedIn = { navStack.removeLast() }, // back to Account screen
            addMode = true,
        )
        is Screen.Home -> HomeScreen(session!!, navStack::add, ::logout)
        is Screen.Sync -> SyncScreen(
            session!!,
            onSyncComplete = {
                if (s.popAfter) navStack.removeLast() else goHomeAfterSync()
            },
        )
        is Screen.LiveTv -> LiveTvScreen(session!!, m3uCache, ::play)
        is Screen.Vod -> VodScreen(session!!, { url, title, info -> play(url, title, info = info) })
        is Screen.Series -> SeriesScreen(session!!, { id, title ->
            navStack.add(Screen.SeriesDetail(id, title))
        }, { url, title -> play(url, title) })
        is Screen.SeriesDetail -> SeriesDetailScreen(session!!, s.seriesId, s.title, { url, title -> play(url, title) })
        is Screen.Player -> PlayerScreen(s.url, s.title, s.playlist, s.startIndex, s.info, isLive = s.isLive, kind = s.kind)
        is Screen.MultiView -> MultiViewScreen(session!!)
        is Screen.Search -> SearchScreen(
            session = session!!,
            m3uChannels = m3uCache.value,
            onPlayLive = { url, title, queue, idx -> play(url, title, queue, idx) },
            onPlayMovie = { url, title, info -> play(url, title, info = info) },
            onOpenDetail = { id, title -> navStack.add(Screen.SeriesDetail(id, title)) },
        )
        is Screen.Favorites -> FavoritesScreen(::playFav)
        is Screen.Account -> AccountScreen(
            sessionManager = sessionManager,
            active = session,
            onSwitch = ::switchAccount,
            onAdd = { navStack.add(Screen.AddAccount) },
            onBack = { navStack.removeLast() },
        )
        is Screen.Settings -> SettingsScreen(
            onAccount = { navStack.add(Screen.Account) },
            onUpdateContents = { navStack.add(Screen.Sync(popAfter = true)) },
            onSignOut = ::logout,
        )
    }
}
