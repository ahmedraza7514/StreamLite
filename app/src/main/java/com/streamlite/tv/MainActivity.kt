package com.streamlite.tv

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
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
import com.streamlite.tv.ui.screens.SplashScreen
import com.streamlite.tv.ui.screens.SyncScreen
import com.streamlite.tv.ui.screens.VodScreen
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.data.CrashReporter
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlSurface
import com.streamlite.tv.ui.theme.StreamLiteTheme
import com.streamlite.tv.ui.theme.SlText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        // 2026-11-05 (Batch 6, Point 16): the old dialog used the default LIGHT
        // container with WHITE text — a white box with unreadable white text
        // on the TV box. Dark container + white text = readable.
        AlertDialog(
            onDismissRequest = { crashReport = null },
            containerColor = SlSurface,
            titleContentColor = SlText,
            textContentColor = SlText,
            title = { androidx.compose.material3.Text("Crash report", color = SlText) },
            text = {
                androidx.compose.material3.Text(
                    crashReport!!,
                    color = SlText,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { crashReport = null }) {
                androidx.compose.material3.Text("Close", color = SlAccent)
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
        // 2026-11-04 (Batch 2): load the player-buffer preference and the
        // saved session IN PARALLEL (was sequential) — cuts startup time on
        // the TV box. Both are IO-bound DataStore reads.
        coroutineScope {
            val bufferJob = async(Dispatchers.IO) {
                BufferPrefStore.load(context.applicationContext)
            }
            val sessionJob = async(Dispatchers.IO) {
                sessionManager.getOnce()
            }
            bufferJob.await()
            session = sessionJob.await()
        }
        navStack.add(Screen.Splash)
        booted = true
    }

    // 2026-11-04 (Batch 2): Back NEVER kills the app while screens remain —
    // it pops to the previous screen (channel list, home, ...). The player
    // crash on back-press (white dialog) is fixed in PlayerScreen via
    // guarded release()/ticker; this handler just pops the stack.
    // 2026-11-05 (Batch 6, Point 16): on the TV box one back press was
    // closing the WHOLE app. Now the handler stays registered whenever the
    // app is booted, and on Home a single back only shows a hint — the app
    // exits only on a SECOND back press within 2 seconds (XCIPTV-style).
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler(enabled = booted) {
        if (navStack.size > 1) {
            navStack.removeLast()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPress = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
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
        // 2026-11-04 (Batch 3, Point 3): RecentViewsStore.add removed — "Recent Played" UI deleted.
        val kind = when {
            info.contains("★") || url.contains("/movie/") -> "vod"
            title.contains("—") -> "episode"
            else -> "live"
        }
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
        is Screen.Splash -> SplashScreen(
            onFinished = {
                // Splash done: go to Sync (update media contents) if logged in, else Login.
                navStack.clear()
                navStack.add(if (session != null) Screen.Sync() else Screen.Login)
            },
        )
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
        is Screen.Player -> PlayerScreen(
            s.url, s.title, s.playlist, s.startIndex, s.info,
            isLive = s.isLive, kind = s.kind,
            // 2026-11-04 (Batch 3, Point 2): back from player pops to previous screen.
            onBack = { navStack.removeLast() },
        )
        is Screen.MultiView -> MultiViewScreen(
            session!!,
            // 2026-11-04 (Batch 4, Point 9): back from 4-screen returns to last
            // screen/category — never closes the whole app.
            onBack = { navStack.removeLast() },
        )
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
