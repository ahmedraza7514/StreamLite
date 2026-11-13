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
    // 2026-11-13 (v2.15.0): nav stack SPLASH se shuru hota hai —
    // app khulte hi pehle frame par splash, koi khaali gehri
    // screen nahi (startup tez karne ke liye).
    val navStack = remember { mutableStateListOf<Screen>(Screen.Splash) }
    var session by remember { mutableStateOf<LoginSession?>(null) }
    var booted by remember { mutableStateOf(true) }
    // 2026-11-13 (v2.15.0): saved session ka load splash ke PECHHE
    // chalta hai — bootReady = load ho gaya; splashDone = splash
    // video khatam. Agli screen tab khulti hai jab DONO sach hon,
    // is liye splash ko session load ka intezar nahi karna parta.
    var bootReady by remember { mutableStateOf(false) }
    var splashDone by remember { mutableStateOf(false) }
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
        bootReady = true
    }

    // Splash khatam + session load: jo pehle ho jaye, wo doosre ka
    // intezar karta hai; dono hon to Sync (logged in) ya Login.
    LaunchedEffect(splashDone, bootReady) {
        if (splashDone && bootReady) {
            navStack.clear()
            navStack.add(if (session != null) Screen.Sync() else Screen.Login)
        }
    }

    // 2026-11-06 (v2.12): ONE guarded back handler owns EVERY back press.
    // Before, back was handled in 4 places (this global handler + a
    // BackHandler in PlayerScreen, MultiViewScreen and AccountScreen), each
    // calling an UNGUARDED removeAt(lastIndex) — if the stack ever emptied,
    // navStack.last() crashed and the app vanished, which looks exactly
    // like "one back press closes the app". The double-back timer also
    // leaked across navigation (back on Home, then navigate, then back
    // again within 2s exited the app). Now: pop only when a previous
    // screen exists; on Home a single back only shows a hint and the app
    // exits only on a SECOND back within 2 seconds; the timer resets the
    // moment the user navigates to another screen.
    var lastBackPress by remember { mutableStateOf(0L) }
    fun popOrExit() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.lastIndex)
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
    // The double-back timer must not survive navigation.
    LaunchedEffect(navStack.size) {
        if (navStack.size > 1) lastBackPress = 0L
    }
    BackHandler(enabled = booted) { popOrExit() }

    // 2026-11-13 (v2.15.0): purana "booted" wala darwaza hata diya —
    // pehle saved session load hone tak sirf gehri khaali screen
    // rehti thi aur splash der se shuru hota tha, is liye app sust
    // lagti thi. Ab splash pehle frame se chalta hai; session load
    // us ke peeche hota hai (uper bootReady/splashDone ki jodi).

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

    // Safety net: the stack must never be empty — a crash here looks like
    // the app closing on its own. If it ever empties, go back to Home.
    if (navStack.isEmpty()) {
        LaunchedEffect(Unit) { navStack.add(Screen.Home) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlBackground),
        )
        return
    }

    when (val s = navStack.last()) {
        is Screen.Splash -> SplashScreen(
            onFinished = { splashDone = true },
        )
        is Screen.Login -> LoginScreen(sessionManager, ::goHome)
        is Screen.AddAccount -> LoginScreen(
            sessionManager,
            onLoggedIn = { navStack.removeAt(navStack.lastIndex) }, // back to Account screen
            addMode = true,
        )
        is Screen.Home -> HomeScreen(session!!, navStack::add, ::logout)
        is Screen.Sync -> SyncScreen(
            session!!,
            onSyncComplete = {
                // Guarded pop: never empty the stack (an empty stack used
                // to crash the app on back-press).
                if (s.popAfter) {
                    if (navStack.size > 1) navStack.removeAt(navStack.lastIndex)
                } else goHomeAfterSync()
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
        )
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
        )
        is Screen.Settings -> SettingsScreen(
            onAccount = { navStack.add(Screen.Account) },
            onUpdateContents = { navStack.add(Screen.Sync(popAfter = true)) },
            onSignOut = ::logout,
        )
    }
}
